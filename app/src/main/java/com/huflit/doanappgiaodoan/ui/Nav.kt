package com.huflit.doanappgiaodoan.ui

import android.content.Context
import android.content.Intent
import com.huflit.doanappgiaodoan.MainActivity
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.ui.admin.AdminDashboardActivity
import com.huflit.doanappgiaodoan.ui.user.CategoryProductsActivity
import com.huflit.doanappgiaodoan.ui.user.ProductDetailActivity

// Các hàm chuyển màn hình dùng nhiều nơi
object Nav {
    const val EXTRA_ID = "id"
    const val EXTRA_NAME = "name"
    const val EXTRA_PHONE = "phone"

    fun product(ctx: Context, id: Int) =
        ctx.startActivity(Intent(ctx, ProductDetailActivity::class.java).putExtra(EXTRA_ID, id))

    fun category(ctx: Context, id: Int, name: String) =
        ctx.startActivity(Intent(ctx, CategoryProductsActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name))

    // Về trang chủ đúng vai trò, xóa các màn cũ
    fun home(ctx: Context) {
        val target = if (Session.isStaff) AdminDashboardActivity::class.java else MainActivity::class.java
        ctx.startActivity(Intent(ctx, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
