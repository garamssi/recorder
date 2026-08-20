#!/usr/bin/env bash
#
# 타이머 녹화 E2E 검증 (기능명세서 11.4절).
#
# 요청한 시간만큼 시간 제한을 설정하고, 지정한 앱을 띄운 뒤 녹화를 시작해서,
# 자동 중지된 결과 파일의 실제 재생 시간이 요청 시간과 맞는지 확인한다.
#
# 사용법:
#   scripts/verify-timer-recording.sh -s 30                    # 현재 화면을 30초 녹화
#   scripts/verify-timer-recording.sh -s 60 -a com.android.chrome
#   scripts/verify-timer-recording.sh -s 30 -m single -a com.android.chrome -l Chrome
#
# 종료 코드: 0 = 통과, 1 = 시간 불일치(실패), 2 = 준비/조작 오류
set -euo pipefail

PACKAGE="io.rami.screenrecorder"
RECORD_DIR="/sdcard/Movies/ScreenRecorder"
SECONDS_WANTED=30
TARGET_APP=""
TARGET_LABEL=""
MODE="full"
TOLERANCE=1.5
KEEP_FILE=0

usage() {
    sed -n '3,16p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

while getopts "s:a:l:m:t:kh" opt; do
    case "$opt" in
        s) SECONDS_WANTED="$OPTARG" ;;
        a) TARGET_APP="$OPTARG" ;;
        l) TARGET_LABEL="$OPTARG" ;;
        m) MODE="$OPTARG" ;;
        t) TOLERANCE="$OPTARG" ;;
        k) KEEP_FILE=1 ;;
        *) usage ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UIAUTO="$SCRIPT_DIR/uiauto.py"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

log() { printf '\033[36m▸\033[0m %s\n' "$*"; }
fail() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 2; }

# ── 준비 확인 ────────────────────────────────────────────────────────────────
for cmd in adb python3 ffprobe; do
    command -v "$cmd" >/dev/null || fail "$cmd 가 필요하다 (PATH 확인)"
done
python3 -c "import PIL" 2>/dev/null || fail "python3 Pillow 가 필요하다: pip3 install Pillow"
[ "$(adb devices | grep -c 'device$')" -eq 1 ] || fail "연결된 기기가 정확히 1대여야 한다"
adb shell pm list packages | grep -q "^package:$PACKAGE$" || fail "$PACKAGE 가 설치되어 있지 않다"
case "$MODE" in
    full|single) ;;
    *) fail "-m 은 full 또는 single 이어야 한다 (부분 영역은 영역 지정이 필요해 자동화하지 않는다)" ;;
esac
[ "$SECONDS_WANTED" -ge 10 ] || fail "시간 제한 최소값은 10초다 (기능명세서 11.4절)"

DENSITY="$(adb shell wm density | grep -oE '[0-9]+' | head -1)"
DENSITY_SCALE="$(python3 -c "print($DENSITY/160)")"

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
        if python3 "$UIAUTO" node "$WORK/ui.xml" "$needle" >/dev/null 2>&1; then return 0; fi
        sleep 1
        waited=$((waited + 1))
    done
    return 1
}

tap_text() {
    local needle="$1"
    wait_text "$needle" "${2:-10}" || fail "화면에서 '$needle' 을(를) 찾지 못했다"
    adb shell input tap $(python3 "$UIAUTO" node "$WORK/ui.xml" "$needle")
    sleep 1
}

# contentDescription 으로만 찾아 탭한다 (라벨 텍스트와 같은 문구일 때 필요).
tap_desc() {
    local needle="$1" limit="${2:-10}" waited=0 point=""
    while [ "$waited" -lt "$limit" ]; do
        ui_dump
        point="$(python3 "$UIAUTO" desc "$WORK/ui.xml" "$needle" 2>/dev/null || true)"
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
    point="$(python3 "$UIAUTO" any-node "$WORK/ui.xml" "$@" 2>/dev/null)" \
        || fail "화면에서 [$*] 중 아무것도 찾지 못했다"
    adb shell input tap $point
    sleep 1
}

