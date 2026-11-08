package com.huflit.doanappgiaodoan.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.huflit.doanappgiaodoan.data.CategoryDto
import com.huflit.doanappgiaodoan.data.ProductDto
import com.huflit.doanappgiaodoan.databinding.ActivityManageProductsBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductRowAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Quản lý món (RecyclerView), lọc theo danh mục bằng Spinner
class ManageProductsActivity : AppCompatActivity() {
    private lateinit var b: ActivityManageProductsBinding
    private var catList: List<CategoryDto> = emptyList()
    private val adapter = ProductRowAdapter(
        adminMode = true,
        onEdit = { openEdit(it.id) },
        onDelete = { confirmDelete(it) },
        onClick = { openEdit(it.id) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageProductsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Quản lý sản phẩm")

        b.rvProducts.layoutManager = LinearLayoutManager(this)
        b.rvProducts.adapter = adapter
        b.fab.setOnClickListener { openEdit(0) }

        api({ categories() }) { list ->
            catList = list
            b.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                listOf("Tất cả danh mục") + list.map { it.name })
            b.spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = load()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (catList.isNotEmpty()) load()
    }

    private fun load() {
        val pos = b.spCategory.selectedItemPosition
        val catId = if (pos <= 0) null else catList[pos - 1].id
        api({ products(categoryId = catId) }) { adapter.submit(it) }
    }

    private fun openEdit(id: Int) =
        startActivity(Intent(this, AddEditProductActivity::class.java).putExtra(Nav.EXTRA_ID, id))

    private fun confirmDelete(p: ProductDto) {
        AlertDialog.Builder(this).setTitle("Xóa món ${p.name}?")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteProduct(p.id) }) { toast("Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}
