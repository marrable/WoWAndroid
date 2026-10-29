package app.gamenative.wow.touch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.min

/** Draws [TouchControls] and feeds it touches. Every finger is tracked on its own, so both thumbs work at once. */
@SuppressLint("ViewConstructor")
class TouchControlsView(context: Context) : View(context) {
    var output: TouchOutput? = null

    private val controls = TouchControls(object : TouchControls.Sink {
        override fun press(out: Out, down: Boolean) {
            val o = output ?: return
            when (out) {
                is Out.Pad -> o.button(out.index, down)
                is Out.DPad -> o.dpad(out.direction, down)
                is Out.Key -> o.key(out.code, down)
            }
        }

        override fun leftStick(x: Float, y: Float) { output?.leftStick(x, y) }
        override fun rightStick(x: Float, y: Float) { output?.rightStick(x, y) }
        override fun haptic() { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        override fun redraw() = invalidate()
    })

    private val density = resources.displayMetrics.density
    private val radius = 8 * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        setBackgroundColor(PaneStyle.INK)
        isHapticFeedbackEnabled = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 8 * density
        controls.layout(pad, pad, w - pad, h - pad, 6 * density, ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val m = controls.mode
        for (c in controls.visible) {
            when (c.kind) {
                Kind.MOVE -> drawStick(canvas, c, controls.moveKnob, PaneStyle.RULE)
                Kind.LOOK -> drawStick(canvas, c, controls.lookKnob, modeColor(m) ?: PaneStyle.RULE)
                else -> drawButton(canvas, c)
            }
        }
    }

    private fun drawStick(canvas: Canvas, c: Control, knob: Pair<Float, Float>, ring: Int) {
        val r = c.rect.width() / 2
        fill.color = PaneStyle.STONE
        canvas.drawCircle(c.rect.centerX(), c.rect.centerY(), r, fill)
        stroke.color = ring
        stroke.strokeWidth = if (ring == PaneStyle.RULE) density else 2 * density
        canvas.drawCircle(c.rect.centerX(), c.rect.centerY(), r, stroke)
        val kx = c.rect.centerX() + knob.first * r * TouchControls.STICK_THROW
        val ky = c.rect.centerY() + knob.second * r * TouchControls.STICK_THROW
        val active = knob.first != 0f || knob.second != 0f
        fill.color = if (active) PaneStyle.BRASS else PaneStyle.STONE2
        canvas.drawCircle(kx, ky, r * 0.42f, fill)
        val text = if (c.kind == Kind.LOOK && controls.mode != null) "Aim" else c.label
        label.color = if (active) PaneStyle.BRASS_INK else PaneStyle.ASH
        label.textSize = min(11 * density, r * 0.2f)
        canvas.drawText(text, kx, ky + label.textSize / 3, label)
    }

    private fun drawButton(canvas: Canvas, c: Control) {
        val mode = c.mode
        val modeColor = mode?.let { modeColor(it) }
        val latchedHere = mode != null && controls.mode == mode && controls.latched
        val pill = c.kind == Kind.SHIFT || (c.kind == Kind.BUTTON && c.rect.height() < c.rect.width() * 0.45f)
        val r = if (pill) c.rect.height() / 2 else radius
        fill.color = when {
            c.held && modeColor != null && !latchedHere -> modeColor
            c.held -> PaneStyle.BRASS
            c.kind == Kind.SHIFT -> PaneStyle.STONE
            else -> PaneStyle.STONE2
        }
        canvas.drawRoundRect(c.rect, r, r, fill)
        stroke.color = modeColor ?: if (c.held) PaneStyle.BRASS else PaneStyle.RULE
        stroke.strokeWidth = if (latchedHere) 2 * density else density
        canvas.drawRoundRect(c.rect, r, r, stroke)

        val ink = when {
            c.held && !latchedHere -> PaneStyle.BRASS_INK
            latchedHere -> modeColor!!
            c.kind == Kind.SHIFT -> PaneStyle.ASH
            else -> PaneStyle.BONE
        }
        val big = c.kind == Kind.SLOT || c.kind == Kind.MODE_ACTION || c.kind == Kind.TARGET || c.label == "Jump" || c.label == "Use"
        label.color = ink
        label.textSize = min((if (big) 14 else 11) * density, c.rect.height() * 0.34f)
        val hasSub = c.sub.isNotEmpty() || c.kind == Kind.SLOT
        val cy = c.rect.centerY() + label.textSize / 3 - if (hasSub) label.textSize * 0.45f else 0f
        canvas.drawText(c.label, c.rect.centerX(), cy, label)
        if (hasSub) {
            sub.color = if (c.held && !latchedHere) PaneStyle.BRASS_INK else PaneStyle.ASH
            sub.textSize = label.textSize * 0.62f
            val text = if (c.kind == Kind.SLOT) (if (controls.shiftOn) "bar 2" else "bar 1") else c.sub
            canvas.drawText(text, c.rect.centerX(), cy + label.textSize, sub)
        }
    }

    private fun modeColor(m: Mode?) = when (m) {
        Mode.ENEMY -> PaneStyle.HOSTILE
        Mode.FRIEND -> PaneStyle.FRIENDLY
        null -> null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                controls.down(event.getPointerId(i), event.getX(i), event.getY(i), now)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                controls.moveTo(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> controls.up(event.getPointerId(event.actionIndex), now)
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    /** Lets go of every button, stick and mode, in the game too. */
    fun release() {
        controls.cancelAll()
        output?.releaseAll()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) release()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
