#!/usr/bin/env bash
#
# 전체 검증 파이프라인 (macOS / Windows Git Bash 겸용).
#
# 환경 점검부터 실기기 녹화까지 한 번에 돌리고, 단계별 결과를 표로 보고한다.
# 개별 스크립트를 손으로 이어붙이면 순서를 빠뜨리거나 실패를 흘려보내기 쉬우므로
# 이 스크립트를 단일 진입점으로 쓴다.
#
# 단계:
#   1. 환경 점검            scripts/doctor.sh
#   2. 정적 분석 + 단위 테스트  ktlintCheck detekt test
#   3. 디버그 빌드           assembleDebug
#   4. 바이트코드 타깃 확인   libs.versions.toml 의 javaTarget 과 실제 class 파일 대조
#   5. 기기 설치
#   6. 기기 스모크           info / shot / dump / find
#   7. 타이머 녹화 E2E       scripts/verify-timer-recording.sh
#   8. 녹화 오디오 검증      scripts/verify-recording-audio.sh
#
# 사용법:
#   scripts/verify-all.sh              # 전 단계, 20초 녹화
#   scripts/verify-all.sh -s 30        # 녹화 길이 지정 (최소 10초)
#   scripts/verify-all.sh -B           # 빌드/테스트 생략 (기기 검증만)
#   scripts/verify-all.sh -D           # 기기 단계 생략 (빌드/테스트만)
#
# 종료 코드: 0 = 전 단계 통과, 1 = 검증 실패, 2 = 준비 오류
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/env.sh"

# 기능명세서 11.4절의 시간 제한 최소값과 맞춘다.
MINIMUM_RECORDING_SECONDS=10
RECORDING_SECONDS=20
RUN_BUILD=1
RUN_DEVICE=1

usage() {
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
    exit "${1:-2}"
}

while getopts "s:BDh" option; do
    case "$option" in
        s) RECORDING_SECONDS="$OPTARG" ;;
        B) RUN_BUILD=0 ;;
        D) RUN_DEVICE=0 ;;
        h) usage 0 ;;
        *) usage ;;
    esac
done

case "$RECORDING_SECONDS" in
    "" | *[!0-9]*) die "-s 는 정수여야 한다: $RECORDING_SECONDS" ;;
esac
[ "$RECORDING_SECONDS" -ge "$MINIMUM_RECORDING_SECONDS" ]     || die "-s 는 ${MINIMUM_RECORDING_SECONDS} 이상이어야 한다 (기능명세서 11.4절)"

make_work_dir
STEP_NAMES=()
STEP_RESULTS=()

# 기기의 녹화 파일 목록을 정렬해 한 줄씩 낸다.
# adb shell 출력에는 CR 이 섞여 오므로 지운다. 두 곳에서 같은 코드를 쓰다가
# 한쪽만 어긋나면 비교 결과가 조용히 망가지므로 여기 한 곳에만 둔다.
list_recordings() {
    adb shell "ls $VIDEO_DIR 2>/dev/null" | tr -d '\r' | sort
}

