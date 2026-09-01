#!/usr/bin/env bash
#
# 화면 꺼짐 중 녹화 지속 검증.
#
# 시간 제한을 건 녹화를 시작한 뒤 화면을 끄고, 꺼져 있는 동안 주기적으로
#   1) 앱 프로세스가 살아 있는지
#   2) 포그라운드 서비스가 유지되는지
#   3) 임시 파일이 실제로 커지는지 (= 프레임이 인코딩되고 있는지)
# 를 표본 조사한 뒤, 결과물의 재생 시간을 요청 시간과 비교한다.
#
# 셋은 서로 다른 실패다. 프로세스가 죽으면 FGS/배터리 최적화 쪽 문제고,
# 서비스는 살았는데 파일이 안 자라면 디스플레이 프레임 공급 쪽 문제다.
# 뭉뚱그리면 원인을 못 짚으므로 표본마다 따로 남긴다.
#
# 사용법:
#   scripts/verify-screen-off.sh                 # 60초 녹화, 시작 후 화면 끄고 관찰
#   scripts/verify-screen-off.sh -s 90 -i 5 -k
#
# 종료 코드: 0 = 통과, 1 = 녹화가 끊김(실패), 2 = 준비/조작 오류
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/env.sh"

RECORD_DIR="$VIDEO_DIR"
TEMP_DIR="cache/recordings"          # MediaStoreRecordingFileStore.TEMP_DIRECTORY
SECONDS_WANTED=60
SAMPLE_INTERVAL=5
TOLERANCE=2.0
KEEP_FILE=0

usage() {
    # 헤더 주석 블록을 그대로 사용법으로 출력한다. 줄 번호를 박아 두면 헤더를
    # 고칠 때마다 범위가 어긋나 코드까지 흘러나온다.
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
    exit "${1:-2}"
}

while getopts "s:i:t:kh" opt; do
    case "$opt" in
        s) SECONDS_WANTED="$OPTARG" ;;
        i) SAMPLE_INTERVAL="$OPTARG" ;;
        t) TOLERANCE="$OPTARG" ;;
        k) KEEP_FILE=1 ;;
        *) usage ;;
    esac
done

UIAUTO="$SCRIPT_DIR/uiauto.py"
make_work_dir

# log 과 die 는 lib/env.sh 가 제공한다. fail 은 다른 검증 스크립트와 어휘를 맞추려는 별칭이다.
fail() { die "$@"; }

# uiauto.py 는 네이티브 python 으로 실행되므로 파일 경로를 호스트 형식으로 바꿔 넘긴다.
uiauto() { python_run "$UIAUTO" "$1" "$(to_host_path "$2")" "${@:3}"; }

require_tools adb python ffprobe
require_single_device
require_app_installed
[ "$SECONDS_WANTED" -ge 30 ] || fail "-s 는 30초 이상이어야 한다 (표본을 몇 개는 떠야 판정이 된다)"

# ── UI 조작 헬퍼 ─────────────────────────────────────────────────────────────
ui_dump() {
    adb shell uiautomator dump /sdcard/_ui.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/_ui.xml > "$WORK/ui.xml" 2>/dev/null || fail "UI 덤프 실패"
}

# 화면에 문구가 나타날 때까지 기다린다. 인자: 문구 [최대 초]
wait_text() {
    local needle="$1" limit="${2:-10}" waited=0
    while [ "$waited" -lt "$limit" ]; do
        ui_dump
        if uiauto node "$WORK/ui.xml" "$needle" >/dev/null 2>&1; then return 0; fi
        sleep 1
        waited=$((waited + 1))
    done
    return 1
}

tap_text() {
    local needle="$1"
    wait_text "$needle" "${2:-10}" || fail "화면에서 '$needle' 을(를) 찾지 못했다"
    adb shell input tap $(uiauto node "$WORK/ui.xml" "$needle")
    sleep 1
}

