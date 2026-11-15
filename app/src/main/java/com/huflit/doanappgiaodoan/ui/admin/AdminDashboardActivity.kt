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
import com.huflit.doanappgiaodoan.data.DashboardDto
import com.huflit.doanappgiaodoan.data.OpenRequest
import com.huflit.doanappgiaodoan.data.Role
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityAdminDashboardBinding
import com.huflit.doanappgiaodoan.databinding.ItemDashboardTileBinding
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.ui.chat.ChatListActivity
import com.huflit.doanappgiaodoan.util.AppPoller
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast

data class Tile(val title: String, val value: String, val desc: String, val target: Class<*>?, val action: (() -> Unit)? = null)

// Trang quản trị (GridView gồm 4 ô thống kê + các nút quản lý), dùng chung cho 2 vai trò:
// - Quản trị sàn: cửa hàng, shipper, người dùng, danh mục, món, đơn toàn sàn, mã giảm giá, chat
// - Chủ cửa hàng: thông tin quán, mở/đóng quán, món của quán, đơn của quán
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var b: ActivityAdminDashboardBinding
    private val adapter = TileAdapter()
    private var stats: List<Tile>? = null
    private var chatUnread = 0
    private var dash: DashboardDto? = null
    private val askNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvName.text = Session.user?.fullName ?: "Admin"
        b.tvRole.text = Role.label(Session.role)

        b.gridView.adapter = adapter
        b.gridView.setOnItemClickListener { _, _, pos, _ ->
            val t = adapter.getItem(pos)
            t.action?.invoke() ?: t.target?.let { startActivity(Intent(this, it)) }
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

    // Biểu đồ doanh thu 7 ngày (chủ quán: của quán mình): lấy thống kê theo ngày, ngày không có đơn thì = 0
    private fun loadChart() {
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

    // Số tin nhắn khách chưa được trả lời (chỉ quản trị sàn)
    private fun loadChatUnread() {
        if (!Session.isAdmin) return
        api({ chatUnread() }, onError = {}) {
            chatUnread = it.count
            showTiles(stats)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        loadChatUnread()
        loadChart()
        AppPoller.onUpdate = { loadChatUnread() }
        api({ dashboard() }) { d ->
            dash = d
            if (Session.isStoreOwner)
                b.tvRole.text = if (d.storeName != null) "Chủ cửa hàng · ${d.storeName}" else "Chủ cửa hàng · chưa được gán quán"
            stats = if (Session.isAdmin) listOf(
                Tile("Cửa hàng", d.totalStores.toString(), "Quán trên sàn", ManageStoresActivity::class.java),
                Tile("Shipper", d.totalShippers.toString(), "Tài xế giao hàng", ManageShippersActivity::class.java),
                Tile("Đơn hôm nay", d.ordersToday.toString(), "${d.pendingOrders} đơn chờ quán xác nhận", ManageOrdersActivity::class.java),
                Tile("Doanh thu tháng", d.revenueThisMonth.toMoney(), "Toàn sàn, đơn hoàn thành", ManageOrdersActivity::class.java)
            ) else listOf(
                Tile("Đơn hôm nay", d.ordersToday.toString(), "${d.pendingOrders} đơn chờ xác nhận", ManageOrdersActivity::class.java),
                Tile("Doanh thu tháng", d.revenueThisMonth.toMoney(), "Đơn hoàn thành", ManageOrdersActivity::class.java),
                Tile("Số món", d.totalProducts.toString(), "Đang bán", ManageProductsActivity::class.java),
                Tile("Khách hàng", d.totalUsers.toString(), "Thành viên đã mua", null)
            )
            showTiles(stats)
        }
    }

    // Nút quản lý theo vai trò
    private fun showTiles(stats: List<Tile>?) {
        val actions = mutableListOf<Tile>()
        if (Session.isAdmin) {
            actions += Tile("Quản lý", "Cửa hàng", "Thêm quán, gán chủ quán, khóa quán", ManageStoresActivity::class.java)
            actions += Tile("Quản lý", "Shipper", "Tài xế, biển số, khóa tài khoản", ManageShippersActivity::class.java)
            actions += Tile("Quản lý", "Người dùng", "Thêm, sửa, xóa, phân quyền", ManageUsersActivity::class.java)
            actions += Tile("Quản lý", "Danh mục", "Danh mục món dùng chung", ManageCategoriesActivity::class.java)
            actions += Tile("Quản lý", "Món ăn", "Món của mọi quán", ManageProductsActivity::class.java)
            actions += Tile("Quản lý", "Đơn hàng", "Toàn sàn, gán shipper, thống kê", ManageOrdersActivity::class.java)
            actions += Tile("Quản lý", "Mã giảm giá", "Mã dùng cho mọi quán", ManageVouchersActivity::class.java)
            actions += Tile("Chăm sóc khách hàng", "Tin nhắn",
                if (chatUnread > 0) "$chatUnread tin chưa trả lời" else "Trả lời khách hàng", ChatListActivity::class.java)
        } else {
            val d = dash
            actions += Tile("Quán của tôi", "Thông tin quán", "Tên, địa chỉ, ảnh, mô tả", StoreEditActivity::class.java)
            if (d?.storeId != null)
                actions += Tile("Trạng thái", if (d.storeIsOpen) "Đang mở cửa" else "Đang đóng cửa",
                    if (d.storeIsOpen) "Bấm để tạm đóng quán" else "Bấm để mở lại quán", null) { toggleOpen(!d.storeIsOpen) }
            actions += Tile("Quản lý", "Món của quán", "Thêm, sửa, xóa món", ManageProductsActivity::class.java)
            actions += Tile("Quản lý", "Đơn của quán", "Nhận đơn, hủy đơn, thống kê", ManageOrdersActivity::class.java)
        }
        val loadingTitles = if (Session.isAdmin) listOf("Cửa hàng", "Shipper", "Đơn hôm nay", "Doanh thu tháng")
            else listOf("Đơn hôm nay", "Doanh thu tháng", "Số món", "Khách hàng")
        adapter.submit((stats ?: loadingTitles.map { Tile(it, "…", "", null) }) + actions)
    }

    // Chủ quán bật/tắt nhận đơn
    private fun toggleOpen(open: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (open) "Mở cửa nhận đơn?" else "Tạm đóng quán?")
            .setMessage(if (open) "Khách sẽ đặt được món của quán." else "Khách sẽ không đặt được món cho tới khi bạn mở lại.")
            .setPositiveButton(if (open) "Mở cửa" else "Đóng quán") { _, _ ->
                api({ setStoreOpen(OpenRequest(open)) }) { toast(it.message ?: "Đã cập nhật"); refresh() }
            }
            .setNegativeButton("Hủy", null).show()
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
