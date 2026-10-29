package app.gamenative.wow.touch

import android.graphics.RectF
import com.winlator.inputcontrols.ExternalController
import com.winlator.xserver.XKeycode
import kotlin.math.hypot
import kotlin.math.min

/** What the pane sends for a touch: a gamepad button, a D-pad direction or a key. */
internal sealed interface Out {
    data class Pad(val index: Int) : Out
    data class DPad(val direction: Int) : Out
    data class Key(val code: XKeycode) : Out
}

/** Forever's two targeting modes, entered by holding a bumper. */
internal enum class Mode(val bumper: Int) {
    ENEMY(ExternalController.IDX_BUTTON_R1.toInt()),
    FRIEND(ExternalController.IDX_BUTTON_L1.toInt()),
}

/** A button shown while a targeting mode is on. [picks] ends the mode once released. */
internal data class ModeAction(val label: String, val sub: String, val out: Out, val picks: Boolean)

internal enum class Kind { MOVE, LOOK, BUTTON, SLOT, SHIFT, TARGET, MODE_ACTION }

internal class Control(
    val kind: Kind,
    label: String,
    sub: String = "",
    val out: Out? = null,
    val mode: Mode? = null,
    val action: ModeAction? = null,
) {
    var label = label
        private set
    var sub = sub
        private set
    val rect = RectF()
    var held = false

    fun relabel(label: String = this.label, sub: String = this.sub) {
        this.label = label
        this.sub = sub
    }
}

/**
 * Layout and touch logic of the controls view, kept apart from the Android view so it can be tested.
 * Follows the mockup: Move stick, Jump/Clear/Auto run, a utility row over twelve action buttons, Use/Enemy/Friend,
 * Look stick. Enemy and Friend hold RB or LB: dragging on them works the right stick (aiming), a quick tap keeps
 * the mode on, and while a mode is on its buttons replace the action grid and Look aims.
 */
internal class TouchControls(private val sink: Sink) {
    interface Sink {
        fun press(out: Out, down: Boolean)
        fun leftStick(x: Float, y: Float)
        fun rightStick(x: Float, y: Float)
        fun haptic()
        fun redraw()
    }

    val move = Control(Kind.MOVE, "Move")
    val look = Control(Kind.LOOK, "Look")
    private val leftColumn = listOf(
        Control(Kind.BUTTON, "Jump", out = Out.Pad(ExternalController.IDX_BUTTON_A.toInt())),
        Control(Kind.BUTTON, "Clear", out = Out.Pad(ExternalController.IDX_BUTTON_B.toInt())),
        Control(Kind.BUTTON, "Auto run", out = Out.Key(XKeycode.KEY_NUM_LOCK)),
    )
    private val rightColumn = listOf(
        Control(Kind.BUTTON, "Use", out = Out.Pad(ExternalController.IDX_BUTTON_X.toInt())),
        Control(Kind.TARGET, "Enemy", "drag to aim", mode = Mode.ENEMY),
        Control(Kind.TARGET, "Friend", "drag to aim", mode = Mode.FRIEND),
    )
    val shift = Control(Kind.SHIFT, "Shift")
    private val utility = listOf(
        shift,
        Control(Kind.BUTTON, "Bags", out = Out.Key(XKeycode.KEY_B)),
        Control(Kind.BUTTON, "Map", out = Out.Key(XKeycode.KEY_M)),
        Control(Kind.BUTTON, "Char", out = Out.Key(XKeycode.KEY_C)),
        Control(Kind.BUTTON, "Spells", out = Out.Key(XKeycode.KEY_P)),
        Control(Kind.BUTTON, "Menu", out = Out.Pad(ExternalController.IDX_BUTTON_Y.toInt())),
    )
    private val slots = SLOT_KEYS.map { (label, code) -> Control(Kind.SLOT, label, out = Out.Key(code)) }
    private val modeControls = Mode.entries.associateWith { m -> actionsFor(m).map { Control(Kind.MODE_ACTION, it.label, it.sub, action = it) } }

    /** Knob offsets, -1 to 1, for drawing. */
    var moveKnob = 0f to 0f
        private set
    var lookKnob = 0f to 0f
        private set

    var shiftOn = false
        private set
    var mode: Mode? = null
        private set
    var latched = false
        private set

    private var modePointer = -1
    private var modeDownX = 0f
    private var modeDownY = 0f
    private var modeDownAt = 0L
    private var modeDragged = false
    private var aimReach = 1f
    private var slop = 1f

    private val pointers = HashMap<Int, Control>()

    /** Everything drawn right now, in draw order. */
    val visible: List<Control>
        get() = buildList {
            add(move); add(look)
            addAll(leftColumn); addAll(rightColumn); addAll(utility)
            addAll(mode?.let { modeControls.getValue(it) } ?: slots)
        }

