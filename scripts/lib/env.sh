#!/usr/bin/env bash
#
# 스크립트 공용 환경 계층 (macOS / Windows Git Bash 겸용).
#
# 존재 이유:
#   1. adb, python, ffmpeg 이 PATH 에 없어도 표준 설치 위치에서 찾아낸다.
#   2. Git Bash(MSYS)의 인자 경로 변환을 무력화한다. MSYS 는 네이티브 exe 에 넘기는
#      `/sdcard/...` 를 `C:/Program Files/Git/sdcard/...` 로 바꿔 버리므로, 이 처리를
#      하지 않으면 모든 `adb shell` 호출이 조용히 엉뚱한 경로를 가리킨다.
#
# 사용법 (스크립트 맨 위):
#   source "$(dirname "${BASH_SOURCE[0]}")/lib/env.sh"
#   require_tools adb python ffprobe
#
# 노출하는 것:
#   IS_WINDOWS            윈도우(MSYS)면 1
#   PACKAGE               앱 applicationId
#   VIDEO_DIR VOICE_DIR   기기 내 결과물 디렉터리
#   adb ...               경로 변환을 처리하는 래퍼 (원본 대신 항상 이것을 쓴다)
#   python_run ...        Pillow 가 있는 인터프리터로 실행
#   ffmpeg_run / ffprobe_run
#   to_host_path <경로>   네이티브 exe 에 넘길 수 있는 로컬 경로로 변환
#   make_work_dir         정리 트랩까지 걸린 작업 디렉터리 생성 → $WORK
#   log / warn / die
set -uo pipefail

# source 시점에 확정한다. 함수 안에서 BASH_SOURCE 를 다시 읽으면 호출 문맥에 따라
# 비어 있을 수 있어(set -u 에서는 그대로 죽는다) 루트를 잘못 잡는다.
ENV_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$ENV_LIB_DIR/../.." && pwd)"

PACKAGE="${PACKAGE:-io.rami.screenrecorder}"
VIDEO_DIR="${VIDEO_DIR:-/sdcard/Movies/ScreenRecorder}"
VOICE_DIR="${VOICE_DIR:-/sdcard/Music/ScreenRecorder}"

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) IS_WINDOWS=1 ;;
    *) IS_WINDOWS=0 ;;
esac

# %LOCALAPPDATA% 는 백슬래시 경로다. 그대로 후보 목록에 넣으면 glob 이스케이프와
# 섞여 다루기 까다로우므로 유닉스 형식으로 미리 바꿔 둔다.
if [ "$IS_WINDOWS" -eq 1 ] && [ -n "${LOCALAPPDATA:-}" ]; then
    LOCAL_APP_DATA="$(cygpath -u "$LOCALAPPDATA")"
else
    LOCAL_APP_DATA="$LOCAL_APP_DATA"
fi

# 윈도우 콘솔 기본 인코딩(cp949 등)에서는 파이썬이 체크 표시나 한글을 출력하다
# UnicodeEncodeError 로 죽는다. 스크립트 출력은 항상 UTF-8 로 고정한다.
export PYTHONIOENCODING=utf-8

