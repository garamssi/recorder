#!/usr/bin/env bash
#
# 녹화·녹음 파일의 오디오 검증 (기능명세서 4.2절).
#
# 기기에 남은 최신 결과물(또는 지정한 파일)을 받아 오디오 트랙이 실제로 소리를 담았는지
# 확인한다. 무음 트랙은 -90dB 근처이므로 임계값보다 조용하면 실패로 본다.
#
# 내부 오디오(AudioPlaybackCapture)나 블루투스 마이크(SCO) 경로가 조용히 깨졌을 때
# 눈으로 영상을 재생해 보지 않고도 잡아내는 것이 목적이다.
#
# 사용법:
#   scripts/verify-recording-audio.sh                       # 최신 녹화(mp4) 검증
#   scripts/verify-recording-audio.sh -t voice              # 최신 음성 녹음(m4a) 검증
#   scripts/verify-recording-audio.sh -f /sdcard/Movies/ScreenRecorder/Rec_x.mp4
#   scripts/verify-recording-audio.sh -d -45                # 평균 임계값을 -45dB로
#
# 종료 코드: 0 = 통과, 1 = 무음/오디오 트랙 없음(실패), 2 = 준비 오류
set -euo pipefail

VIDEO_DIR="/sdcard/Movies/ScreenRecorder"
VOICE_DIR="/sdcard/Music/ScreenRecorder"
KIND="video"
REMOTE_FILE=""
# 무음 트랙은 -91dB 내외로 측정된다. 조용한 실내 마이크도 -60dB 이상이므로 -80dB을 경계로 둔다.
MEAN_THRESHOLD_DB=-80
KEEP_FILE=0

usage() {
    sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

while getopts "t:f:d:kh" opt; do
    case "$opt" in
        t) KIND="$OPTARG" ;;
        f) REMOTE_FILE="$OPTARG" ;;
        d) MEAN_THRESHOLD_DB="$OPTARG" ;;
        k) KEEP_FILE=1 ;;
        *) usage ;;
    esac
done

fail() {
    echo "실패: $*" >&2
    exit 1
}

abort() {
    echo "오류: $*" >&2
    exit 2
}

command -v adb >/dev/null || abort "adb를 찾을 수 없다 (PATH에 platform-tools 추가)"
command -v ffprobe >/dev/null || abort "ffprobe를 찾을 수 없다 (brew install ffmpeg)"
command -v ffmpeg >/dev/null || abort "ffmpeg를 찾을 수 없다 (brew install ffmpeg)"
[ "$(adb devices | grep -c "device$")" -ge 1 ] || abort "연결된 기기가 없다"

if [ -z "$REMOTE_FILE" ]; then
    case "$KIND" in
        video) SEARCH_DIR="$VIDEO_DIR"; EXTENSION="mp4" ;;
        voice) SEARCH_DIR="$VOICE_DIR"; EXTENSION="m4a" ;;
        *) abort "알 수 없는 종류: $KIND (video|voice)" ;;
    esac
    NEWEST=$(adb shell "ls -t $SEARCH_DIR/*.$EXTENSION 2>/dev/null | head -1" | tr -d '\r')
    [ -n "$NEWEST" ] || abort "$SEARCH_DIR 에 .$EXTENSION 파일이 없다"
    REMOTE_FILE="$NEWEST"
fi

LOCAL_FILE="$(mktemp -d)/$(basename "$REMOTE_FILE")"
adb pull "$REMOTE_FILE" "$LOCAL_FILE" >/dev/null 2>&1 || abort "파일을 받지 못했다: $REMOTE_FILE"
trap '[ "$KEEP_FILE" -eq 1 ] || rm -rf "$(dirname "$LOCAL_FILE")"' EXIT

echo "검증 대상: $REMOTE_FILE"

AUDIO_INFO=$(ffprobe -v error -select_streams a:0 \
    -show_entries stream=codec_name,sample_rate,channels -of csv=p=0 "$LOCAL_FILE")
[ -n "$AUDIO_INFO" ] || fail "오디오 트랙이 없다"
echo "오디오 트랙: $AUDIO_INFO"

LEVELS=$(ffmpeg -hide_banner -nostats -i "$LOCAL_FILE" -af volumedetect -f null /dev/null 2>&1)
MEAN_DB=$(echo "$LEVELS" | sed -n 's/.*mean_volume: \(-\{0,1\}[0-9.]*\) dB.*/\1/p')
MAX_DB=$(echo "$LEVELS" | sed -n 's/.*max_volume: \(-\{0,1\}[0-9.]*\) dB.*/\1/p')
[ -n "$MEAN_DB" ] || abort "볼륨을 측정하지 못했다"

echo "평균 ${MEAN_DB}dB / 최대 ${MAX_DB}dB (임계값 ${MEAN_THRESHOLD_DB}dB)"
if awk -v mean="$MEAN_DB" -v limit="$MEAN_THRESHOLD_DB" 'BEGIN { exit !(mean < limit) }'; then
    fail "오디오가 사실상 무음이다 (평균 ${MEAN_DB}dB)"
fi

echo "통과: 오디오가 정상적으로 녹음되었다"
