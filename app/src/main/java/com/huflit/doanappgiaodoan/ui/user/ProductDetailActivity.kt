package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.AddCartRequest
import com.huflit.doanappgiaodoan.data.GuestCart
import com.huflit.doanappgiaodoan.data.GuestCartItem
import com.huflit.doanappgiaodoan.data.ProductDto
import com.huflit.doanappgiaodoan.data.ReviewDto
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityProductDetailBinding
import com.huflit.doanappgiaodoan.databinding.ItemReviewBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.ui.auth.RegisterActivity
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.ratingText
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toDisplayDate
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast

// Chi tiết món: chọn size, số lượng, thêm giỏ. Khách vãng lai thêm vào giỏ trên máy.
class ProductDetailActivity : AppCompatActivity() {
    private lateinit var b: ActivityProductDetailBinding
    private var current: ProductDto? = null
    private var qty = 1
    private var isFavorite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Chi tiết món")
        b.toolbar.inflateMenu(R.menu.menu_product)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorite -> toggleFavorite()
                else -> startActivity(Intent(this, CartActivity::class.java))
            }
            true
        }

        b.rvReviews.layoutManager = LinearLayoutManager(this)
        b.btnMinus.setOnClickListener { if (qty > 1) { qty--; updatePrice() } }
        b.btnPlus.setOnClickListener { if (qty < 50) { qty++; updatePrice() } }
        b.rgSize.setOnCheckedChangeListener { _, _ -> updatePrice() }
        b.btnAddCart.setOnClickListener { doAddToCart() }
        // Chỉ thành viên mới được đánh giá (đánh giá từ đơn đã hoàn thành)
        b.tvWriteReview.setOnClickListener {
            if (Session.isLoggedIn) toast("Vào Đơn hàng > đơn đã hoàn thành để đánh giá món này")
            else showLoginRequired()
        }
        load(intent.getIntExtra(Nav.EXTRA_ID, 0))
    }

    private fun load(id: Int) {
        api({ product(id) }) { detail ->
            val p = detail.product
            current = p
            isFavorite = detail.isFavorite
            updateHeart()
            b.toolbar.title = p.name
            b.tvName.text = p.name
            b.tvDesc.text = p.description ?: ""
            b.ratingBar.rating = p.avgRating.toFloat()
            b.tvRating.text = "${ratingText(p.avgRating, p.ratingCount)} · Đã bán ${p.soldCount}"
            ImageLoader.load(b.img, b.imgText, p.imageUrl, p.name)

            // RadioGroup size: S/M/L, giá đổi theo size
            b.rgSize.removeAllViews()
            p.sizes.forEachIndexed { i, s ->
                val rb = RadioButton(this).apply {
                    this.id = View.generateViewId()
                    text = "${s.size} · ${s.price.toMoney()}"
                    tag = s.size
                    setPadding(0, 0, 24, 0)
                }
                b.rgSize.addView(rb)
                if (s.size == "M" || (i == 0 && p.sizes.none { it.size == "M" })) rb.isChecked = true
            }

            b.tvReviewTitle.text = "Đánh giá (${detail.reviews.size})"
            b.tvNoReview.visibility = if (detail.reviews.isEmpty()) View.VISIBLE else View.GONE
            b.rvReviews.adapter = ReviewAdapter(detail.reviews)
            updatePrice()
        }
    }

    private fun selectedSize(): String? =
        b.rgSize.findViewById<RadioButton>(b.rgSize.checkedRadioButtonId)?.tag as? String

    private fun updatePrice() {
        b.tvQty.text = qty.toString()
        val price = current?.sizes?.find { it.size == selectedSize() }?.price ?: return
        b.btnAddCart.text = "Thêm vào giỏ · ${(price * qty).toMoney()}"
    }

    private fun doAddToCart() {
        val p = current ?: return
        val size = selectedSize() ?: run { toast("Vui lòng chọn size"); return }
        val price = p.sizes.first { it.size == size }.price
        if (Session.isLoggedIn) {
            api({ addToCart(AddCartRequest(p.id, size, qty)) }) { toast("Đã thêm vào giỏ hàng") }
        } else {
            GuestCart.add(GuestCartItem(p.id, p.name, p.imageUrl, size, price, qty))
            toast("Đã thêm vào giỏ hàng")
        }
    }

    // Bấm trái tim: thêm / bỏ món yêu thích (chỉ thành viên)
    private fun toggleFavorite() {
        val p = current ?: return
        if (!Session.isLoggedIn) { showLoginRequired(); return }
        val adding = !isFavorite
        isFavorite = adding
        updateHeart()
        api({ if (adding) addFavorite(p.id) else removeFavorite(p.id) },
            onError = { isFavorite = !adding; updateHeart(); toast(it) }) {
            toast(if (adding) "Đã thêm vào món yêu thích" else "Đã bỏ khỏi món yêu thích")
        }
    }

    private fun updateHeart() {
        b.toolbar.menu.findItem(R.id.action_favorite)?.setIcon(if (isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_outline)
    }

    // Dialog yêu cầu đăng nhập (khách vãng lai)
    private fun showLoginRequired() {
        AlertDialog.Builder(this)
            .setTitle("Cần đăng nhập")
            .setMessage("Khách vẫn đặt món được. Riêng đánh giá món, lưu món yêu thích và xem lịch sử đơn thì cần có tài khoản.")
            .setPositiveButton("Đăng nhập") { _, _ -> startActivity(Intent(this, LoginActivity::class.java)) }
            .setNeutralButton("Đăng ký") { _, _ -> startActivity(Intent(this, RegisterActivity::class.java)) }
            .setNegativeButton("Để sau", null)
            .show()
    }
}

// RecyclerView: danh sách đánh giá
class ReviewAdapter(private val items: List<ReviewDto>) : RecyclerView.Adapter<ReviewAdapter.VH>() {
    class VH(val b: ItemReviewBinding) : RecyclerView.ViewHolder(b.root)
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.b.tvUser.text = r.userName
        h.b.tvStars.text = "★".repeat(r.stars) + "☆".repeat(5 - r.stars)
        h.b.tvComment.text = r.comment ?: ""
        h.b.tvComment.visibility = if (r.comment.isNullOrBlank()) View.GONE else View.VISIBLE
        h.b.tvDate.text = r.createdAt.toDisplayDate()
    }
}
