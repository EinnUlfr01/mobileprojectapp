package com.huflit.doanappgiaodoan.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.huflit.doanappgiaodoan.data.ChatMessageDto
import com.huflit.doanappgiaodoan.data.ChatThreadDto
import com.huflit.doanappgiaodoan.data.SendMessageRequest
import com.huflit.doanappgiaodoan.databinding.ActivityChatBinding
import com.huflit.doanappgiaodoan.databinding.ItemChatMeBinding
import com.huflit.doanappgiaodoan.databinding.ItemChatOtherBinding
import com.huflit.doanappgiaodoan.util.AppPoller
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toShortTime
import com.huflit.doanappgiaodoan.util.toast

// Chat trực tuyến (dùng chung):
// - Khách hàng: conversationId = 0, chat với cửa hàng
// - Nhân viên: conversationId = id cuộc trò chuyện với 1 khách
// Mỗi 3 giây hỏi server lấy tin mới (afterId = id tin cuối cùng) để cập nhật gần như tức thời.
class ChatActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatBinding
    private var conversationId = 0
    private val isStaffView get() = conversationId != 0
    private lateinit var adapter: ChatAdapter
    private var lastId = 0
    private var loading = false
    private val handler = Handler(Looper.getMainLooper())
    private val pollTask = object : Runnable {
        override fun run() {
            fetch()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)
        conversationId = intent.getIntExtra(EXTRA_CONVERSATION, 0)
        setupToolbar(b.toolbar, intent.getStringExtra(EXTRA_TITLE) ?: "Chat với cửa hàng")
        if (!isStaffView) b.toolbar.subtitle = "Thường trả lời trong vài phút"

        // Tin "của mình": khách -> tin không phải của nhân viên; nhân viên -> tin của nhân viên
        adapter = ChatAdapter(isMine = { m -> m.isFromStaff == isStaffView })
        b.rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.rvMessages.adapter = adapter

        intent.getStringExtra(EXTRA_PREFILL)?.let { b.edtMessage.setText(it); b.edtMessage.setSelection(it.length) }
        b.btnSend.setOnClickListener { send() }
    }

    override fun onResume() {
        super.onResume()
        AppPoller.chatScreenOpen = true // đang mở màn chat thì không bắn thông báo tin nhắn
        handler.post(pollTask)
    }

    override fun onPause() {
        super.onPause()
        AppPoller.chatScreenOpen = false
        handler.removeCallbacks(pollTask)
    }

    private fun fetch() {
        if (loading) return
        loading = true
        val after = lastId
        val onThread: (ChatThreadDto) -> Unit = { t ->
            loading = false
            if (after == 0 && isStaffView) b.toolbar.title = t.customerName
            addMessages(t.messages)
        }
        if (isStaffView) api({ conversation(conversationId, after) }, onError = { loading = false }, onSuccess = onThread)
        else api({ myChat(after) }, onError = { loading = false }, onSuccess = onThread)
    }

    private fun addMessages(list: List<ChatMessageDto>) {
        val fresh = list.filter { it.id > lastId }
        if (fresh.isNotEmpty()) {
            lastId = fresh.maxOf { it.id }
            adapter.add(fresh)
            b.rvMessages.scrollToPosition(adapter.itemCount - 1)
        }
        b.tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun send() {
        val text = b.edtMessage.text.toString().trim()
        if (text.isEmpty()) return
        b.btnSend.isEnabled = false
        val body = SendMessageRequest(text)
        val onSent: (ChatMessageDto) -> Unit = { m ->
            b.btnSend.isEnabled = true
            b.edtMessage.text?.clear()
            addMessages(listOf(m))
        }
        val onErr: (String) -> Unit = { b.btnSend.isEnabled = true; toast(it) }
        if (isStaffView) api({ reply(conversationId, body) }, onErr, onSent)
        else api({ sendToShop(body) }, onErr, onSent)
    }

    companion object {
        const val POLL_MS = 3000L
        const val EXTRA_CONVERSATION = "conversationId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PREFILL = "prefill"

        // Khách mở chat với cửa hàng, có thể điền sẵn nội dung (vd hỏi về 1 đơn)
        fun openForCustomer(ctx: Context, prefill: String? = null) =
            ctx.startActivity(Intent(ctx, ChatActivity::class.java).putExtra(EXTRA_PREFILL, prefill))

        // Nhân viên mở cuộc trò chuyện với 1 khách
        fun openForStaff(ctx: Context, conversationId: Int, customerName: String) =
            ctx.startActivity(Intent(ctx, ChatActivity::class.java)
                .putExtra(EXTRA_CONVERSATION, conversationId).putExtra(EXTRA_TITLE, customerName))
    }
}

// RecyclerView 2 kiểu dòng: tin của mình (phải, nền xanh) và tin của người kia (trái, nền trắng)
class ChatAdapter(private val isMine: (ChatMessageDto) -> Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<ChatMessageDto>()

    class MeVH(val b: ItemChatMeBinding) : RecyclerView.ViewHolder(b.root)
    class OtherVH(val b: ItemChatOtherBinding) : RecyclerView.ViewHolder(b.root)

    fun add(list: List<ChatMessageDto>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (isMine(items[position])) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 1) MeVH(ItemChatMeBinding.inflate(inf, parent, false))
        else OtherVH(ItemChatOtherBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        when (holder) {
            is MeVH -> {
                holder.b.tvContent.text = m.content
                holder.b.tvTime.text = m.createdAt.toShortTime()
            }
            is OtherVH -> {
                holder.b.tvContent.text = m.content
                holder.b.tvTime.text = m.createdAt.toShortTime()
                // Chỉ hiện tên người gửi khi khác người gửi tin ngay trước
                val showName = position == 0 || items[position - 1].senderName != m.senderName || isMine(items[position - 1])
                holder.b.tvSender.text = m.senderName
                holder.b.tvSender.visibility = if (showName) View.VISIBLE else View.GONE
            }
        }
    }
}
