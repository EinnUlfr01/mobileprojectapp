package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.databinding.ActivityStoreBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductRowAdapter
import com.huflit.doanappgiaodoan.ui.storeInfo
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar

// Màn 1 quán: ảnh, tên, trạng thái mở/đóng, địa chỉ, thực đơn của quán (RecyclerView)
class StoreActivity : AppCompatActivity() {
    private lateinit var b: ActivityStoreBinding
    private val adapter = ProductRowAdapter { Nav.product(this, it.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Cửa hàng")
        b.toolbar.inflateMenu(R.menu.menu_product)
        b.toolbar.menu.findItem(R.id.action_favorite)?.isVisible = false
        b.toolbar.setOnMenuItemClickListener { startActivity(Intent(this, CartActivity::class.java)); true }
        b.rvProducts.layoutManager = LinearLayoutManager(this)
        b.rvProducts.adapter = adapter
        load(intent.getIntExtra(Nav.EXTRA_ID, 0))
    }

    private fun load(id: Int) {
        api({ store(id) }) { d ->
            val s = d.store
            b.toolbar.title = s.name
            b.tvName.text = s.name
            b.tvInfo.text = storeInfo(s)
            b.tvAddress.text = "📍 ${s.address}"
            b.tvDesc.text = s.description ?: ""
            b.tvDesc.visibility = if (s.description.isNullOrBlank()) View.GONE else View.VISIBLE
            b.tvOpen.text = if (s.isOpen) "Đang mở cửa" else "Đang đóng cửa"
            b.tvOpen.setTextColor(getColor(if (s.isOpen) R.color.primary_dark else R.color.danger))
            ImageLoader.load(b.img, b.imgText, s.imageUrl, s.name)
            b.btnCall.setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}"))) }
            b.tvMenuTitle.text = "Thực đơn (${d.products.size} món)"
            b.tvEmpty.visibility = if (d.products.isEmpty()) View.VISIBLE else View.GONE
            adapter.submit(d.products)
        }
    }
}
