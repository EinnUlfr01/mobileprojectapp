package com.huflit.doanappgiaodoan.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.ConversationDto
import com.huflit.doanappgiaodoan.databinding.ActivityChatListBinding
import com.huflit.doanappgiaodoan.databinding.ItemConversationBinding
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toShortTime

// Nhân viên: danh sách khách đã nhắn tin (ListView), khách có tin chưa đọc nằm trên cùng.
// Tự làm mới mỗi 5 giây.
class ChatListActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatListBinding
    private val adapter = ConversationAdapter()
    private var query = ""
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            load()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Tin nhắn khách hàng")

        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ ->
            val c = adapter.getItem(pos)
            ChatActivity.openForStaff(this, c.id, "${c.customerName} · ${c.customerPhone}")
        }
        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean { query = q ?: ""; load(); return true }
            override fun onQueryTextChange(q: String?): Boolean { query = q ?: ""; load(); return true }
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private fun load() {
        api({ conversations(query.trim().ifEmpty { null }) }, onError = {}) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            val unread = list.sumOf { it.unread }
            b.toolbar.subtitle = if (unread > 0) "$unread tin chưa đọc" else null
        }
    }
}

class ConversationAdapter : BaseAdapter() {
    private var items: List<ConversationDto> = emptyList()
    fun submit(list: List<ConversationDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemConversationBinding.bind(convertView)
        val c = items[position]
        b.tvAvatar.text = c.customerName.trim().take(1).uppercase()
        b.tvName.text = c.customerName
        b.tvLast.text = c.lastMessage ?: ""
        b.tvTime.text = c.lastMessageAt.toShortTime()
        b.tvUnread.text = if (c.unread > 99) "99+" else c.unread.toString()
        b.tvUnread.visibility = if (c.unread > 0) View.VISIBLE else View.GONE
        b.tvName.paint.isFakeBoldText = c.unread > 0
        return b.root
    }
}
