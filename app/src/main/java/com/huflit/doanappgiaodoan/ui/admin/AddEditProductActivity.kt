package com.huflit.doanappgiaodoan.ui.admin

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.huflit.doanappgiaodoan.data.CategoryDto
import com.huflit.doanappgiaodoan.data.ProductRequest
import com.huflit.doanappgiaodoan.data.SizeDto
import com.huflit.doanappgiaodoan.databinding.ActivityAddEditProductBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.ImageUpload
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Thêm / sửa món: tên, mô tả, ảnh (link), danh mục, giá từng size
class AddEditProductActivity : AppCompatActivity() {
    private lateinit var b: ActivityAddEditProductBinding
    private var productId = 0
    private var catList: List<CategoryDto> = emptyList()
    private var pendingCategoryId: Int? = null

    // Chọn ảnh trong thư viện điện thoại -> upload lên API -> điền đường dẫn vào ô "Link ảnh"
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        b.btnPickImage.isEnabled = false
        b.btnPickImage.text = "Đang tải ảnh lên…"
        val reset = { b.btnPickImage.isEnabled = true; b.btnPickImage.text = "Chọn ảnh từ điện thoại" }
        api({ upload(ImageUpload.toPart(this@AddEditProductActivity, uri)) }, onError = { reset(); toast(it) }) {
            reset()
            b.edtImage.setText(it.url)
            toast("Đã tải ảnh lên")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddEditProductBinding.inflate(layoutInflater)
        setContentView(b.root)
        productId = intent.getIntExtra(Nav.EXTRA_ID, 0)
        setupToolbar(b.toolbar, if (productId == 0) "Thêm món" else "Sửa món")

        b.edtImage.doAfterTextChanged { previewImage() }
        b.edtName.doAfterTextChanged { previewImage() }
        b.btnSave.setOnClickListener { saveProduct() }
        b.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        previewImage()

        api({ categories() }) { list ->
            catList = list
            b.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list.map { it.name })
            selectCategory()
        }
        if (productId != 0) {
            api({ product(productId) }) { detail ->
                val p = detail.product
                b.edtName.setText(p.name)
                b.edtDesc.setText(p.description ?: "")
                b.edtImage.setText(p.imageUrl ?: "")
                b.cbNew.isChecked = p.isNew
                p.sizes.forEach { s ->
                    val price = s.price.toLong().toString()
                    when (s.size) { "S" -> b.edtS.setText(price); "M" -> b.edtM.setText(price); "L" -> b.edtL.setText(price) }
                }
                pendingCategoryId = p.categoryId
                selectCategory()
            }
        }
    }

    private fun selectCategory() {
        val id = pendingCategoryId ?: return
        val idx = catList.indexOfFirst { it.id == id }
        if (idx >= 0) b.spCategory.setSelection(idx)
    }

    private fun previewImage() {
        val name = b.edtName.text.toString().ifBlank { "?" }
        ImageLoader.load(b.img, b.imgText, b.edtImage.text.toString().trim().ifEmpty { null }, name)
    }

    private fun saveProduct() {
        val name = b.edtName.text.toString().trim()
        b.tilName.error = null
        if (name.isEmpty()) { b.tilName.error = "Nhập tên món"; return }
        if (catList.isEmpty()) { toast("Chưa có danh mục, hãy tạo danh mục trước"); return }

        val sizes = listOf("S" to b.edtS, "M" to b.edtM, "L" to b.edtL).mapNotNull { (size, edt) ->
            edt.text.toString().toDoubleOrNull()?.takeIf { it > 0 }?.let { SizeDto(size, it) }
        }
        if (sizes.isEmpty()) { toast("Nhập giá cho ít nhất 1 size"); return }

        val req = ProductRequest(
            categoryId = catList[b.spCategory.selectedItemPosition].id,
            name = name,
            description = b.edtDesc.text.toString().trim().ifEmpty { null },
            imageUrl = b.edtImage.text.toString().trim().ifEmpty { null },
            isNew = b.cbNew.isChecked,
            sizes = sizes
        )
        b.btnSave.isEnabled = false
        val onErr: (String) -> Unit = { b.btnSave.isEnabled = true; toast(it) }
        if (productId == 0) api({ createProduct(req) }, onErr) { toast("Đã thêm món"); finish() }
        else api({ updateProduct(productId, req) }, onErr) { toast("Đã lưu món"); finish() }
    }
}
