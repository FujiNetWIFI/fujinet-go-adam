package online.fujinet.go.adam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import online.fujinet.go.adam.input.GameControllerMapper
import online.fujinet.go.adam.ui.EmulatorScreen
import online.fujinet.go.adam.ui.theme.FujiNetGoAdamTheme

/**
 * FujiNet Go Adam main screen: the ADAM display plus on-screen controller and
 * keyboard. The native layer (ADAMEm + in-process FujiNet over AdamNet BoIP) is
 * owned by [EmulatorSessionService] (a foreground service) so it keeps running
 * across activity changes (e.g. the FujiNet web admin) and while backgrounded.
 * The session itself is a process singleton; the Shutdown button stops both.
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: SessionController

    // Routes Bluetooth/USB game controllers to joystick port 0.
    private val gamepad by lazy {
        GameControllerMapper { up, down, left, right, fireL, fireR ->
            session.joystick(0, up, down, left, right, fireL, fireR)
        }
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == EmulatorSessionService.ACTION_SHUTDOWN) {
            shutdown()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        session = SessionController.get(applicationContext)

        // Keep the emulator + FujiNet alive in the background.
        maybeRequestNotificationPermission()
        EmulatorSessionService.start(this)

        setContent {
            FujiNetGoAdamTheme {
                EmulatorScreen(session = session, onShutdown = ::shutdown)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == EmulatorSessionService.ACTION_SHUTDOWN) {
            shutdown()
        }
    }

    /** Stop the emulator + FujiNet and close the app. */
    private fun shutdown() {
        EmulatorSessionService.shutdown(this)
        finishAndRemoveTask()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (::session.isInitialized && gamepad.onMotion(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::session.isInitialized && gamepad.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    // Intentionally no session.stop() here: the foreground service owns the
    // session's lifetime so it survives this activity being finished (Back /
    // navigating away). Stopping is explicit, via shutdown().
}
