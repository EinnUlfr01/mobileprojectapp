package com.huflit.doanappgiaodoan.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.huflit.doanappgiaodoan.data.CartDto
import com.huflit.doanappgiaodoan.data.GuestCart
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.data.UpdateCartRequest
import com.huflit.doanappgiaodoan.databinding.ActivityCartBinding
import com.huflit.doanappgiaodoan.databinding.ItemCartBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toMoney

// Một dòng trong giỏ (dùng chung cho giỏ trên server và giỏ của khách)
data class CartLine(
    val cartId: Int, val productId: Int, val name: String, val imageUrl: String?,
    val size: String, val quantity: Int, val unitPrice: Double
) {
    val lineTotal get() = unitPrice * quantity
}

// Giỏ hàng: user lấy từ server, khách lấy từ máy
class CartActivity : AppCompatActivity() {
    private lateinit var b: ActivityCartBinding
    private lateinit var adapter: CartAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCartBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Giỏ hàng")

        adapter = CartAdapter(onQty = ::changeQty, onDelete = { changeQty(it, 0) })
        b.rvCart.layoutManager = LinearLayoutManager(this)
        b.rvCart.adapter = adapter
        b.btnShop.setOnClickListener { Nav.home(this) }
        b.btnCheckout.setOnClickListener { startActivity(Intent(this, CheckoutActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (Session.isLoggedIn) api({ cart() }) { show(fromServer(it)) }
        else show(GuestCart.items().map { CartLine(0, it.productId, it.productName, it.imageUrl, it.size, it.quantity, it.unitPrice) })
    }

    private fun fromServer(c: CartDto) =
        c.items.map { CartLine(it.id, it.productId, it.productName, it.imageUrl, it.size, it.quantity, it.unitPrice) }

    private fun changeQty(line: CartLine, qty: Int) {
        if (Session.isLoggedIn) {
            if (qty <= 0) api({ deleteCart(line.cartId) }) { show(fromServer(it)) }
            else api({ updateCart(line.cartId, UpdateCartRequest(qty)) }) { show(fromServer(it)) }
        } else {
            GuestCart.setQuantity(line.productId, line.size, qty)
            load()
        }
    }

    // Hiện danh sách + thống kê giỏ (số món, tổng số lượng, tạm tính)
    private fun show(lines: List<CartLine>) {
        adapter.submit(lines)
        val empty = lines.isEmpty()
        b.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        b.rvCart.visibility = if (empty) View.GONE else View.VISIBLE
        b.summary.visibility = if (empty) View.GONE else View.VISIBLE
        b.tvStats.text = "${lines.size} món · Tổng số lượng ${lines.sumOf { it.quantity }}"
        b.tvSubtotal.text = lines.sumOf { it.lineTotal }.toMoney()
    }
}

// RecyclerView: món trong giỏ có nút +/− và xóa
class CartAdapter(
    private val onQty: (CartLine, Int) -> Unit,
    private val onDelete: (CartLine) -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {
    private var items: List<CartLine> = emptyList()
    class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<CartLine>) { items = list; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val l = items[position]
        h.b.tvName.text = l.name
        h.b.tvSub.text = "Size ${l.size} · ${l.unitPrice.toMoney()}"
        h.b.tvQty.text = l.quantity.toString()
        h.b.tvLineTotal.text = l.lineTotal.toMoney()
        ImageLoader.load(h.b.img, h.b.imgText, l.imageUrl, l.name)
        h.b.btnMinus.setOnClickListener { onQty(l, l.quantity - 1) }
        h.b.btnPlus.setOnClickListener { onQty(l, l.quantity + 1) }
        h.b.btnDelete.setOnClickListener { onDelete(l) }
    }
}
