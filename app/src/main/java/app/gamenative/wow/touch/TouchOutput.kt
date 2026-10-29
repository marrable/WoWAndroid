package app.gamenative.wow.touch

import com.winlator.inputcontrols.ExternalController
import com.winlator.inputcontrols.GamepadState
import com.winlator.widget.InputControlsView
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer

/**
 * Sends the pane's input to the game: gamepad state through upstream's virtual gamepad (the WoW Touch
 * profile's GamepadState, as InputControlsView does it), keys straight to the X server. Remembers what is
 * held so [releaseAll] can let go of everything when the pane hides or goes away.
 */
class TouchOutput(private val xServer: XServer, private val inputControls: InputControlsView) {
    private val heldKeys = linkedSetOf<XKeycode>()

    private val state: GamepadState? get() = inputControls.profile?.gamepadState

    fun button(index: Int, down: Boolean) {
        val s = state ?: return
        when (index) {
            ExternalController.IDX_BUTTON_L2.toInt() -> s.triggerL = if (down) 1f else 0f
            ExternalController.IDX_BUTTON_R2.toInt() -> s.triggerR = if (down) 1f else 0f
        }
        s.setPressed(index, down)
        commit()
    }

    /** [direction] is 0 up, 1 right, 2 down, 3 left, as in [GamepadState.dpad]. */
    fun dpad(direction: Int, down: Boolean) {
        val s = state ?: return
        s.dpad[direction] = down
        commit()
    }

    /** Axes run from -1 to 1, screen style: up and left are negative. */
    fun leftStick(x: Float, y: Float) = stick(false, x, y)

    fun rightStick(x: Float, y: Float) = stick(true, x, y)

    private fun stick(right: Boolean, x: Float, y: Float) {
        val s = state ?: return
        if (s.updateThumbstick(right, x, y)) commit()
    }

    fun key(code: XKeycode, down: Boolean) {
        if (down) {
            if (heldKeys.add(code)) xServer.injectKeyPress(code)
        } else if (heldKeys.remove(code)) {
            xServer.injectKeyRelease(code)
        }
    }

    /** Presses [code] with Shift held around the press, as a shifted character needs, leaving only [code] down. */
    fun keyDown(code: XKeycode, shift: Boolean) {
        val wrap = shift && XKeycode.KEY_SHIFT_L !in heldKeys
        if (wrap) xServer.injectKeyPress(XKeycode.KEY_SHIFT_L)
        key(code, true)
        if (wrap) xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L)
    }

    /** Types [text] key by key on a US layout; characters it has no key for are skipped. */
    fun type(text: String) {
        for (c in text) {
            val (code, shift) = ChatKeyboard.codeFor(c) ?: continue
            keyDown(code, shift)
            key(code, false)
        }
    }

    fun releaseAll() {
        heldKeys.toList().forEach { key(it, false) }
        val s = state ?: return
        s.buttons = 0
        s.dpad.fill(false)
        s.thumbLX = 0f; s.thumbLY = 0f; s.thumbRX = 0f; s.thumbRY = 0f
        s.triggerL = 0f; s.triggerR = 0f
        commit()
    }

    private fun commit() {
        val s = state ?: return
        val winHandler = xServer.winHandler ?: return
        winHandler.sendGamepadState()
        winHandler.sendVirtualGamepadState(s)
    }
}
