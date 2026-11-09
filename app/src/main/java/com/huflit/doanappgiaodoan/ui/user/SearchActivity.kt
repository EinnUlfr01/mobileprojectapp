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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.huflit.doanappgiaodoan.data.ProductDto

// Tìm món theo tên (ListView), dùng chung cho user và khách
class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySearchBinding
    private val adapter = ProductListAdapter()
    private var allProducts: List<ProductDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Tìm kiếm")

        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, position, _ -> Nav.product(this, adapter.getItem(position).id) }

        // Danh sách lựa chọn sắp xếp
        val sortOptions = listOf(
            "Mặc định",
            "Bán chạy",
            "Giá thấp → cao",
            "Giá cao → thấp"
        )

        // Hiển thị các lựa chọn lên Spinner
        b.spSort.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        // Khi người dùng đổi lựa chọn sắp xếp
        b.spSort.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    showProducts()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        // Khi nhập giá tối thiểu hoặc tối đa
        b.etMinPrice.doAfterTextChanged {
            showProducts()
        }

        b.etMaxPrice.doAfterTextChanged {
            showProducts()
        }


        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { search(query); return true }
            override fun onQueryTextChange(newText: String?): Boolean { search(newText); return true }
        })

        b.btnClearFilters.setOnClickListener {
            b.etMinPrice.text?.clear()
            b.etMaxPrice.text?.clear()

            b.spSort.setSelection(0)

            b.searchView.setQuery("", false)

            showProducts()
        }

        b.searchView.requestFocus()
        search("")
    }


    private fun search(q: String?) {

        api({
            products(q = q?.trim()?.ifEmpty { null })
        }) { list ->

            // Lưu kết quả API
            allProducts = list

            // Áp dụng bộ lọc và hiển thị
            showProducts()
        }
    }

    private fun showProducts() {

        // Đọc khoảng giá từ EditText
        val minPrice =
            b.etMinPrice.text.toString().toLongOrNull()

        val maxPrice =
            b.etMaxPrice.text.toString().toLongOrNull()

        // Lọc và sắp xếp danh sách
        val filtered = ProductFilters.apply(
            allProducts,
            minPrice,
            maxPrice,
            b.spSort.selectedItemPosition
        )

        // Hiển thị danh sách sau khi lọc
        adapter.submit(filtered)

        // Đếm số kết quả
        b.tvResultCount.text =
            "Tìm thấy ${filtered.size} món"

        // Hiện thông báo khi danh sách rỗng
        b.tvEmpty.visibility =
            if (filtered.isEmpty()) View.VISIBLE
            else View.GONE
    }

}
