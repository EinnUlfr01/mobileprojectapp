package com.huflit.doanappgiaodoan.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.CategoryDto
import com.huflit.doanappgiaodoan.data.CategoryRequest
import com.huflit.doanappgiaodoan.databinding.ActivityManageCategoriesBinding
import com.huflit.doanappgiaodoan.databinding.DialogCategoryBinding
import com.huflit.doanappgiaodoan.databinding.ItemAdminRowBinding
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.ImageUpload
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Quản lý danh mục (ListView): thêm, sửa, xóa bằng Dialog
class ManageCategoriesActivity : AppCompatActivity() {
    private lateinit var b: ActivityManageCategoriesBinding
    private val adapter = CategoryAdminAdapter(onEdit = { showDialog(it) }, onDelete = { confirmDelete(it) })
    private var openDialog: DialogCategoryBinding? = null

    // Chọn ảnh danh mục từ điện thoại -> upload -> điền vào ô link ảnh của dialog đang mở
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val d = openDialog ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        d.btnPickImage.isEnabled = false
        api({ upload(ImageUpload.toPart(this@ManageCategoriesActivity, uri)) }, onError = { d.btnPickImage.isEnabled = true; toast(it) }) {
            d.btnPickImage.isEnabled = true
            d.edtImage.setText(it.url)
            toast("Đã tải ảnh lên")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageCategoriesBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Quản lý danh mục")
        b.listView.adapter = adapter
        b.fab.setOnClickListener { showDialog(null) }
        load()
    }

    private fun load() = api({ categories() }) { adapter.submit(it) }

    private fun showDialog(c: CategoryDto?) {
        val d = DialogCategoryBinding.inflate(layoutInflater)
        c?.let { d.edtName.setText(it.name); d.edtImage.setText(it.imageUrl ?: "") }
        openDialog = d
        d.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (c == null) "Thêm danh mục" else "Sửa danh mục")
            .setView(d.root).setPositiveButton("Lưu", null).setNegativeButton("Hủy", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = d.edtName.text.toString().trim()
                if (name.isEmpty()) { d.tilName.error = "Nhập tên danh mục"; return@setOnClickListener }
                val req = CategoryRequest(name, d.edtImage.text.toString().trim().ifEmpty { null })
                if (c == null) api({ createCategory(req) }) { toast("Đã thêm"); dialog.dismiss(); load() }
                else api({ updateCategory(c.id, req) }) { toast("Đã lưu"); dialog.dismiss(); load() }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(c: CategoryDto) {
        AlertDialog.Builder(this).setTitle("Xóa danh mục ${c.name}?")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteCategory(c.id) }) { toast("Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}

class CategoryAdminAdapter(
    private val onEdit: (CategoryDto) -> Unit,
    private val onDelete: (CategoryDto) -> Unit
) : BaseAdapter() {
    private var items: List<CategoryDto> = emptyList()
    fun submit(list: List<CategoryDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemAdminRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemAdminRowBinding.bind(convertView)
        val c = items[position]
        ImageLoader.load(b.img, b.imgText, c.imageUrl, c.name)
        b.tvTitle.text = c.name
        b.tvSub.text = "${c.productCount} món"
        b.tvBadge.visibility = View.GONE
        b.btnEdit.setOnClickListener { onEdit(c) }
        b.btnDelete.setOnClickListener { onDelete(c) }
        return b.root
    }
}
