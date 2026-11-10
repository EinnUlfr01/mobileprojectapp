package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.CancelRequest
import com.huflit.doanappgiaodoan.data.OrderDto
import com.huflit.doanappgiaodoan.data.OrderItemDto
import com.huflit.doanappgiaodoan.data.OrderStatus
import com.huflit.doanappgiaodoan.data.QR_METHOD
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityOrderDetailBinding
import com.huflit.doanappgiaodoan.databinding.FooterOrderDetailBinding
import com.huflit.doanappgiaodoan.databinding.HeaderOrderDetailBinding
import com.huflit.doanappgiaodoan.databinding.ItemOrderLineBinding
import com.huflit.doanappgiaodoan.databinding.ItemTimelineBinding
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

    private val qrLauncher = registerForActivityResult(QrPaymentActivity.Contract()) { paid ->
        if (paid) toast("Thanh toán thành công")
        load()
    }

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
            val o = current ?: return@setOnClickListener
            if (o.paymentMethod == QR_METHOD) qrLauncher.launch(o.code)
            else api({ payOrder(orderId) }) { link -> payLauncher.launch(link.paymentUrl) }
        }
        b.btnCancel.setOnClickListener { askCancelReason() }
    }

    // Hủy đơn: chọn lý do (lý do lưu vào đơn và hiện trên dòng thời gian)
    private fun askCancelReason() {
        val reasons = arrayOf("Đổi ý, không muốn đặt nữa", "Muốn đổi món hoặc đổi size", "Nhập sai địa chỉ / số điện thoại",
            "Chờ lâu quá", "Lý do khác")
        AlertDialog.Builder(this).setTitle("Vì sao bạn muốn hủy đơn?")
            .setItems(reasons) { _, i ->
                if (i < reasons.size - 1) confirmCancel(reasons[i])
                else {
                    val input = EditText(this).apply { hint = "Nhập lý do"; setPadding(48, 32, 48, 32) }
                    AlertDialog.Builder(this).setTitle("Lý do hủy").setView(input)
                        .setPositiveButton("Hủy đơn") { _, _ -> confirmCancel(input.text.toString().trim().ifEmpty { "Lý do khác" }) }
                        .setNegativeButton("Quay lại", null).show()
                }
            }
            .setNegativeButton("Không hủy", null).show()
    }

    private fun confirmCancel(reason: String) {
        api({ cancelOrder(orderId, CancelRequest(reason)) }) { toast(it.message ?: "Đã hủy"); load() }
    }

    // Dòng thời gian theo dõi đơn: Đã đặt → Đang giao → Giao thành công (hoặc Đã đặt → Đã hủy)
    private fun buildTimeline(o: OrderDto) {
        header.llTimeline.removeAllViews()
        val history = o.history.orEmpty()
        val cancelled = o.status == OrderStatus.CANCELLED
        val steps = if (cancelled) listOf(OrderStatus.PENDING to "Đã đặt hàng", OrderStatus.CANCELLED to "Đơn đã hủy")
        else listOf(OrderStatus.PENDING to "Đã đặt hàng, chờ cửa hàng chuẩn bị", OrderStatus.SHIPPING to "Đang giao hàng",
            OrderStatus.DONE to "Giao thành công")
        val current = steps.indexOfFirst { it.first == o.status }.coerceAtLeast(0)
        val green = getColor(R.color.primary)
        val red = getColor(R.color.danger)
        val grey = getColor(R.color.line)

        steps.forEachIndexed { i, (status, title) ->
            val item = ItemTimelineBinding.inflate(layoutInflater, header.llTimeline, false)
            val reached = i <= current
            val logs = history.filter { it.status == status }
            val log = logs.firstOrNull()
            val time = log?.createdAt?.toDisplayDate() ?: if (i == 0) o.createdAt.toDisplayDate() else null
            val note = logs.mapNotNull { it.note }.joinToString(" · ").ifEmpty { null }
                ?: if (status == OrderStatus.CANCELLED) o.cancelReason else null
            val by = log?.changedBy?.takeIf { status != OrderStatus.PENDING }?.let { "bởi $it" }
            item.tvTitle.text = title
            item.tvTitle.setTextColor(getColor(if (reached) R.color.ink else R.color.muted))
            item.tvSub.text = if (reached) listOfNotNull(time, by, note).joinToString(" · ") else "Đang chờ"
            item.dot.background.mutate().setTint(if (!reached) grey else if (status == OrderStatus.CANCELLED) red else green)
            item.line.setBackgroundColor(if (i < current) green else grey)
            item.line.visibility = if (i == steps.size - 1) View.INVISIBLE else View.VISIBLE
            header.llTimeline.addView(item.root)
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
            buildTimeline(o)
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
            b.btnPay.text = if (o.paymentMethod == QR_METHOD) "Thanh toán bằng mã QR" else "Thanh toán qua VNPay"
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
