package com.huflit.doanappgiaodoan.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.ApiClient
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.admin.ManageOrdersActivity
import com.huflit.doanappgiaodoan.ui.shipper.ShipperActivity
import com.huflit.doanappgiaodoan.ui.chat.ChatActivity
import com.huflit.doanappgiaodoan.ui.chat.ChatListActivity
import com.huflit.doanappgiaodoan.ui.user.OrderDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Khi app đang mở, cứ 8 giây hỏi server 1 lần:
// - Khách: có thông báo đơn hàng mới không, cửa hàng có trả lời tin nhắn không
// - Quản trị sàn / chủ quán: có đơn mới không, khách có nhắn tin không (chat chỉ quản trị sàn)
// - Shipper: có đơn mới đang chờ shipper nhận không
// Có thì hiện thông báo trên thanh thông báo của điện thoại.
object AppPoller {
    private const val INTERVAL_MS = 8000L
    private const val CHANNEL_ID = "don_hang"

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var running = false
    private var busy = false

    @Volatile var chatScreenOpen = false     // đang ở màn chat thì không báo tin nhắn
    var onUpdate: (() -> Unit)? = null        // màn hình đang mở muốn cập nhật số đếm (chấm đỏ)

    private val tick = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Đơn hàng và tin nhắn", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Trạng thái đơn hàng, thanh toán, tin nhắn hỗ trợ"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun start(context: Context) {
        app = context.applicationContext
        if (running) return
        running = true
        ChatRealtime.addListener(onRealtimeMessage)
        ChatRealtime.connect()
        handler.postDelayed(tick, 1500)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        ChatRealtime.removeListener(onRealtimeMessage)
        ChatRealtime.disconnect()
    }

    // Tin nhắn realtime tới: không ở màn chat thì bật thông báo ngay, không đợi lần hỏi tiếp theo
    private val onRealtimeMessage: (Int, com.huflit.doanappgiaodoan.data.ChatMessageDto) -> Unit = { _, m ->
        val fromOtherSide = m.isFromStaff != Session.isStaff
        if (fromOtherSide && !chatScreenOpen) {
            if (Session.isStaff)
                show(CHAT_NOTIFICATION_ID, "Tin nhắn từ ${m.senderName}", m.content, Intent(app, ChatListActivity::class.java))
            else
                show(CHAT_NOTIFICATION_ID, "Hỗ trợ khách hàng đã trả lời", m.content, Intent(app, ChatActivity::class.java))
        }
        onUpdate?.invoke()
    }

    private fun poll() {
        // Tự nối lại nếu bị rớt, đổi tài khoản thì nối bằng token mới, đăng xuất thì ngắt
        ChatRealtime.connect()
        if (busy || !Session.isLoggedIn) return
        busy = true
        scope.launch {
            try {
                when {
                    Session.isManager -> pollStaff()
                    Session.isShipper -> pollShipper()
                    else -> pollCustomer()
                }
                onUpdate?.invoke()
            } catch (_: Exception) {
                // mất mạng / server tắt: bỏ qua, lần sau thử lại
            } finally {
                busy = false
            }
        }
    }

    // ---------- Khách hàng ----------
    private suspend fun pollCustomer() {
        val api = ApiClient.api
        val last = state.getInt(key("notif"), -1)
        val list = api.notifications(afterId = maxOf(last, 0)).sortedBy { it.id }
        if (last < 0) {
            // Lần đầu: chỉ ghi nhớ, không bắn thông báo cũ
            state.edit().putInt(key("notif"), list.maxOfOrNull { it.id } ?: 0).apply()
        } else if (list.isNotEmpty()) {
            list.takeLast(3).forEach { n ->
                val open = if (n.orderId != null)
                    Intent(app, OrderDetailActivity::class.java).putExtra(Nav.EXTRA_ID, n.orderId) else null
                show(n.id, n.title, n.body, open)
            }
            state.edit().putInt(key("notif"), list.last().id).apply()
        }

        val unread = api.chatUnread().count
        val lastChat = state.getInt(key("chat"), 0)
        if (unread > lastChat && !chatScreenOpen && !ChatRealtime.isConnected)
            show(CHAT_NOTIFICATION_ID, "Hỗ trợ khách hàng đã trả lời", "Bạn có $unread tin nhắn mới", Intent(app, ChatActivity::class.java))
        state.edit().putInt(key("chat"), unread).apply()
    }

    // ---------- Quản trị sàn / chủ quán ----------
    private suspend fun pollStaff() {
        val a = ApiClient.api.staffAlerts()
        val lastOrder = state.getInt(key("order"), -1)
        if (lastOrder >= 0 && a.latestOrderId > lastOrder)
            show(ORDER_NOTIFICATION_ID, "Có đơn hàng mới", "${a.pendingOrders} đơn đang chờ quán xác nhận",
                Intent(app, ManageOrdersActivity::class.java))
        state.edit().putInt(key("order"), a.latestOrderId).apply()
        if (!Session.isAdmin) return // chỉ quản trị sàn trả lời chat
        val lastChat = state.getInt(key("chat"), 0)
        if (a.chatUnread > lastChat && !chatScreenOpen && !ChatRealtime.isConnected)
            show(CHAT_NOTIFICATION_ID, "Tin nhắn mới từ khách hàng", "${a.chatUnread} tin chưa trả lời",
                Intent(app, ChatListActivity::class.java))
        state.edit().putInt(key("chat"), a.chatUnread).apply()
    }

    // ---------- Shipper: báo khi có đơn mới chờ nhận ----------
    private suspend fun pollShipper() {
        val s = ApiClient.api.shipperSummary()
        val last = state.getInt(key("job"), -1)
        if (last >= 0 && s.latestAvailableId > last && s.available > 0)
            show(ORDER_NOTIFICATION_ID, "Có đơn chờ giao", "${s.available} đơn đang chờ shipper nhận",
                Intent(app, ShipperActivity::class.java))
        if (s.latestAvailableId > 0 || last < 0) state.edit().putInt(key("job"), s.latestAvailableId).apply()
    }

    // ---------- Hiện thông báo ----------
    private fun show(id: Int, title: String, body: String, target: Intent?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        // Bấm thông báo: mở trang chủ đúng vai trò rồi mở màn chi tiết phía trên
        val home = Intent(app, Nav.homeClass())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val intents = if (target != null) arrayOf(home, target) else arrayOf(home)
        val pending = PendingIntent.getActivities(app, id, intents,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    // Lưu trạng thái riêng theo từng tài khoản
    private val state get() = app.getSharedPreferences("poller", Context.MODE_PRIVATE)
    private fun key(name: String) = "${Session.user?.id ?: 0}_$name"

    private const val CHAT_NOTIFICATION_ID = 900001
    private const val ORDER_NOTIFICATION_ID = 900002
}