    fun layout(left: Float, top: Float, right: Float, bottom: Float, gap: Float, slopPx: Float) {
        slop = slopPx
        val width = right - left
        val height = bottom - top
        val stick = min(height, width * 0.2f)
        val column = width * 0.095f
        val cy = (top + bottom) / 2
        move.rect.set(left, cy - stick / 2, left + stick, cy + stick / 2)
        look.rect.set(right - stick, cy - stick / 2, right, cy + stick / 2)
        aimReach = stick / 2

        val leftX = move.rect.right + gap
        stack(leftColumn, leftX, top, leftX + column, bottom, gap)
        val rightX = look.rect.left - gap - column
        stack(rightColumn, rightX, top, rightX + column, bottom, gap)

        val midLeft = leftX + column + gap
        val midRight = rightX - gap
        val utilityHeight = (height * 0.18f).coerceAtLeast(gap * 4)
        row(utility, midLeft, top, midRight, top + utilityHeight, gap)
        val gridTop = top + utilityHeight + gap
        grid(slots, 6, midLeft, gridTop, midRight, bottom, gap)
        modeControls.values.forEach { grid(it, 4, midLeft, gridTop, midRight, bottom, gap) }
    }

    fun down(pointer: Int, x: Float, y: Float, now: Long) {
        val hit = visible.lastOrNull { it.rect.contains(x, y) } ?: return
        pointers[pointer] = hit
        sink.haptic()
        when (hit.kind) {
            Kind.MOVE, Kind.LOOK -> stickTo(hit, x, y)
            Kind.BUTTON -> press(hit, true)
            Kind.SLOT -> {
                if (shiftOn) sink.press(SHIFT_KEY, true)
                press(hit, true)
            }
            Kind.SHIFT -> {
                shiftOn = !shiftOn
                hit.held = shiftOn
                slots.forEachIndexed { i, c -> c.relabel(if (shiftOn) "S${SLOT_KEYS[i].first}" else SLOT_KEYS[i].first) }
            }
            Kind.TARGET -> targetDown(hit, pointer, x, y, now)
            Kind.MODE_ACTION -> {
                hit.held = true
                sink.press(hit.action!!.out, true)
            }
        }
        sink.redraw()
    }

    fun moveTo(pointer: Int, x: Float, y: Float) {
        val c = pointers[pointer] ?: return
        when (c.kind) {
            Kind.MOVE, Kind.LOOK -> stickTo(c, x, y)
            Kind.TARGET -> if (pointer == modePointer) aim(x, y)
            else -> return
        }
        sink.redraw()
    }

    fun up(pointer: Int, now: Long) {
        val c = pointers.remove(pointer) ?: return
        when (c.kind) {
            Kind.MOVE -> { moveKnob = 0f to 0f; sink.leftStick(0f, 0f) }
            Kind.LOOK -> { lookKnob = 0f to 0f; sink.rightStick(0f, 0f) }
            Kind.BUTTON -> press(c, false)
            Kind.SLOT -> {
                press(c, false)
                if (shiftOn && pointers.values.none { it.kind == Kind.SLOT }) sink.press(SHIFT_KEY, false)
            }
            Kind.SHIFT -> Unit
            Kind.TARGET -> if (pointer == modePointer) targetUp(now)
            Kind.MODE_ACTION -> {
                c.held = false
                sink.press(c.action!!.out, false)
                if (c.action.picks) leaveMode()
            }
        }
        sink.redraw()
    }

    /** Lets go of everything, for when the pane hides or loses focus. */
    fun cancelAll() {
        pointers.clear()
        moveKnob = 0f to 0f
        lookKnob = 0f to 0f
        visible.forEach { if (it.kind != Kind.SHIFT) it.held = false }
        mode = null
        latched = false
        modePointer = -1
        sink.redraw()
    }

    private fun targetDown(c: Control, pointer: Int, x: Float, y: Float, now: Long) {
        val m = c.mode!!
        if (mode == m && latched) {
            leaveMode()
            pointers.remove(pointer)
            return
        }
        if (mode != null) leaveMode()
        mode = m
        latched = false
        c.held = true
        modePointer = pointer
        modeDownX = x
        modeDownY = y
        modeDownAt = now
        modeDragged = false
        sink.press(Out.Pad(m.bumper), true)
    }

    private fun aim(x: Float, y: Float) {
        val dx = x - modeDownX
        val dy = y - modeDownY
        if (!modeDragged && hypot(dx, dy) < slop) return
        modeDragged = true
        val (ax, ay) = stickVector(dx, dy, aimReach)
        lookKnob = ax to ay
        sink.rightStick(ax, ay)
    }

    private fun targetUp(now: Long) {
        modePointer = -1
        if (isTap(modeDragged, now - modeDownAt)) {
            latched = true
            rightColumn.first { it.mode == mode }.relabel(sub = "tap to leave")
            return
        }
        leaveMode()
    }

    private fun leaveMode() {
        val m = mode ?: return
        modeControls.getValue(m).forEach { if (it.held) { it.held = false; sink.press(it.action!!.out, false) } }
        if (modeDragged || lookKnob != (0f to 0f)) {
            lookKnob = 0f to 0f
            sink.rightStick(0f, 0f)
        }
        sink.press(Out.Pad(m.bumper), false)
        rightColumn.forEach { if (it.mode != null) { it.held = false; it.relabel(sub = "drag to aim") } }
        mode = null
        latched = false
        modePointer = -1
        modeDragged = false
        pointers.values.removeAll { it.kind == Kind.MODE_ACTION || it.kind == Kind.TARGET }
    }

