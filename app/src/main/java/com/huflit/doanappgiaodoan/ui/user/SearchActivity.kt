package com.huflit.doanappgiaodoan.ui.user

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.ProductDto
import com.huflit.doanappgiaodoan.data.StoreDto
import com.huflit.doanappgiaodoan.databinding.ActivitySearchBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductListAdapter
import com.huflit.doanappgiaodoan.ui.StoreRowAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar

// Tìm kiếm quán ăn và món ăn
class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySearchBinding
    private val productAdapter = ProductListAdapter()
    private val storeAdapter = StoreRowAdapter { store ->
        saveHistory(b.searchView.query.toString())
        Nav.store(this, store.id)
    }
    private var allProducts: List<ProductDto> = emptyList()
    private var allStores: List<StoreDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Tìm kiếm")

        b.rvStores.layoutManager = LinearLayoutManager(this)
        b.rvStores.adapter = storeAdapter

        b.listView.adapter = productAdapter
        b.listView.setOnItemClickListener { _, _, position, _ ->
            saveHistory(b.searchView.query.toString())
            Nav.product(this, productAdapter.getItem(position).id)
        }

        // Danh sách lựa chọn sắp xếp
        val sortOptions = listOf("Mặc định", "Bán chạy", "Giá thấp → cao", "Giá cao → thấp")
        b.spSort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        b.spSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                showProducts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        b.etMinPrice.doAfterTextChanged { showProducts() }
        b.etMaxPrice.doAfterTextChanged { showProducts() }

        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { saveHistory(query); search(query); return true }
            override fun onQueryTextChange(newText: String?): Boolean {
                b.suggestBar.visibility = if (newText.isNullOrBlank()) View.VISIBLE else View.GONE
                search(newText)
                return true
            }
        })

        b.btnClearFilters.setOnClickListener {
            b.etMinPrice.text?.clear()
            b.etMaxPrice.text?.clear()
            b.spSort.setSelection(0)
            b.searchView.setQuery("", false)
            showProducts()
        }

        showSuggestions()
        b.searchView.requestFocus()
        search("")
    }

    private fun search(q: String?) {
        val keyword = q?.trim()?.ifEmpty { null }

        // Tìm quán ăn phù hợp
        api({ stores(q = keyword) }, onError = {}) { storesList ->
            allStores = storesList
            storeAdapter.submit(storesList)
            val hasStores = storesList.isNotEmpty()
            b.tvStoresHeader.visibility = if (hasStores) View.VISIBLE else View.GONE
            b.rvStores.visibility = if (hasStores) View.VISIBLE else View.GONE
            updateTotalResults()
        }

        // Tìm món ăn phù hợp
        api({ products(q = keyword) }, onError = {}) { list ->
            allProducts = list
            showProducts()
        }
    }

    private val prefs by lazy { getSharedPreferences("search_history", MODE_PRIVATE) }
    private val popular = listOf("Cơm tấm", "Phở", "Trà sữa", "Bún bò", "Gà rán", "Gong Cha")

    private fun history(): List<String> =
        prefs.getString("items", "")!!.split("\n").filter { it.isNotBlank() }

    private fun saveHistory(raw: String?) {
        val q = raw?.trim().orEmpty()
        if (q.length < 2) return
        val list = (listOf(q) + history().filter { !it.equals(q, ignoreCase = true) }).take(8)
        prefs.edit().putString("items", list.joinToString("\n")).apply()
        showSuggestions()
    }

    private fun showSuggestions() {
        b.llSuggest.removeAllViews()
        val recent = history()
        fun chip(text: String, onClick: () -> Unit) = Chip(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
            setOnClickListener { onClick() }
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.muted))
            setPadding(0, 0, 16, 0)
        }
        if (recent.isNotEmpty()) {
            b.llSuggest.addView(label("Gần đây"))
            recent.forEach { q -> b.llSuggest.addView(chip(q) { b.searchView.setQuery(q, true) }) }
            b.llSuggest.addView(chip("Xóa lịch sử") {
                prefs.edit().remove("items").apply()
                showSuggestions()
            })
        }
        b.llSuggest.addView(label(if (recent.isEmpty()) "Phổ biến" else "  Phổ biến"))
        popular.forEach { q -> b.llSuggest.addView(chip(q) { b.searchView.setQuery(q, true) }) }
    }

    private fun showProducts() {
        val minPrice = b.etMinPrice.text.toString().toLongOrNull()
        val maxPrice = b.etMaxPrice.text.toString().toLongOrNull()

        val filtered = ProductFilters.apply(
            allProducts, minPrice, maxPrice, b.spSort.selectedItemPosition
        )

        productAdapter.submit(filtered)
        val hasProducts = filtered.isNotEmpty()
        b.tvProductsHeader.visibility = if (hasProducts && allStores.isNotEmpty()) View.VISIBLE else View.GONE
        b.listView.visibility = if (hasProducts) View.VISIBLE else View.GONE

        updateTotalResults()
    }

    private fun updateTotalResults() {
        val storeCount = allStores.size
        val productCount = productAdapter.count
        val total = storeCount + productCount

        b.tvResultCount.text = "Tìm thấy $total kết quả ($storeCount quán, $productCount món)"
        b.tvEmpty.visibility = if (total == 0) View.VISIBLE else View.GONE
    }
}
