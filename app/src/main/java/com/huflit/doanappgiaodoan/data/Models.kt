package com.huflit.doanappgiaodoan.data

// Các lớp dữ liệu khớp với JSON của FoodOrder.API

data class MessageDto(val message: String?)

data class UserDto(
    val id: Int, val fullName: String, val email: String, val phone: String,
    val defaultAddress: String?, val role: String,
    val vehiclePlate: String? = null, val isLocked: Boolean = false,
    val storeId: Int? = null, val storeName: String? = null   // chủ quán: quán đang quản lý
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
    val avgRating: Double, val ratingCount: Int, val minPrice: Double, val sizes: List<SizeDto>,
    val storeId: Int = 0, val storeName: String = "", val storeIsOpen: Boolean = true
)
data class ReviewDto(val id: Int, val userName: String, val stars: Int, val comment: String?, val createdAt: String)
data class ProductDetailDto(val product: ProductDto, val reviews: List<ReviewDto>, val isFavorite: Boolean = false)
data class ProductRequest(
    val categoryId: Int, val name: String, val description: String?, val imageUrl: String?,
    val isNew: Boolean, val sizes: List<SizeDto>, val storeId: Int? = null
)

data class CartItemDto(
    val id: Int, val productId: Int, val productName: String, val imageUrl: String?,
    val size: String, val quantity: Int, val unitPrice: Double, val lineTotal: Double
)
data class CartDto(
    val items: List<CartItemDto>, val itemCount: Int, val totalQuantity: Int, val subtotal: Double,
    val storeId: Int? = null, val storeName: String? = null
)
data class AddCartRequest(val productId: Int, val size: String, val quantity: Int)
data class UpdateCartRequest(val quantity: Int)

data class OrderLineRequest(val productId: Int, val size: String, val quantity: Int)
data class CreateOrderRequest(
    val receiverName: String, val phone: String, val address: String, val note: String?,
    val paymentMethod: String, val items: List<OrderLineRequest>?,
    val voucherCode: String? = null
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
    val paymentUrl: String? = null,
    val voucherCode: String? = null,
    val discount: Double = 0.0,
    val cancelReason: String? = null,
    val history: List<OrderLogDto>? = null,
    val storeId: Int? = null, val storeName: String? = null, val storeAddress: String? = null, val storePhone: String? = null,
    val shipperId: Int? = null, val shipperName: String? = null, val shipperPhone: String? = null, val shipperPlate: String? = null
)
data class OrderLogDto(val status: String, val note: String?, val changedBy: String, val createdAt: String)
data class CancelRequest(val reason: String?)
data class PaymentLinkDto(val paymentUrl: String)
data class UploadDto(val url: String)
data class UpdateStatusRequest(val status: String, val note: String? = null)
data class CreateReviewRequest(val orderId: Int, val productId: Int, val stars: Int, val comment: String?)

data class DashboardDto(
    val totalUsers: Int, val ordersToday: Int, val revenueThisMonth: Double,
    val totalProducts: Int, val pendingOrders: Int,
    val totalStores: Int = 0, val totalShippers: Int = 0,
    val storeId: Int? = null, val storeName: String? = null, val storeIsOpen: Boolean = false
)
data class AdminUserRequest(
    val fullName: String, val email: String, val phone: String, val password: String?,
    val role: String, val defaultAddress: String?, val vehiclePlate: String? = null
)
data class LockRequest(val isLocked: Boolean)
data class StatRowDto(val label: String, val orderCount: Int, val quantity: Int, val revenue: Double)
data class StatsDto(val rows: List<StatRowDto>, val topProducts: List<StatRowDto>, val totalRevenue: Double, val totalOrders: Int)

// Món trong giỏ của khách vãng lai, lưu trên máy
data class GuestCartItem(
    val productId: Int, val productName: String, val imageUrl: String?,
    val size: String, val unitPrice: Double, var quantity: Int,
    val storeId: Int = 0, val storeName: String? = null
)

// Luồng đơn: chờ quán xác nhận -> quán chuẩn bị -> shipper giao -> hoàn thành
object OrderStatus {
    const val PENDING = "Đang xử lý"
    const val PREPARING = "Đang chuẩn bị"
    const val SHIPPING = "Đang giao"
    const val DONE = "Hoàn thành"
    const val CANCELLED = "Đã hủy"
    val ALL = listOf(PENDING, PREPARING, SHIPPING, DONE, CANCELLED)
}