# 플로팅 버블(빨간 원)을 찾아 탭한다.
tap_bubble() {
    adb shell screencap -p /sdcard/_shot.png >/dev/null
    adb pull /sdcard/_shot.png "$WORK/shot.png" >/dev/null 2>&1
    local point
    point="$(python3 "$UIAUTO" bubble "$WORK/shot.png" "$DENSITY_SCALE")" \
        || fail "플로팅 버블을 찾지 못했다 — 설정 > 녹화 > 플로팅 캡처 버튼을 켜라"
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
#
# 키보드가 뜨면 다이얼로그가 위로 밀려 좌표가 바뀐다. 예전 좌표를 그대로 쓰면
# 다이얼로그 바깥을 눌러 창이 닫혀 버리므로, 필드마다 위치를 다시 찾는다.
set_number_field() {
    local index="$1" value="$2" coords
    ui_dump
    coords="$(python3 "$UIAUTO" edit-texts "$WORK/ui.xml" | sed -n "$((index + 1))p")"
    [ -n "$coords" ] || fail "입력 필드 $index 번을 찾지 못했다"
    adb shell input tap $coords
    adb shell input keyevent KEYCODE_MOVE_END
    for _ in 1 2 3 4 5; do adb shell input keyevent KEYCODE_DEL; done
    adb shell input text "$value"
    sleep 1
}

# ── 1. 앱을 열고 캡처 모드·시간 제한을 설정한다 ──────────────────────────────
log "앱 실행"
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/.MainActivity" >/dev/null
wait_text "녹화 준비 완료" 15 || fail "홈 화면이 뜨지 않았다"

if [ "$MODE" = "single" ]; then
    log "캡처 모드: 단일 앱"
    tap_text "단일 앱"
else
    log "캡처 모드: 전체 화면"
    tap_text "전체 화면"
fi

log "시간 제한 ${SECONDS_WANTED}초 설정"
tap_text "변경"
# 이미 시간 제한이 걸려 있으면 칩 라벨이 "직접 입력" 대신 그 시간으로 바뀐다.
# 항상 "제한 없음"으로 되돌려 라벨을 예측 가능하게 만든 뒤 입력한다.
tap_text "제한 없음"
tap_text "직접 입력"
wait_text "시간 제한 직접 입력" 10 || fail "시간 제한 입력 다이얼로그가 열리지 않았다"
set_number_field 0 "$((SECONDS_WANTED / 3600))"
set_number_field 1 "$((SECONDS_WANTED % 3600 / 60))"
set_number_field 2 "$((SECONDS_WANTED % 60))"
hide_keyboard
tap_text "확인"          # 시간 제한 다이얼로그 확정
hide_keyboard
tap_text "확인"          # 녹화 옵션 시트 닫기

# ── 2. 녹화 대상 앱을 띄우고 녹화를 시작한다 ─────────────────────────────────
BEFORE="$(adb shell ls "$RECORD_DIR" 2>/dev/null | tr -d '\r' | sort || true)"

if [ -n "$TARGET_APP" ]; then
    log "녹화 대상 앱 실행: $TARGET_APP"
    adb shell monkey -p "$TARGET_APP" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
        || fail "$TARGET_APP 를 실행하지 못했다"
    sleep 4
    log "플로팅 버블로 녹화 시작"
    tap_bubble          # 펼치기
    tap_bubble          # 펼침 메뉴의 빨간 버튼 = 화면 녹화
else
    log "홈 화면의 녹화 버튼으로 시작"
    tap_desc "녹화 시작"
fi

log "화면 공유 동의 처리"
wait_text "Share one app" 15 || wait_text "한 개의 앱 공유" 5 \
    || fail "MediaProjection 동의 다이얼로그가 뜨지 않았다"
if [ "$MODE" = "single" ]; then
    tap_any_text "Next" "다음"
    [ -n "$TARGET_LABEL" ] || fail "-m single 에는 앱 목록에서 고를 이름(-l)이 필요하다"
    tap_text "$TARGET_LABEL" 15
else
    tap_any_text "Share one app" "한 개의 앱 공유"          # 선택 드롭다운 열기
    tap_any_text "Share entire screen" "전체 화면 공유"
    tap_any_text "Share screen" "화면 공유" "Start now" "지금 시작"
fi

# ── 3. 자동 중지를 기다린다 ──────────────────────────────────────────────────
# 카운트다운 + 녹화 + 파일 마무리. 고정 대기 대신 새 파일이 생길 때까지 폴링한다.
DEADLINE=$((SECONDS_WANTED + 60))
log "자동 중지 대기 (최대 ${DEADLINE}초)"
NEW_FILE=""
for _ in $(seq 1 "$DEADLINE"); do
    sleep 1
    AFTER="$(adb shell ls "$RECORD_DIR" 2>/dev/null | tr -d '\r' | sort || true)"
    NEW_FILE="$(comm -13 <(printf '%s\n' "$BEFORE") <(printf '%s\n' "$AFTER") | grep -E '\.mp4$' | head -1 || true)"
    [ -n "$NEW_FILE" ] && break
done
[ -n "$NEW_FILE" ] || fail "제한 시간이 지나도 녹화 파일이 생기지 않았다"
log "저장된 파일: $NEW_FILE"

# ── 4. 실제 재생 시간을 확인한다 ─────────────────────────────────────────────
adb pull "$RECORD_DIR/$NEW_FILE" "$WORK/$NEW_FILE" >/dev/null 2>&1 \
    || fail "파일을 가져오지 못했다: $NEW_FILE"
# ffprobe 는 스트림 앞부분 디코딩 경고를 stderr 로 쏟는다. 길이 계산에는 영향이 없어 버린다.
ACTUAL="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$WORK/$NEW_FILE" 2>/dev/null)"
[ -n "$ACTUAL" ] || fail "재생 시간을 읽지 못했다 (파일이 손상됐을 수 있다)"

if [ "$KEEP_FILE" -eq 1 ]; then
    cp "$WORK/$NEW_FILE" "./$NEW_FILE"
    log "파일 사본: ./$NEW_FILE"
fi

python3 - "$SECONDS_WANTED" "$ACTUAL" "$TOLERANCE" "$NEW_FILE" <<'PY'
import sys

wanted, actual, tolerance, name = float(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
diff = actual - wanted
ok = abs(diff) <= tolerance
mark = "\033[32m✓ 통과\033[0m" if ok else "\033[31m✗ 실패\033[0m"
print(f"\n{mark}  {name}")
print(f"  요청 시간 : {wanted:.2f}s")
print(f"  실제 길이 : {actual:.2f}s")
print(f"  차이      : {diff:+.2f}s (허용 ±{tolerance}s)")
sys.exit(0 if ok else 1)
PY
