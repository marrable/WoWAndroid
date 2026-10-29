package app.gamenative.wow.touch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.gamenative.data.TouchGestureConfig
import com.winlator.container.Container
import com.winlator.inputcontrols.ExternalController
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.xserver.XServer
import kotlin.math.roundToInt

/**
 * The root of the game screen while the touch pane is on: the game on top ([gameFrame], where XServerScreen puts
 * the game and the touchpad), a bar that drags the game's height and switches between controls and keyboard, and
 * the pane below. Upstream's InputControlsView sits behind the pane, covered, only so its virtual gamepad stays on.
 * While a physical controller is connected the bar and pane hide and the game takes the whole screen.
 * The root is a FrameLayout, so overlays upstream adds to it (the radial menu) still cover the whole screen.
 */
@SuppressLint("ViewConstructor")
class TouchPane(context: Context, private var aspect: Float) : FrameLayout(context) {
    private val column = Column(context)
    val gameFrame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val bar = Bar(context)
    private val content = FrameLayout(context)
    val controls = TouchControlsView(context)
    val keyboard = ChatKeyboardView(context).apply { visibility = GONE }

    private val inputManager = context.getSystemService(InputManager::class.java)
    private var paneShown = true
    private var showingKeyboard = false

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateForControllers()
        override fun onInputDeviceRemoved(deviceId: Int) = updateForControllers()
        override fun onInputDeviceChanged(deviceId: Int) = updateForControllers()
    }

    init {
        setBackgroundColor(Color.BLACK)
        column.addView(gameFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (BAR_DP * resources.displayMetrics.density).roundToInt()))
        column.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(controls, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(keyboard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Puts upstream's InputControlsView behind the pane, sized so it can load its profile, never seen or touched. */
    fun hostInputControls(view: View) {
        content.addView(view, 0, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Connects the pane to the game, and makes a tap on the game click where the finger is. */
    fun attach(xServer: XServer, inputControls: InputControlsView, touchpad: TouchpadView?, container: Container) {
        val output = TouchOutput(xServer, inputControls)
        controls.output = output
        keyboard.output = output
        touchpad?.setTouchscreenMode(true)
        touchpad?.setGestureConfig(TouchGestureConfig.fromJson(container.gestureConfig))
    }

    private fun showKeyboard(show: Boolean) {
        if (showingKeyboard == show) return
        showingKeyboard = show
        controls.release()
        keyboard.release()
        controls.visibility = if (show) GONE else VISIBLE
        keyboard.visibility = if (show) VISIBLE else GONE
        bar.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        inputManager?.registerInputDeviceListener(deviceListener, Handler(Looper.getMainLooper()))
        updateForControllers()
    }

    override fun onDetachedFromWindow() {
        inputManager?.unregisterInputDeviceListener(deviceListener)
        super.onDetachedFromWindow()
    }

    private fun updateForControllers() {
        val connected = InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.let { ExternalController.isGameController(it) } == true
        }
        setPaneShown(!connected)
    }

    private fun setPaneShown(shown: Boolean) {
        if (paneShown == shown) return
        paneShown = shown
        if (!shown) {
            controls.release()
            keyboard.release()
        }
        bar.visibility = if (shown) VISIBLE else GONE
        content.visibility = if (shown) VISIBLE else GONE
        column.requestLayout()
    }

    /** Game, bar and pane, top to bottom, with the game's height set from its shape. */
    private inner class Column(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val params = gameFrame.layoutParams as LinearLayout.LayoutParams
            // Set in place: reassigning layoutParams here would ask for another layout pass mid-measure.
            params.height = if (paneShown) gameHeight(width, height, bar.layoutParams.height, aspect) else height
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun dragTo(y: Float, save: Boolean) {
        val gameHeight = (y - bar.height / 2f).coerceAtLeast(1f)
        aspect = TouchPaneSettings.clampAspect(width / gameHeight)
        column.requestLayout()
        if (save) TouchPaneSettings.setGameAspect(context, aspect)
    }

    /**
     * The bar between game and pane: Controls and Keyboard tabs in the middle, and anywhere else on it drags the
     * game's height between 16:9 and 21:9.
     */
    private inner class Bar(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val style = PaneStyle(density)
        private val line = Paint().apply { color = PaneStyle.RULE }
        private val tabs = listOf(RectF(), RectF())
        private val tabNames = listOf("Controls", "Keyboard")
        private var onTabs = false

        init {
            setBackgroundColor(PaneStyle.STONE)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val tabW = 104 * density
            val tabH = h - 6 * density
            val top = (h - tabH) / 2
            tabs[0].set(w / 2f - tabW, top, w / 2f, top + tabH)
            tabs[1].set(w / 2f, top, w / 2f + tabW, top + tabH)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), density, line)
            canvas.drawRect(0f, height - density, width.toFloat(), height.toFloat(), line)
            val w = 48 * density
            val h = 4 * density
            listOf(tabs[0].left - 24 * density - w, tabs[1].right + 24 * density).forEach { x ->
                style.fill.color = PaneStyle.RULE
                canvas.drawRoundRect(x, height / 2f - h / 2, x + w, height / 2f + h / 2, h, h, style.fill)
            }
            tabs.forEachIndexed { i, r ->
                val on = (i == 1) == showingKeyboard
                style.drawKey(canvas, r, tabNames[i], held = on, quiet = !on, pill = true, maxSp = 12)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onTabs = tabs.any { it.contains(event.x, event.y) }
                MotionEvent.ACTION_MOVE -> if (!onTabs) dragTo(yInPane(event), save = false)
                MotionEvent.ACTION_UP -> {
                    if (!onTabs) {
                        dragTo(yInPane(event), save = true)
                    } else {
                        tabs.indexOfFirst { it.contains(event.x, event.y) }.takeIf { it >= 0 }?.let {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            showKeyboard(it == 1)
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> if (!onTabs) dragTo(yInPane(event), save = true)
            }
            return true
        }

        private fun yInPane(event: MotionEvent) = event.rawY - IntArray(2).also { this@TouchPane.getLocationOnScreen(it) }[1]
    }

    companion object {
        const val BAR_DP = 32

        /** The game's height for a width and shape, leaving room for the bar and at least a fifth of the screen for the pane. */
        fun gameHeight(width: Int, height: Int, barHeight: Int, aspect: Float): Int {
            if (width <= 0 || height <= 0) return 0
            val wanted = (width / aspect).roundToInt()
            val most = height - barHeight - height / 5
            return wanted.coerceIn(0, most.coerceAtLeast(0))
        }
    }
}
