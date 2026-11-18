package com.huflit.doanappgiaodoan.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.MainActivity
import com.huflit.doanappgiaodoan.data.LoginRequest
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityLoginBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.toast

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnLogin.setOnClickListener { doLogin() }

        // Vào app không cần tài khoản
        b.btnGuest.setOnClickListener {
            Session.isGuest = true
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }

        b.tvRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.tvDemo.setOnClickListener { pickDemoAccount() }
    }

    // Chọn nhanh tài khoản mẫu khi demo (dữ liệu mẫu do API tạo sẵn)
    private fun pickDemoAccount() {
        val accounts = listOf(
            Triple("Khách hàng", "user@gmail.com", "123456"),
            Triple("Quản trị sàn", "admin@gmail.com", "Admin@123"),
            Triple("Chủ quán Cơm Tấm Sài Gòn", "quan1@gmail.com", "Quan@123"),
            Triple("Chủ quán Phở Bò Hà Nội", "quan2@gmail.com", "Quan@123"),
            Triple("Shipper 1", "shipper1@gmail.com", "Shipper@123"),
            Triple("Shipper 2", "shipper2@gmail.com", "Shipper@123"),
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tài khoản demo")
            .setItems(accounts.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, i ->
                b.edtAccount.setText(accounts[i].second)
                b.edtPassword.setText(accounts[i].third)
                doLogin()
            }
            .show()
    }

    private fun doLogin() {
        val account = b.edtAccount.text.toString().trim()
        val password = b.edtPassword.text.toString()
        if (account.isEmpty()) { b.edtAccount.error = "Nhập email hoặc số điện thoại"; return }
        if (password.isEmpty()) { b.edtPassword.error = "Nhập mật khẩu"; return }

        b.btnLogin.isEnabled = false
        api({ login(LoginRequest(account, password)) }, onError = {
            b.btnLogin.isEnabled = true
            toast(it)
        }) { res ->
            Session.save(res)
            com.huflit.doanappgiaodoan.util.ChatRealtime.connect()
            toast("Xin chào ${res.user.fullName}")
            Nav.home(this) // user -> trang chủ, admin -> trang quản trị
        }
    }
}
