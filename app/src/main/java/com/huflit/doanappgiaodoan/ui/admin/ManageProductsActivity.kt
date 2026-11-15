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
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.data.StoreDto
import com.huflit.doanappgiaodoan.databinding.ActivityManageProductsBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductRowAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Quản lý món (RecyclerView), lọc theo danh mục bằng Spinner.
// - Quản trị sàn: thấy món của mọi quán, lọc thêm theo quán
// - Chủ quán: chỉ thấy và sửa món của quán mình
class ManageProductsActivity : AppCompatActivity() {
    private lateinit var b: ActivityManageProductsBinding
    private var catList: List<CategoryDto> = emptyList()
    private var storeList: List<StoreDto> = emptyList()
    private var myStoreId: Int? = null      // chủ quán: id quán của mình
    private var ready = false
    private val adapter = ProductRowAdapter(
        adminMode = true,
        onEdit = { openEdit(it.id) },
        onDelete = { confirmDelete(it) },
        onClick = { openEdit(it.id) }
    )
    private val reload = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = load()
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageProductsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, if (Session.isAdmin) "Món ăn trên sàn" else "Món của quán")

        b.rvProducts.layoutManager = LinearLayoutManager(this)
        b.rvProducts.adapter = adapter
        b.fab.setOnClickListener { openEdit(0) }

        if (Session.isAdmin) {
            b.spStore.visibility = View.VISIBLE
            api({ allStores() }) { list ->
                storeList = list
                b.spStore.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Tất cả quán") + list.map { it.name })
                b.spStore.onItemSelectedListener = reload
                loadCategories()
            }
        } else {
            api({ myStore() }, onError = { toast(it); finish() }) { s ->
                myStoreId = s.id
                b.toolbar.title = "Món của ${s.name}"
                loadCategories()
            }
        }
    }

    private fun loadCategories() {
        api({ categories() }) { list ->
            catList = list
            b.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                listOf("Tất cả danh mục") + list.map { it.name })
            b.spCategory.onItemSelectedListener = reload
            ready = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (ready) load()
    }

    private fun selectedStoreId(): Int? {
        if (!Session.isAdmin) return myStoreId
        val pos = b.spStore.selectedItemPosition
        return if (pos <= 0) null else storeList.getOrNull(pos - 1)?.id
    }

    private fun load() {
        val pos = b.spCategory.selectedItemPosition
        val catId = if (pos <= 0) null else catList.getOrNull(pos - 1)?.id
        api({ products(categoryId = catId, storeId = selectedStoreId()) }) { adapter.submit(it) }
    }

    private fun openEdit(id: Int) =
        startActivity(Intent(this, AddEditProductActivity::class.java)
            .putExtra(Nav.EXTRA_ID, id)
            .putExtra(AddEditProductActivity.EXTRA_STORE_ID, selectedStoreId() ?: 0))

    private fun confirmDelete(p: ProductDto) {
        AlertDialog.Builder(this).setTitle("Xóa món ${p.name}?")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteProduct(p.id) }) { toast("Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}
