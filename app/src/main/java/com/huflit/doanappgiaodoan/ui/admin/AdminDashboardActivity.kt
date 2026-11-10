package com.huflit.doanappgiaodoan.ui.admin

import android.Manifest
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.Role
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityAdminDashboardBinding
import com.huflit.doanappgiaodoan.databinding.ItemDashboardTileBinding
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.ui.chat.ChatListActivity
import com.huflit.doanappgiaodoan.util.AppPoller
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.toMoney

data class Tile(val title: String, val value: String, val desc: String, val target: Class<*>?)

// Trang chủ quản trị: GridView gồm 4 ô thống kê + các nút quản lý theo vai trò
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var b: ActivityAdminDashboardBinding
    private val adapter = TileAdapter()
    private var stats: List<Tile>? = null
    private var chatUnread = 0
    private val askNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvName.text = Session.user?.fullName ?: "Admin"
        b.tvRole.text = Role.label(Session.role)

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

        // Android 13+ hỏi quyền hiện thông báo (đơn mới, tin nhắn mới)
        if (Build.VERSION.SDK_INT >= 33) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onPause() {
        super.onPause()
        AppPoller.onUpdate = null
    }

    // Biểu đồ doanh thu 7 ngày: lấy thống kê theo ngày, ngày không có đơn thì = 0
    private fun loadChart() {
        if (!Session.canManageOrders) { b.chartCard.visibility = View.GONE; return }
        val fmtQuery = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val fmtLabel = SimpleDateFormat("dd/MM", Locale.US)
        val days = (6 downTo 0).map { Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -it) }.time }
        api({ stats("day", fmtQuery.format(days.first()), fmtQuery.format(days.last())) }, onError = {}) { s ->
            val byLabel = s.rows.associate { it.label to it.revenue }
            val labels = days.map { fmtLabel.format(it) }
            b.chart.setData(labels, labels.map { byLabel[it] ?: 0.0 })
            b.tvWeekTotal.text = s.totalRevenue.toMoney()
            b.chartCard.visibility = View.VISIBLE
        }
    }

    // Số tin nhắn khách chưa được trả lời
    private fun loadChatUnread() {
        api({ chatUnread() }, onError = {}) {
            chatUnread = it.count
            showTiles(stats)
        }
    }

    override fun onResume() {
        super.onResume()
        loadChatUnread()
        loadChart()
        AppPoller.onUpdate = { loadChatUnread() }
        api({ dashboard() }) { d ->
            // Ô thống kê: chỉ dẫn tới màn mà vai trò hiện tại được vào
            val orders = if (Session.canManageOrders) ManageOrdersActivity::class.java else null
            val products = if (Session.canManageMenu) ManageProductsActivity::class.java else null
            val users = if (Session.canManageUsers) ManageUsersActivity::class.java else null
            stats = listOf(
                Tile("Tổng user", d.totalUsers.toString(), "Thành viên đã đăng ký", users),
                Tile("Đơn hôm nay", d.ordersToday.toString(), "${d.pendingOrders} đơn chờ xử lý", orders),
                Tile("Doanh thu tháng", d.revenueThisMonth.toMoney(), "Đơn đã hoàn thành", orders),
                Tile("Số món", d.totalProducts.toString(), "Đang bán", products)
            )
            showTiles(stats)
        }
    }

    // Nút quản lý hiện theo vai trò:
    // Quản trị viên: tất cả; Quản lý thực đơn: danh mục + sản phẩm; Nhân viên đơn hàng: đơn hàng
    private fun showTiles(stats: List<Tile>?) {
        val actions = mutableListOf<Tile>()
        if (Session.canManageUsers)
            actions += Tile("Quản lý", "Người dùng", "Thêm, sửa, xóa, phân quyền", ManageUsersActivity::class.java)
        if (Session.canManageMenu) {
            actions += Tile("Quản lý", "Danh mục", "Thêm, sửa, xóa danh mục", ManageCategoriesActivity::class.java)
            actions += Tile("Quản lý", "Sản phẩm", "Món ăn theo danh mục", ManageProductsActivity::class.java)
        }
        if (Session.canManageMenu)
            actions += Tile("Quản lý", "Mã giảm giá", "Tạo mã giảm %, giảm tiền, freeship", ManageVouchersActivity::class.java)
        if (Session.canManageOrders)
            actions += Tile("Quản lý", "Đơn hàng", "Trạng thái và thống kê", ManageOrdersActivity::class.java)
        // Mọi vai trò quản trị đều trả lời chat của khách
        actions += Tile("Chat trực tuyến", "Tin nhắn",
            if (chatUnread > 0) "$chatUnread tin chưa trả lời" else "Trả lời khách hàng", ChatListActivity::class.java)
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
