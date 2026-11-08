package com.huflit.doanappgiaodoan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huflit.doanappgiaodoan.data.CategoryDto
import com.huflit.doanappgiaodoan.data.ProductDto
import com.huflit.doanappgiaodoan.databinding.ItemCategoryChipBinding
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

// Đổ dữ liệu món vào thẻ (dùng chung cho RecyclerView và GridView)
fun ItemProductCardBinding.fill(p: ProductDto) {
    tvName.text = p.name
    tvPrice.text = p.minPrice.toMoney()
    tvRating.text = ratingText(p.avgRating, p.ratingCount)
    tvNew.visibility = if (p.isNew) View.VISIBLE else View.GONE
    ImageLoader.load(img, imgText, p.imageUrl, p.name)
}

fun ItemProductRowBinding.fill(p: ProductDto) {
    tvName.text = p.name
    tvSub.text = "${p.categoryName} · Đã bán ${p.soldCount}"
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
