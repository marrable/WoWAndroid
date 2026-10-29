package app.gamenative.wow.touch

import android.content.Context
import android.os.Build
import android.view.WindowManager
import app.gamenative.ui.screen.wow.fitScreenSize
import com.winlator.container.Container
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Whether the touch pane sits below the game, and how tall the game is above it. Kept in app prefs and
 * copied onto the container at launch, so XServerScreen only has to read the container.
 */
object TouchPaneSettings {
    /** Game width over height. 2:1 leaves about 35 mm of controls on the Fold 8 Wide's inner screen. */
    const val DEFAULT_ASPECT = 2f
    const val MIN_ASPECT = 16f / 9f
    const val MAX_ASPECT = 21f / 9f

    private const val PREFS = "wow_touch_pane"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ASPECT = "gameAspect"

    private const val EXTRA_ON = "touchPane"
    private const val EXTRA_ASPECT = "touchPaneAspect"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun gameAspect(context: Context): Float = clampAspect(prefs(context).getFloat(KEY_ASPECT, DEFAULT_ASPECT))

    fun setGameAspect(context: Context, aspect: Float) {
        prefs(context).edit().putFloat(KEY_ASPECT, clampAspect(aspect)).apply()
    }

    /** Called from the launch code: puts the pane and its game shape on the container, or takes them off. */
    fun applyTo(context: Context, container: Container) {
        val on = isEnabled(context)
        container.putExtra(EXTRA_ON, if (on) "1" else null)
        container.putExtra(EXTRA_ASPECT, if (on) gameAspect(context).toString() else null)
    }

    /** The pane only goes in landscape; portrait keeps upstream's own layout. */
    fun isOn(container: Container, isPortrait: Boolean): Boolean = !isPortrait && container.getExtra(EXTRA_ON) == "1"

    fun aspectOf(container: Container): Float =
        clampAspect(container.getExtra(EXTRA_ASPECT, DEFAULT_ASPECT.toString()).toFloatOrNull() ?: DEFAULT_ASPECT)

    fun clampAspect(aspect: Float): Float = aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /**
     * The game's render size with the pane on: the GPU's pixel budget ([baseSize]) reshaped to the game's part of
     * the screen, so it fills that part instead of being letterboxed. Landscape, so the long side is the width.
     */
    fun renderSize(context: Context, baseSize: String): String {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        return renderSize(baseSize, max(w, h), gameAspect(context))
    }

    internal fun renderSize(baseSize: String, windowWidth: Int, aspect: Float): String =
        fitScreenSize(baseSize, windowWidth, (windowWidth / clampAspect(aspect)).roundToInt())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
