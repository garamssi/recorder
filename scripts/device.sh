#!/usr/bin/env bash
#
# 실기기 제어 CLI (macOS / Windows Git Bash 겸용).
#
# 빌드·설치부터 UI 조작, 로그 확인, 결과물 회수까지 한 곳에서 처리한다.
# 모든 기기 경로는 scripts/lib/env.sh 의 adb 래퍼를 거치므로 Git Bash 의
# 인자 경로 변환에 망가지지 않는다.
#
# 사용법: scripts/device.sh <명령> [인자...]
#
#   doctor                 개발 환경 점검 (scripts/doctor.sh 위임)
#   info                   기기 모델·OS·해상도·density·앱 설치 여부
#   build                  디버그 APK 빌드
#   install                빌드 후 재설치
#   uninstall              앱 제거
#   perms                  런타임 권한 부여 (마이크·알림)
#   start                  앱 실행
#   stop                   앱 강제 종료
#   restart                강제 종료 후 실행
#   shot [파일]            스크린샷 저장 (기본 ./shot.png)
#   dump [파일]            uiautomator XML 덤프 (기본 ./ui.xml)
#   find <문구>            화면에서 문구의 중심 좌표 출력
#   tap <문구>             문구를 탭
#   tapdesc <문구>         contentDescription 으로만 찾아 탭
#   bubble                 플로팅 버블(빨간 원)을 탭
#   tapxy <x> <y>          좌표를 탭
#   text <문자열>          텍스트 입력
#   key <KEYCODE>          키 이벤트 (예: KEYCODE_BACK)
#   back | home            뒤로 | 홈
#   log [정규식]           앱 프로세스 logcat 추적
#   crash                  최근 크래시 로그
#   files [video|voice]    기기 내 결과물 목록 (최신순)
#   pull-latest [video|voice] [대상디렉터리]
#                          최신 결과물 회수 (기본 ./)
#
# 종료 코드: 0 = 성공, 2 = 준비/조작 오류
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/env.sh"

UIAUTO="$SCRIPT_DIR/uiauto.py"

usage() {
    # 헤더 주석 블록을 그대로 사용법으로 출력한다. 줄 번호를 박아 두면 헤더를
    # 고칠 때마다 범위가 어긋나 코드까지 흘러나온다.
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
    exit "${1:-2}"
}

# ── 화면 상태 읽기 ───────────────────────────────────────────────────────────

# uiautomator XML 을 로컬로 가져온다. 인자: 저장 경로.
dump_ui() {
    adb shell uiautomator dump /sdcard/_ui.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/_ui.xml > "$1" 2>/dev/null
    [ -s "$1" ] || die "UI 덤프가 비어 있다 (화면이 꺼져 있는지 확인하라)"
}

capture_screen() {
    adb shell screencap -p /sdcard/_shot.png >/dev/null
    adb pull /sdcard/_shot.png "$1" >/dev/null 2>&1 || die "스크린샷을 받지 못했다"
    adb shell rm -f /sdcard/_shot.png
}

# uiauto.py 하위 명령으로 좌표를 얻는다. 인자: 하위명령, 나머지 인자...
locate() {
    local subcommand="$1"; shift
    make_work_dir
    dump_ui "$WORK/ui.xml"
    python_run "$UIAUTO" "$subcommand" "$(to_host_path "$WORK/ui.xml")" "$@"
}

# ── 명령 구현 ────────────────────────────────────────────────────────────────

cmd_info() {
    require_tools adb
    require_single_device
    printf '기기      : %s (%s)\n' "$(adb shell getprop ro.product.model | tr -d '\r')" \
        "$(adb devices | grep 'device$' | head -1 | cut -f1)"
    printf 'Android   : %s (API %s)\n' "$(adb shell getprop ro.build.version.release | tr -d '\r')" \
        "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
    printf '해상도    : %s\n' "$(adb shell wm size | tr -d '\r' | sed 's/.*: //')"
    printf 'density   : %s (배율 %s)\n' "$(adb shell wm density | tr -d '\r' | sed 's/.*: //')" \
        "$(device_density_scale)"
    if adb shell pm list packages | tr -d '\r' | grep -q "^package:$PACKAGE$"; then
        printf '앱        : %s 설치됨 (버전 %s)\n' "$PACKAGE" \
            "$(adb shell dumpsys package "$PACKAGE" | grep -m1 versionName | tr -d '\r ' | cut -d= -f2)"
    else
        printf '앱        : %s 미설치\n' "$PACKAGE"
    fi
}

cmd_build() { gradle_run assembleDebug; }

cmd_install() {
    require_tools adb
    require_single_device
    cmd_build
    local apk="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
    [ -f "$apk" ] || die "APK 를 찾지 못했다: $apk"
    log "설치: $apk"
    adb install -r "$apk"
}

