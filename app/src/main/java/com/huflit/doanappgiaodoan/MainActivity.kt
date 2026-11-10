package com.huflit.doanappgiaodoan

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.databinding.ActivityMainBinding
import com.huflit.doanappgiaodoan.ui.BannerSlider
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
import com.huflit.doanappgiaodoan.ui.chat.ChatActivity
import com.huflit.doanappgiaodoan.ui.user.NotificationsActivity
import com.huflit.doanappgiaodoan.util.AppPoller
import com.huflit.doanappgiaodoan.util.api

// Trang chủ: dùng chung cho user và khách vãng lai (kiểm tra Session.isLoggedIn)
class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val categoryAdapter = CategoryChipAdapter { Nav.category(this, it.id, it.name) }
    private val bestAdapter = ProductCardAdapter { Nav.product(this, it.id) }
    private val newAdapter = ProductRowAdapter { Nav.product(this, it.id) }
    private lateinit var banner: BannerSlider
    private val askNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        // Banner trượt: bấm banner mã giảm giá thì sao chép mã GIAM20, banner khác mở trang tìm món
        banner = BannerSlider(b.rvBanner, b.bannerDots,
            listOf(R.drawable.banner_1, R.drawable.banner_2, R.drawable.banner_3)) { index ->
            if (index == 1) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("voucher", "GIAM20"))
                android.widget.Toast.makeText(this, "Đã sao chép mã GIAM20, dán ở bước thanh toán nhé", android.widget.Toast.LENGTH_SHORT).show()
            } else startActivity(Intent(this, SearchActivity::class.java))
        }

        b.tvSearch.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        b.tvGuestBanner.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.swipe.setOnRefreshListener { loadData() }

        // Chat với cửa hàng: khách vãng lai phải đăng nhập trước
        b.btnChat.setOnClickListener {
            if (Session.isLoggedIn) ChatActivity.openForCustomer(this)
            else AlertDialog.Builder(this).setTitle("Cần đăng nhập")
                .setMessage("Đăng nhập để chat trực tuyến với cửa hàng và xem lại lịch sử tin nhắn.")
                .setPositiveButton("Đăng nhập") { _, _ -> startActivity(Intent(this, LoginActivity::class.java)) }
                .setNegativeButton("Để sau", null).show()
        }
        b.btnBell.setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }

        // Android 13+ hỏi quyền hiện thông báo (đơn đổi trạng thái, tin nhắn mới)
        if (Session.isLoggedIn && Build.VERSION.SDK_INT >= 33) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)

        setupBottomNav()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        b.bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
        b.btnBell.visibility = if (Session.isLoggedIn) android.view.View.VISIBLE else android.view.View.GONE
        refreshBadges()
        banner.start()
        AppPoller.onUpdate = { refreshBadges() }
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
    override fun onPause() {
        super.onPause()
        AppPoller.onUpdate = null
        banner.stop()
    }

    // Chấm đỏ: số tin nhắn chưa đọc và số thông báo chưa đọc
    private fun refreshBadges() {
        if (!Session.isLoggedIn) {
            showBadge(b.tvChatBadge, 0); showBadge(b.tvBellBadge, 0); return
        }
        api({ chatUnread() }, onError = {}) { showBadge(b.tvChatBadge, it.count) }
        api({ notificationUnread() }, onError = {}) { showBadge(b.tvBellBadge, it.count) }
    }

    private fun showBadge(tv: TextView, count: Int) {
        tv.text = if (count > 9) "9+" else count.toString()
        tv.visibility = if (count > 0) android.view.View.VISIBLE else android.view.View.GONE
    }

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
