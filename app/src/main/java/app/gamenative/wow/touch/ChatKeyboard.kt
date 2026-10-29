package app.gamenative.wow.touch

import android.graphics.RectF
import com.winlator.xserver.XKeycode

/**
 * The chat keyboard: letters split between the two thumbs, and a middle column of chat channels, emotes, Esc and
 * Space. Keys go to the game as US-layout key presses. Channel buttons type their slash command, which also opens
 * WoW's chat box, so nothing is pressed when the keyboard opens (Enter there would log in on the login screen).
 */
internal class ChatKeyboard(private val sink: Sink) {
    interface Sink {
        /** Presses or releases one key, holding Shift around the press when [shift] is set. */
        fun key(code: XKeycode, shift: Boolean, down: Boolean)

        /** Types a whole string, key by key. */
        fun type(text: String)
        fun haptic()
        fun redraw()
    }

    class Key(val id: String, var label: String, val wide: Float = 1f, val quiet: Boolean = false) {
        val rect = RectF()
        var held = false
    }

    private val layers = mapOf(
        false to (keys(LEFT_ABC) to keys(RIGHT_ABC)),
        true to (keys(LEFT_SYM) to keys(RIGHT_SYM)),
    )
    private val middle = listOf(
        CHANNELS.map { (id, label, _) -> Key(id, label, quiet = true) },
        EMOTES.map { (id, label) -> Key(id, label, quiet = true) },
        listOf(Key("Esc", "Esc", quiet = true), Key("Space", "Space", wide = 3f)),
    )

    var symbols = false
        private set
    var shift = false
        private set

    private val pointers = HashMap<Int, Key>()

    val visible: List<Key>
        get() = layers.getValue(symbols).let { (l, r) -> l.flatten() + r.flatten() } + middle.flatten()

    fun layout(left: Float, top: Float, right: Float, bottom: Float, gap: Float) {
        val width = right - left
        val mid = width * 0.26f
        val half = (width - mid - gap * 2) / 2
        layers.values.forEach { (l, r) ->
            rows(l, left, top, left + half, bottom, gap)
            rows(r, right - half, top, right, bottom, gap)
        }
        rows(middle, left + half + gap, top, right - half - gap, bottom, gap)
    }

    fun down(pointer: Int, x: Float, y: Float) {
        val key = visible.lastOrNull { it.rect.contains(x, y) } ?: return
        pointers[pointer] = key
        sink.haptic()
        key.held = true
        when (key.id) {
            "Shift" -> { shift = !shift; relabel() }
            "123", "abc" -> { symbols = !symbols; shift = false; relabel() }
            else -> {
                CHANNELS.firstOrNull { it.first == key.id }?.let { sink.type(it.third); return sink.redraw() }
                EMOTES.firstOrNull { it.first == key.id }?.let { sink.type("/${it.first}\n"); return sink.redraw() }
                val (code, shifted) = codeFor(key.id) ?: return
                sink.key(code, shifted || (shift && key.id.length == 1), true)
                if (shift && key.id.length == 1) { shift = false; relabel() }
            }
        }
        sink.redraw()
    }

    fun up(pointer: Int) {
        val key = pointers.remove(pointer) ?: return
        // Shift stays lit while it's set for the next letter.
        key.held = key.id == "Shift" && shift
        codeFor(key.id)?.let { (code, _) -> sink.key(code, false, false) }
        sink.redraw()
    }

    fun cancelAll() {
        pointers.values.forEach { key -> key.held = false; codeFor(key.id)?.let { sink.key(it.first, false, false) } }
        pointers.clear()
        sink.redraw()
    }

    private fun relabel() {
        layers.values.forEach { (l, r) ->
            (l.flatten() + r.flatten()).forEach { k ->
                if (k.id.length == 1 && k.id[0].isLetter()) k.label = if (shift) k.id.uppercase() else k.id
                if (k.id == "Shift") k.held = shift
            }
        }
    }

