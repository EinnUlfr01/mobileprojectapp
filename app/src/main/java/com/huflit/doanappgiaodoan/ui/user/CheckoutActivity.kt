package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.MainActivity
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.CreateOrderRequest
import com.huflit.doanappgiaodoan.data.GuestCart
import com.huflit.doanappgiaodoan.data.OrderDto
import com.huflit.doanappgiaodoan.data.OrderLineRequest
import com.huflit.doanappgiaodoan.data.PAYMENT_METHODS
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityCheckoutBinding
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast

// Đặt hàng: user dùng giỏ trên server + điền sẵn thông tin,
// khách vãng lai chỉ cần tên, SĐT, địa chỉ và phương thức thanh toán
class CheckoutActivity : AppCompatActivity() {
    private lateinit var b: ActivityCheckoutBinding
    private var lines: List<CartLine> = emptyList()
    private val shippingFee = 15000.0
    private var pendingOrder: OrderDto? = null

    // Mở màn thanh toán VNPay, nhận kết quả thành công / thất bại
    private val payLauncher = registerForActivityResult(PaymentActivity.Contract()) { paid ->
        pendingOrder?.let { onDone(it, paid) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, if (Session.isLoggedIn) "Xác nhận đơn hàng" else "Thông tin nhận hàng")

        PAYMENT_METHODS.forEachIndexed { i, m ->
            b.rgPayment.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = if (i == 0) "$m khi nhận hàng" else "$m (thẻ ATM, Visa, QR ngân hàng)"
                tag = m
                isChecked = i == 0
                minHeight = 48
            })
        }

        val user = Session.user
        if (Session.isLoggedIn && user != null) {
            b.edtName.setText(user.fullName)
            b.edtPhone.setText(user.phone)
            b.edtAddress.setText(user.defaultAddress ?: "")
            api({ cart() }) { c ->
                showLines(c.items.map { CartLine(it.id, it.productId, it.productName, it.imageUrl, it.size, it.quantity, it.unitPrice) })
            }
        } else {
            b.tvGuestNote.visibility = View.VISIBLE
            showLines(GuestCart.items().map { CartLine(0, it.productId, it.productName, it.imageUrl, it.size, it.quantity, it.unitPrice) })
        }

        b.btnConfirm.setOnClickListener { confirm() }
    }

    private fun showLines(list: List<CartLine>) {
        lines = list
        b.llItems.removeAllViews()
        list.forEach { l ->
            b.llItems.addView(TextView(this).apply {
                text = "${l.quantity} × ${l.name} (${l.size})  —  ${l.lineTotal.toMoney()}"
                setTextColor(getColor(R.color.ink))
                setPadding(0, 6, 0, 6)
            })
        }
        val subtotal = list.sumOf { it.lineTotal }
        b.tvSubtotal.text = "Tạm tính: ${subtotal.toMoney()}"
        b.tvShip.text = "Phí giao hàng: ${shippingFee.toMoney()}"
        b.tvTotal.text = "Tổng thanh toán: ${(subtotal + shippingFee).toMoney()}"
        b.btnConfirm.isEnabled = list.isNotEmpty()
    }

    private fun confirm() {
        val name = b.edtName.text.toString().trim()
        val phone = b.edtPhone.text.toString().trim()
        val address = b.edtAddress.text.toString().trim()
        listOf(b.tilName, b.tilPhone, b.tilAddress).forEach { it.error = null }
        var ok = true
        if (name.isEmpty()) { b.tilName.error = "Vui lòng nhập tên người nhận"; ok = false }
        if (!Regex("^0\\d{9}$").matches(phone)) { b.tilPhone.error = "Số điện thoại phải đủ 10 số"; ok = false }
        if (address.isEmpty()) { b.tilAddress.error = "Vui lòng nhập địa chỉ giao hàng"; ok = false }
        if (!ok) return
        if (lines.isEmpty()) { toast("Giỏ hàng đang trống"); return }

        val payment = b.rgPayment.findViewById<RadioButton>(b.rgPayment.checkedRadioButtonId)?.tag as? String ?: "Tiền mặt"
        val req = CreateOrderRequest(
            receiverName = name, phone = phone, address = address,
            note = b.edtNote.text.toString().trim().ifEmpty { null },
            paymentMethod = payment,
            // User: để null, server tự lấy giỏ hàng. Khách: gửi giỏ trên máy.
            items = if (Session.isLoggedIn) null else lines.map { OrderLineRequest(it.productId, it.size, it.quantity) }
        )

        send(req)
    }

    private fun send(req: CreateOrderRequest) {
        b.btnConfirm.isEnabled = false
        api({ createOrder(req) }, onError = { b.btnConfirm.isEnabled = true; toast(it) }) { order ->
            val url = order.paymentUrl
            if (url != null) {
                // Đơn đã tạo, giỏ đã xóa trên server -> xóa luôn giỏ của khách rồi mở VNPay
                if (!Session.isLoggedIn) GuestCart.clear()
                pendingOrder = order
                payLauncher.launch(url)
            } else onDone(order, false)
        }
    }

    private fun onDone(order: OrderDto, paid: Boolean) {
        if (order.paymentUrl == null) toast("Đặt hàng thành công")
        else if (paid) toast("Thanh toán VNPay thành công")
        else toast("Chưa thanh toán. Đơn vẫn được giữ, bạn có thể thanh toán lại hoặc trả tiền mặt khi nhận hàng")
        if (Session.isLoggedIn) {
            // Về trang chủ rồi mở lịch sử đơn
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            startActivity(Intent(this, OrderHistoryActivity::class.java))
        } else {
            GuestCart.clear()
            startActivity(Intent(this, OrderSuccessActivity::class.java)
                .putExtra("code", order.code)
                .putExtra("name", order.receiverName)
                .putExtra("phone", order.phone)
                .putExtra("payment", order.paymentMethod + if (order.paymentUrl == null) "" else if (paid) " (đã thanh toán)" else " (chưa thanh toán)")
                .putExtra("total", order.total)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finish()
    }
}
