package app.gamenative.wow.touch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.winlator.xserver.XKeycode

/** Draws [ChatKeyboard] and feeds it touches, one finger per key, so fast two-thumb typing works. */
@SuppressLint("ViewConstructor")
class ChatKeyboardView(context: Context) : View(context) {
    var output: TouchOutput? = null

    private val keyboard = ChatKeyboard(object : ChatKeyboard.Sink {
        override fun key(code: XKeycode, shift: Boolean, down: Boolean) {
            val o = output ?: return
            if (down) o.keyDown(code, shift) else o.key(code, false)
        }

        override fun type(text: String) { output?.type(text) }
        override fun haptic() { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        override fun redraw() = invalidate()
    })

    private val density = resources.displayMetrics.density
    private val style = PaneStyle(density)

    init {
        setBackgroundColor(PaneStyle.INK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 8 * density
        keyboard.layout(pad, pad, w - pad, h - pad, 5 * density)
    }

    override fun onDraw(canvas: Canvas) {
        for (k in keyboard.visible) style.drawKey(canvas, k.rect, k.label, k.held, quiet = k.quiet, pill = k.quiet && k.id.length > 1 && k.id[0].isLowerCase(), maxSp = if (k.quiet) 12 else 16)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                keyboard.down(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> keyboard.up(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> keyboard.cancelAll()
        }
        return true
    }

    fun release() {
        keyboard.cancelAll()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) release()
    }
}