# contentDescription 으로만 찾아 탭한다 (라벨 텍스트와 같은 문구일 때 필요).
tap_desc() {
    local needle="$1" limit="${2:-10}" waited=0 point=""
    while [ "$waited" -lt "$limit" ]; do
        ui_dump
        point="$(uiauto desc "$WORK/ui.xml" "$needle" 2>/dev/null || true)"
        [ -n "$point" ] && break
        sleep 1
        waited=$((waited + 1))
    done
    [ -n "$point" ] || fail "화면에서 '$needle' 버튼을 찾지 못했다"
    adb shell input tap $point
    sleep 1
}

# 시스템 UI는 기기 언어에 따라 문구가 달라 후보를 여러 개 받는다.
tap_any_text() {
    ui_dump
    local point
    point="$(uiauto any-node "$WORK/ui.xml" "$@" 2>/dev/null)" \
        || fail "화면에서 [$*] 중 아무것도 찾지 못했다"
    adb shell input tap $point
    sleep 1
}

# 키보드가 실제로 떠 있을 때만 내린다. 무턱대고 BACK 을 보내면 다이얼로그까지 닫힌다.
hide_keyboard() {
    if adb shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then
        adb shell input keyevent KEYCODE_BACK
        sleep 1
    fi
}

# 시/분/초 입력 필드에 값을 넣는다. 인자: 0-기준 인덱스, 값.
set_number_field() {
    local index="$1" value="$2" coords
    ui_dump
    coords="$(uiauto edit-texts "$WORK/ui.xml" | sed -n "$((index + 1))p")"
    [ -n "$coords" ] || fail "입력 필드 $index 번을 찾지 못했다"
    adb shell input tap $coords
    adb shell input keyevent KEYCODE_MOVE_END
    for _ in 1 2 3 4 5; do adb shell input keyevent KEYCODE_DEL; done
    adb shell input text "$value"
    sleep 1
}

# ── 상태 표본 ────────────────────────────────────────────────────────────────

# 앱 전용 캐시에 쌓이는 임시 녹화 파일의 크기 합계(바이트). 없으면 0.
temp_bytes() {
    adb shell run-as "$PACKAGE" ls -l "$TEMP_DIR" 2>/dev/null | tr -d '\r' \
        | awk '$5 ~ /^[0-9]+$/ { total += $5 } END { print total + 0 }'
}

