package com.huflit.doanappgiaodoan.ui.user

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.databinding.ActivityFavoritesBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductGridAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar

// Món yêu thích (GridView 2 cột)
class FavoritesActivity : AppCompatActivity() {
    private lateinit var b: ActivityFavoritesBinding
    private val adapter = ProductGridAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Món yêu thích")
        b.gridView.adapter = adapter
        b.gridView.setOnItemClickListener { _, _, pos, _ -> Nav.product(this, adapter.getItem(pos).id) }
    }

    // Quay lại từ chi tiết món (có thể vừa bỏ tim) thì tải lại
    override fun onResume() {
        super.onResume()
        api({ favorites() }) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
