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
import com.huflit.doanappgiaodoan.MainActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.ApiClient
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.admin.AdminDashboardActivity
import com.huflit.doanappgiaodoan.ui.admin.ManageOrdersActivity
import com.huflit.doanappgiaodoan.ui.chat.ChatActivity
import com.huflit.doanappgiaodoan.ui.chat.ChatListActivity
import com.huflit.doanappgiaodoan.ui.user.OrderDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Khi app đang mở, cứ 8 giây hỏi server 1 lần:
// - Khách: có thông báo đơn hàng mới không, cửa hàng có trả lời tin nhắn không
// - Nhân viên: có đơn mới không, khách có nhắn tin không
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
            ch.description = "Trạng thái đơn hàng, thanh toán, tin nhắn từ cửa hàng"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun start(context: Context) {
        app = context.applicationContext
        if (running) return
        running = true
        handler.postDelayed(tick, 1500)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun poll() {
        if (busy || !Session.isLoggedIn) return
        busy = true
        scope.launch {
            try {
                if (Session.isStaff) pollStaff() else pollCustomer()
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
        if (unread > lastChat && !chatScreenOpen)
            show(CHAT_NOTIFICATION_ID, "Cửa hàng đã trả lời", "Bạn có $unread tin nhắn mới", Intent(app, ChatActivity::class.java))
        state.edit().putInt(key("chat"), unread).apply()
    }

    // ---------- Nhân viên ----------
    private suspend fun pollStaff() {
        val a = ApiClient.api.staffAlerts()
        if (Session.canManageOrders) {
            val lastOrder = state.getInt(key("order"), -1)
            if (lastOrder >= 0 && a.latestOrderId > lastOrder)
                show(ORDER_NOTIFICATION_ID, "Có đơn hàng mới", "${a.pendingOrders} đơn đang chờ xử lý",
                    Intent(app, ManageOrdersActivity::class.java))
            state.edit().putInt(key("order"), a.latestOrderId).apply()
        }
        val lastChat = state.getInt(key("chat"), 0)
        if (a.chatUnread > lastChat && !chatScreenOpen)
            show(CHAT_NOTIFICATION_ID, "Tin nhắn mới từ khách hàng", "${a.chatUnread} tin chưa trả lời",
                Intent(app, ChatListActivity::class.java))
        state.edit().putInt(key("chat"), a.chatUnread).apply()
    }

    // ---------- Hiện thông báo ----------
    private fun show(id: Int, title: String, body: String, target: Intent?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        // Bấm thông báo: mở trang chủ đúng vai trò rồi mở màn chi tiết phía trên
        val home = Intent(app, if (Session.isStaff) AdminDashboardActivity::class.java else MainActivity::class.java)
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