# 한 단계를 실행하고 결과를 모은다. 실패해도 멈추지 않고 끝까지 돌려서
# 어디까지 되는지 한눈에 보이게 한다. 인자: 이름, 명령...
run_step() {
    local name="$1"
    shift
    printf '
[1m▶ %s[0m
' "$name"
    if "$@"; then
        STEP_NAMES+=("$name")
        STEP_RESULTS+=("통과")
    else
        STEP_NAMES+=("$name")
        STEP_RESULTS+=("실패")
    fi
}

record_skip() {
    STEP_NAMES+=("$1")
    STEP_RESULTS+=("생략")
}

# ── 단계 구현 ────────────────────────────────────────────────────────────────

step_static_analysis() { gradle_run ktlintCheck detekt test; }

step_build() { gradle_run assembleDebug; }

# 선언한 javaTarget 과 실제 산출 바이트코드가 맞는지 본다. 툴체인 설정이 어긋나면
# 빌드는 통과하면서 산출물만 다른 버전이 되는 일이 있어 별도로 확인한다.
step_bytecode_target() {
    local target expected actual class_file javap
    target="$(grep -m1 '^javaTarget = ' "$PROJECT_ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
    [ -n "$target" ] || { warn "libs.versions.toml 에서 javaTarget 을 읽지 못했다"; return 1; }
    # class 파일 major 버전은 자바 feature 버전 + 44 다 (Java 17 = 61).
    expected=$((target + 44))
    class_file="$(find "$PROJECT_ROOT/domain/build/classes" -name "*.class" 2>/dev/null | head -1)"
    [ -n "$class_file" ] || { warn "컴파일된 class 파일이 없다 (빌드를 먼저 실행하라)"; return 1; }
    _resolve_java_home || return 1
    javap="$JAVA_HOME_FOR_GRADLE/bin/javap"
    actual="$("$javap" -v -p "$(to_host_path "$class_file")" 2>/dev/null | grep -m1 'major version' | tr -dc '0-9')"
    log "javaTarget=$target → 기대 major $expected / 실제 major ${actual:-읽기실패}"
    [ "$actual" = "$expected" ]
}

step_install() {
    local apk="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
    [ -f "$apk" ] || { warn "APK 가 없다: $apk"; return 1; }
    adb install -r "$apk"
}

# 기기가 실제로 조작 가능한 상태인지 최소한으로 확인한다.
step_device_smoke() {
    require_single_device
    require_app_installed
    "$SCRIPT_DIR/device.sh" info || return 1
    "$SCRIPT_DIR/device.sh" restart || return 1
    sleep 2
    "$SCRIPT_DIR/device.sh" shot "$WORK/smoke.png" >/dev/null || return 1
    [ -s "$WORK/smoke.png" ] || { warn "스크린샷이 비어 있다"; return 1; }
    "$SCRIPT_DIR/device.sh" find "녹화 준비 완료" >/dev/null || return 1
    log "홈 화면 확인 + 스크린샷 $(wc -c < "$WORK/smoke.png") 바이트"
}

step_timer_recording() {
    "$SCRIPT_DIR/verify-timer-recording.sh" -s "$RECORDING_SECONDS"
}

step_recording_audio() { "$SCRIPT_DIR/verify-recording-audio.sh"; }

# 이 실행 중에 새로 나타난 녹화를 보고한다. 삭제는 하지 않는다.
#
# 디렉터리 목록 diff 로 '내가 만든 파일'을 추정하던 앞선 구현은 한 번 오판을 냈고
# (실행 전부터 있던 파일을 새 파일로 집었다) 원인을 규명하지 못했다. 추정에 기대어
# 파일을 지우는 것은 위험하므로, 목록과 삭제 명령만 알려주고 판단은 사람에게 남긴다.
report_new_recordings() {
    local current new_files name
    current="$(list_recordings)"
    new_files="$(comm -13 <(printf '%s\n' "$RECORDINGS_BEFORE") <(printf '%s\n' "$current"))"
    [ -n "$new_files" ] || return 0
    warn "이 실행 중에 새로 생긴 녹화가 있다. 필요하면 직접 지운다:"
    for name in $new_files; do
        printf '      adb shell rm %s/%s\n' "$VIDEO_DIR" "$name" >&2
    done
}

# ── 실행 ─────────────────────────────────────────────────────────────────────

printf '[1m전체 검증 시작[0m — 녹화 %s초 / 빌드 %s / 기기 %s
'     "$RECORDING_SECONDS"     "$([ "$RUN_BUILD" -eq 1 ] && echo 포함 || echo 생략)"     "$([ "$RUN_DEVICE" -eq 1 ] && echo 포함 || echo 생략)"

run_step "1. 환경 점검" "$SCRIPT_DIR/doctor.sh"

if [ "$RUN_BUILD" -eq 1 ]; then
    run_step "2. 정적 분석 + 단위 테스트" step_static_analysis
    run_step "3. 디버그 빌드" step_build
else
    record_skip "2. 정적 분석 + 단위 테스트"
    record_skip "3. 디버그 빌드"
fi

run_step "4. 바이트코드 타깃 확인" step_bytecode_target

if [ "$RUN_DEVICE" -eq 1 ]; then
    RECORDINGS_BEFORE="$(list_recordings)"
    run_step "5. 기기 설치" step_install
    run_step "6. 기기 스모크" step_device_smoke
    run_step "7. 타이머 녹화 E2E" step_timer_recording
    run_step "8. 녹화 오디오 검증" step_recording_audio
    report_new_recordings
else
    record_skip "5. 기기 설치"
    record_skip "6. 기기 스모크"
    record_skip "7. 타이머 녹화 E2E"
    record_skip "8. 녹화 오디오 검증"
fi

# ── 보고 ─────────────────────────────────────────────────────────────────────

printf '
[1m검증 결과[0m
'
FAILED=0
for index in "${!STEP_NAMES[@]}"; do
    case "${STEP_RESULTS[$index]}" in
        통과) printf '  [32m✓[0m %s
' "${STEP_NAMES[$index]}" ;;
        생략) printf '  [33m-[0m %s (생략)
' "${STEP_NAMES[$index]}" ;;
        *) printf '  [31m✗[0m %s
' "${STEP_NAMES[$index]}"; FAILED=$((FAILED + 1)) ;;
    esac
done

if [ "$FAILED" -eq 0 ]; then
    printf '
[32m전체 통과[0m
'
    exit 0
fi
printf '
[31m%d 단계 실패[0m
' "$FAILED"
exit 1
