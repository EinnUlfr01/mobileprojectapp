package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.OrderDto
import com.huflit.doanappgiaodoan.data.OrderItemDto
import com.huflit.doanappgiaodoan.data.OrderStatus
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityOrderDetailBinding
import com.huflit.doanappgiaodoan.databinding.FooterOrderDetailBinding
import com.huflit.doanappgiaodoan.databinding.HeaderOrderDetailBinding
import com.huflit.doanappgiaodoan.databinding.ItemOrderLineBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toDisplayDate
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast

// Chi tiết 1 đơn (ListView các món). Nút "Đánh giá" chỉ hiện khi đơn đã hoàn thành.
class OrderDetailActivity : AppCompatActivity() {
    private lateinit var b: ActivityOrderDetailBinding
    private lateinit var header: HeaderOrderDetailBinding
    private lateinit var footer: FooterOrderDetailBinding
    private var orderId = 0
    private var current: OrderDto? = null
    private val adapter = OrderLineAdapter { item -> openReview(item) }

    // Thanh toán lại bằng VNPay
    private val payLauncher = registerForActivityResult(PaymentActivity.Contract()) { paid ->
        toast(if (paid) "Thanh toán thành công" else "Thanh toán chưa thành công")
        load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Chi tiết đơn")
        orderId = intent.getIntExtra(Nav.EXTRA_ID, 0)

        header = HeaderOrderDetailBinding.inflate(layoutInflater, b.listView, false)
        footer = FooterOrderDetailBinding.inflate(layoutInflater, b.listView, false)
        b.listView.addHeaderView(header.root, null, false)
        b.listView.addFooterView(footer.root, null, false)
        b.listView.adapter = adapter

        // Đặt lại: cho các món của đơn này vào giỏ rồi mở giỏ hàng
        b.btnReorder.setOnClickListener {
            api({ reorder(orderId) }) {
                toast(it.message ?: "Đã thêm vào giỏ")
                startActivity(Intent(this, CartActivity::class.java))
            }
        }
        // Mở chat với cửa hàng, điền sẵn mã đơn
        b.btnChatOrder.setOnClickListener {
            val code = current?.code ?: return@setOnClickListener
            com.huflit.doanappgiaodoan.ui.chat.ChatActivity.openForCustomer(this, "Mình cần hỗ trợ đơn #$code: ")
        }
        b.btnPay.setOnClickListener {
            api({ payOrder(orderId) }) { link -> payLauncher.launch(link.paymentUrl) }
        }
        b.btnCancel.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Hủy đơn?").setMessage("Bạn chắc chắn muốn hủy đơn này?")
                .setPositiveButton("Hủy đơn") { _, _ -> api({ cancelOrder(orderId) }) { toast(it.message ?: "Đã hủy"); load() } }
                .setNegativeButton("Không", null).show()
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        api({ order(orderId) }) { o ->
            current = o
            b.toolbar.title = "Đơn #${o.code}"
            header.tvStatus.text = o.status
            header.tvInfo.text = "Ngày đặt: ${o.createdAt.toDisplayDate()}\n" +
                "Người nhận: ${o.receiverName} · ${o.phone}\n" +
                "Địa chỉ: ${o.address}\n" +
                (o.note?.let { "Ghi chú: $it\n" } ?: "") +
                "Thanh toán: ${o.paymentMethod} (${if (o.isPaid) "đã thanh toán" else "chưa thanh toán"})"
            footer.tvTotals.text = "Tạm tính: ${o.subtotal.toMoney()}\n" +
                "Phí giao hàng: ${o.shippingFee.toMoney()}\n" +
                (if (o.discount > 0) "Mã ${o.voucherCode}: -${o.discount.toMoney()}\n" else "") +
                "Tổng thanh toán: ${o.total.toMoney()}"
            // Chỉ chủ đơn đánh giá được, và chỉ khi đơn hoàn thành
            val canReview = o.status == OrderStatus.DONE && !Session.isStaff
            adapter.submit(o.items, canReview)
            b.btnCancel.visibility = if (o.status == OrderStatus.PENDING && !Session.isStaff) View.VISIBLE else View.GONE
            // Đơn chưa trả tiền: cho thanh toán online bằng VNPay
            val canPay = !Session.isStaff && !o.isPaid && o.status != OrderStatus.CANCELLED && o.status != OrderStatus.DONE
            b.btnPay.visibility = if (canPay) View.VISIBLE else View.GONE
            b.memberActions.visibility = if (!Session.isStaff) View.VISIBLE else View.GONE
        }
    }

    private fun openReview(item: OrderItemDto) {
        startActivity(Intent(this, ReviewActivity::class.java)
            .putExtra("orderId", orderId)
            .putExtra("productId", item.productId)
            .putExtra("productName", item.productName))
    }
}

class OrderLineAdapter(private val onReview: (OrderItemDto) -> Unit) : BaseAdapter() {
    private var items: List<OrderItemDto> = emptyList()
    private var canReview = false
    fun submit(list: List<OrderItemDto>, canReview: Boolean) { items = list; this.canReview = canReview; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemOrderLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemOrderLineBinding.bind(convertView)
        val line = items[position]
        b.tvName.text = line.productName
        b.tvSub.text = "Size ${line.size} · ${line.quantity} × ${line.unitPrice.toMoney()}"
        b.tvLineTotal.text = line.lineTotal.toMoney()
        b.btnReview.visibility = if (canReview) View.VISIBLE else View.GONE
        b.btnReview.isEnabled = !line.reviewed
        b.btnReview.text = if (line.reviewed) "Đã đánh giá" else "Đánh giá"
        b.btnReview.setOnClickListener { onReview(line) }
        return b.root
    }
}
