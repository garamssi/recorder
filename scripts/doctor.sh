#!/usr/bin/env bash
#
# 개발 환경 점검 (macOS / Windows Git Bash 겸용).
#
# 스크립트가 의존하는 것들이 실제로 동작하는지 확인한다. "설치되어 있는지"가 아니라
# "동작하는지"를 본다. Microsoft Store 의 python3 스텁처럼 존재하지만 쓸 수 없는
# 경우가 있고, Git Bash 의 인자 경로 변환처럼 조용히 결과만 틀어지는 문제도 있다.
#
# 사용법: scripts/doctor.sh
# 종료 코드: 0 = 모두 통과, 1 = 실패 항목 있음
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/env.sh"

PASSED=0
FAILED=0

pass() { printf '  \033[32m✓\033[0m %-26s %s\n' "$1" "${2:-}"; PASSED=$((PASSED + 1)); }
miss() { printf '  \033[31m✗\033[0m %-26s %s\n' "$1" "${2:-}"; FAILED=$((FAILED + 1)); }
skip() { printf '  \033[33m-\033[0m %-26s %s\n' "$1" "${2:-}"; }

echo
printf '\033[1m플랫폼\033[0m\n'
pass "OS" "$(uname -s) / $([ "$IS_WINDOWS" -eq 1 ] && echo 'Windows(Git Bash)' || echo 'POSIX')"

printf '\n\033[1m도구\033[0m\n'
if _resolve_adb; then pass "adb" "$ADB_BIN"; else miss "adb" "Android SDK platform-tools 설치 또는 ANDROID_HOME 설정"; fi

if _resolve_python; then
    pass "python" "$PYTHON_BIN"
    if "$PYTHON_BIN" -c 'import PIL' >/dev/null 2>&1; then
        pass "Pillow" "$("$PYTHON_BIN" -c 'import PIL; print(PIL.__version__)')"
    else
        miss "Pillow" "\"$PYTHON_BIN\" -m pip install Pillow  (버블 탐지에 필요)"
    fi
else
    miss "python" "python3 설치 필요"
    miss "Pillow" "python 부재"
fi

for tool in ffmpeg ffprobe; do
    if binary="$(_resolve_ffmpeg_tool "$tool")"; then
        pass "$tool" "$("$binary" -version 2>&1 | head -1 | cut -d' ' -f1-3)"
    else
        miss "$tool" "winget install Gyan.FFmpeg  (macOS: brew install ffmpeg)"
    fi
done

printf '\n\033[1m기기\033[0m\n'
if [ -n "${ADB_BIN:-}" ]; then
    DEVICE_COUNT="$(adb devices | grep -c 'device$')"
    if [ "$DEVICE_COUNT" -eq 1 ]; then
        pass "연결" "$(adb shell getprop ro.product.model | tr -d '\r') / Android $(adb shell getprop ro.build.version.release | tr -d '\r')"
    elif [ "$DEVICE_COUNT" -eq 0 ]; then
        miss "연결" "기기가 없다 (USB 디버깅 허용 확인)"
    else
        miss "연결" "$DEVICE_COUNT 대 연결됨 — 1대만 남기거나 ANDROID_SERIAL 지정"
    fi

    # 이 검사가 핵심이다. MSYS 가 /sdcard 를 로컬 경로로 바꿔 버리면 여기서 걸린다.
    if [ "$DEVICE_COUNT" -ge 1 ]; then
        if adb shell ls / | tr -d '\r' | grep -qx sdcard; then
            pass "기기 경로 전달" "adb 래퍼가 /sdcard 를 그대로 넘긴다"
        else
            miss "기기 경로 전달" "MSYS 경로 변환이 살아 있다 (lib/env.sh 의 adb 래퍼를 쓰고 있는지 확인)"
        fi

        if adb shell pm list packages | tr -d '\r' | grep -q "^package:$PACKAGE$"; then
            pass "앱 설치" "$PACKAGE"
        else
            miss "앱 설치" "scripts/device.sh install"
        fi
    else
        skip "기기 경로 전달" "기기 없음"
        skip "앱 설치" "기기 없음"
    fi
else
    skip "연결" "adb 없음"
fi

printf '\n\033[1m빌드\033[0m\n'
if _resolve_java_home; then
    pass "JDK (Gradle 실행용)" "$JAVA_VERSION_FOR_GRADLE — $JAVA_HOME_FOR_GRADLE"
else
    miss "JDK" "$MINIMUM_JAVA_VERSION 이상 필요: winget install EclipseAdoptium.Temurin.25.JDK (macOS: brew install --cask temurin)"
fi

# 순수 Kotlin 모듈이 jvmToolchain(25) 을 요구한다. Gradle 이 툴체인을 찾지 못하면
# 빌드가 실패하므로, 실행 JDK 와 별개로 여기서 확인한다.
if _java_toolchain_available 25; then
    pass "JDK 25 툴체인" "core:common / domain 컴파일용"
else
    miss "JDK 25 툴체인" "winget install EclipseAdoptium.Temurin.25.JDK"
fi

printf '\n통과 %d / 실패 %d\n\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ] || exit 1
