package com.huflit.doanappgiaodoan.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.RegisterRequest
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityRegisterBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

class RegisterActivity : AppCompatActivity() {
    private lateinit var b: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Đăng ký tài khoản")

        // Mở từ màn "Đặt hàng thành công" của khách: điền sẵn tên + SĐT
        intent.getStringExtra(Nav.EXTRA_PHONE)?.let { b.edtPhone.setText(it) }
        intent.getStringExtra(Nav.EXTRA_NAME)?.let { b.edtName.setText(it) }

        b.btnRegister.setOnClickListener { doRegister() }
        b.tvLogin.setOnClickListener { finish() }
    }

    private fun doRegister() {
        val name = b.edtName.text.toString().trim()
        val email = b.edtEmail.text.toString().trim()
        val phone = b.edtPhone.text.toString().trim()
        val pass = b.edtPassword.text.toString()
        val confirm = b.edtConfirm.text.toString()

        // Kiểm tra dữ liệu, báo lỗi ngay tại ô nhập
        listOf(b.tilName, b.tilEmail, b.tilPhone, b.tilPassword, b.tilConfirm).forEach { it.error = null }
        var ok = true
        if (name.isEmpty()) { b.tilName.error = "Vui lòng nhập họ tên"; ok = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = "Email không đúng định dạng"; ok = false }
        if (!Regex("^0\\d{9}$").matches(phone)) { b.tilPhone.error = "Số điện thoại phải đủ 10 số, bắt đầu bằng 0"; ok = false }
        if (pass.length < 6) { b.tilPassword.error = "Mật khẩu tối thiểu 6 ký tự"; ok = false }
        if (confirm != pass) { b.tilConfirm.error = "Mật khẩu nhập lại không khớp"; ok = false }
        if (!ok) return

        b.btnRegister.isEnabled = false
        api({ register(RegisterRequest(name, email, phone, pass)) }, onError = {
            b.btnRegister.isEnabled = true
            if (it.contains("Email")) b.tilEmail.error = it
            else if (it.contains("điện thoại")) b.tilPhone.error = it
            else toast(it)
        }) { res ->
            // Đăng ký xong thì đăng nhập luôn
            Session.save(res)
            com.huflit.doanappgiaodoan.util.ChatRealtime.connect()
            toast("Đăng ký thành công")
            Nav.home(this)
        }
    }
}