    companion object {
        private val LEFT_ABC = listOf("q w e r t", "a s d f g", "Shift z x c v b")
        private val RIGHT_ABC = listOf("y u i o p", "h j k l Back", "n m . 123 Enter")
        private val LEFT_SYM = listOf("1 2 3 4 5", "- / : ; (", "Shift @ # & * _")
        private val RIGHT_SYM = listOf("6 7 8 9 0", ") ! ? ' Back", ", \" . abc Enter")
        private val WIDE = mapOf("Shift" to 1.5f, "Back" to 1.5f, "Enter" to 1.6f, "123" to 1.2f, "abc" to 1.2f)
        private val QUIET = setOf("Shift", "Back", "Enter", "123", "abc")

        /** Channel buttons: id, label, what they type. A leading slash opens the chat box in WoW. */
        val CHANNELS = listOf(
            Triple("say", "Say", "/s "),
            Triple("party", "Party", "/p "),
            Triple("guild", "Guild", "/g "),
            Triple("whisper", "Whisper", "/w "),
            Triple("reply", "Reply", "/r "),
        )
        val EMOTES = listOf("wave" to "Wave", "thank" to "Thank", "cheer" to "Cheer", "lol" to "Lol", "bow" to "Bow")

        private fun keys(rows: List<String>) = rows.map { row ->
            row.split(" ").map { id -> Key(id, id, WIDE[id] ?: 1f, quiet = id in QUIET) }
        }

        /** The US-layout key, and whether it needs Shift, for a key id or a typed character. */
        fun codeFor(id: String): Pair<XKeycode, Boolean>? = when (id) {
            "Back" -> XKeycode.KEY_BKSP to false
            "Enter" -> XKeycode.KEY_ENTER to false
            "Esc" -> XKeycode.KEY_ESC to false
            "Space" -> XKeycode.KEY_SPACE to false
            else -> id.singleOrNull()?.let(::codeFor)
        }

        fun codeFor(c: Char): Pair<XKeycode, Boolean>? {
            if (c in 'a'..'z') return XKeycode.valueOf("KEY_${c.uppercaseChar()}") to false
            if (c in 'A'..'Z') return XKeycode.valueOf("KEY_$c") to true
            if (c in '0'..'9') return XKeycode.valueOf("KEY_$c") to false
            SHIFTED_DIGITS.indexOf(c).takeIf { it >= 0 }?.let { return XKeycode.valueOf("KEY_${(it + 1) % 10}") to true }
            return SYMBOLS[c]
        }

        private const val SHIFTED_DIGITS = "!@#$%^&*()"
        private val SYMBOLS = mapOf(
            ' ' to (XKeycode.KEY_SPACE to false), '\n' to (XKeycode.KEY_ENTER to false),
            '-' to (XKeycode.KEY_MINUS to false), '_' to (XKeycode.KEY_MINUS to true),
            '=' to (XKeycode.KEY_EQUAL to false), '+' to (XKeycode.KEY_EQUAL to true),
            '[' to (XKeycode.KEY_BRACKET_LEFT to false), '{' to (XKeycode.KEY_BRACKET_LEFT to true),
            ']' to (XKeycode.KEY_BRACKET_RIGHT to false), '}' to (XKeycode.KEY_BRACKET_RIGHT to true),
            ';' to (XKeycode.KEY_SEMICOLON to false), ':' to (XKeycode.KEY_SEMICOLON to true),
            '\'' to (XKeycode.KEY_APOSTROPHE to false), '"' to (XKeycode.KEY_APOSTROPHE to true),
            ',' to (XKeycode.KEY_COMMA to false), '<' to (XKeycode.KEY_COMMA to true),
            '.' to (XKeycode.KEY_PERIOD to false), '>' to (XKeycode.KEY_PERIOD to true),
            '/' to (XKeycode.KEY_SLASH to false), '?' to (XKeycode.KEY_SLASH to true),
            '\\' to (XKeycode.KEY_BACKSLASH to false), '|' to (XKeycode.KEY_BACKSLASH to true),
            '`' to (XKeycode.KEY_GRAVE to false), '~' to (XKeycode.KEY_GRAVE to true),
        )

        private fun rows(rows: List<List<Key>>, l: Float, t: Float, r: Float, b: Float, gap: Float) {
            val h = (b - t - gap * (rows.size - 1)) / rows.size
            rows.forEachIndexed { i, row ->
                val y = t + i * (h + gap)
                val unit = (r - l - gap * (row.size - 1)) / row.sumOf { it.wide.toDouble() }.toFloat()
                var x = l
                row.forEach { k ->
                    k.rect.set(x, y, x + unit * k.wide, y + h)
                    x += unit * k.wide + gap
                }
            }
        }
    }
}
