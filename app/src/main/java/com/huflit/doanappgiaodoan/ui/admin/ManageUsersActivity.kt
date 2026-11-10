package com.huflit.doanappgiaodoan.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.AdminUserRequest
import com.huflit.doanappgiaodoan.data.Role
import com.huflit.doanappgiaodoan.data.UserDto
import com.huflit.doanappgiaodoan.databinding.ActivityManageUsersBinding
import com.huflit.doanappgiaodoan.databinding.DialogUserBinding
import com.huflit.doanappgiaodoan.databinding.ItemAdminRowBinding
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Quản lý user (ListView): thêm, sửa, xóa, chuyển vai trò (Thành viên / Quản trị viên / Quản lý thực đơn / Nhân viên đơn hàng) bằng Dialog
class ManageUsersActivity : AppCompatActivity() {
    private lateinit var b: ActivityManageUsersBinding
    private val roles = Role.ALL
    private val filters = listOf("Tất cả vai trò") + Role.ALL.map { Role.label(it) }
    private val adapter = UserAdapter(onEdit = { showDialog(it) }, onDelete = { confirmDelete(it) })
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageUsersBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Quản lý người dùng")

        b.listView.adapter = adapter
        b.spRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        b.spRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = load()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean { query = q ?: ""; load(); return true }
            override fun onQueryTextChange(q: String?): Boolean { query = q ?: ""; load(); return true }
        })
        b.fab.setOnClickListener { showDialog(null) }
    }

    private fun load() {
        val pos = b.spRole.selectedItemPosition
        val role = if (pos <= 0) null else roles[pos - 1]
        api({ users(query.trim().ifEmpty { null }, role) }) { adapter.submit(it) }
    }

    private fun showDialog(user: UserDto?) {
        val d = DialogUserBinding.inflate(layoutInflater)
        d.spRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles.map { Role.label(it) })
        if (user != null) {
            d.edtName.setText(user.fullName)
            d.edtEmail.setText(user.email)
            d.edtPhone.setText(user.phone)
            d.edtAddress.setText(user.defaultAddress ?: "")
            d.spRole.setSelection(roles.indexOf(user.role).coerceAtLeast(0))
        } else d.tilPassword.hint = "Mật khẩu (tối thiểu 6 ký tự)"

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (user == null) "Thêm người dùng" else "Sửa người dùng")
            .setView(d.root).setPositiveButton("Lưu", null).setNegativeButton("Hủy", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val req = AdminUserRequest(
                    fullName = d.edtName.text.toString().trim(),
                    email = d.edtEmail.text.toString().trim(),
                    phone = d.edtPhone.text.toString().trim(),
                    password = d.edtPassword.text.toString().ifEmpty { null },
                    role = roles[d.spRole.selectedItemPosition],
                    defaultAddress = d.edtAddress.text.toString().trim().ifEmpty { null }
                )
                val onOk: (UserDto) -> Unit = { toast("Đã lưu"); dialog.dismiss(); load() }
                if (user == null) api({ createUser(req) }, onSuccess = onOk)
                else api({ updateUser(user.id, req) }, onSuccess = onOk)
            }
        }
        dialog.show()
    }

    private fun confirmDelete(user: UserDto) {
        AlertDialog.Builder(this).setTitle("Xóa ${user.fullName}?")
            .setMessage("Đơn hàng cũ của người này vẫn được giữ lại.")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteUser(user.id) }) { toast("Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}

class UserAdapter(
    private val onEdit: (UserDto) -> Unit,
    private val onDelete: (UserDto) -> Unit
) : BaseAdapter() {
    private var items: List<UserDto> = emptyList()
    fun submit(list: List<UserDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemAdminRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemAdminRowBinding.bind(convertView)
        val u = items[position]
        ImageLoader.load(b.img, b.imgText, null, u.fullName)
        b.tvTitle.text = u.fullName
        b.tvSub.text = "${u.email} · ${u.phone}"
        b.tvBadge.text = Role.label(u.role)
        b.tvBadge.visibility = View.VISIBLE
        b.btnEdit.setOnClickListener { onEdit(u) }
        b.btnDelete.setOnClickListener { onDelete(u) }
        return b.root
    }
}
