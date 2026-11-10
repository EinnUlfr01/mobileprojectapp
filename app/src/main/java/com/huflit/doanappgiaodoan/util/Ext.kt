package com.huflit.doanappgiaodoan.util

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.huflit.doanappgiaodoan.data.ApiClient
import com.huflit.doanappgiaodoan.data.ApiService
import com.huflit.doanappgiaodoan.data.MessageDto
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ---------- Tiền, ngày ----------
private val moneyFormat = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US))

fun Double.toMoney(): String = moneyFormat.format(this).replace(',', '.') + "đ"

// Server trả giờ UTC dạng "2026-11-08T16:25:21.89" -> hiển thị giờ máy "08/11/2026 23:25"
fun String.toDisplayDate(): String = try {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date = input.parse(this.take(19))
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(date!!)
} catch (e: Exception) {
    this.take(10)
}

// Giờ ngắn cho chat/thông báo: hôm nay chỉ hiện "14:05", ngày khác hiện "08/11 14:05"
fun String.toShortTime(): String = try {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date = input.parse(this.take(19))!!
    val day = SimpleDateFormat("yyyyMMdd", Locale.US)
    val sameDay = day.format(date) == day.format(java.util.Date())
    SimpleDateFormat(if (sameDay) "HH:mm" else "dd/MM HH:mm", Locale.US).format(date)
} catch (e: Exception) {
    ""
}

fun ratingText(avg: Double, count: Int): String =
    if (count == 0) "Chưa có đánh giá" else "★ %.1f (%d)".format(Locale.US, avg, count)

fun AppCompatActivity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun AppCompatActivity.setupToolbar(toolbar: MaterialToolbar, title: String, showBack: Boolean = true) {
    toolbar.title = title
    if (showBack) {
        toolbar.setNavigationIcon(com.huflit.doanappgiaodoan.R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }
    }
}

// ---------- Gọi API ----------
// Dùng: api({ products() }) { list -> ... }
fun <T> AppCompatActivity.api(
    call: suspend ApiService.() -> T,
    onError: ((String) -> Unit)? = null,
    onSuccess: (T) -> Unit
) {
    lifecycleScope.launch {
        val result = try {
            Result.success(ApiClient.api.call())
        } catch (e: HttpException) {
            if (e.code() == 401 && Session.isLoggedIn) {
                Session.logout()
                toast("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại")
                startActivity(Intent(this@api, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                return@launch
            }
            Log.e("API_LOI", "HTTP ${e.code()} ${e.response()?.raw()?.request?.url}", e)
            Result.failure(Exception(errorMessage(e)))
        } catch (e: IOException) {
            Log.e("API_LOI", "Lỗi kết nối tới ${ApiClient.BASE_URL}", e)
            Result.failure(Exception("Không kết nối được server. Kiểm tra API đã chạy ở cổng 5090 chưa"))
        } catch (e: Exception) {
            Log.e("API_LOI", "Lỗi khác khi gọi API", e)
            Result.failure(Exception(e.message ?: "Có lỗi xảy ra"))
        }
        result.onSuccess(onSuccess).onFailure {
            val msg = it.message ?: "Có lỗi xảy ra"
            if (onError != null) onError(msg) else toast(msg)
        }
    }
}

private fun errorMessage(e: HttpException): String {
    val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
    val msg = try { Gson().fromJson(body, MessageDto::class.java)?.message } catch (_: Exception) { null }
    return msg ?: when (e.code()) {
        403 -> "Bạn không có quyền thực hiện thao tác này"
        404 -> "Không tìm thấy dữ liệu"
        else -> "Lỗi máy chủ (${e.code()})"
    }
}

// ---------- Ảnh món ----------
// Món không có ảnh thì hiện chữ cái đầu trên nền màu. Có link ảnh thì tải về và cache.
object ImageLoader {
    private val cache = LruCache<String, Bitmap>(30)
    private val colors = listOf("#1E6B47", "#B5651D", "#2F5D8A", "#8A3B5C", "#5C6B1E", "#6B3FA0")
    private val scope = CoroutineScope(Dispatchers.Main)

    fun load(image: ImageView, placeholder: TextView, rawUrl: String?, name: String) {
        // Ảnh lưu trên API dạng "images/pho-bo.jpg" -> ghép với địa chỉ API
        val url = if (rawUrl.isNullOrBlank() || rawUrl.startsWith("http")) rawUrl else ApiClient.BASE_URL + rawUrl.trimStart('/')
        placeholder.text = name.trim().take(1).uppercase()
        placeholder.setBackgroundColor(Color.parseColor(colors[Math.abs(name.hashCode()) % colors.size]))
        placeholder.visibility = View.VISIBLE
        image.setImageDrawable(null)
        image.visibility = View.GONE
        image.tag = url
        if (url.isNullOrBlank()) return

        cache.get(url)?.let { show(image, placeholder, it); return }
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try { decodeSmall(URL(url).openStream().use { it.readBytes() }) } catch (_: Exception) { null }
            }
            if (bmp != null) {
                cache.put(url, bmp)
                if (image.tag == url) show(image, placeholder, bmp)
            }
        }
    }

    // Thu nhỏ ảnh lớn (ảnh chụp điện thoại) về khoảng 600px để không tốn bộ nhớ
    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 600) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun show(image: ImageView, placeholder: TextView, bmp: Bitmap) {
        image.setImageBitmap(bmp)
        image.visibility = View.VISIBLE
        placeholder.visibility = View.GONE
    }
}
