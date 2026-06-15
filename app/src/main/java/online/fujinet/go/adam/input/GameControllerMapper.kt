package online.fujinet.go.adam.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Maps a Bluetooth/USB game controller to the ADAM/ColecoVision digital joystick,
 * borrowing FujiNet Go 800's GameControllerMapper approach: read the analog stick
 * (AXIS_X/Y) with the d-pad hat (AXIS_HAT_X/Y) as a fallback, apply a deadzone,
 * and treat the two face buttons as the controller's two fire buttons. D-pads that
 * report as key events (KEYCODE_DPAD_*) are handled too. State is merged and
 * pushed via [onState]; returns true when an event was consumed.
 */
class GameControllerMapper(
    private val deadzone: Float = DEFAULT_DEADZONE,
    private val onState: (up: Boolean, down: Boolean, left: Boolean, right: Boolean, fireL: Boolean, fireR: Boolean) -> Unit,
) {
    private var axisX = 0f
    private var axisY = 0f
    private var hatUp = false
    private var hatDown = false
    private var hatLeft = false
    private var hatRight = false
    private var fireL = false
    private var fireR = false

    fun onMotion(event: MotionEvent): Boolean {
        if (!event.isFromController()) return false
        axisX = normalize(select(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_HAT_X)))
        axisY = normalize(select(event.getAxisValue(MotionEvent.AXIS_Y), event.getAxisValue(MotionEvent.AXIS_HAT_Y)))
        push()
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        if (!event.isFromController()) return false
        val pressed = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> hatUp = pressed
            KeyEvent.KEYCODE_DPAD_DOWN -> hatDown = pressed
            KeyEvent.KEYCODE_DPAD_LEFT -> hatLeft = pressed
            KeyEvent.KEYCODE_DPAD_RIGHT -> hatRight = pressed
            // Coleco/ADAM controllers have two fire buttons; map the primary face
            // buttons to them. A/X -> left fire, B/Y -> right fire.
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X -> fireL = pressed
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_Y -> fireR = pressed
            else -> return false
        }
        push()
        return true
    }

    private fun push() {
        onState(
            axisY <= -DIR_THRESHOLD || hatUp,
            axisY >= DIR_THRESHOLD || hatDown,
            axisX <= -DIR_THRESHOLD || hatLeft,
            axisX >= DIR_THRESHOLD || hatRight,
            fireL,
            fireR,
        )
    }

    private fun select(primary: Float, fallback: Float): Float =
        if (abs(primary) >= deadzone) primary else fallback

    private fun normalize(value: Float): Float =
        if (abs(value) < deadzone) 0f else value.coerceIn(-1f, 1f)

    private fun MotionEvent.isFromController(): Boolean =
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

    private fun KeyEvent.isFromController(): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private companion object {
        const val DEFAULT_DEADZONE = 0.3f
        const val DIR_THRESHOLD = 0.35f
    }
}
