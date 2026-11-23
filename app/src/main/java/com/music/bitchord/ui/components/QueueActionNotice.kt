package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.player.playerBarSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** A unique queue result, so repeating the same action restarts its lifetime. */
data class QueueActionNotice(
    val id: Int,
    val message: String,
)

/** Brief queue confirmation shown immediately above the mini player. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun QueueActionNoticeHost(
    notice: QueueActionNotice?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    hazeState: HazeState? = null,
    /** Styled like the mini player (frosted pill) rather than the player sheet's dark slab. */
    matchBar: Boolean = false,
    /** With [matchBar]: the refractive liquid glass the floating bars use, not a plain frost. */
    liquidGlass: Boolean = false,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            if (reduceAnimation) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn() + slideInVertically { height -> height / 2 }) togetherWith
                    (fadeOut() + slideOutVertically { height -> height / 2 })
            }
        },
        contentKey = { it?.id },
        label = "queueActionNotice",
        modifier = modifier.fillMaxWidth(),
    ) { current ->
        if (current != null && matchBar) {
            val shape = RoundedCornerShape(percent = 50)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(shape)
                    .then(
                        if (liquidGlass) {
                            Modifier.liquidGlass(shape = shape)
                        } else if (hazeState == null || reduceDynamicBlur) {
                            Modifier.background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier.optimizedHazeEffect(
                                state = hazeState,
                                style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                            )
                        },
                    )
                    .border(if (liquidGlass) GLASS_EDGE_WIDTH else 0.5.dp, if (liquidGlass) GLASS_EDGE_COLOR else Color.White.copy(alpha = 0.10f), shape)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        } else if (current != null) {
            Surface(
                color = if (hazeState != null) Color.Transparent else Color(0xFF282828),
                contentColor = Color.White,
                shape = RoundedCornerShape(if (hazeState != null) 16.dp else 5.dp),
                shadowElevation = if (hazeState != null) 0.dp else 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .then(
                        if (hazeState != null) Modifier.playerBarSurface(RoundedCornerShape(16.dp), hazeState)
                        else Modifier,
                    )
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = if (hazeState != null) 0.75f else 1f),
                        modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                    )
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) {
                            Text(actionLabel, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
