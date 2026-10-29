package app.gamenative.wow.touch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** Colours and key drawing shared by the pane's views: the launcher palette, plus the game's hostile red and friendly green. */
internal class PaneStyle(private val density: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    val radius = 8 * density

    /** A plain key: [held] fills it bronze, [quiet] is for modifier keys. */
    fun drawKey(canvas: Canvas, rect: RectF, text: String, held: Boolean, quiet: Boolean = false, pill: Boolean = false, maxSp: Int = 14) {
        val r = if (pill) rect.height() / 2 else radius
        fill.color = when {
            held -> BRASS
            quiet -> STONE
            else -> STONE2
        }
        canvas.drawRoundRect(rect, r, r, fill)
        stroke.color = if (held) BRASS else RULE
        stroke.strokeWidth = density
        canvas.drawRoundRect(rect, r, r, stroke)
        label.color = when {
            held -> BRASS_INK
            quiet -> ASH
            else -> BONE
        }
        label.textSize = min(maxSp * density, rect.height() * 0.4f)
        canvas.drawText(text, rect.centerX(), rect.centerY() + label.textSize / 3, label)
    }

    companion object {
        val INK = Color.parseColor("#10171A")
        val STONE = Color.parseColor("#19232A")
        val STONE2 = Color.parseColor("#24323A")
        val RULE = Color.parseColor("#30414A")
        val BONE = Color.parseColor("#EFE6D6")
        val ASH = Color.parseColor("#A8A08F")
        val BRASS = Color.parseColor("#C9A06A")
        val BRASS_INK = Color.parseColor("#1D150B")
        val HOSTILE = Color.parseColor("#D2564B")
        val FRIENDLY = Color.parseColor("#8CC08E")
    }
}