log() { printf '\033[36m▸\033[0m %s\n' "$*"; }
warn() { printf '\033[33m!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 2; }

# ── 경로 변환 ────────────────────────────────────────────────────────────────

# 로컬 경로를 네이티브 실행 파일이 이해하는 형태로 바꾼다.
# Git Bash 의 /tmp/x 는 adb.exe 에게 존재하지 않는 경로다.
to_host_path() {
    if [ "$IS_WINDOWS" -eq 1 ]; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# ── 도구 탐색 ────────────────────────────────────────────────────────────────

# 후보 목록에서 실행 가능한 첫 경로를 출력한다.
# 와일드카드가 있는 후보만 전개한다. 공백이 든 경로(Program Files 등)를
# 단어 분리로 망가뜨리지 않으려면 무조건 전개해서는 안 된다.
_first_executable() {
    local candidate match
    for candidate in "$@"; do
        if [ "$candidate" = "${candidate%%[*?]*}" ]; then
            # 와일드카드가 없는 후보는 그대로 검사한다. glob 으로 넘기면 윈도우 경로의
            # 백슬래시가 이스케이프로 해석되어 경로가 망가진다.
            [ -x "$candidate" ] && { printf %s "$candidate"; return 0; }
            continue
        fi
        # compgen -G 는 결과를 줄 단위로 내보내므로 공백이 든 경로도 안전하다.
        while IFS= read -r match; do
            [ -x "$match" ] && { printf %s "$match"; return 0; }
        done < <(compgen -G "$candidate" 2>/dev/null)
    done
    return 1
}

# `type -P` 를 쓰는 이유: `command -v adb` 는 아래에서 정의하는 동명의 래퍼 *함수* 를
# 찾아내 자기 자신을 호출하는 무한 재귀가 된다. type -P 는 PATH 의 실행 파일만 본다.
_resolve_adb() {
    [ -n "${ADB_BIN:-}" ] && return 0
    ADB_BIN="$(type -P adb 2>/dev/null)" && [ -n "$ADB_BIN" ] && return 0
    ADB_BIN="$(_first_executable \
        "${ANDROID_HOME:-/nonexistent}/platform-tools/adb.exe" \
        "${ANDROID_HOME:-/nonexistent}/platform-tools/adb" \
        "${ANDROID_SDK_ROOT:-/nonexistent}/platform-tools/adb.exe" \
        "${ANDROID_SDK_ROOT:-/nonexistent}/platform-tools/adb" \
        "$LOCAL_APP_DATA/Android/Sdk/platform-tools/adb.exe" \
        "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe" \
        "$HOME/Library/Android/sdk/platform-tools/adb" \
        "$HOME/Android/Sdk/platform-tools/adb")" || return 1
}

# Pillow 가 import 되는 인터프리터를 우선한다. uiauto.py 의 bubble 명령이 Pillow 를
# 요구하는데, 윈도우에는 Microsoft Store 스텁 python3 처럼 패키지가 없는 것이 섞여 있다.
_resolve_python() {
    [ -n "${PYTHON_BIN:-}" ] && return 0
    local candidate fallback=""
    for candidate in python3 python; do
        type -P "$candidate" >/dev/null 2>&1 || continue
        local resolved
        resolved="$("$candidate" -c 'import sys; print(sys.executable)' 2>/dev/null)" || continue
        [ -n "$resolved" ] || continue
        [ -z "$fallback" ] && fallback="$resolved"
        if "$candidate" -c 'import PIL' >/dev/null 2>&1; then
            PYTHON_BIN="$resolved"; return 0
        fi
    done
    if type -P py >/dev/null 2>&1; then
        local launcher
        launcher="$(py -3 -c 'import sys; print(sys.executable)' 2>/dev/null)"
        if [ -n "$launcher" ]; then
            [ -z "$fallback" ] && fallback="$launcher"
            if py -3 -c 'import PIL' >/dev/null 2>&1; then PYTHON_BIN="$launcher"; return 0; fi
        fi
    fi
    [ -n "$fallback" ] || return 1
    PYTHON_BIN="$fallback"
}

_resolve_ffmpeg_tool() {
    local name="$1"
    _first_executable \
        "$(type -P "$name" 2>/dev/null || echo /nonexistent)" \
        "$LOCAL_APP_DATA/Microsoft/WinGet/Links/$name.exe" \
        "$LOCAL_APP_DATA/Microsoft/WinGet/Packages/Gyan.FFmpeg"*"/ffmpeg-"*"/bin/$name.exe" \
        "/c/ProgramData/chocolatey/bin/$name.exe" \
        "/opt/homebrew/bin/$name" \
        "/usr/local/bin/$name"
}

# 요구하는 도구를 모두 확보한다. 못 찾으면 설치 방법까지 알려주고 중단한다.
require_tools() {
    local tool
    for tool in "$@"; do
        case "$tool" in
            adb) _resolve_adb || die "adb 를 찾지 못했다. Android SDK platform-tools 를 설치하거나 ANDROID_HOME 을 설정하라." ;;
            python)
                _resolve_python || die "python 을 찾지 못했다."
                "$PYTHON_BIN" -c 'import PIL' >/dev/null 2>&1 \
                    || warn "Pillow 가 없다 (버블 탐지 불가): \"$PYTHON_BIN\" -m pip install Pillow"
                ;;
            ffmpeg) FFMPEG_BIN="$(_resolve_ffmpeg_tool ffmpeg)" || die "ffmpeg 를 찾지 못했다: winget install Gyan.FFmpeg (macOS: brew install ffmpeg)" ;;
            ffprobe) FFPROBE_BIN="$(_resolve_ffmpeg_tool ffprobe)" || die "ffprobe 를 찾지 못했다: winget install Gyan.FFmpeg (macOS: brew install ffmpeg)" ;;
            *) die "알 수 없는 도구 요구: $tool" ;;
        esac
    done
}

# ── 래퍼 ─────────────────────────────────────────────────────────────────────

