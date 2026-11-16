package com.huflit.doanappgiaodoan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huflit.doanappgiaodoan.data.CategoryDto
import com.huflit.doanappgiaodoan.data.ProductDto
import com.huflit.doanappgiaodoan.data.StoreDto
import com.huflit.doanappgiaodoan.databinding.ItemCategoryChipBinding
import com.huflit.doanappgiaodoan.databinding.ItemStoreCardBinding
import com.huflit.doanappgiaodoan.databinding.ItemStoreRowBinding
import com.huflit.doanappgiaodoan.databinding.ItemProductCardBinding
import com.huflit.doanappgiaodoan.databinding.ItemProductRowBinding
import com.huflit.doanappgiaodoan.util.ImageLoader
import com.huflit.doanappgiaodoan.util.ratingText
import com.huflit.doanappgiaodoan.util.toMoney

// ---------- RecyclerView: danh mục cuộn ngang (trang chủ) ----------
class CategoryChipAdapter(
    private var items: List<CategoryDto> = emptyList(),
    private val onClick: (CategoryDto) -> Unit
) : RecyclerView.Adapter<CategoryChipAdapter.VH>() {
    class VH(val b: ItemCategoryChipBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<CategoryDto>) { items = list; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        h.b.tvName.text = c.name
        ImageLoader.load(h.b.img, h.b.imgText, c.imageUrl, c.name)
        h.b.root.setOnClickListener { onClick(c) }
    }
}

// ---------- RecyclerView: quán ăn cuộn ngang (trang chủ) ----------
class StoreCardAdapter(
    private var items: List<StoreDto> = emptyList(),
    private val onClick: (StoreDto) -> Unit
) : RecyclerView.Adapter<StoreCardAdapter.VH>() {
    class VH(val b: ItemStoreCardBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<StoreDto>) { items = list; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStoreCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = items[position]
        h.b.tvName.text = s.name
        h.b.tvAddress.text = listOfNotNull(s.storeCategoryName, s.address).joinToString(" · ")
        h.b.tvInfo.text = storeInfo(s)
        h.b.tvClosed.visibility = if (s.isOpen) View.GONE else View.VISIBLE
        ImageLoader.load(h.b.img, h.b.imgText, s.imageUrl, s.name)
        h.b.root.setOnClickListener { onClick(s) }
    }
}

// ---------- RecyclerView: dòng quán ăn (kết quả tìm kiếm) ----------
fun ItemStoreRowBinding.fill(s: StoreDto) {
    tvName.text = s.name + if (s.isOpen) "" else "  (đang đóng cửa)"
    tvCategory.text = listOfNotNull(s.storeCategoryName, "${s.productCount} món").joinToString(" · ")
    tvAddress.text = "📍 ${s.address}"
    tvRating.text = storeInfo(s)
    ImageLoader.load(img, imgText, s.imageUrl, s.name)
}

class StoreRowAdapter(
    private var items: List<StoreDto> = emptyList(),
    private val onClick: (StoreDto) -> Unit
) : RecyclerView.Adapter<StoreRowAdapter.VH>() {
    class VH(val b: ItemStoreRowBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<StoreDto>) { items = list; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStoreRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = items[position]
        h.b.fill(s)
        h.b.root.setOnClickListener { onClick(s) }
    }
}

// "★ 4.8 · 12 món · 30 đơn"
fun storeInfo(s: StoreDto): String = listOfNotNull(
    if (s.avgRating > 0) "★ %.1f".format(java.util.Locale.US, s.avgRating) else null,
    "${s.productCount} món",
    if (s.orderCount > 0) "${s.orderCount} đơn" else null
).joinToString(" · ")

// Đổ dữ liệu món vào thẻ (dùng chung cho RecyclerView và GridView)
fun ItemProductCardBinding.fill(p: ProductDto) {
    tvName.text = p.name
    tvStore.text = p.storeName
    tvStore.visibility = if (p.storeName.isBlank()) View.GONE else View.VISIBLE
    tvPrice.text = p.minPrice.toMoney()
    tvRating.text = ratingText(p.avgRating, p.ratingCount)
    tvNew.visibility = if (p.isNew) View.VISIBLE else View.GONE
    ImageLoader.load(img, imgText, p.imageUrl, p.name)
}

fun ItemProductRowBinding.fill(p: ProductDto) {
    tvName.text = p.name
    tvSub.text = listOf(p.storeName, p.categoryName, "Đã bán ${p.soldCount}").filter { it.isNotBlank() }.joinToString(" · ")
    tvPrice.text = p.minPrice.toMoney()
    tvRating.text = ratingText(p.avgRating, p.ratingCount)
    ImageLoader.load(img, imgText, p.imageUrl, p.name)
}

// ---------- RecyclerView: thẻ món cuộn ngang (Bán chạy) ----------
class ProductCardAdapter(
    private var items: List<ProductDto> = emptyList(),
    private val onClick: (ProductDto) -> Unit
) : RecyclerView.Adapter<ProductCardAdapter.VH>() {
    class VH(val b: ItemProductCardBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<ProductDto>) { items = list; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        h.b.fill(p)
        h.b.root.setOnClickListener { onClick(p) }
    }
}

// ---------- RecyclerView: dòng món (Món mới, Quản lý món của admin) ----------
class ProductRowAdapter(
    private var items: List<ProductDto> = emptyList(),
    private val adminMode: Boolean = false,
    private val onEdit: ((ProductDto) -> Unit)? = null,
    private val onDelete: ((ProductDto) -> Unit)? = null,
    private val onClick: (ProductDto) -> Unit
) : RecyclerView.Adapter<ProductRowAdapter.VH>() {
    class VH(val b: ItemProductRowBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<ProductDto>) { items = list; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        h.b.fill(p)
        h.b.adminActions.visibility = if (adminMode) View.VISIBLE else View.GONE
        h.b.btnEdit.setOnClickListener { onEdit?.invoke(p) }
        h.b.btnDelete.setOnClickListener { onDelete?.invoke(p) }
        h.b.root.setOnClickListener { onClick(p) }
    }
}

// ---------- GridView: lưới món theo danh mục ----------
class ProductGridAdapter(private var items: List<ProductDto> = emptyList()) : BaseAdapter() {
    fun submit(list: List<ProductDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemProductCardBinding.bind(convertView)
        b.root.layoutParams = b.root.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        b.fill(items[position])
        return b.root
    }
}

// ---------- ListView: kết quả tìm kiếm ----------
class ProductListAdapter(private var items: List<ProductDto> = emptyList()) : BaseAdapter() {
    fun submit(list: List<ProductDto>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemProductRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        else ItemProductRowBinding.bind(convertView)
        b.fill(items[position])
        return b.root
    }
}
