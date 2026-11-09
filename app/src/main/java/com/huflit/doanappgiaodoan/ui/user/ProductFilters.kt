package com.huflit.doanappgiaodoan.ui.user

import com.huflit.doanappgiaodoan.data.ProductDto

object ProductFilters {

    fun apply(
        products: List<ProductDto>,
        minPrice: Long?,
        maxPrice: Long?,
        sortPosition: Int
    ): List<ProductDto> {

        // Lọc sản phẩm theo khoảng giá
        val filtered = products.filter { product ->
            val price = product.minPrice

            (minPrice == null || price >= minPrice.toDouble()) &&
                    (maxPrice == null || price <= maxPrice.toDouble())
        }

        // Sắp xếp danh sách sau khi lọc
        return when (sortPosition) {
            1 -> filtered.sortedByDescending { it.soldCount }
            2 -> filtered.sortedBy { it.minPrice }
            3 -> filtered.sortedByDescending { it.minPrice }
            else -> filtered
        }
    }
}