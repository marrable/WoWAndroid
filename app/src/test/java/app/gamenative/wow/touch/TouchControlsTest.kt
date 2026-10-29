package app.gamenative.wow.touch

import com.winlator.inputcontrols.ExternalController
import com.winlator.xserver.XKeycode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchControlsTest {
    private val sent = mutableListOf<String>()
    private lateinit var controls: TouchControls

    private val rb = ExternalController.IDX_BUTTON_R1.toInt()
    private val lb = ExternalController.IDX_BUTTON_L1.toInt()
    private val a = ExternalController.IDX_BUTTON_A.toInt()

    @Before
    fun setUp() {
        controls = TouchControls(object : TouchControls.Sink {
            override fun press(out: Out, down: Boolean) {
                val what = when (out) {
                    is Out.Pad -> "pad${out.index}"
                    is Out.DPad -> "dpad${out.direction}"
                    is Out.Key -> out.code.name
                }
                sent += "$what ${if (down) "down" else "up"}"
            }

            override fun leftStick(x: Float, y: Float) { sent += "left %.2f %.2f".format(x, y) }
            override fun rightStick(x: Float, y: Float) { sent += "right %.2f %.2f".format(x, y) }
            override fun haptic() = Unit
            override fun redraw() = Unit
        })
        controls.layout(0f, 0f, 2400f, 560f, 16f, 20f)
    }

    private fun center(label: String) = controls.visible.first { it.label == label }.rect.let { it.centerX() to it.centerY() }

    private fun tap(label: String, pointer: Int = 0, at: Long = 0) {
        val (x, y) = center(label)
        controls.down(pointer, x, y, at)
        controls.up(pointer, at + 50)
    }

    @Test
    fun stickVectorScalesToReachAndClampsToTheUnitCircle() {
        assertEquals(0.5f to 0f, TouchControls.stickVector(50f, 0f, 100f))
        val (x, y) = TouchControls.stickVector(300f, 400f, 100f)
        assertEquals(0.6f, x, 0.001f)
        assertEquals(0.8f, y, 0.001f)
        assertEquals(0f to 0f, TouchControls.stickVector(10f, 10f, 0f))
    }

    @Test
    fun onlyAShortStillPressCountsAsATap() {
        assertTrue(TouchControls.isTap(dragged = false, heldMs = 120))
        assertFalse(TouchControls.isTap(dragged = true, heldMs = 120))
        assertFalse(TouchControls.isTap(dragged = false, heldMs = 600))
    }

    @Test
    fun quickTapOnEnemyKeepsRbHeldUntilTappedAgain() {
        tap("Enemy")
        assertEquals(listOf("pad$rb down"), sent)
        assertEquals(Mode.ENEMY, controls.mode)
        assertTrue(controls.latched)
        assertTrue("mode buttons replace the action grid", controls.visible.any { it.label == "Last enemy" })
        assertFalse(controls.visible.any { it.label == "1" })

        tap("Enemy", at = 1000)
        assertEquals("pad$rb up", sent.last())
        assertNull(controls.mode)
        assertTrue(controls.visible.any { it.label == "1" })
    }

    @Test
    fun draggingOnEnemyAimsWithTheRightStickAndLetsGoOnRelease() {
        val (x, y) = center("Enemy")
        controls.down(3, x, y, 0)
        controls.moveTo(3, x + 5f, y)
        assertEquals("small moves don't aim", listOf("pad$rb down"), sent)
        controls.moveTo(3, x + 2000f, y)
        assertEquals("right 1.00 0.00", sent.last())
        controls.up(3, 800)
        assertEquals(listOf("right 0.00 0.00", "pad$rb up"), sent.takeLast(2))
        assertNull(controls.mode)
    }

    @Test
    fun pickingSelfInFriendModeTargetsAndEndsTheMode() {
        tap("Friend")
        tap("Self", pointer = 1, at = 1000)
        assertEquals(listOf("pad$lb down", "pad$a down", "pad$a up", "pad$lb up"), sent)
        assertNull(controls.mode)
    }

    @Test
    fun partyButtonsUseTheDpad() {
        tap("Friend")
        tap("Party 2", pointer = 1, at = 1000)
        assertEquals(listOf("pad$lb down", "dpad1 down", "dpad1 up", "pad$lb up"), sent)
    }

    @Test
    fun markerKeepsTheModeOn() {
        tap("Enemy")
        tap("Marker", pointer = 1, at = 1000)
        assertEquals(Mode.ENEMY, controls.mode)
    }

    @Test
    fun shiftSetHoldsShiftAroundTheActionKey() {
        tap("Shift")
        assertTrue(controls.shiftOn)
        tap("S1", pointer = 1)
        assertEquals(listOf("KEY_SHIFT_L down", "KEY_1 down", "KEY_1 up", "KEY_SHIFT_L up"), sent)
    }

    @Test
    fun actionButtonsSendNumberKeys() {
        tap("=")
        assertEquals(listOf("${XKeycode.KEY_EQUAL.name} down", "${XKeycode.KEY_EQUAL.name} up"), sent)
    }

    @Test
    fun moveStickReturnsToCentreOnRelease() {
        val (x, y) = center("Move")
        controls.down(0, x, y, 0)
        controls.moveTo(0, x, y - 1000f)
        assertEquals("sliding off the stick keeps it at full tilt", "left 0.00 -1.00", sent.last())
        controls.up(0, 100)
        assertEquals("left 0.00 0.00", sent.last())
    }

    @Test
    fun gameHeightFollowsTheShapeButLeavesRoomForControls() {
        assertEquals(1224, TouchPane.gameHeight(2448, 1848, 58, 2f))
        // A shape too tall for the screen still leaves a fifth of it for the controls.
        assertEquals(1848 - 58 - 369, TouchPane.gameHeight(2448, 1848, 58, 1f))
        assertEquals(0, TouchPane.gameHeight(0, 1848, 58, 2f))
    }

    @Test
    fun renderSizeKeepsThePixelBudgetAtTheGamesShape() {
        val (w, h) = TouchPaneSettings.renderSize("1280x720", 2448, 2f).split("x").map { it.toInt() }
        assertEquals(2f, w.toFloat() / h, 0.01f)
        assertEquals(1280 * 720f, (w * h).toFloat(), 1280 * 720 * 0.01f)
    }
}
