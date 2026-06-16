package online.fujinet.go.adam.ui

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import online.fujinet.go.adam.SessionController

// The ADAM/TMS9928 frame buffer rendered by adam_host.c (WIDTH x HEIGHT).
private const val FRAME_RATIO = 256f / 212f

/**
 * Hosts the ADAM video output. The native layer renders ADAMEm's RGB565 frames
 * directly into this SurfaceView's Surface (see session_runtime.cpp::OnFrame).
 *
 * The surface is sized to the emulator's aspect ratio and centered on black, so
 * the frame is letter-/pillar-boxed (never stretched) regardless of orientation
 * or how much room the layout gives it.
 */
@Composable
fun EmulatorSurface(
    session: SessionController,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Wider area than the frame -> bound by height (pillarbox);
        // otherwise bound by width (letterbox).
        val surfaceModifier = if (maxWidth / maxHeight > FRAME_RATIO) {
            Modifier.fillMaxHeight().aspectRatio(FRAME_RATIO)
        } else {
            Modifier.fillMaxWidth().aspectRatio(FRAME_RATIO)
        }

        AndroidView(
            modifier = surfaceModifier,
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            holder.requestFrameRate60()
                            session.attachSurface(holder.surface)
                            session.startIfNeeded()
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            holder.requestFrameRate60()
                            session.attachSurface(holder.surface)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            session.detachSurface()
                        }
                    })
                }
            },
        )
    }
}

// Tell the compositor this surface produces a fixed 60 fps. On a 120Hz /
// variable-refresh phone this makes the panel present at 60 (or a 60 multiple)
// instead of judder-mapping 60 fps content onto e.g. 90/120Hz.
private fun SurfaceHolder.requestFrameRate60() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface.isValid) {
        surface.setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
    }
}