const val QR_METHOD = "Chuyển khoản QR"
val PAYMENT_METHODS = listOf("Tiền mặt", "VNPay", QR_METHOD) // danh sách thật lấy từ API /api/payments/methods

data class BankQrDto(
    val orderCode: String, val qrUrl: String, val bankName: String, val accountNo: String, val accountName: String,
    val amount: Double, val content: String, val isPaid: Boolean, val expiresAt: String, val autoCheck: Boolean
)
data class PaymentStatusDto(val orderCode: String, val isPaid: Boolean, val status: String)

// Vai trò (khớp với Roles bên API)
object Role {
    const val USER = "User"
    const val ADMIN = "Admin"
    const val STORE_OWNER = "StoreOwner"
    const val SHIPPER = "Shipper"
    val ALL = listOf(USER, ADMIN, STORE_OWNER, SHIPPER)

    fun label(role: String?): String = when (role) {
        ADMIN -> "Quản trị sàn"
        STORE_OWNER -> "Chủ cửa hàng"
        SHIPPER -> "Shipper"
        else -> "Khách hàng"
    }
}

// ---------- Cửa hàng ----------
data class StoreDto(
    val id: Int, val name: String, val address: String, val phone: String, val description: String?,
    val imageUrl: String?, val isOpen: Boolean, val isActive: Boolean, val ownerId: Int?, val ownerName: String?,
    val ownerEmail: String?, val productCount: Int, val avgRating: Double, val orderCount: Int,
    val storeCategoryId: Int? = null, val storeCategoryName: String? = null
)
data class StoreRequest(
    val name: String, val address: String, val phone: String, val description: String?, val imageUrl: String?,
    val isOpen: Boolean, val isActive: Boolean, val ownerId: Int?, val storeCategoryId: Int? = null
)
// Loại quán (bảng DanhMucQuanAn)
data class StoreCategoryDto(val id: Int, val name: String, val imageUrl: String?, val storeCount: Int)
data class StoreCategoryRequest(val name: String, val imageUrl: String?)
data class StoreDetailDto(val store: StoreDto, val products: List<ProductDto>)
data class OpenRequest(val isOpen: Boolean)

// ---------- Shipper ----------
data class ShipperDto(
    val id: Int, val fullName: String, val email: String, val phone: String, val vehiclePlate: String?,
    val isLocked: Boolean, val delivering: Int, val deliveredTotal: Int, val deliveredToday: Int
)
data class AssignShipperRequest(val shipperId: Int?)
data class ShipperSummaryDto(val available: Int, val delivering: Int, val deliveredToday: Int, val incomeToday: Double, val latestAvailableId: Int)

// ---------- Chat ----------
data class ChatMessageDto(val id: Int, val isFromStaff: Boolean, val senderName: String, val content: String, val createdAt: String)
data class ChatThreadDto(val conversationId: Int, val customerName: String, val messages: List<ChatMessageDto>)
data class SendMessageRequest(val content: String)
data class ConversationDto(
    val id: Int, val customerId: Int, val customerName: String, val customerPhone: String,
    val lastMessage: String?, val lastMessageAt: String, val unread: Int
)
data class CountDto(val count: Int)

// ---------- Thông báo ----------
data class NotificationDto(val id: Int, val title: String, val body: String, val orderId: Int?, val isRead: Boolean, val createdAt: String)
data class StaffAlertsDto(val pendingOrders: Int, val latestOrderId: Int, val chatUnread: Int)

// ---------- Mã giảm giá ----------
data class VoucherDto(
    val id: Int, val code: String, val description: String, val type: String, val value: Double,
    val minOrder: Double, val maxDiscount: Double, val usageLimit: Int, val usedCount: Int,
    val expiresAt: String?, val isActive: Boolean
)
data class VoucherRequest(
    val code: String, val description: String, val type: String, val value: Double, val minOrder: Double,
    val maxDiscount: Double, val usageLimit: Int, val expiresAt: String?, val isActive: Boolean
)
data class CheckVoucherRequest(val code: String, val subtotal: Double)
data class VoucherResultDto(val code: String, val discount: Double, val message: String)

object VoucherType {
    const val PERCENT = "Percent"
    const val AMOUNT = "Amount"
    const val FREE_SHIP = "FreeShip"
    val ALL = listOf(PERCENT, AMOUNT, FREE_SHIP)
    fun label(t: String) = when (t) { PERCENT -> "Giảm %"; FREE_SHIP -> "Miễn phí ship"; else -> "Giảm tiền" }
}
