package com.huflit.doanappgiaodoan.ui.admin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.huflit.doanappgiaodoan.R

// Biểu đồ cột tự vẽ bằng Canvas (không dùng thư viện): mỗi cột 1 ngày, số tiền ghi trên đầu cột
class BarChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var labels: List<String> = emptyList()
    private var values: List<Double> = emptyList()
    private var highlight = -1 // cột được tô đậm (hôm nay)

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.primary) }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.primary); alpha = 120 }
    private val axisPaint = Paint().apply { color = context.getColor(R.color.line); strokeWidth = density }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted); textSize = 11 * density; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ink); textSize = 10 * density; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val rect = RectF()

    fun setData(labels: List<String>, values: List<Double>, highlightIndex: Int = values.size - 1) {
        this.labels = labels
        this.values = values
        this.highlight = highlightIndex
        contentDescription = labels.zip(values).joinToString(", ") { (l, v) -> "$l: ${short(v)}" }
        invalidate()
    }

    // 125000 -> "125k", 1500000 -> "1,5tr"
    private fun short(v: Double): String = when {
        v >= 1_000_000 -> String.format(java.util.Locale.US, "%.1ftr", v / 1_000_000).replace('.', ',').replace(",0tr", "tr")
        v >= 1_000 -> "${(v / 1000).toLong()}k"
        v > 0 -> v.toLong().toString()
        else -> "0"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val top = 18 * density          // chỗ ghi số tiền trên cột
        val bottom = height - 20 * density // chỗ ghi ngày dưới cột
        val chartH = bottom - top
        val slot = width.toFloat() / values.size
        val barW = slot * 0.55f
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

        canvas.drawLine(0f, bottom, width.toFloat(), bottom, axisPaint)
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val h = (v / max * chartH).toFloat().coerceAtLeast(if (v > 0) 3 * density else 0f)
            rect.set(cx - barW / 2, bottom - h, cx + barW / 2, bottom)
            canvas.drawRoundRect(rect, 4 * density, 4 * density, if (i == highlight) barPaint else dimPaint)
            canvas.drawText(short(v), cx, bottom - h - 4 * density, valuePaint)
            canvas.drawText(labels.getOrElse(i) { "" }, cx, height - 5 * density, labelPaint)
        }
    }
}
