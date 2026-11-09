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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.huflit.doanappgiaodoan.data.ProductDto


// Món theo danh mục (GridView 2 cột)
class CategoryProductsActivity : AppCompatActivity() {
    private lateinit var b: ActivityCategoryProductsBinding
    private val adapter = ProductGridAdapter()
    private var allProducts: List<ProductDto> = emptyList()
    private var categoryId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCategoryProductsBinding.inflate(layoutInflater)
        setContentView(b.root)
        categoryId = intent.getIntExtra(Nav.EXTRA_ID, 0)
        setupToolbar(b.toolbar, intent.getStringExtra(Nav.EXTRA_NAME) ?: "Danh mục")

        b.gridView.adapter = adapter
        b.gridView.setOnItemClickListener { _, _, position, _ -> Nav.product(this, adapter.getItem(position).id) }

// Các lựa chọn sắp xếp
        val sortOptions = listOf(
            "Mặc định",
            "Bán chạy",
            "Giá thấp → cao",
            "Giá cao → thấp"
        )

        b.spSort.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sortOptions
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        // Khi thay đổi cách sắp xếp
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

        // Khi nhập khoảng giá
        b.etMinPrice.doAfterTextChanged {
            showProducts()
        }

        b.etMaxPrice.doAfterTextChanged {
            showProducts()
        }

        // Khi xóa bộ lọc
        b.btnClearFilters.setOnClickListener {
            b.etMinPrice.text?.clear()
            b.etMaxPrice.text?.clear()

            b.spSort.setSelection(0)

            showProducts()
        }

        load()
    }


    private fun load() {

        // Lấy sản phẩm thuộc danh mục hiện tại
        api({ products(categoryId = categoryId) }) { list ->

            allProducts = list

            showProducts()
        }
    }

    private fun showProducts() {

        // Đọc điều kiện giá
        val minPrice =
            b.etMinPrice.text.toString().toLongOrNull()

        val maxPrice =
            b.etMaxPrice.text.toString().toLongOrNull()

        // Lọc và sắp xếp
        val filtered = ProductFilters.apply(
            allProducts,
            minPrice,
            maxPrice,
            b.spSort.selectedItemPosition
        )

        // Cập nhật GridView
        adapter.submit(filtered)

        // Hiển thị số kết quả
        b.tvResultCount.text =
            "Tìm thấy ${filtered.size} món"

        // Hiển thị thông báo nếu không có món
        b.tvEmpty.visibility =
            if (filtered.isEmpty()) View.VISIBLE
            else View.GONE
    }

}
