package com.huflit.doanappgiaodoan.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.StoreDto
import com.huflit.doanappgiaodoan.databinding.ActivityAdminListBinding
import com.huflit.doanappgiaodoan.databinding.ItemAdminRowBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.storeInfo
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Quản trị sàn: danh sách cửa hàng (ListView), thêm / sửa / xóa quán, xem thực đơn của quán
class ManageStoresActivity : AppCompatActivity() {
    private lateinit var b: ActivityAdminListBinding
    private val adapter = StoreAdminAdapter(onEdit = { openEdit(it.id) }, onDelete = { confirmDelete(it) })
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Quản lý cửa hàng")
        b.searchView.queryHint = "Tìm theo tên quán, địa chỉ"
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ -> showActions(adapter.getItem(pos)) }
        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean { query = q ?: ""; load(); return true }
            override fun onQueryTextChange(q: String?): Boolean { query = q ?: ""; load(); return true }
        })
        b.fab.setOnClickListener { openEdit(0) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        api({ allStores(query.trim().ifEmpty { null }) }) { list ->
            adapter.submit(list)
            b.tvSummary.text = "${list.size} quán · ${list.count { it.isActive && it.isOpen }} đang mở · ${list.count { !it.isActive }} đang khóa"
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showActions(s: StoreDto) {
        AlertDialog.Builder(this).setTitle(s.name)
            .setItems(arrayOf("Sửa thông tin, gán chủ quán", "Xem quán như khách hàng", "Xem đơn của quán")) { _, i ->
                when (i) {
                    0 -> openEdit(s.id)
                    1 -> Nav.store(this, s.id)
                    else -> startActivity(Intent(this, ManageOrdersActivity::class.java).putExtra(ManageOrdersActivity.EXTRA_STORE_ID, s.id))
                }
            }.show()
    }

    private fun openEdit(id: Int) =
        startActivity(Intent(this, StoreEditActivity::class.java).putExtra(Nav.EXTRA_ID, id))

    private fun confirmDelete(s: StoreDto) {
        AlertDialog.Builder(this).setTitle("Xóa quán ${s.name}?")
            .setMessage("Chỉ xóa được quán chưa có món và chưa có đơn. Quán đã hoạt động thì nên tạm khóa.")
            .setPositiveButton("Xóa") { _, _ -> api({ deleteStore(s.id) }) { toast(it.message ?: "Đã xóa"); load() } }
            .setNegativeButton("Hủy", null).show()
    }
}

class StoreAdminAdapter(
    private val onEdit: (StoreDto) -> Unit,
    private val onDelete: (StoreDto) -> Unit
) : BaseAdapter() {
    private var items: List<StoreDto> = emptyList()
    fun submit(list: List<StoreDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemAdminRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemAdminRowBinding.bind(convertView)
        val s = items[position]
        ImageLoader.load(b.img, b.imgText, s.imageUrl, s.name)
        b.tvTitle.text = s.name
        b.tvSub.text = "${s.address}\nChủ quán: ${s.ownerName ?: "chưa gán"} · ${storeInfo(s)}"
        b.tvBadge.visibility = View.VISIBLE
        b.tvBadge.text = when { !s.isActive -> "Đã khóa"; s.isOpen -> "Đang mở"; else -> "Đóng cửa" }
        b.tvBadge.setTextColor(parent.context.getColor(if (!s.isActive) R.color.danger else R.color.primary_dark))
        b.btnEdit.setOnClickListener { onEdit(s) }
        b.btnDelete.setOnClickListener { onDelete(s) }
        return b.root
    }
}
