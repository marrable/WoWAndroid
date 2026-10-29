package app.gamenative.wow.touch

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
class ChatKeyboardTest {
    private val sent = mutableListOf<String>()
    private lateinit var keyboard: ChatKeyboard

    @Before
    fun setUp() {
        keyboard = ChatKeyboard(object : ChatKeyboard.Sink {
            override fun key(code: XKeycode, shift: Boolean, down: Boolean) {
                sent += "${if (shift) "shift+" else ""}${code.name} ${if (down) "down" else "up"}"
            }

            override fun type(text: String) { sent += "type[$text]" }
            override fun haptic() = Unit
            override fun redraw() = Unit
        })
        keyboard.layout(0f, 0f, 2400f, 560f, 10f)
    }

    private fun tap(label: String, pointer: Int = 0) {
        val r = keyboard.visible.first { it.label == label }.rect
        keyboard.down(pointer, r.centerX(), r.centerY())
        keyboard.up(pointer)
    }

    @Test
    fun charactersMapToUsKeys() {
        assertEquals(XKeycode.KEY_A to false, ChatKeyboard.codeFor('a'))
        assertEquals(XKeycode.KEY_A to true, ChatKeyboard.codeFor('A'))
        assertEquals(XKeycode.KEY_1 to true, ChatKeyboard.codeFor('!'))
        assertEquals(XKeycode.KEY_0 to true, ChatKeyboard.codeFor(')'))
        assertEquals(XKeycode.KEY_SLASH to false, ChatKeyboard.codeFor('/'))
        assertEquals(XKeycode.KEY_APOSTROPHE to true, ChatKeyboard.codeFor('"'))
        assertEquals(XKeycode.KEY_ENTER to false, ChatKeyboard.codeFor('\n'))
        assertNull("no key for characters outside a US layout", ChatKeyboard.codeFor('é'))
    }

    @Test
    fun lettersPressAndRelease() {
        tap("h")
        tap("i", pointer = 1)
        assertEquals(listOf("KEY_H down", "KEY_H up", "KEY_I down", "KEY_I up"), sent)
    }

    @Test
    fun shiftAppliesToTheNextLetterOnly() {
        tap("Shift")
        assertTrue(keyboard.shift)
        tap("H")
        tap("i")
        assertEquals(listOf("shift+KEY_H down", "KEY_H up", "KEY_I down", "KEY_I up"), sent)
        assertFalse(keyboard.shift)
    }

    @Test
    fun channelButtonsTypeTheirSlashCommand() {
        tap("Party")
        assertEquals(listOf("type[/p ]"), sent)
    }

    @Test
    fun emotesSendAtOnce() {
        tap("Wave")
        assertEquals(listOf("type[/wave\n]"), sent)
    }

    @Test
    fun symbolSetSendsShiftedDigits() {
        tap("123")
        assertTrue(keyboard.symbols)
        tap("!")
        assertEquals(listOf("shift+KEY_1 down", "KEY_1 up"), sent)
        tap("abc")
        assertFalse(keyboard.symbols)
        assertTrue(keyboard.visible.any { it.label == "q" })
    }

    @Test
    fun enterAndEscAreKeys() {
        tap("Enter")
        tap("Esc")
        assertEquals(listOf("KEY_ENTER down", "KEY_ENTER up", "KEY_ESC down", "KEY_ESC up"), sent)
    }
}