cmd_perms() {
    require_tools adb
    local permission
    for permission in android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do
        adb shell pm grant "$PACKAGE" "$permission" 2>/dev/null \
            || warn "권한 부여 실패(이미 부여됐거나 대상 아님): $permission"
    done
    log "런타임 권한 처리 완료"
}

cmd_start() {
    require_tools adb
    require_app_installed
    adb shell am start -n "$PACKAGE/.MainActivity" >/dev/null
    log "실행: $PACKAGE"
}

cmd_stop() { require_tools adb; adb shell am force-stop "$PACKAGE"; log "종료: $PACKAGE"; }

cmd_log() {
    require_tools adb
    local pid
    pid="$(adb shell pidof -s "$PACKAGE" | tr -d '\r')"
    [ -n "$pid" ] || die "앱이 실행 중이 아니다 (scripts/device.sh start)"
    log "logcat --pid=$pid (Ctrl+C 로 중단)"
    if [ "$#" -gt 0 ]; then adb logcat --pid="$pid" | grep -E "$1"; else adb logcat --pid="$pid"; fi
}

cmd_crash() {
    require_tools adb
    adb logcat -b crash -d -t 300 | tr -d '\r'
}

# 인자: video|voice → 디렉터리와 확장자를 정한다.
resolve_output_kind() {
    case "${1:-video}" in
        video) OUTPUT_DIR="$VIDEO_DIR"; OUTPUT_EXTENSION="mp4" ;;
        voice) OUTPUT_DIR="$VOICE_DIR"; OUTPUT_EXTENSION="m4a" ;;
        *) die "종류는 video 또는 voice 여야 한다: $1" ;;
    esac
}

cmd_files() {
    require_tools adb
    resolve_output_kind "${1:-video}"
    adb shell "ls -lt $OUTPUT_DIR/*.$OUTPUT_EXTENSION 2>/dev/null" | tr -d '\r' \
        || die "$OUTPUT_DIR 에 .$OUTPUT_EXTENSION 파일이 없다"
}

cmd_pull_latest() {
    require_tools adb
    resolve_output_kind "${1:-video}"
    local destination="${2:-.}" newest
    newest="$(adb shell "ls -t $OUTPUT_DIR/*.$OUTPUT_EXTENSION 2>/dev/null | head -1" | tr -d '\r')"
    [ -n "$newest" ] || die "$OUTPUT_DIR 에 .$OUTPUT_EXTENSION 파일이 없다"
    mkdir -p "$destination"
    adb pull "$newest" "$destination/$(basename "$newest")" >/dev/null 2>&1 \
        || die "파일을 받지 못했다: $newest"
    log "회수: $destination/$(basename "$newest")"
}

cmd_bubble() {
    require_tools adb python
    make_work_dir
    capture_screen "$WORK/shot.png"
    local point
    point="$(python_run "$UIAUTO" bubble "$(to_host_path "$WORK/shot.png")" "$(device_density_scale)")" \
        || die "플로팅 버블을 찾지 못했다 — 설정 > 녹화 > 플로팅 캡처 버튼을 켜라"
    adb shell input tap $point
    log "버블 탭: $point"
}

# ── 진입점 ───────────────────────────────────────────────────────────────────

COMMAND="${1:-}"
[ -n "$COMMAND" ] || usage
shift || true

case "$COMMAND" in
    doctor) exec "$SCRIPT_DIR/doctor.sh" "$@" ;;
    info) cmd_info ;;
    build) cmd_build ;;
    install) cmd_install ;;
    uninstall) require_tools adb; adb uninstall "$PACKAGE" ;;
    perms) cmd_perms ;;
    start) cmd_start ;;
    stop) cmd_stop ;;
    restart) cmd_stop; cmd_start ;;
    shot) require_tools adb; capture_screen "${1:-./shot.png}"; log "저장: ${1:-./shot.png}" ;;
    dump) require_tools adb; dump_ui "${1:-./ui.xml}"; log "저장: ${1:-./ui.xml}" ;;
    find) require_tools adb python; locate any-node "$@" ;;
    tap) require_tools adb python; adb shell input tap $(locate any-node "$@") ;;
    tapdesc) require_tools adb python; adb shell input tap $(locate desc "$@") ;;
    bubble) cmd_bubble ;;
    tapxy) require_tools adb; adb shell input tap "$1" "$2" ;;
    text) require_tools adb; adb shell input text "$1" ;;
    key) require_tools adb; adb shell input keyevent "$1" ;;
    back) require_tools adb; adb shell input keyevent KEYCODE_BACK ;;
    home) require_tools adb; adb shell input keyevent KEYCODE_HOME ;;
    log) cmd_log "$@" ;;
    crash) cmd_crash ;;
    files) cmd_files "$@" ;;
    pull-latest) cmd_pull_latest "$@" ;;
    -h | --help | help) usage 0 ;;
    *) die "알 수 없는 명령: $COMMAND (scripts/device.sh help)" ;;
esac
