package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.min

/** Grouped posture durations and independently scaled danger-event curve. */
class HealthTrendChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(178, 190, 210)
        textAlign = Paint.Align.CENTER
    }
    private val colors = intArrayOf(Color.rgb(55, 198, 155), Color.rgb(255, 190, 66), Color.rgb(255, 107, 114))
    private var data = emptyList<DailyHealthTrendSummary>()
    private fun dateFormat() = SimpleDateFormat("E", resources.configuration.locales[0])

    fun setData(summaries: List<DailyHealthTrendSummary>) {
        data = summaries.sortedBy { it.dayStartMs }.takeLast(7)
        contentDescription = if (data.none { it.totalSeconds > 0 }) context.getString(R.string.trend_empty) else
            data.joinToString("\n") {
                if (it.totalSeconds == 0L) dateFormat().format(Date(it.dayStartMs)) + ": " + context.getString(R.string.trend_empty)
                else context.getString(R.string.trend_day_description, dateFormat().format(Date(it.dayStartMs)),
                    it.safeSeconds / 60, it.warningSeconds / 60, it.dangerSeconds / 60, it.dangerEvents)
            }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 5 * d
        val right = width - 5 * d
        val top = 16 * d
        label.textSize = 12 * resources.displayMetrics.scaledDensity
        val bottom = height - label.fontSpacing - 14 * d
        if (right <= left || bottom <= top) return
        val h = bottom - top
        val slots = data.size.coerceAtLeast(7)
        val slot = (right - left) / slots
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = Color.argb(100, 133, 149, 174)
        paint.pathEffect = DashPathEffect(floatArrayOf(5*d, 5*d), 0f)
        repeat(4) { i -> canvas.drawLine(left, top + h*i/4, right, top + h*i/4, paint) }
        for (i in 1 until slots) canvas.drawLine(left+slot*i, top, left+slot*i, bottom, paint)
        paint.pathEffect = null
        canvas.drawLine(left, bottom, right, bottom, paint)
        val maxSeconds = data.maxOfOrNull { maxOf(it.safeSeconds, it.warningSeconds, it.dangerSeconds) }?.coerceAtLeast(1) ?: 1L
        val maxEvents = (data.maxOfOrNull { it.dangerEvents } ?: 0).coerceAtLeast(1)
        val points = data.mapIndexed { i, item -> PointF(left + slot*(i+.5f), bottom-h*item.dangerEvents.coerceAtLeast(0)/maxEvents) }
        val barWidth = min(9*d, slot*.19f)
        data.forEachIndexed { i, item ->
            paint.style = Paint.Style.FILL
            listOf(item.safeSeconds, item.warningSeconds, item.dangerSeconds).forEachIndexed { j, seconds ->
                if (seconds > 0) {
                    val x = points[i].x + (j-1)*barWidth*1.23f
                    val y = bottom - h*.58f*seconds/maxSeconds
                    paint.color = ColorUtils.setAlphaComponent(colors[j], 185)
                    canvas.drawRoundRect(x-barWidth/2, y, x+barWidth/2, bottom, 3*d, 3*d, paint)
                }
            }
            val text = dateFormat().format(Date(item.dayStartMs))
            val savedSize = label.textSize
            if (label.measureText(text) > slot-2*d) label.textSize *= (slot-2*d)/label.measureText(text)
            canvas.drawText(text, points[i].x, bottom + 8*d - label.fontMetrics.top, label)
            label.textSize = savedSize
        }
        // Each adjacent pair uses bounded horizontal cubic handles: no overshoot or bridging missing days.
        for (i in 0 until data.lastIndex) {
            if (data[i].totalSeconds <= 0 || data[i+1].totalSeconds <= 0 ||
                data[i+1].dayStartMs - data[i].dayStartMs > 36*60*60*1000L) continue
            val a = points[i]; val b = points[i+1]; val middle = (a.x+b.x)/2
            val startColor = curveColor(i, data.size); val endColor = curveColor(i+1, data.size)
            val path = Path().apply { moveTo(a.x,a.y); cubicTo(middle,a.y,middle,b.y,b.x,b.y) }
            val area = Path(path).apply { lineTo(b.x,bottom); lineTo(a.x,bottom); close() }
            paint.style = Paint.Style.FILL
            paint.shader = ComposeShader(
                LinearGradient(a.x,0f,b.x,0f,startColor,endColor,Shader.TileMode.CLAMP),
                LinearGradient(0f,top,0f,bottom,0x70FFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP), PorterDuff.Mode.DST_IN)
            canvas.drawPath(area,paint)
            paint.shader = LinearGradient(a.x,0f,b.x,0f,startColor,endColor,Shader.TileMode.CLAMP)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3*d
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(path,paint)
        }
        points.forEachIndexed { i, p ->
            if (data[i].totalSeconds <= 0) return@forEachIndexed
            val color = curveColor(i,data.size)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(p.x,p.y,13*d,intArrayOf(ColorUtils.setAlphaComponent(color,150),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawCircle(p.x,p.y,13*d,paint)
            paint.shader = null
            paint.color = color
            canvas.drawCircle(p.x,p.y,4.5f*d,paint)
            paint.color = ColorUtils.blendARGB(color,Color.WHITE,.7f)
            canvas.drawCircle(p.x,p.y,3.2f*d,paint)
        }
        if (data.none { it.totalSeconds > 0 }) {
            canvas.drawText(context.getString(R.string.trend_empty),(left+right)/2,top+h/2,label)
        }
    }

    private fun curveColor(index: Int, count: Int): Int {
        val fraction = index.toFloat() / (count-1).coerceAtLeast(1)
        return if (fraction < .5f) ColorUtils.blendARGB(colors[0],colors[1],fraction*2)
        else ColorUtils.blendARGB(colors[1],colors[2],(fraction-.5f)*2)
    }
}
