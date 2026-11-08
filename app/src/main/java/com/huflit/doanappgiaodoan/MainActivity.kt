package com.huflit.doanappgiaodoan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityMainBinding
import com.huflit.doanappgiaodoan.ui.CategoryChipAdapter
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.ProductCardAdapter
import com.huflit.doanappgiaodoan.ui.ProductRowAdapter
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.ui.auth.RegisterActivity
import com.huflit.doanappgiaodoan.ui.user.CartActivity
import com.huflit.doanappgiaodoan.ui.user.OrderHistoryActivity
import com.huflit.doanappgiaodoan.ui.user.ProfileActivity
import com.huflit.doanappgiaodoan.ui.user.SearchActivity
import com.huflit.doanappgiaodoan.util.api

// Trang chủ: dùng chung cho user và khách vãng lai (kiểm tra Session.isLoggedIn)
class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val categoryAdapter = CategoryChipAdapter { Nav.category(this, it.id, it.name) }
    private val bestAdapter = ProductCardAdapter { Nav.product(this, it.id) }
    private val newAdapter = ProductRowAdapter { Nav.product(this, it.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // RecyclerView ngang cho danh mục và món bán chạy, dọc cho món mới
        b.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvCategories.adapter = categoryAdapter
        b.rvBestSeller.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvBestSeller.adapter = bestAdapter
        b.rvNew.layoutManager = LinearLayoutManager(this)
        b.rvNew.adapter = newAdapter

        b.tvSearch.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        b.tvGuestBanner.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.swipe.setOnRefreshListener { loadData() }

        setupBottomNav()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        b.bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
        val user = Session.user
        if (Session.isLoggedIn && user != null) {
            b.tvHello.text = "Xin chào"
            b.tvName.text = user.fullName
            b.tvGuestBanner.visibility = android.view.View.GONE
        } else {
            b.tvHello.text = "Xin chào"
            b.tvName.text = "Khách"
            b.tvGuestBanner.visibility = android.view.View.VISIBLE
        }
    }

    // Thanh dưới: khách thì có nút Đăng nhập thay cho Đơn hàng / Tài khoản
    private fun setupBottomNav() {
        b.bottomNav.menu.clear()
        b.bottomNav.inflateMenu(if (Session.isLoggedIn) R.menu.menu_bottom_user else R.menu.menu_bottom_guest)
        b.bottomNav.selectedItemId = R.id.nav_home
        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_cart -> startActivity(Intent(this, CartActivity::class.java))
                R.id.nav_orders -> startActivity(Intent(this, OrderHistoryActivity::class.java))
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_login -> startActivity(Intent(this, LoginActivity::class.java))
            }
            item.itemId == R.id.nav_home
        }
    }

    private fun loadData() {
        b.swipe.isRefreshing = true
        api({ categories() }, onError = { b.swipe.isRefreshing = false; showError(it) }) {
            categoryAdapter.submit(it)
        }
        api({ products(sort = "bestseller", take = 8) }) { bestAdapter.submit(it) }
        api({ products(sort = "new", isNew = true) }, onError = { b.swipe.isRefreshing = false }) {
            newAdapter.submit(it)
            b.swipe.isRefreshing = false
        }
    }

    private fun showError(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }
}
