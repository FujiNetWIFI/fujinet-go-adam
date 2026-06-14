package com.mantismoonlabs.fujinetgoadam

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mantismoonlabs.fujinetgoadam.ui.EmulatorScreen
import com.mantismoonlabs.fujinetgoadam.ui.theme.FujiNetGoAdamTheme

/**
 * FujiNet Go Adam main screen: the ADAM display plus on-screen controller and
 * keyboard. The native layer (ADAMEm + in-process FujiNet over AdamNet BoIP)
 * is started once the emulator surface exists.
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: SessionController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        session = SessionController(applicationContext)
        setContent {
            FujiNetGoAdamTheme {
                EmulatorScreen(session = session)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            session.stop()
        }
    }
}