app_pid() { adb shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r'; }

# 녹화 포그라운드 서비스가 살아 있으면 yes.
#
# 패키지 전체의 isForeground 를 보면 안 된다. 플로팅 버블 서비스도 포그라운드라
# 녹화가 끝난 뒤에도 계속 yes 가 나와 중단 시점을 놓친다.
foreground_state() {
    if adb shell dumpsys activity services "$PACKAGE" 2>/dev/null | tr -d '\r' \
        | grep -q "RecordingForegroundService"; then echo yes; else echo no; fi
}

wakefulness() {
    adb shell dumpsys power 2>/dev/null | tr -d '\r' \
        | sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | head -1
}

# ── 1. 시간 제한을 걸고 녹화를 시작한다 ──────────────────────────────────────
log "앱 실행"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/.MainActivity" >/dev/null
wait_text "녹화 준비 완료" 15 || fail "홈 화면이 뜨지 않았다"

tap_text "전체 화면"

log "시간 제한 ${SECONDS_WANTED}초 설정"
tap_text "변경"
# 이미 시간 제한이 걸려 있으면 칩 라벨이 "직접 입력" 대신 그 시간으로 바뀐다.
# 항상 "제한 없음"으로 되돌려 라벨을 예측 가능하게 만든 뒤 입력한다.
tap_text "제한 없음"
tap_text "직접 입력"
wait_text "저장" 10 || fail "시간 제한 입력 창이 열리지 않았다"
set_number_field 0 "$((SECONDS_WANTED / 3600))"
set_number_field 1 "$((SECONDS_WANTED % 3600 / 60))"
set_number_field 2 "$((SECONDS_WANTED % 60))"
hide_keyboard
tap_text "저장"
hide_keyboard
tap_text "확인"

BEFORE="$(adb shell ls "$RECORD_DIR" 2>/dev/null | tr -d '\r' | sort || true)"

log "녹화 시작"
tap_desc "녹화 시작"

log "화면 공유 동의 처리"
wait_text "Share one app" 15 || wait_text "한 개의 앱 공유" 5 \
    || fail "MediaProjection 동의 다이얼로그가 뜨지 않았다"
tap_any_text "Share one app" "한 개의 앱 공유"
tap_any_text "Share entire screen" "전체 화면 공유"
tap_any_text "Share screen" "화면 공유" "Start now" "지금 시작"

# 카운트다운이 끝나고 임시 파일이 실제로 생길 때까지 기다린다. 여기서 화면을 끄면
# 시작 자체가 안 된 것과 꺼져서 멈춘 것을 구분할 수 없다.
log "임시 파일 생성 대기"
STARTED=0
for _ in $(seq 1 20); do
    sleep 1
    [ "$(temp_bytes)" -gt 0 ] && { STARTED=1; break; }
done
[ "$STARTED" -eq 1 ] || fail "녹화가 시작되지 않았다 ($TEMP_DIR 가 비어 있다)"

# ── 2. 화면을 끄고 표본을 뜬다 ───────────────────────────────────────────────
log "화면 끄기"
adb shell input keyevent KEYCODE_SLEEP
sleep 2
SCREEN_STATE="$(wakefulness)"
[ "$SCREEN_STATE" = "Asleep" ] || [ "$SCREEN_STATE" = "Dozing" ] \
    || warn "화면이 꺼지지 않았다 (mWakefulness=$SCREEN_STATE) — 표본의 의미가 약해진다"
log "화면 상태: mWakefulness=$SCREEN_STATE"

printf '\n  %-6s  %-8s  %-6s  %-12s  %s\n' 경과 PID FGS 임시바이트 증가
printf '  %s\n' "------  --------  ------  ------------  --------"

SAMPLE_LOG="$WORK/samples.tsv"
: > "$SAMPLE_LOG"
PREVIOUS_BYTES="$(temp_bytes)"
ELAPSED=0
DEADLINE=$((SECONDS_WANTED + 15))
while [ "$ELAPSED" -lt "$DEADLINE" ]; do
    sleep "$SAMPLE_INTERVAL"
    ELAPSED=$((ELAPSED + SAMPLE_INTERVAL))
    PID="$(app_pid)"
    FGS="$(foreground_state)"
    BYTES="$(temp_bytes)"
    DELTA=$((BYTES - PREVIOUS_BYTES))
    PREVIOUS_BYTES="$BYTES"
    printf '  %-6s  %-8s  %-6s  %-12s  %+d\n' "${ELAPSED}s" "${PID:---}" "$FGS" "$BYTES" "$DELTA"
    printf '%s\t%s\t%s\t%s\t%s\n' "$ELAPSED" "${PID:-none}" "$FGS" "$BYTES" "$DELTA" >> "$SAMPLE_LOG"
done
echo

# ── 3. 화면을 켜고 결과물을 확인한다 ─────────────────────────────────────────
log "화면 켜기"
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
if adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -q "isKeyguardShowing=true"; then
    adb shell input keyevent KEYCODE_MENU
    sleep 1
fi

log "결과 파일 대기 (최대 60초)"
NEW_FILE=""
for _ in $(seq 1 60); do
    AFTER="$(adb shell ls "$RECORD_DIR" 2>/dev/null | tr -d '\r' | sort || true)"
    NEW_FILE="$(comm -13 <(printf '%s\n' "$BEFORE") <(printf '%s\n' "$AFTER") | grep -E '\.mp4$' | head -1 || true)"
    [ -n "$NEW_FILE" ] && break
    sleep 1
done

# ── 4. 판정 ──────────────────────────────────────────────────────────────────
TOTAL_SAMPLES="$(wc -l < "$SAMPLE_LOG" | tr -d ' ')"
DEAD_SAMPLES="$(awk -F'\t' '$2 == "none"' "$SAMPLE_LOG" | wc -l | tr -d ' ')"
NO_FGS_SAMPLES="$(awk -F'\t' '$3 == "no"' "$SAMPLE_LOG" | wc -l | tr -d ' ')"
# 제한 시간을 넘긴 표본은 자동 중지로 임시 파일이 결과물로 옮겨진 뒤라 증가량이
# 0 이거나 음수인 게 정상이다. 정체 판정에서 뺀다.
STALLED_SAMPLES="$(awk -F'\t' -v limit="$SECONDS_WANTED" '$1 < limit && $5 <= 0' "$SAMPLE_LOG" | wc -l | tr -d ' ')"

printf '  프로세스 죽은 표본 : %s / %s\n' "$DEAD_SAMPLES" "$TOTAL_SAMPLES"
printf '  FGS 끊긴 표본      : %s / %s\n' "$NO_FGS_SAMPLES" "$TOTAL_SAMPLES"
printf '  파일 정체 표본     : %s / %s\n' "$STALLED_SAMPLES" "$TOTAL_SAMPLES"

VERDICT=0
if [ "$DEAD_SAMPLES" -gt 0 ]; then
    warn "화면이 꺼진 동안 앱 프로세스가 죽었다 → FGS/배터리 최적화 쪽"
    VERDICT=1
fi
if [ "$NO_FGS_SAMPLES" -gt 0 ]; then
    warn "포그라운드 서비스가 유지되지 않았다"
    VERDICT=1
fi
if [ "$STALLED_SAMPLES" -gt 0 ]; then
    warn "서비스는 살았는데 임시 파일이 자라지 않은 구간이 있다 → 프레임 공급 쪽"
    VERDICT=1
fi

if [ -z "$NEW_FILE" ]; then
    printf '\n\033[31m✗ 실패\033[0m  제한 시간이 지나도 결과 파일이 생기지 않았다\n'
    exit 1
fi

log "저장된 파일: $NEW_FILE"
adb pull "$RECORD_DIR/$NEW_FILE" "$WORK/$NEW_FILE" >/dev/null 2>&1 \
    || fail "파일을 가져오지 못했다: $NEW_FILE"
# ffprobe 는 스트림 앞부분 디코딩 경고를 stderr 로 쏟는다. 길이 계산에는 영향이 없어 버린다.
ACTUAL="$(ffprobe_run -v error -show_entries format=duration -of csv=p=0 "$(to_host_path "$WORK/$NEW_FILE")" 2>/dev/null)"
[ -n "$ACTUAL" ] || fail "재생 시간을 읽지 못했다 (파일이 손상됐을 수 있다)"

if [ "$KEEP_FILE" -eq 1 ]; then
    cp "$WORK/$NEW_FILE" "./$NEW_FILE"
    log "파일 사본: ./$NEW_FILE"
fi

"$PYTHON_BIN" - "$SECONDS_WANTED" "$ACTUAL" "$TOLERANCE" "$NEW_FILE" "$VERDICT" <<'PY'
import sys

wanted, actual, tolerance = float(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3])
name, verdict = sys.argv[4], int(sys.argv[5])
diff = actual - wanted
ok = abs(diff) <= tolerance and verdict == 0
mark = "\033[32m✓ 통과\033[0m" if ok else "\033[31m✗ 실패\033[0m"
print(f"\n{mark}  {name}")
print(f"  요청 시간 : {wanted:.2f}s")
print(f"  실제 길이 : {actual:.2f}s")
print(f"  차이      : {diff:+.2f}s (허용 ±{tolerance}s)")
sys.exit(0 if ok else 1)
PY
