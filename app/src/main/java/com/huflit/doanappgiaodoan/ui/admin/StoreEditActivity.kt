package com.huflit.doanappgiaodoan.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.huflit.doanappgiaodoan.data.Role
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.data.StoreCategoryDto
import com.huflit.doanappgiaodoan.data.StoreDto
import com.huflit.doanappgiaodoan.data.StoreRequest
import com.huflit.doanappgiaodoan.data.UserDto
import com.huflit.doanappgiaodoan.databinding.ActivityStoreEditBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.ImageUpload
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Thêm / sửa quán.
// - Quản trị sàn: mở từ Quản lý cửa hàng (id = 0 là thêm mới), có gán chủ quán và khóa quán
// - Chủ quán: mở từ trang quản trị, sửa thông tin quán của mình
class StoreEditActivity : AppCompatActivity() {
    private lateinit var b: ActivityStoreEditBinding
    private val isAdmin get() = Session.isAdmin
    private var storeId = 0
    private var owners: List<UserDto> = emptyList()
    private var pendingOwnerId: Int? = null
    private var storeCats: List<StoreCategoryDto> = emptyList()
    private var pendingCatId: Int? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        b.btnPickImage.isEnabled = false
        b.btnPickImage.text = "Đang tải ảnh lên…"
        val reset = { b.btnPickImage.isEnabled = true; b.btnPickImage.text = "Chọn ảnh quán từ điện thoại" }
        api({ upload(ImageUpload.toPart(this@StoreEditActivity, uri)) }, onError = { reset(); toast(it) }) {
            reset()
            b.edtImage.setText(it.url)
            toast("Đã tải ảnh lên")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStoreEditBinding.inflate(layoutInflater)
        setContentView(b.root)
        storeId = intent.getIntExtra(Nav.EXTRA_ID, 0)
        setupToolbar(b.toolbar, when { !isAdmin -> "Thông tin quán của tôi"; storeId == 0 -> "Thêm cửa hàng"; else -> "Sửa cửa hàng" })

        b.adminBox.visibility = if (isAdmin) View.VISIBLE else View.GONE
        b.swOpen.isChecked = true
        b.swActive.isChecked = true
        b.edtImage.doAfterTextChanged { preview() }
        b.edtName.doAfterTextChanged { preview() }
        b.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        b.btnSave.setOnClickListener { save() }
        preview()

        // Loại quán (bảng DanhMucQuanAn)
        api({ storeCategories() }, onError = {}) { list ->
            storeCats = list
            b.spStoreCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                listOf("Chưa phân loại") + list.map { it.name })
            selectCategory()
        }

        if (isAdmin) {
            // Danh sách tài khoản chủ quán để gán
            api({ users(role = Role.STORE_OWNER) }) { list ->
                owners = list
                b.spOwner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Chưa gán chủ quán") + list.map { u ->
                        u.fullName + " · " + u.email + (if (u.storeId != null && u.storeId != storeId) " (đang quản lý quán khác)" else "")
                    })
                selectOwner()
            }
            if (storeId != 0) api({ store(storeId) }) { fill(it.store) }
        } else {
            api({ myStore() }, onError = { toast(it); finish() }) { fill(it) }
        }
    }

    private fun fill(s: StoreDto) {
        storeId = s.id
        b.edtName.setText(s.name)
        b.edtAddress.setText(s.address)
        b.edtPhone.setText(s.phone)
        b.edtDesc.setText(s.description ?: "")
        b.edtImage.setText(s.imageUrl ?: "")
        b.swOpen.isChecked = s.isOpen
        b.swActive.isChecked = s.isActive
        pendingOwnerId = s.ownerId
        selectOwner()
        pendingCatId = s.storeCategoryId
        selectCategory()
    }

    private fun selectCategory() {
        val id = pendingCatId ?: return
        val idx = storeCats.indexOfFirst { it.id == id }
        if (idx >= 0) b.spStoreCategory.setSelection(idx + 1)
    }

    private fun selectOwner() {
        val id = pendingOwnerId ?: return
        val idx = owners.indexOfFirst { it.id == id }
        if (idx >= 0) b.spOwner.setSelection(idx + 1)
    }

    private fun preview() {
        ImageLoader.load(b.img, b.imgText, b.edtImage.text.toString().trim().ifEmpty { null }, b.edtName.text.toString().ifBlank { "?" })
    }

    private fun save() {
        val name = b.edtName.text.toString().trim()
        b.tilName.error = null
        if (name.isEmpty()) { b.tilName.error = "Nhập tên quán"; return }
        val ownerPos = b.spOwner.selectedItemPosition
        val req = StoreRequest(
            name = name,
            address = b.edtAddress.text.toString().trim(),
            phone = b.edtPhone.text.toString().trim(),
            description = b.edtDesc.text.toString().trim().ifEmpty { null },
            imageUrl = b.edtImage.text.toString().trim().ifEmpty { null },
            isOpen = b.swOpen.isChecked,
            isActive = b.swActive.isChecked,
            ownerId = if (isAdmin && ownerPos > 0) owners[ownerPos - 1].id else null,
            storeCategoryId = b.spStoreCategory.selectedItemPosition.let { if (it > 0) storeCats.getOrNull(it - 1)?.id else pendingCatId }
        )
        b.btnSave.isEnabled = false
        val onErr: (String) -> Unit = { b.btnSave.isEnabled = true; toast(it) }
        val onOk: (StoreDto) -> Unit = { toast("Đã lưu thông tin quán"); finish() }
        when {
            !isAdmin -> api({ updateMyStore(req) }, onErr, onOk)
            storeId == 0 -> api({ createStore(req) }, onErr, onOk)
            else -> api({ updateStore(storeId, req) }, onErr, onOk)
        }
    }
}
