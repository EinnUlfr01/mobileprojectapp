package com.huflit.doanappgiaodoan.ui.user

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.huflit.doanappgiaodoan.R
import com.huflit.doanappgiaodoan.data.BankQrDto
import com.huflit.doanappgiaodoan.databinding.ActivityQrPaymentBinding
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toShortTime
import com.huflit.doanappgiaodoan.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

// Thanh toán bằng chuyển khoản: hiện mã VietQR (quét bằng app ngân hàng hoặc MoMo),
// cứ 4 giây hỏi server xem tiền đã về chưa. Tiền về thì báo thành công và quay lại.
class QrPaymentActivity : AppCompatActivity() {
    private lateinit var b: ActivityQrPaymentBinding
    private var code = ""
    private var paid = false
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            check(manual = false)
            handler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQrPaymentBinding.inflate(layoutInflater)
        setContentView(b.root)
        code = intent.getStringExtra(EXTRA_CODE) ?: ""
        setupToolbar(b.toolbar, "Chuyển khoản QR · #$code")

        b.btnCheck.setOnClickListener { check(manual = true) }
        b.btnLater.setOnClickListener { finish() }

        api({ bankQr(code) }, onError = { toast(it); finish() }) { show(it) }
    }

    override fun onResume() {
        super.onResume()
        if (!paid) handler.postDelayed(poll, 4000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(poll)
    }

    private fun show(q: BankQrDto) {
        b.tvBank.text = q.bankName
        b.tvAccountName.text = q.accountName
        b.tvAccountNo.text = q.accountNo
        b.tvAmount.text = q.amount.toMoney()
        b.tvContent.text = q.content
        b.tvNote.text = (if (q.autoCheck) "Hệ thống tự xác nhận vài giây sau khi tiền về."
        else "Cửa hàng sẽ xác nhận sau khi kiểm tra tài khoản. Bạn có thể đóng màn này, trạng thái thanh toán cập nhật trong chi tiết đơn.") +
            "\nMã có hiệu lực tới ${q.expiresAt.toShortTime()}. Nhớ giữ đúng số tiền và nội dung chuyển khoản."
        b.btnCopyAccount.setOnClickListener { copy("Số tài khoản", q.accountNo) }
        b.btnCopyContent.setOnClickListener { copy("Nội dung chuyển khoản", q.content) }
        loadQr(q.qrUrl)
        if (q.isPaid) onPaid()
    }

    // Tải ảnh mã QR từ img.vietqr.io
    private fun loadQr(url: String) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try { URL(url).openStream().use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
            }
            if (bmp != null) {
                b.imgQr.setImageBitmap(bmp)
                b.imgQr.visibility = View.VISIBLE
                b.imgText.visibility = View.GONE
            } else {
                b.imgText.text = "Không tải được mã QR.\nHãy chuyển khoản theo thông tin bên dưới."
            }
        }
    }

    private fun check(manual: Boolean) {
        if (paid) return
        api({ paymentStatus(code) }, onError = { if (manual) toast(it) }) { s ->
            if (s.isPaid) onPaid()
            else if (manual) toast("Chưa thấy tiền về. Chuyển xong đợi vài giây, hoặc cửa hàng sẽ xác nhận sau.")
        }
    }

    private fun onPaid() {
        if (paid) return
        paid = true
        handler.removeCallbacks(poll)
        b.tvStatus.text = "Đã nhận tiền, thanh toán thành công"
        b.tvStatus.setTextColor(getColor(R.color.white))
        b.tvStatus.setBackgroundColor(getColor(R.color.primary))
        b.btnCheck.isEnabled = false
        handler.postDelayed({
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PAID, true))
            finish()
        }, 1800)
    }

    private fun copy(label: String, text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        toast("Đã sao chép $label")
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_PAID = "paid"
    }

    // Dùng với registerForActivityResult: input = mã đơn, output = true nếu đã nhận tiền
    class Contract : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String) =
            Intent(context, QrPaymentActivity::class.java).putExtra(EXTRA_CODE, input)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            resultCode == Activity.RESULT_OK && intent?.getBooleanExtra(EXTRA_PAID, false) == true
    }
}
