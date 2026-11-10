package com.huflit.doanappgiaodoan.data

// Các lớp dữ liệu khớp với JSON của FoodOrder.API

data class MessageDto(val message: String?)

data class UserDto(
    val id: Int, val fullName: String, val email: String, val phone: String,
    val defaultAddress: String?, val role: String
)
data class LoginRequest(val emailOrPhone: String, val password: String)
data class RegisterRequest(val fullName: String, val email: String, val phone: String, val password: String)
data class LoginResponse(val token: String, val user: UserDto)
data class UpdateProfileRequest(val fullName: String, val phone: String, val defaultAddress: String?)
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)

data class CategoryDto(val id: Int, val name: String, val imageUrl: String?, val productCount: Int)
data class CategoryRequest(val name: String, val imageUrl: String?)

data class SizeDto(val size: String, val price: Double)
data class ProductDto(
    val id: Int, val categoryId: Int, val categoryName: String, val name: String,
    val description: String?, val imageUrl: String?, val isNew: Boolean, val soldCount: Int,
    val avgRating: Double, val ratingCount: Int, val minPrice: Double, val sizes: List<SizeDto>
)
data class ReviewDto(val id: Int, val userName: String, val stars: Int, val comment: String?, val createdAt: String)
data class ProductDetailDto(val product: ProductDto, val reviews: List<ReviewDto>)
data class ProductRequest(
    val categoryId: Int, val name: String, val description: String?, val imageUrl: String?,
    val isNew: Boolean, val sizes: List<SizeDto>
)

data class CartItemDto(
    val id: Int, val productId: Int, val productName: String, val imageUrl: String?,
    val size: String, val quantity: Int, val unitPrice: Double, val lineTotal: Double
)
data class CartDto(val items: List<CartItemDto>, val itemCount: Int, val totalQuantity: Int, val subtotal: Double)
data class AddCartRequest(val productId: Int, val size: String, val quantity: Int)
data class UpdateCartRequest(val quantity: Int)

data class OrderLineRequest(val productId: Int, val size: String, val quantity: Int)
data class CreateOrderRequest(
    val receiverName: String, val phone: String, val address: String, val note: String?,
    val paymentMethod: String, val items: List<OrderLineRequest>?
)
data class OrderItemDto(
    val productId: Int, val productName: String, val size: String, val quantity: Int,
    val unitPrice: Double, val lineTotal: Double, val reviewed: Boolean
)
data class OrderDto(
    val id: Int, val code: String, val isGuest: Boolean, val receiverName: String, val phone: String,
    val address: String, val note: String?, val paymentMethod: String, val isPaid: Boolean,
    val status: String, val subtotal: Double, val shippingFee: Double, val total: Double,
    val createdAt: String, val itemCount: Int, val items: List<OrderItemDto>,
    val paymentUrl: String? = null
)
data class PaymentLinkDto(val paymentUrl: String)
data class UploadDto(val url: String)
data class UpdateStatusRequest(val status: String)
data class CreateReviewRequest(val orderId: Int, val productId: Int, val stars: Int, val comment: String?)

data class DashboardDto(
    val totalUsers: Int, val ordersToday: Int, val revenueThisMonth: Double,
    val totalProducts: Int, val pendingOrders: Int
)
data class AdminUserRequest(
    val fullName: String, val email: String, val phone: String, val password: String?,
    val role: String, val defaultAddress: String?
)
data class StatRowDto(val label: String, val orderCount: Int, val quantity: Int, val revenue: Double)
data class StatsDto(val rows: List<StatRowDto>, val topProducts: List<StatRowDto>, val totalRevenue: Double, val totalOrders: Int)

// Món trong giỏ của khách vãng lai, lưu trên máy
data class GuestCartItem(
    val productId: Int, val productName: String, val imageUrl: String?,
    val size: String, val unitPrice: Double, var quantity: Int
)

object OrderStatus {
    const val PENDING = "Đang xử lý"
    const val SHIPPING = "Đang giao"
    const val DONE = "Hoàn thành"
    const val CANCELLED = "Đã hủy"
    val ALL = listOf(PENDING, SHIPPING, DONE, CANCELLED)
}

val PAYMENT_METHODS = listOf("Tiền mặt", "VNPay")

// Vai trò (khớp với Roles bên API)
object Role {
    const val USER = "User"
    const val ADMIN = "Admin"
    const val MENU_MANAGER = "MenuManager"
    const val ORDER_STAFF = "OrderStaff"
    val ALL = listOf(USER, ADMIN, MENU_MANAGER, ORDER_STAFF)

    fun label(role: String?): String = when (role) {
        ADMIN -> "Quản trị viên"
        MENU_MANAGER -> "Quản lý thực đơn"
        ORDER_STAFF -> "Nhân viên đơn hàng"
        else -> "Thành viên"
    }
}
