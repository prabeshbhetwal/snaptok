package app.snaptok.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.ImageButton
import kotlinx.coroutines.flow.MutableStateFlow

internal data class SaveFeedback(val text: String, val time: Long = System.currentTimeMillis())
internal object FloatingFeedback { val latest = MutableStateFlow(SaveFeedback("", 0)) }

internal class FloatingButton(context: Context) : ImageButton(context) {
    var progress = -1
    var count = 0
    var message = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun performClick(): Boolean = super.performClick()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 4f * resources.displayMetrics.density
        if (progress >= 0) {
            paint.color = Color.rgb(185, 246, 202); paint.style = Paint.Style.STROKE; paint.strokeWidth = inset / 2
            canvas.drawArc(inset, inset, width - inset, height - inset, -90f, 360f * progress / 100f, false, paint)
        }
        if (count > 0 || message.isNotEmpty()) {
            val label = message.ifEmpty { count.toString() }
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(33, 48, 43)
            val radius = height * .22f
            canvas.drawCircle(width * .75f, height * .22f, radius, paint)
            paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = height * .22f
            canvas.drawText(label, width * .75f, height * .29f, paint)
        }
    }
}
