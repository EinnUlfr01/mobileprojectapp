package com.huflit.doanappgiaodoan.ui.user

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.databinding.ActivityPaymentBinding
import com.huflit.doanappgiaodoan.util.setupToolbar

// Mở trang thanh toán VNPay trong WebView.
// Khi VNPay chuyển về ".../api/payments/vnpay-return" thì đọc vnp_ResponseCode để biết thành công hay không.
class PaymentActivity : AppCompatActivity() {
    private lateinit var b: ActivityPaymentBinding
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Thanh toán VNPay")

        b.webView.settings.javaScriptEnabled = true
        b.webView.settings.domStorageEnabled = true
        b.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                b.progress.visibility = View.GONE
                if (url.contains(RETURN_PATH)) onReturn(Uri.parse(url))
            }
        }
        b.webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: "")
    }

    // Trang kết quả đã tải xong (server đã ghi nhận) -> chờ 1.5 giây cho người dùng đọc rồi quay về app
    private fun onReturn(uri: Uri) {
        if (finished) return
        finished = true
        val success = uri.getQueryParameter("vnp_ResponseCode") == "00"
        Handler(Looper.getMainLooper()).postDelayed({
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SUCCESS, success))
            finish()
        }, 1500)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (b.webView.canGoBack() && !finished) b.webView.goBack() else super.onBackPressed()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_SUCCESS = "success"
        const val RETURN_PATH = "/api/payments/vnpay-return"
    }

    // Dùng với registerForActivityResult: input = link thanh toán, output = true nếu thanh toán thành công
    class Contract : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String) =
            Intent(context, PaymentActivity::class.java).putExtra(EXTRA_URL, input)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            resultCode == Activity.RESULT_OK && intent?.getBooleanExtra(EXTRA_SUCCESS, false) == true
    }
}
