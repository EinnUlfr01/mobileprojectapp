package com.huflit.doanappgiaodoan.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityAdminDashboardBinding
import com.huflit.doanappgiaodoan.databinding.ItemDashboardTileBinding
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.toMoney

data class Tile(val title: String, val value: String, val desc: String, val target: Class<*>?)

// Trang chủ admin: GridView gồm 4 ô thống kê + 4 nút quản lý
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var b: ActivityAdminDashboardBinding
    private val adapter = TileAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvName.text = Session.user?.fullName ?: "Admin"

        b.gridView.adapter = adapter
        b.gridView.setOnItemClickListener { _, _, pos, _ ->
            adapter.getItem(pos).target?.let { startActivity(Intent(this, it)) }
        }
        b.btnLogout.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Đăng xuất?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    Session.logout()
                    startActivity(Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }.setNegativeButton("Hủy", null).show()
        }
        showTiles(null)
    }

    override fun onResume() {
        super.onResume()
        api({ dashboard() }) { d ->
            showTiles(listOf(
                Tile("Tổng user", d.totalUsers.toString(), "Thành viên đã đăng ký", ManageUsersActivity::class.java),
                Tile("Đơn hôm nay", d.ordersToday.toString(), "${d.pendingOrders} đơn chờ xử lý", ManageOrdersActivity::class.java),
                Tile("Doanh thu tháng", d.revenueThisMonth.toMoney(), "Đơn đã hoàn thành", ManageOrdersActivity::class.java),
                Tile("Số món", d.totalProducts.toString(), "Đang bán", ManageProductsActivity::class.java)
            ))
        }
    }

    private fun showTiles(stats: List<Tile>?) {
        val actions = listOf(
            Tile("Quản lý", "Người dùng", "Thêm, sửa, xóa, phân quyền", ManageUsersActivity::class.java),
            Tile("Quản lý", "Danh mục", "Thêm, sửa, xóa danh mục", ManageCategoriesActivity::class.java),
            Tile("Quản lý", "Sản phẩm", "Món ăn theo danh mục", ManageProductsActivity::class.java),
            Tile("Quản lý", "Đơn hàng", "Trạng thái và thống kê", ManageOrdersActivity::class.java)
        )
        val loading = listOf("Tổng user", "Đơn hôm nay", "Doanh thu tháng", "Số món").map { Tile(it, "…", "", null) }
        adapter.submit((stats ?: loading) + actions)
    }
}

class TileAdapter : BaseAdapter() {
    private var items: List<Tile> = emptyList()
    fun submit(list: List<Tile>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemDashboardTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemDashboardTileBinding.bind(convertView)
        val t = items[position]
        b.tvTitle.text = t.title
        b.tvValue.text = t.value
        b.tvDesc.text = t.desc
        return b.root
    }
}
