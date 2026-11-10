package com.huflit.doanappgiaodoan.ui

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.huflit.doanappgiaodoan.R

// Banner trượt trên trang chủ: RecyclerView ngang + PagerSnapHelper (lướt từng ảnh),
// tự chuyển ảnh sau 3.5 giây, chấm tròn phía dưới cho biết đang ở ảnh nào.
class BannerSlider(
    private val rv: RecyclerView,
    private val dots: LinearLayout,
    private val banners: List<Int>,
    private val onClick: (Int) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val layoutManager = LinearLayoutManager(rv.context, LinearLayoutManager.HORIZONTAL, false)
    private val snap = PagerSnapHelper()
    private val density = rv.resources.displayMetrics.density
    private var current = 0

    private val autoNext = object : Runnable {
        override fun run() {
            current = (current + 1) % banners.size
            rv.smoothScrollToPosition(current)
            handler.postDelayed(this, DELAY)
        }
    }

    init {
        // Ảnh banner tỉ lệ 1024x506, tính chiều cao theo bề ngang màn hình (trừ lề 16dp mỗi bên)
        val margin = dp(16)
        val imageWidth = rv.resources.displayMetrics.widthPixels - margin * 2
        rv.layoutParams.height = imageWidth * 506 / 1024
        rv.layoutManager = layoutManager
        rv.adapter = Adapter(margin)
        snap.attachToRecyclerView(rv)

        repeat(banners.size) { dots.addView(View(rv.context)) }
        updateDots()

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> stop() // người dùng đang tự kéo thì dừng tự chuyển
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        snap.findSnapView(layoutManager)?.let { current = layoutManager.getPosition(it) }
                        updateDots()
                        start()
                    }
                }
            }
        })
    }

    fun start() {
        handler.removeCallbacks(autoNext)
        handler.postDelayed(autoNext, DELAY)
    }

    fun stop() = handler.removeCallbacks(autoNext)

    private fun updateDots() {
        for (i in 0 until dots.childCount) {
            val active = i == current
            val dot = dots.getChildAt(i)
            dot.layoutParams = LinearLayout.LayoutParams(dp(if (active) 18 else 7), dp(7)).apply {
                marginStart = dp(3); marginEnd = dp(3)
            }
            dot.background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(ContextCompat.getColor(rv.context, if (active) R.color.primary else R.color.line))
            }
        }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private inner class Adapter(private val margin: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val image = ShapeableImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(dp(16).toFloat()).build()
            }
            val frame = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setPadding(margin, 0, margin, 0)
                addView(image)
            }
            return object : RecyclerView.ViewHolder(frame) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val image = (holder.itemView as FrameLayout).getChildAt(0) as ImageView
            image.setImageResource(banners[position])
            image.setOnClickListener { onClick(position) }
        }

        override fun getItemCount() = banners.size
    }

    companion object { private const val DELAY = 3500L }
}
