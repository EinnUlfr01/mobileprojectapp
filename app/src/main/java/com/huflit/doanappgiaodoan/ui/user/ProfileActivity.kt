package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.huflit.doanappgiaodoan.data.ChangePasswordRequest
import com.huflit.doanappgiaodoan.data.Role
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.data.UpdateProfileRequest
import com.huflit.doanappgiaodoan.data.UserDto
import com.huflit.doanappgiaodoan.databinding.ActivityProfileBinding
import com.huflit.doanappgiaodoan.databinding.DialogChangePasswordBinding
import com.huflit.doanappgiaodoan.ui.auth.LoginActivity
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toast

// Tài khoản: xem/sửa thông tin, đổi mật khẩu, đăng xuất
class ProfileActivity : AppCompatActivity() {
    private lateinit var b: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Tài khoản")

        Session.user?.let { show(it) }
        api({ me() }) { u -> show(u); Session.user = u }

        b.btnSave.setOnClickListener { saveProfile() }
        b.btnFavorites.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.btnChat.setOnClickListener { com.huflit.doanappgiaodoan.ui.chat.ChatActivity.openForCustomer(this) }
        b.btnPassword.setOnClickListener { showChangePassword() }
        b.btnLogout.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Đăng xuất?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    Session.logout() // xóa SharedPreferences
                    startActivity(Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                .setNegativeButton("Hủy", null).show()
        }
    }

    private fun show(u: UserDto) {
        b.tvAvatar.text = u.fullName.trim().take(1).uppercase()
        b.tvFullName.text = u.fullName
        b.tvEmail.text = "${u.email} · ${Role.label(u.role)}"
        b.edtName.setText(u.fullName)
        b.edtPhone.setText(u.phone)
        b.edtAddress.setText(u.defaultAddress ?: "")
    }

    private fun saveProfile() {
        val name = b.edtName.text.toString().trim()
        val phone = b.edtPhone.text.toString().trim()
        b.tilName.error = null; b.tilPhone.error = null
        if (name.isEmpty()) { b.tilName.error = "Vui lòng nhập họ tên"; return }
        if (!Regex("^0\\d{9}$").matches(phone)) { b.tilPhone.error = "Số điện thoại phải đủ 10 số"; return }
        val address = b.edtAddress.text.toString().trim().ifEmpty { null }
        api({ updateMe(UpdateProfileRequest(name, phone, address)) }) { u ->
            Session.user = u
            show(u)
            toast("Đã lưu thông tin")
        }
    }

    private fun showChangePassword() {
        val d = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle("Đổi mật khẩu").setView(d.root)
            .setPositiveButton("Lưu", null).setNegativeButton("Hủy", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val old = d.edtOld.text.toString()
                val new = d.edtNew.text.toString()
                if (new.length < 6) { d.tilNew.error = "Tối thiểu 6 ký tự"; return@setOnClickListener }
                api({ changePassword(ChangePasswordRequest(old, new)) }, onError = { d.tilOld.error = it }) {
                    toast("Đã đổi mật khẩu")
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
