package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.NotificationDto
import com.huflit.doanappgiaodoan.databinding.ActivityNotificationsBinding
import com.huflit.doanappgiaodoan.databinding.ItemNotificationBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toShortTime

// Danh sách thông báo (ListView). Mở màn này thì đánh dấu tất cả đã đọc.
class NotificationsActivity : AppCompatActivity() {
    private lateinit var b: ActivityNotificationsBinding
    private val adapter = NotificationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Thông báo")
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ ->
            adapter.getItem(pos).orderId?.let {
                startActivity(Intent(this, OrderDetailActivity::class.java).putExtra(Nav.EXTRA_ID, it))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        api({ notifications() }) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            if (list.any { !it.isRead }) api({ readAllNotifications() }, onError = {}) { }
        }
    }
}

class NotificationAdapter : BaseAdapter() {
    private var items: List<NotificationDto> = emptyList()
    fun submit(list: List<NotificationDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemNotificationBinding.bind(convertView)
        val n = items[position]
        b.tvTitle.text = n.title
        b.tvBody.text = n.body
        b.tvTime.text = n.createdAt.toShortTime()
        b.dot.visibility = if (n.isRead) View.INVISIBLE else View.VISIBLE
        return b.root
    }
}
