package com.huflit.doanappgiaodoan.ui.shipper

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.huflit.doanappgiaodoan.data.OrderDto
import com.huflit.doanappgiaodoan.data.OrderStatus
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityShipperBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.ui.user.OrderDetailActivity
import com.huflit.doanappgiaodoan.ui.user.OrderListAdapter
import com.huflit.doanappgiaodoan.util.AppPoller
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.showActionSheet
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast

// Màn shipper (ListView + TabLayout):
// - Đơn chờ nhận: quán đã nhận đơn, chưa có shipper -> bấm "Nhận đơn"
// - Đang giao: tới quán lấy món ("Đã lấy món") rồi giao cho khách ("Đã giao xong")
// - Đã giao: lịch sử đơn đã giao
class ShipperActivity : AppCompatActivity() {
    private lateinit var b: ActivityShipperBinding
    private val adapter = OrderListAdapter(showReceiver = true)
    private var tab = 0
    private val askNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityShipperBinding.inflate(layoutInflater)
        setContentView(b.root)
        val me = Session.user
        b.tvName.text = me?.fullName ?: "Shipper"
        b.tvRole.text = "Shipper" + (me?.vehiclePlate?.let { " · $it" } ?: "")

        listOf("Đơn chờ nhận", "Đang giao", "Đã giao").forEach { b.tabs.addTab(b.tabs.newTab().setText(it)) }
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; load() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) { load() }
        })
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ -> showActions(adapter.getItem(pos)) }
        b.swipe.setOnRefreshListener { load() }
        b.btnLogout.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Đăng xuất?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    Session.logout()
                    startActivity(Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }.setNegativeButton("Hủy", null).show()
        }
        if (Build.VERSION.SDK_INT >= 33) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        load()
        AppPoller.onUpdate = { load() }
    }

    override fun onPause() {
        super.onPause()
        AppPoller.onUpdate = null
    }

    private fun load() {
        api({ shipperSummary() }, onError = {}) { s ->
            b.tvAvailable.text = s.available.toString()
            b.tvDelivering.text = s.delivering.toString()
            b.tvToday.text = "${s.deliveredToday} đơn · ${s.incomeToday.toMoney()}"
            b.tabs.getTabAt(0)?.text = "Đơn chờ nhận (${s.available})"
            b.tabs.getTabAt(1)?.text = "Đang giao (${s.delivering})"
        }
        val onList: (List<OrderDto>) -> Unit = { list ->
            b.swipe.isRefreshing = false
            adapter.submit(list)
            b.tvEmpty.text = when (tab) {
                0 -> "Chưa có đơn nào chờ giao. Khi quán nhận đơn, đơn sẽ hiện ở đây."
                1 -> "Bạn chưa nhận đơn nào. Vào tab Đơn chờ nhận để nhận đơn."
                else -> "Chưa giao đơn nào"
            }
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        val onErr: (String) -> Unit = { b.swipe.isRefreshing = false; toast(it) }
        when (tab) {
            0 -> api({ shipperAvailable() }, onErr, onList)
            1 -> api({ shipperMine(false) }, onErr, onList)
            else -> api({ shipperMine(true) }, onErr, onList)
        }
    }

    // Bấm vào đơn: thông tin lấy/giao hàng + các nút theo trạng thái
    private fun showActions(o: OrderDto) {
        val collect = if (o.isPaid) "Khách đã thanh toán" else "Thu tiền mặt: ${o.total.toMoney()}"
        val info = "Lấy món tại: ${o.storeName ?: "?"}\n${o.storeAddress ?: ""}\n\n" +
            "Giao tới: ${o.receiverName} · ${o.phone}\n${o.address}" +
            (o.note?.let { "\nGhi chú: $it" } ?: "") + "\n\n$collect · Phí ship ${o.shippingFee.toMoney()}"
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        when {
            tab == 0 -> actions += "Nhận đơn này" to { act("Nhận đơn #${o.code}?") { shipperAccept(o.id) } }
            o.status == OrderStatus.PREPARING -> {
                actions += "Đã lấy món tại quán" to { act("Xác nhận đã lấy món?") { shipperPickup(o.id) } }
                actions += "Trả đơn (không giao được)" to { act("Trả đơn #${o.code} cho shipper khác?") { shipperRelease(o.id) } }
            }
            o.status == OrderStatus.SHIPPING ->
                actions += "Đã giao xong" to { act(if (o.isPaid) "Xác nhận đã giao cho khách?" else "Xác nhận đã giao và thu ${o.total.toMoney()}?") { shipperDeliver(o.id) } }
        }
        if (tab != 2) {
            actions += "Gọi khách (${o.phone})" to { dial(o.phone) }
            o.storePhone?.let { p -> actions += "Gọi quán ($p)" to { dial(p) } }
            actions += "Chỉ đường tới khách" to { openMap(o.address) }
        }
        actions += "Xem chi tiết đơn" to { startActivity(Intent(this, OrderDetailActivity::class.java).putExtra(Nav.EXTRA_ID, o.id)) }
        showActionSheet("Đơn #${o.code} · ${o.status}", info, actions, boldFirst = tab != 2)
    }

    // Hỏi xác nhận rồi gọi API, xong thì tải lại
    private fun act(question: String, call: suspend com.huflit.doanappgiaodoan.data.ApiService.() -> com.huflit.doanappgiaodoan.data.MessageDto) {
        AlertDialog.Builder(this).setTitle(question)
            .setPositiveButton("Đồng ý") { _, _ -> api(call) { toast(it.message ?: "Đã cập nhật"); load() } }
            .setNegativeButton("Hủy", null).show()
    }

    private fun dial(phone: String) = startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

    private fun openMap(address: String) {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { toast("Máy chưa có ứng dụng bản đồ") }
    }
}