    private fun stickTo(c: Control, x: Float, y: Float) {
        val (sx, sy) = stickVector(x - c.rect.centerX(), y - c.rect.centerY(), c.rect.width() / 2 * STICK_THROW)
        if (c.kind == Kind.MOVE) {
            moveKnob = sx to sy
            sink.leftStick(sx, sy)
        } else {
            lookKnob = sx to sy
            sink.rightStick(sx, sy)
        }
    }

    private fun press(c: Control, down: Boolean) {
        c.held = down
        c.out?.let { sink.press(it, down) }
    }

    companion object {
        /** How far, as a share of the stick's radius, the finger travels for full deflection. */
        const val STICK_THROW = 0.75f
        const val TAP_MS = 300L

        private val SHIFT_KEY = Out.Key(XKeycode.KEY_SHIFT_L)
        val SLOT_KEYS = listOf(
            "1" to XKeycode.KEY_1, "2" to XKeycode.KEY_2, "3" to XKeycode.KEY_3, "4" to XKeycode.KEY_4,
            "5" to XKeycode.KEY_5, "6" to XKeycode.KEY_6, "7" to XKeycode.KEY_7, "8" to XKeycode.KEY_8,
            "9" to XKeycode.KEY_9, "0" to XKeycode.KEY_0, "-" to XKeycode.KEY_MINUS, "=" to XKeycode.KEY_EQUAL,
        )

        /** Finger offset to stick axes: full deflection at [reach], clamped to the unit circle. */
        fun stickVector(dx: Float, dy: Float, reach: Float): Pair<Float, Float> {
            if (reach <= 0f) return 0f to 0f
            val len = hypot(dx, dy)
            val scale = if (len > reach) 1f / len else 1f / reach
            return dx * scale to dy * scale
        }

        /** A press on Enemy or Friend that neither moved nor lasted keeps the mode on instead of ending it. */
        fun isTap(dragged: Boolean, heldMs: Long) = !dragged && heldMs < TAP_MS

        /** Forever's mode buttons, from its controller guides. Party 1 to 4's D-pad order is still to check in game. */
        fun actionsFor(mode: Mode): List<ModeAction> = when (mode) {
            Mode.ENEMY -> listOf(
                ModeAction("Last enemy", "A", Out.Pad(ExternalController.IDX_BUTTON_A.toInt()), picks = true),
                ModeAction("Ranged", "X", Out.Pad(ExternalController.IDX_BUTTON_X.toInt()), picks = false),
                ModeAction("Assist", "B", Out.Pad(ExternalController.IDX_BUTTON_B.toInt()), picks = true),
                ModeAction("Marker", "Y", Out.Pad(ExternalController.IDX_BUTTON_Y.toInt()), picks = false),
            )
            Mode.FRIEND -> listOf(
                ModeAction("Self", "A", Out.Pad(ExternalController.IDX_BUTTON_A.toInt()), picks = true),
                ModeAction("Pet", "X", Out.Pad(ExternalController.IDX_BUTTON_X.toInt()), picks = true),
                ModeAction("Assist", "B", Out.Pad(ExternalController.IDX_BUTTON_B.toInt()), picks = true),
                ModeAction("Marker", "Y", Out.Pad(ExternalController.IDX_BUTTON_Y.toInt()), picks = false),
                ModeAction("Party 1", "D-pad up", Out.DPad(0), picks = true),
                ModeAction("Party 2", "D-pad right", Out.DPad(1), picks = true),
                ModeAction("Party 3", "D-pad down", Out.DPad(2), picks = true),
                ModeAction("Party 4", "D-pad left", Out.DPad(3), picks = true),
            )
        }

        private fun stack(cs: List<Control>, l: Float, t: Float, r: Float, b: Float, gap: Float) {
            val h = (b - t - gap * (cs.size - 1)) / cs.size
            cs.forEachIndexed { i, c -> c.rect.set(l, t + i * (h + gap), r, t + i * (h + gap) + h) }
        }

        private fun row(cs: List<Control>, l: Float, t: Float, r: Float, b: Float, gap: Float) {
            val w = (r - l - gap * (cs.size - 1)) / cs.size
            cs.forEachIndexed { i, c -> c.rect.set(l + i * (w + gap), t, l + i * (w + gap) + w, b) }
        }

        private fun grid(cs: List<Control>, columns: Int, l: Float, t: Float, r: Float, b: Float, gap: Float) {
            val rows = (cs.size + columns - 1) / columns
            val w = (r - l - gap * (columns - 1)) / columns
            val h = (b - t - gap * (rows - 1)) / rows
            cs.forEachIndexed { i, c ->
                val x = l + (i % columns) * (w + gap)
                val y = t + (i / columns) * (h + gap)
                c.rect.set(x, y, x + w, y + h)
            }
        }
    }
}
