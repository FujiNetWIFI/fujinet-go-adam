package online.fujinet.go.adam.ui

import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Analog thumb-stick, borrowed from FujiNet Go 800's JoystickPadControl. Drag
 * within the pad; the position is converted to the ADAM/ColecoVision digital
 * 8-way joystick (diagonals = two directions) and pushed to [controller].
 * Releasing recenters. A single owning pointer, so a second finger on the fire
 * button never steals the stick.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JoystickPad(controller: Controller, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var nub by remember { mutableStateOf(PointF(0f, 0f)) }
    var pointerId by remember { mutableStateOf<Int?>(null) }

    fun reset() {
        pointerId = null
        nub = PointF(0f, 0f)
        controller.move(up = false, down = false, left = false, right = false)
    }

    fun apply(px: Float, py: Float) {
        val ax = axis(px, padSize.width)
        val ay = axis(py, padSize.height)
        nub = PointF(ax, ay)
        controller.move(
            up = ay <= -DIR_THRESHOLD,
            down = ay >= DIR_THRESHOLD,
            left = ax <= -DIR_THRESHOLD,
            right = ax >= DIR_THRESHOLD,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
            .onSizeChanged { padSize = it }
            .pointerInteropFilter { e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        if (pointerId == null) {
                            pointerId = e.getPointerId(e.actionIndex)
                            apply(e.getX(e.actionIndex), e.getY(e.actionIndex))
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pid = pointerId ?: return@pointerInteropFilter false
                        val idx = e.findPointerIndex(pid)
                        if (idx < 0) {
                            reset()
                            return@pointerInteropFilter true
                        }
                        apply(e.getX(idx), e.getY(idx))
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (e.getPointerId(e.actionIndex) == pointerId) reset()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        reset()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize(0.3f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        )
        val travel = min(padSize.width, padSize.height) * 0.2f
        Box(
            Modifier
                .offset { IntOffset((nub.x * travel).roundToInt(), (nub.y * travel).roundToInt()) }
                .fillMaxSize(0.24f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
        )
    }
}

private const val DIR_THRESHOLD = 0.35f
private const val DEADZONE = 0.15f

/** Normalize a touch coordinate within [extent] to -1..1 with a centre deadzone. */
private fun axis(value: Float, extent: Int): Float {
    if (extent == 0) return 0f
    val half = extent / 2f
    val n = ((value - half) / half).coerceIn(-1f, 1f)
    return if (abs(n) < DEADZONE) 0f else n
}
