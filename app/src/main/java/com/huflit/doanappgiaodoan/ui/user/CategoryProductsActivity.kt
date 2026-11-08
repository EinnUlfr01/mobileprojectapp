package com.huflit.doanappgiaodoan.ui.user

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.databinding.ActivityCategoryProductsBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductGridAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar

// Món theo danh mục (GridView 2 cột)
class CategoryProductsActivity : AppCompatActivity() {
    private lateinit var b: ActivityCategoryProductsBinding
    private val adapter = ProductGridAdapter()
    private var categoryId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCategoryProductsBinding.inflate(layoutInflater)
        setContentView(b.root)
        categoryId = intent.getIntExtra(Nav.EXTRA_ID, 0)
        setupToolbar(b.toolbar, intent.getStringExtra(Nav.EXTRA_NAME) ?: "Danh mục")

        b.gridView.adapter = adapter
        b.gridView.setOnItemClickListener { _, _, position, _ -> Nav.product(this, adapter.getItem(position).id) }
        b.rgSort.setOnCheckedChangeListener { _, _ -> load() }
        load()
    }

    private fun load() {
        val sort = when (b.rgSort.checkedRadioButtonId) {
            R.id.rbBest -> "bestseller"
            R.id.rbCheap -> "price_asc"
            else -> null
        }
        api({ products(categoryId = categoryId, sort = sort) }) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
