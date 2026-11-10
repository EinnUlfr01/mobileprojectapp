package com.huflit.doanappgiaodoan.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.huflit.doanappgiaodoan.data.ApiClient
import com.huflit.doanappgiaodoan.data.ChatMessageDto
import com.huflit.doanappgiaodoan.data.Session
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.CopyOnWriteArraySet

// Kết nối realtime tới server (SignalR, đường dẫn /hubs/chat).
// Có tin nhắn mới, server đẩy sự kiện "message" (conversationId, tin nhắn) xuống ngay lập tức.
// Màn hình nào cần nghe thì addListener / removeListener.
object ChatRealtime {
    private const val TAG = "CHAT_REALTIME"
    private val main = Handler(Looper.getMainLooper())
    private var connection: HubConnection? = null
    private var connectedToken: String? = null
    private val listeners = CopyOnWriteArraySet<(Int, ChatMessageDto) -> Unit>()
    private val typingListeners = CopyOnWriteArraySet<(Int, Boolean) -> Unit>()
    private var lastTypingSent = 0L

    val isConnected get() = connection?.connectionState == HubConnectionState.CONNECTED

    fun addListener(l: (Int, ChatMessageDto) -> Unit) { listeners += l }
    fun removeListener(l: (Int, ChatMessageDto) -> Unit) { listeners -= l }

    // "Đang nhập…": (conversationId, người đang gõ có phải nhân viên không)
    fun addTypingListener(l: (Int, Boolean) -> Unit) { typingListeners += l }
    fun removeTypingListener(l: (Int, Boolean) -> Unit) { typingListeners -= l }

    // Báo cho bên kia mình đang gõ (tối đa 1 lần / 2 giây cho đỡ tốn mạng)
    fun sendTyping(conversationId: Int) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 2000 || !isConnected) return
        lastTypingSent = now
        try { connection?.send("Typing", conversationId) } catch (_: Exception) { }
    }

    // Gọi khi app hiện lên màn hình (FoodApp) và định kỳ để tự nối lại nếu bị rớt
    fun connect() {
        val token = Session.token
        if (!Session.isLoggedIn || token.isNullOrEmpty()) { disconnect(); return }
        // Đổi tài khoản thì nối lại bằng token mới
        if (connection != null && connectedToken != token) disconnect()
        val conn = connection ?: HubConnectionBuilder.create(ApiClient.BASE_URL + "hubs/chat")
            .withAccessTokenProvider(Single.defer { Single.just(Session.token ?: "") })
            .build()
            .also { c ->
                c.on("message", { conversationId: Int, message: ChatMessageDto ->
                    // SignalR gọi trên luồng nền -> chuyển về luồng giao diện
                    main.post { listeners.forEach { it(conversationId, message) } }
                }, Int::class.javaObjectType, ChatMessageDto::class.java)
                c.on("typing", { conversationId: Int, fromStaff: Boolean ->
                    main.post { typingListeners.forEach { it(conversationId, fromStaff) } }
                }, Int::class.javaObjectType, Boolean::class.javaObjectType)
                c.onClosed { e -> Log.w(TAG, "Mất kết nối realtime: ${e?.message}") }
                connection = c
                connectedToken = token
            }
        if (conn.connectionState == HubConnectionState.DISCONNECTED) {
            try {
                conn.start().subscribe(
                    { Log.i(TAG, "Đã kết nối realtime") },
                    { e -> Log.w(TAG, "Không kết nối được realtime, dùng chế độ hỏi định kỳ: ${e.message}") }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi kết nối realtime", e)
            }
        }
    }

    fun disconnect() {
        try { connection?.stop() } catch (_: Exception) { }
        connection = null
        connectedToken = null
    }
}
