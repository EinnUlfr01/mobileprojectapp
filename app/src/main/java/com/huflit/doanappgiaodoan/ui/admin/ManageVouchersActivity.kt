package com.huflit.doanappgiaodoan.ui.admin

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.VoucherDto
import com.huflit.doanappgiaodoan.data.VoucherRequest
import com.huflit.doanappgiaodoan.data.VoucherType
import com.huflit.doanappgiaodoan.databinding.ActivityManageVouchersBinding
import com.huflit.doanappgiaodoan.databinding.DialogVoucherBinding
import com.huflit.doanappgiaodoan.databinding.ItemAdminRowBinding
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast
import java.util.Calendar
import java.util.Locale

// Quản lý mã giảm giá (ListView + Dialog). Quản trị viên và Quản lý thực đơn dùng được.
class ManageVouchersActivity : AppCompatActivity() {
    private lateinit var b: ActivityManageVouchersBinding
    private val adapter = VoucherAdapter(onEdit = { showDialog(it) }, onDelete = { confirmDelete(it) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageVouchersBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Mã giảm giá")
        b.listView.adapter = adapter
        b.fab.setOnClickListener { showDialog(null) }
        load()
    }

    private fun load() = api({ allVouchers() }) { adapter.submit(it) }

    private fun showDialog(v: VoucherDto?) {
        val d = DialogVoucherBinding.inflate(layoutInflater)
        d.spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, VoucherType.ALL.map { VoucherType.label(it) })
        var expiry: Calendar? = null
        fun showExpiry() {
            d.btnExpiry.text = expiry?.let {
                String.format(Locale.US, "Hết hạn: %02d/%02d/%04d", it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.MONTH) + 1, it.get(Calendar.YEAR))
            } ?: "Không hết hạn (bấm để chọn ngày)"
        }
        if (v != null) {
            d.edtCode.setText(v.code)
            d.edtDesc.setText(v.description)
            d.spType.setSelection(VoucherType.ALL.indexOf(v.type).coerceAtLeast(0))
            d.edtValue.setText(v.value.toLong().toString())
            d.edtMinOrder.setText(v.minOrder.toLong().toString())
            d.edtMaxDiscount.setText(v.maxDiscount.toLong().toString())
            d.edtUsageLimit.setText(v.usageLimit.toString())
            d.cbActive.isChecked = v.isActive
            v.expiresAt?.let { s ->
                expiry = Calendar.getInstance().apply {
                    set(s.substring(0, 4).toInt(), s.substring(5, 7).toInt() - 1, s.substring(8, 10).toInt())
                }
            }
        }
        showExpiry()
        d.btnExpiry.setOnClickListener {
            val c = expiry ?: Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, day ->
                expiry = Calendar.getInstance().apply { set(y, m, day) }
                showExpiry()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                setButton(DatePickerDialog.BUTTON_NEUTRAL, "Không hết hạn") { _, _ -> expiry = null; showExpiry() }
            }.show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (v == null) "Thêm mã giảm giá" else "Sửa mã ${v.code}")
            .setView(d.root).setPositiveButton("Lưu", null).setNegativeButton("Hủy", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = d.edtCode.text.toString().trim().uppercase()
                if (code.length < 3) { d.tilCode.error = "Mã tối thiểu 3 ký tự"; return@setOnClickListener }
                val type = VoucherType.ALL[d.spType.selectedItemPosition]
                val value = d.edtValue.text.toString().toDoubleOrNull() ?: 0.0
                if (type != VoucherType.FREE_SHIP && value <= 0) { d.tilValue.error = "Nhập giá trị giảm"; return@setOnClickListener }
                // Hết hạn vào cuối ngày đã chọn (giờ VN = 16:59:59 UTC)
                val expiresAt = expiry?.let {
                    String.format(Locale.US, "%04d-%02d-%02dT16:59:59", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
                }
                val req = VoucherRequest(
                    code = code,
                    description = d.edtDesc.text.toString().trim(),
                    type = type,
                    value = value,
                    minOrder = d.edtMinOrder.text.toString().toDoubleOrNull() ?: 0.0,
                    maxDiscount = d.edtMaxDiscount.text.toString().toDoubleOrNull() ?: 0.0,
                    usageLimit = d.edtUsageLimit.text.toString().toIntOrNull() ?: 0,
                    expiresAt = expiresAt,
                    isActive = d.cbActive.isChecked
                )
                val onOk: (VoucherDto) -> Unit = { toast("Đã lưu"); dialog.dismiss(); load() }
                if (v == null) api({ createVoucher(req) }, onSuccess = onOk)
                else api({ updateVoucher(v.id, req) }, onSuccess = onOk)
            }
        }
        dialog.show()
    }

    private fun confirmDelete(v: VoucherDto) {
        AlertDialog.Builder(this).setTitle("Xóa mã ${v.code}?")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteVoucher(v.id) }) { toast("Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}

class VoucherAdapter(
    private val onEdit: (VoucherDto) -> Unit,
    private val onDelete: (VoucherDto) -> Unit
) : BaseAdapter() {
    private var items: List<VoucherDto> = emptyList()
    fun submit(list: List<VoucherDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemAdminRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemAdminRowBinding.bind(convertView)
        val v = items[position]
        ImageLoader.load(b.img, b.imgText, null, v.code)
        b.tvTitle.text = v.code
        val value = when (v.type) {
            VoucherType.PERCENT -> "Giảm ${v.value.toLong()}%" + if (v.maxDiscount > 0) " (tối đa ${v.maxDiscount.toMoney()})" else ""
            VoucherType.FREE_SHIP -> "Miễn phí ship"
            else -> "Giảm ${v.value.toMoney()}"
        }
        val limit = if (v.usageLimit > 0) "${v.usedCount}/${v.usageLimit} lượt" else "${v.usedCount} lượt"
        b.tvSub.text = "$value · Đơn từ ${v.minOrder.toMoney()} · $limit"
        b.tvBadge.text = if (v.isActive) "Đang chạy" else "Tạm dừng"
        b.tvBadge.visibility = View.VISIBLE
        b.btnEdit.setOnClickListener { onEdit(v) }
        b.btnDelete.setOnClickListener { onDelete(v) }
        return b.root
    }
}
