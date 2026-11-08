package com.huflit.doanappgiaodoan.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Lưu phiên đăng nhập bằng SharedPreferences
object Session {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    }

    val token: String? get() = prefs.getString("token", null)

    var user: UserDto?
        get() = prefs.getString("user", null)?.let { gson.fromJson(it, UserDto::class.java) }
        set(value) { prefs.edit().putString("user", value?.let { gson.toJson(it) }).apply() }

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty() && user != null
    val isAdmin: Boolean get() = isLoggedIn && user?.role == "Admin"

    // Khách vãng lai đã bấm "Tiếp tục với tư cách khách"
    var isGuest: Boolean
        get() = prefs.getBoolean("guest", false)
        set(value) { prefs.edit().putBoolean("guest", value).apply() }

    fun save(res: LoginResponse) {
        prefs.edit()
            .putString("token", res.token)
            .putString("user", gson.toJson(res.user))
            .putBoolean("guest", false)
            .apply()
    }

    fun logout() {
        prefs.edit().remove("token").remove("user").remove("guest").apply()
    }
}

// Giỏ hàng của khách vãng lai, lưu tạm trên máy
object GuestCart {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<GuestCartItem>>() {}.type

    fun init(context: Context) {
        prefs = context.getSharedPreferences("guest_cart", Context.MODE_PRIVATE)
    }

    fun items(): MutableList<GuestCartItem> =
        prefs.getString("items", null)?.let { gson.fromJson<MutableList<GuestCartItem>>(it, type) } ?: mutableListOf()

    private fun save(list: List<GuestCartItem>) {
        prefs.edit().putString("items", gson.toJson(list)).apply()
    }

    fun add(item: GuestCartItem) {
        val list = items()
        val found = list.find { it.productId == item.productId && it.size == item.size }
        if (found != null) found.quantity += item.quantity else list.add(item)
        save(list)
    }

    fun setQuantity(productId: Int, size: String, qty: Int) {
        val list = items()
        if (qty <= 0) list.removeAll { it.productId == productId && it.size == size }
        else list.find { it.productId == productId && it.size == size }?.quantity = qty
        save(list)
    }

    fun clear() = save(emptyList())
}
