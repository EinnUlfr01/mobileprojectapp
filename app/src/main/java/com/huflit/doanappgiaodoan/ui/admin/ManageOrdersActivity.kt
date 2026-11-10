package com.huflit.doanappgiaodoan.ui.admin

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.huflit.doanappgiaodoan.data.OrderDto
import com.huflit.doanappgiaodoan.data.OrderStatus
import com.huflit.doanappgiaodoan.data.StatRowDto
import com.huflit.doanappgiaodoan.data.UpdateStatusRequest
import com.huflit.doanappgiaodoan.databinding.ActivityManageOrdersBinding
import com.huflit.doanappgiaodoan.databinding.ItemStatRowBinding
import com.huflit.doanappgiaodoan.ui.Nav
import com.huflit.doanappgiaodoan.ui.user.OrderDetailActivity
import com.huflit.doanappgiaodoan.ui.user.OrderListAdapter
import com.huflit.doanappgiaodoan.util.api
import com.huflit.doanappgiaodoan.util.setupToolbar
import com.huflit.doanappgiaodoan.util.toMoney
import com.huflit.doanappgiaodoan.util.toast
import java.util.Calendar
import java.util.Locale

// Quản lý đơn: tab Danh sách đơn (ListView, đổi trạng thái) và tab Thống kê
class ManageOrdersActivity : AppCompatActivity() {
    private lateinit var b: ActivityManageOrdersBinding
    private val adapter = OrderListAdapter(showReceiver = true)
    private val statusFilters = listOf("Mọi trạng thái") + OrderStatus.ALL
    private val typeFilters = listOf("Mọi loại khách", "Thành viên", "Khách vãng lai")
    private val groupOptions = listOf("Theo ngày" to "day", "Theo tháng" to "month", "Theo user" to "user", "Theo danh mục" to "category")
    private val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -29) }
    private val to = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageOrdersBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(b.toolbar, "Quản lý đơn hàng")

        // ---------- Tabs ----------
        b.tabs.addTab(b.tabs.newTab().setText("Danh sách đơn"))
        b.tabs.addTab(b.tabs.newTab().setText("Thống kê"))
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val isList = tab.position == 0
                b.tabList.visibility = if (isList) View.VISIBLE else View.GONE
                b.tabStats.visibility = if (isList) View.GONE else View.VISIBLE
                if (isList) loadOrders() else loadStats()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // ---------- Tab danh sách ----------
        val reload = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = loadOrders()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        b.spStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statusFilters)
        b.spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeFilters)
        b.spStatus.onItemSelectedListener = reload
        b.spType.onItemSelectedListener = reload
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ -> showOrderActions(adapter.getItem(pos)) }

        // ---------- Tab thống kê ----------
        b.spGroup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groupOptions.map { it.first })
        b.spGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (b.tabStats.visibility == View.VISIBLE) loadStats()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        b.btnFrom.setOnClickListener { pickDate(from) }
        b.btnTo.setOnClickListener { pickDate(to) }
        updateDateButtons()
    }

    override fun onResume() {
        super.onResume()
        if (b.tabStats.visibility == View.VISIBLE) loadStats() else loadOrders()
    }

    private fun loadOrders() {
        val sPos = b.spStatus.selectedItemPosition
        val status = if (sPos <= 0) null else statusFilters[sPos]
        val type = when (b.spType.selectedItemPosition) { 1 -> "member"; 2 -> "guest"; else -> null }
        api({ adminOrders(status, type) }) { list ->
            adapter.submit(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // Bấm vào đơn: xem chi tiết hoặc đổi trạng thái
    private fun showOrderActions(o: OrderDto) {
        val confirmPaid = !o.isPaid && o.paymentMethod != "Tiền mặt" && o.status != OrderStatus.CANCELLED
        val options = arrayOf("Xem chi tiết đơn") + OrderStatus.ALL.map { "Chuyển sang: $it" } +
            (if (confirmPaid) arrayOf("Xác nhận đã nhận tiền (${o.total.toMoney()})") else emptyArray())
        AlertDialog.Builder(this)
            .setTitle("Đơn #${o.code} · ${o.status}\n${o.receiverName} · ${o.phone}")
            .setItems(options) { _, which ->
                if (confirmPaid && which == options.size - 1) {
                    api({ confirmPaid(o.id) }) { toast(it.message ?: "Đã xác nhận"); loadOrders() }
                } else if (which == 0) {
                    startActivity(Intent(this, OrderDetailActivity::class.java).putExtra(Nav.EXTRA_ID, o.id))
                } else {
                    val status = OrderStatus.ALL[which - 1]
                    if (status == OrderStatus.CANCELLED) askCancelReason(o)
                    else changeStatus(o, status, null)
                }
            }
            .show()
    }

    private fun changeStatus(o: OrderDto, status: String, note: String?) {
        api({ updateOrderStatus(o.id, UpdateStatusRequest(status, note)) }) { toast("Đã chuyển sang $status"); loadOrders() }
    }

    // Cửa hàng hủy đơn phải ghi lý do, khách sẽ thấy trong thông báo và dòng thời gian
    private fun askCancelReason(o: OrderDto) {
        val reasons = arrayOf("Hết món", "Quán sắp đóng cửa", "Không liên lạc được khách", "Ngoài khu vực giao hàng", "Lý do khác")
        AlertDialog.Builder(this).setTitle("Lý do hủy đơn #${o.code}")
            .setItems(reasons) { _, i ->
                if (i < reasons.size - 1) changeStatus(o, OrderStatus.CANCELLED, reasons[i])
                else {
                    val input = android.widget.EditText(this).apply { hint = "Nhập lý do"; setPadding(48, 32, 48, 32) }
                    AlertDialog.Builder(this).setTitle("Lý do hủy").setView(input)
                        .setPositiveButton("Hủy đơn") { _, _ ->
                            changeStatus(o, OrderStatus.CANCELLED, input.text.toString().trim().ifEmpty { "Lý do khác" })
                        }
                        .setNegativeButton("Quay lại", null).show()
                }
            }
            .setNegativeButton("Không hủy", null).show()
    }

    private fun pickDate(cal: Calendar) {
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            updateDateButtons()
            loadStats()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun Calendar.display() = String.format(Locale.US, "%02d/%02d/%04d", get(Calendar.DAY_OF_MONTH), get(Calendar.MONTH) + 1, get(Calendar.YEAR))
    private fun Calendar.query() = String.format(Locale.US, "%04d-%02d-%02d", get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))

    private fun updateDateButtons() {
        b.btnFrom.text = "Từ ${from.display()}"
        b.btnTo.text = "Đến ${to.display()}"
    }

    private fun loadStats() {
        val group = groupOptions[b.spGroup.selectedItemPosition.coerceAtLeast(0)].second
        api({ stats(group, from.query(), to.query()) }) { s ->
            b.tvSummary.text = "${s.totalOrders} đơn hoàn thành · Doanh thu ${s.totalRevenue.toMoney()}"
            fillRows(b.llRows, s.rows, byRevenue = true)
            fillRows(b.llTop, s.topProducts, byRevenue = false)
        }
    }

    // Mỗi dòng có thanh ngang tỉ lệ với giá trị lớn nhất (biểu đồ cột nằm ngang)
    private fun fillRows(container: android.widget.LinearLayout, rows: List<StatRowDto>, byRevenue: Boolean) {
        container.removeAllViews()
        if (rows.isEmpty()) {
            container.addView(android.widget.TextView(this).apply { text = "Chưa có dữ liệu trong khoảng này" })
            return
        }
        val max = rows.maxOf { if (byRevenue) it.revenue else it.quantity.toDouble() }.coerceAtLeast(1.0)
        rows.forEach { r ->
            val item = ItemStatRowBinding.inflate(layoutInflater, container, false)
            item.tvLabel.text = r.label
            item.tvValue.text = if (byRevenue) r.revenue.toMoney() else "${r.quantity} phần"
            item.tvSub.text = if (byRevenue) "${r.orderCount} đơn · ${r.quantity} phần" else "Doanh thu ${r.revenue.toMoney()}"
            val v = if (byRevenue) r.revenue else r.quantity.toDouble()
            item.bar.progress = (v / max * 1000).toInt()
            container.addView(item.root)
        }
    }
}
