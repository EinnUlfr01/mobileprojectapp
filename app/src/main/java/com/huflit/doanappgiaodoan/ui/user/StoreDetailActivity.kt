package com.huflit.doanappgiaodoan.ui.user

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.huflit.doanappgiaodoan.databinding.ActivityStoreDetailBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductCardAdapter
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar

// Màn hình chi tiết quán ăn + danh sách các món quán đang bán
class StoreDetailActivity : AppCompatActivity() {
    private lateinit var b: ActivityStoreDetailBinding
    private val productAdapter = ProductCardAdapter { Nav.product(this, it.id) }
    private var storeId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStoreDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        storeId = intent.getIntExtra(Nav.EXTRA_ID, 0)
        setupToolbar(b.toolbar, "Thông tin quán")

        b.rvProducts.layoutManager = GridLayoutManager(this, 2)
        b.rvProducts.adapter = productAdapter

        loadStore()
    }

    private fun loadStore() {
        api({ store(storeId) }) { detail ->
            val s = detail.store
            b.toolbar.title = s.name
            b.tvStoreName.text = s.name
            b.tvCategory.text = s.storeCategoryName
            b.tvRating.text = "⭐ ${s.rating} · Đã bán ${s.soldCount} món"
            b.tvAddress.text = if (!s.address.isNullOrBlank()) "📍 ${s.address}" else "📍 Địa chỉ đang cập nhật"
            b.tvPhone.text = if (!s.phone.isNullOrBlank()) "📞 ${s.phone}" else "📞 Chưa có SĐT"
            b.tvDesc.text = s.description ?: ""
            b.tvDesc.visibility = if (s.description.isNullOrBlank()) View.GONE else View.VISIBLE
            ImageLoader.load(b.img, b.imgText, s.imageUrl, s.name)

            val products = detail.products
            productAdapter.submit(products)
            b.tvEmpty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