# adb 원본을 가리는 래퍼. MSYS 경로 변환을 끄고, 로컬 측 인자만 직접 변환한다.
# pull/push/install 은 인자 위치에 따라 로컬/기기 경로가 갈리므로 개별 처리한다.
adb() {
    _resolve_adb || die "adb 를 찾지 못했다."
    local subcommand="${1:-}"
    case "$subcommand" in
        pull) shift; _adb_with_trailing_local pull "$@" ;;
        install) shift; _adb_with_trailing_local install "$@" ;;
        push) shift; _adb_push "$@" ;;
        *) MSYS2_ARG_CONV_EXCL='*' "$ADB_BIN" "$@" ;;
    esac
}

# 마지막 인자만 로컬 경로인 형태 (adb pull <remote> <local>, adb install <apk>).
_adb_with_trailing_local() {
    local subcommand="$1"; shift
    local args=("$@") last_index=$(($# - 1))
    if [ "$last_index" -ge 0 ]; then
        args[$last_index]="$(to_host_path "${args[$last_index]}")"
    fi
    MSYS2_ARG_CONV_EXCL='*' "$ADB_BIN" "$subcommand" "${args[@]}"
}

# adb push <local...> <remote>: 마지막만 기기 경로다.
_adb_push() {
    local args=() index=0 total=$#
    for value in "$@"; do
        index=$((index + 1))
        if [ "$index" -lt "$total" ]; then args+=("$(to_host_path "$value")"); else args+=("$value"); fi
    done
    MSYS2_ARG_CONV_EXCL='*' "$ADB_BIN" push "${args[@]}"
}

python_run() {
    _resolve_python || die "python 을 찾지 못했다."
    local script="$1"; shift
    "$PYTHON_BIN" "$(to_host_path "$script")" "$@"
}

# ffmpeg/ffprobe 는 네이티브 exe 이므로 파일 인자를 반드시 변환해서 넘긴다.
ffmpeg_run() { "${FFMPEG_BIN:?require_tools ffmpeg 를 먼저 호출하라}" "$@"; }
ffprobe_run() { "${FFPROBE_BIN:?require_tools ffprobe 를 먼저 호출하라}" "$@"; }

# ── 공용 유틸 ────────────────────────────────────────────────────────────────

# 종료 시 자동 정리되는 작업 디렉터리를 $WORK 에 만든다.
make_work_dir() {
    WORK="$(mktemp -d)"
    trap 'rm -rf "$WORK"' EXIT
}

require_single_device() {
    local count
    count="$(adb devices | grep -c 'device$' || true)"
    [ "$count" -ge 1 ] || die "연결된 기기가 없다 (adb devices 확인)"
    [ "$count" -eq 1 ] || die "기기가 $count 대 연결되어 있다. 1대만 남기거나 ANDROID_SERIAL 을 지정하라."
}

require_app_installed() {
    adb shell pm list packages | tr -d '\r' | grep -q "^package:$PACKAGE$" \
        || die "$PACKAGE 가 설치되어 있지 않다 (scripts/device.sh install)"
}

# 기기 dpi 를 dp→px 배율로 환산한다.
device_density_scale() {
    local dpi
    dpi="$(adb shell wm density | grep -oE '[0-9]+' | head -1)"
    [ -n "$dpi" ] || die "기기 density 를 읽지 못했다"
    awk -v dpi="$dpi" 'BEGIN { print dpi / 160 }'
}

# Gradle 실행 JDK 의 최소 버전. AGP 9 / Gradle 9 요구치다.
MINIMUM_JAVA_VERSION=17

# 후보 java 실행 파일을 줄 단위로 나열한다. 버전 비교는 호출부에서 한다.
_java_candidates() {
    local pattern
    for pattern in "${JAVA_CANDIDATE_PATTERNS[@]}"; do
        compgen -G "$pattern" 2>/dev/null || true
    done
}

JAVA_CANDIDATE_PATTERNS=(
    "/c/Program Files/Eclipse Adoptium/jdk-*/bin/java.exe"
    "/c/Program Files/Java/jdk-*/bin/java.exe"
    "/c/Program Files/Microsoft/jdk-*/bin/java.exe"
    "/c/Program Files/Android/Android Studio/jbr/bin/java.exe"
    "/Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java"
    "$HOME/.sdkman/candidates/java/*/bin/java"
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
    "${JAVA_HOME:-/nonexistent}/bin/java.exe"
    "${JAVA_HOME:-/nonexistent}/bin/java"
)

# JDK 의 feature 버전(17, 21, 25 ...)을 읽는다. release 파일을 먼저 보는 이유는
# 후보마다 java 를 실행하면 스크립트 시작이 눈에 띄게 느려지기 때문이다.
_java_feature_version() {
    local home="$1" version=""
    if [ -r "$home/release" ]; then
        version="$(grep -m1 '^JAVA_VERSION=' "$home/release" | cut -d'"' -f2 | cut -d. -f1)"
    fi
    if [ -z "$version" ] && [ -x "$home/bin/java" ]; then
        version="$("$home/bin/java" -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1)"
    fi
    # 1.8 형식은 '1' 이 되어 최소 버전 검사에서 자연히 걸러진다.
    case "$version" in
        "" | *[!0-9]*) return 1 ;;
    esac
    printf %s "$version"
}

# 설치된 것 중 가장 새 JDK 로 Gradle 을 돌린다. 보안 패치를 받는 것은 여기서 고른
# 이 JDK 바이너리이므로, 최신을 쓰는 편이 유지보수에 유리하다.
# 특정 버전을 강제하려면 JAVA_HOME_FOR_GRADLE 을 미리 지정한다.
# 특정 feature 버전의 JDK 가 설치되어 있는지 본다. build.gradle.kts 의
# jvmToolchain(N) 은 그 버전 JDK 가 없으면 빌드를 실패시킨다.
_java_toolchain_available() {
    local wanted="$1" binary home
    while IFS= read -r binary; do
        [ -x "$binary" ] || continue
        home="$(cd "$(dirname "$(dirname "$binary")")" && pwd)" || continue
        [ "$(_java_feature_version "$home" 2>/dev/null)" = "$wanted" ] && return 0
    done < <(_java_candidates)
    return 1
}

_resolve_java_home() {
    [ -n "${JAVA_HOME_FOR_GRADLE:-}" ] && return 0
    local binary home version best_home="" best_version=0
    while IFS= read -r binary; do
        [ -x "$binary" ] || continue
        home="$(cd "$(dirname "$(dirname "$binary")")" && pwd)" || continue
        version="$(_java_feature_version "$home")" || continue
        [ "$version" -ge "$MINIMUM_JAVA_VERSION" ] || continue
        if [ "$version" -gt "$best_version" ]; then
            best_version="$version"
            best_home="$home"
        fi
    done < <(_java_candidates)
    [ -n "$best_home" ] || return 1
    JAVA_HOME_FOR_GRADLE="$best_home"
    JAVA_VERSION_FOR_GRADLE="$best_version"
}

# Gradle 을 프로젝트 루트에서 실행한다. Windows 에서는 gradlew.bat 을 쓴다 —
# 유닉스 래퍼는 java 탐색 방식이 달라 Git Bash 에서 환경에 따라 실패한다.
# Android SDK 루트. local.properties 는 .gitignore 대상이라 기계마다 없을 수 있으므로,
# 이미 찾아 둔 adb 위치(<sdk>/platform-tools/adb)에서 역산해 환경변수로 넘긴다.
_resolve_android_home() {
    [ -n "${ANDROID_HOME_FOR_GRADLE:-}" ] && return 0
    local candidate
    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        if [ -n "$candidate" ] && [ -d "$candidate/platform-tools" ]; then
            ANDROID_HOME_FOR_GRADLE="$candidate"; return 0
        fi
    done
    _resolve_adb || return 1
    candidate="$(cd "$(dirname "$(dirname "$ADB_BIN")")" && pwd)"
    [ -d "$candidate/platform-tools" ] || return 1
    ANDROID_HOME_FOR_GRADLE="$candidate"
}

gradle_run() {
    _resolve_java_home || die "JDK 17 을 찾지 못했다: winget install EclipseAdoptium.Temurin.17.JDK"
    _resolve_android_home || die "Android SDK 를 찾지 못했다. ANDROID_HOME 을 설정하라."
    cd "$PROJECT_ROOT" || die "프로젝트 루트로 이동하지 못했다: $PROJECT_ROOT"
    local java_home_host sdk_home_host wrapper
    java_home_host="$(to_host_path "$JAVA_HOME_FOR_GRADLE")"
    sdk_home_host="$(to_host_path "$ANDROID_HOME_FOR_GRADLE")"
    if [ "$IS_WINDOWS" -eq 1 ]; then
        # cmd.exe 는 /c 로 받은 이름을 현재 디렉터리에서 찾지 않으므로 절대 경로로 넘긴다.
        wrapper="$(to_host_path "$PROJECT_ROOT/gradlew.bat")"
        JAVA_HOME="$java_home_host" ANDROID_HOME="$sdk_home_host" cmd.exe //c "$wrapper" "$@"
    else
        JAVA_HOME="$JAVA_HOME_FOR_GRADLE" ANDROID_HOME="$sdk_home_host" ./gradlew "$@"
    fi
}
