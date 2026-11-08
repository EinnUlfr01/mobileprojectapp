package com.huflit.doanappgiaodoan.ui.user

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.CreateReviewRequest
import com.huflit.doanappgiaodoan.databinding.ActivityReviewBinding
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Đánh giá món đã mua: lưu vào CSDL, server cập nhật rating trung bình
class ReviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Đánh giá món ăn")

        val orderId = intent.getIntExtra("orderId", 0)
        val productId = intent.getIntExtra("productId", 0)
        b.tvProduct.text = intent.getStringExtra("productName")

        b.btnSend.setOnClickListener {
            val stars = b.ratingBar.rating.toInt()
            if (stars < 1) { toast("Vui lòng chọn số sao"); return@setOnClickListener }
            b.btnSend.isEnabled = false
            val comment = b.edtComment.text.toString().trim().ifEmpty { null }
            api({ review(CreateReviewRequest(orderId, productId, stars, comment)) },
                onError = { b.btnSend.isEnabled = true; toast(it) }) {
                toast(it.message ?: "Đã gửi đánh giá")
                finish() // quay lại chi tiết đơn
            }
        }
    }
}
