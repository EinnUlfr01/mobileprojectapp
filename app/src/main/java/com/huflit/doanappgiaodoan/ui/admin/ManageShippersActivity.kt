package com.huflit.doanappgiaodoan.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.AdminUserRequest
import com.huflit.doanappgiaodoan.data.LockRequest
import com.huflit.doanappgiaodoan.data.Role
import com.huflit.doanappgiaodoan.data.ShipperDto
import com.huflit.doanappgiaodoan.data.UserDto
import com.huflit.doanappgiaodoan.databinding.ActivityAdminListBinding
import com.huflit.doanappgiaodoan.databinding.DialogUserBinding
import com.huflit.doanappgiaodoan.databinding.ItemAdminRowBinding
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Quản trị sàn: quản lý shipper (ListView) - thêm, sửa, khóa / mở khóa, xóa, xem số đơn đang giao và đã giao
class ManageShippersActivity : AppCompatActivity() {
    private lateinit var b: ActivityAdminListBinding
    private val adapter = ShipperAdapter(onEdit = { showDialog(it) }, onDelete = { confirmDelete(it) })
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Quản lý shipper")
        b.searchView.queryHint = "Tìm theo tên, SĐT, biển số"
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ -> showActions(adapter.getItem(pos)) }
        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean { query = q ?: ""; load(); return true }
            override fun onQueryTextChange(q: String?): Boolean { query = q ?: ""; load(); return true }
        })
        b.fab.setOnClickListener { showDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        api({ shippers(query.trim().ifEmpty { null }) }) { list ->
            adapter.submit(list)
            b.tvSummary.text = "${list.size} shipper · ${list.count { it.delivering > 0 }} đang giao · " +
                "${list.sumOf { it.deliveredToday }} đơn giao xong hôm nay"
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showActions(s: ShipperDto) {
        val options = arrayOf("Sửa thông tin", if (s.isLocked) "Mở khóa tài khoản" else "Khóa tài khoản", "Gọi ${s.phone}", "Xóa shipper")
        AlertDialog.Builder(this).setTitle(s.fullName)
            .setItems(options) { _, i ->
                when (i) {
                    0 -> showDialog(s)
                    1 -> api({ lockUser(s.id, LockRequest(!s.isLocked)) }) { toast(it.message ?: "Đã cập nhật"); load() }
                    2 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}")))
                    else -> confirmDelete(s)
                }
            }.show()
    }

    // Thêm / sửa shipper: dùng lại dialog người dùng, vai trò cố định là Shipper, có ô biển số xe
    private fun showDialog(s: ShipperDto?) {
        val d = DialogUserBinding.inflate(layoutInflater)
        d.spRole.visibility = View.GONE
        d.tvRoleLabel.visibility = View.GONE
        d.tilAddress.visibility = View.GONE
        d.tilPlate.visibility = View.VISIBLE
        if (s != null) {
            d.edtName.setText(s.fullName)
            d.edtEmail.setText(s.email)
            d.edtPhone.setText(s.phone)
            d.edtPlate.setText(s.vehiclePlate ?: "")
        } else d.tilPassword.hint = "Mật khẩu (tối thiểu 6 ký tự)"

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (s == null) "Thêm shipper" else "Sửa shipper")
            .setView(d.root).setPositiveButton("Lưu", null).setNegativeButton("Hủy", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val req = AdminUserRequest(
                    fullName = d.edtName.text.toString().trim(),
                    email = d.edtEmail.text.toString().trim(),
                    phone = d.edtPhone.text.toString().trim(),
                    password = d.edtPassword.text.toString().ifEmpty { null },
                    role = Role.SHIPPER,
                    defaultAddress = null,
                    vehiclePlate = d.edtPlate.text.toString().trim().ifEmpty { null }
                )
                val onOk: (UserDto) -> Unit = { toast("Đã lưu"); dialog.dismiss(); load() }
                if (s == null) api({ createUser(req) }, onSuccess = onOk)
                else api({ updateUser(s.id, req) }, onSuccess = onOk)
            }
        }
        dialog.show()
    }

    private fun confirmDelete(s: ShipperDto) {
        AlertDialog.Builder(this).setTitle("Xóa shipper ${s.fullName}?")
            .setMessage("Các đơn đã giao vẫn được giữ lại. Nếu chỉ tạm ngừng, hãy dùng Khóa tài khoản.")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteUser(s.id) }) { toast("Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}

class ShipperAdapter(
    private val onEdit: (ShipperDto) -> Unit,
    private val onDelete: (ShipperDto) -> Unit
) : BaseAdapter() {
    private var items: List<ShipperDto> = emptyList()
    fun submit(list: List<ShipperDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemAdminRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemAdminRowBinding.bind(convertView)
        val s = items[position]
        ImageLoader.load(b.img, b.imgText, null, s.fullName)
        b.tvTitle.text = s.fullName
        b.tvSub.text = "${s.phone} · ${s.vehiclePlate ?: "chưa có biển số"}\nĐã giao ${s.deliveredTotal} đơn (hôm nay ${s.deliveredToday})"
        b.tvBadge.visibility = View.VISIBLE
        b.tvBadge.text = when { s.isLocked -> "Đã khóa"; s.delivering > 0 -> "Đang giao ${s.delivering}"; else -> "Rảnh" }
        b.tvBadge.setTextColor(parent.context.getColor(if (s.isLocked) R.color.danger else R.color.primary_dark))
        b.btnEdit.setOnClickListener { onEdit(s) }
        b.btnDelete.setOnClickListener { onDelete(s) }
        return b.root
    }
}
