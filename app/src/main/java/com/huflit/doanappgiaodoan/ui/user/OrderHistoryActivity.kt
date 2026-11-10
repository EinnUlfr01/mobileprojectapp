package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.OrderDto
import com.huflit.doanappgiaodoan.data.OrderStatus
import com.huflit.doanappgiaodoan.databinding.ActivityOrderHistoryBinding
import com.huflit.doanappgiaodoan.databinding.ItemOrderBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toDisplayDate
import com.huflit.doanappgiaodoan.util.toMoney

// Lịch sử đơn (ListView), lọc theo trạng thái bằng Spinner
class OrderHistoryActivity : AppCompatActivity() {
    private lateinit var b: ActivityOrderHistoryBinding
    private val adapter = OrderListAdapter()
    private val filters = listOf("Tất cả") + OrderStatus.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOrderHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Đơn hàng của tôi")

        b.spStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        b.spStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = load()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, OrderDetailActivity::class.java).putExtra(Nav.EXTRA_ID, adapter.getItem(pos).id))
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val pos = b.spStatus.selectedItemPosition
        val status = if (pos <= 0) null else filters[pos]
        api({ myOrders(status) }) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}

// ListView adapter cho đơn hàng (dùng cả ở màn quản lý đơn của admin)
class OrderListAdapter(private val showReceiver: Boolean = false) : BaseAdapter() {
    private var items: List<OrderDto> = emptyList()
    fun submit(list: List<OrderDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemOrderBinding.bind(convertView)
        val o = items[position]
        b.tvCode.text = "#${o.code}"
        b.tvStatus.text = o.status
        val color = when (o.status) {
            OrderStatus.CANCELLED -> R.color.danger
            OrderStatus.DONE -> R.color.primary_dark
            else -> R.color.star
        }
        b.tvStatus.setTextColor(parent.context.getColor(color))
        val who = if (showReceiver) "${o.receiverName}${if (o.isGuest) " (khách vãng lai)" else ""} · " else ""
        val paid = if (o.paymentMethod == "VNPay") (if (o.isPaid) " (đã TT)" else " (chưa TT)") else ""
        b.tvSub.text = "$who${o.createdAt.toDisplayDate()} · ${o.itemCount} món · ${o.paymentMethod}$paid"
        b.tvTotal.text = o.total.toMoney()
        return b.root
    }
}
