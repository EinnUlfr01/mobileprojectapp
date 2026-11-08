package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.databinding.ActivityOrderSuccessBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.auth.RegisterActivity
import com.huflit.doanappgiaodoan.util.toMoney

// Khách vãng lai đặt hàng thành công + gợi ý tạo tài khoản
class OrderSuccessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityOrderSuccessBinding.inflate(layoutInflater)
        setContentView(b.root)

        val name = intent.getStringExtra("name") ?: ""
        val phone = intent.getStringExtra("phone") ?: ""
        b.tvInfo.text = "Mã đơn: ${intent.getStringExtra("code")}\n" +
            "Người nhận: $name\n" +
            "Số điện thoại: $phone\n" +
            "Thanh toán: ${intent.getStringExtra("payment")}\n" +
            "Tổng tiền: ${intent.getDoubleExtra("total", 0.0).toMoney()}"

        b.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java)
                .putExtra(Nav.EXTRA_PHONE, phone).putExtra(Nav.EXTRA_NAME, name))
        }
        b.btnHome.setOnClickListener { Nav.home(this) }
    }
}
