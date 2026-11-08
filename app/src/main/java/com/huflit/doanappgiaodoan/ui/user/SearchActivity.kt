package com.huflit.doanappgiaodoan.ui.user

import android.os.Bundle
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.databinding.ActivitySearchBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductListAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar

// Tìm món theo tên (ListView), dùng chung cho user và khách
class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySearchBinding
    private val adapter = ProductListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Tìm kiếm")

        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, position, _ -> Nav.product(this, adapter.getItem(position).id) }

        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { search(query); return true }
            override fun onQueryTextChange(newText: String?): Boolean { search(newText); return true }
        })
        b.searchView.requestFocus()
        search("")
    }

    private fun search(q: String?) {
        api({ products(q = q?.trim()?.ifEmpty { null }) }) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
