package com.huflit.doanappgiaodoan.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.MainActivity
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivitySplashBinding
import com.huflit.doanappgiaodoan.ui.admin.AdminDashboardActivity

// Màn hình chào: chờ 2 giây rồi kiểm tra đăng nhập
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)

        Handler(Looper.getMainLooper()).postDelayed({
            val next = when {
                Session.isStaff -> AdminDashboardActivity::class.java
                Session.isLoggedIn || Session.isGuest -> MainActivity::class.java
                else -> LoginActivity::class.java
            }
            startActivity(Intent(this, next))
            finish()
        }, 2000)
    }
}
