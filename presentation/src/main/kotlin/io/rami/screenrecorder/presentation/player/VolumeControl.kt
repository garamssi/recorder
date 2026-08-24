package io.rami.screenrecorder.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.MediaVolume
import io.rami.screenrecorder.presentation.R

/**
 * 플레이어 볼륨 컨트롤 (기능명세서 10절).
 *
 * 시스템 미디어 볼륨에 연동되므로 하드웨어 볼륨 키로 바꾼 값도 그대로 반영된다.
 * 음소거 버튼과 슬라이더를 하나의 pill 안에 묶어 영상 위에서도 한 덩어리로 읽히게 한다.
 */
@Composable
internal fun VolumeControl(
    volume: MediaVolume,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = VOLUME_PILL_ALPHA))
                .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlassIconButton(
            icon = if (volume.isSilent) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription =
                stringResource(if (volume.isSilent) R.string.player_unmute else R.string.player_mute),
            onClick = onToggleMute,
        )
        Slider(
            // 음소거 중에는 단계가 남아 있어도 0으로 보여 준다 — 실제로 소리가 안 나기 때문이다.
            value = if (volume.isMuted) 0f else volume.fraction,
            onValueChange = onVolumeChange,
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = VOLUME_TRACK_ALPHA),
                ),
            modifier = Modifier.width(VOLUME_SLIDER_WIDTH),
        )
    }
}

private val VOLUME_SLIDER_WIDTH = 140.dp
private const val VOLUME_PILL_ALPHA = 0.15f
private const val VOLUME_TRACK_ALPHA = 0.35f
