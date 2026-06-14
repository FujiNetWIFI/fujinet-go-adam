package online.fujinet.go.adam.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import online.fujinet.go.adam.SessionController

/**
 * Hosts the ADAM video output. The native layer renders ADAMEm's RGB565 frames
 * directly into this SurfaceView's Surface (see session_runtime.cpp::OnFrame),
 * so Compose only has to own attach/detach and start the session once the
 * surface exists.
 */
@Composable
fun EmulatorSurface(
    session: SessionController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        session.attachSurface(holder.surface)
                        session.startIfNeeded()
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
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
