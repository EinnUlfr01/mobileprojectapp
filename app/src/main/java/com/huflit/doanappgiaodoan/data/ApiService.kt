package com.huflit.doanappgiaodoan.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    // ---------- Tài khoản ----------
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): LoginResponse
    @GET("api/me") suspend fun me(): UserDto
    @PUT("api/me") suspend fun updateMe(@Body body: UpdateProfileRequest): UserDto
    @PUT("api/me/password") suspend fun changePassword(@Body body: ChangePasswordRequest): MessageDto

    // ---------- Danh mục, món ----------
    @GET("api/categories") suspend fun categories(): List<CategoryDto>
    @GET("api/products") suspend fun products(
        @Query("categoryId") categoryId: Int? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        @Query("isNew") isNew: Boolean? = null,
        @Query("take") take: Int? = null
    ): List<ProductDto>
    @GET("api/products/{id}") suspend fun product(@Path("id") id: Int): ProductDetailDto

    // ---------- Giỏ hàng (user) ----------
    @GET("api/cart") suspend fun cart(): CartDto
    @POST("api/cart") suspend fun addToCart(@Body body: AddCartRequest): CartDto
    @PUT("api/cart/{id}") suspend fun updateCart(@Path("id") id: Int, @Body body: UpdateCartRequest): CartDto
    @DELETE("api/cart/{id}") suspend fun deleteCart(@Path("id") id: Int): CartDto

    // ---------- Đơn hàng ----------
    @POST("api/orders") suspend fun createOrder(@Body body: CreateOrderRequest): OrderDto
    @GET("api/orders/my") suspend fun myOrders(@Query("status") status: String? = null): List<OrderDto>
    @GET("api/orders/{id}") suspend fun order(@Path("id") id: Int): OrderDto
    @PUT("api/orders/{id}/cancel") suspend fun cancelOrder(@Path("id") id: Int): MessageDto
    @POST("api/orders/{id}/pay") suspend fun payOrder(@Path("id") id: Int): PaymentLinkDto
    @POST("api/reviews") suspend fun review(@Body body: CreateReviewRequest): MessageDto

    // ---------- Admin ----------
    @GET("api/admin/dashboard") suspend fun dashboard(): DashboardDto
    @GET("api/admin/users") suspend fun users(@Query("q") q: String? = null, @Query("role") role: String? = null): List<UserDto>
    @POST("api/admin/users") suspend fun createUser(@Body body: AdminUserRequest): UserDto
    @PUT("api/admin/users/{id}") suspend fun updateUser(@Path("id") id: Int, @Body body: AdminUserRequest): UserDto
    @DELETE("api/admin/users/{id}") suspend fun deleteUser(@Path("id") id: Int): MessageDto

    @POST("api/categories") suspend fun createCategory(@Body body: CategoryRequest): CategoryDto
    @PUT("api/categories/{id}") suspend fun updateCategory(@Path("id") id: Int, @Body body: CategoryRequest): MessageDto
    @DELETE("api/categories/{id}") suspend fun deleteCategory(@Path("id") id: Int): MessageDto

    @POST("api/products") suspend fun createProduct(@Body body: ProductRequest): ProductDto
    @PUT("api/products/{id}") suspend fun updateProduct(@Path("id") id: Int, @Body body: ProductRequest): ProductDto
    @DELETE("api/products/{id}") suspend fun deleteProduct(@Path("id") id: Int): MessageDto

    // ---------- Chat trực tuyến ----------
    @GET("api/chat/me") suspend fun myChat(@Query("afterId") afterId: Int = 0): ChatThreadDto
    @POST("api/chat/me") suspend fun sendToShop(@Body body: SendMessageRequest): ChatMessageDto
    @GET("api/chat/unread") suspend fun chatUnread(): CountDto
    @GET("api/chat/conversations") suspend fun conversations(@Query("q") q: String? = null): List<ConversationDto>
    @GET("api/chat/conversations/{id}") suspend fun conversation(@Path("id") id: Int, @Query("afterId") afterId: Int = 0): ChatThreadDto
    @POST("api/chat/conversations/{id}") suspend fun reply(@Path("id") id: Int, @Body body: SendMessageRequest): ChatMessageDto

    // ---------- Thông báo ----------
    @GET("api/notifications") suspend fun notifications(@Query("afterId") afterId: Int = 0): List<NotificationDto>
    @GET("api/notifications/unread") suspend fun notificationUnread(): CountDto
    @PUT("api/notifications/read-all") suspend fun readAllNotifications(): MessageDto
    @GET("api/admin/alerts") suspend fun staffAlerts(): StaffAlertsDto

    // ---------- Mã giảm giá ----------
    @GET("api/vouchers") suspend fun vouchers(): List<VoucherDto>
    @POST("api/vouchers/check") suspend fun checkVoucher(@Body body: CheckVoucherRequest): VoucherResultDto
    @GET("api/vouchers/all") suspend fun allVouchers(): List<VoucherDto>
    @POST("api/vouchers") suspend fun createVoucher(@Body body: VoucherRequest): VoucherDto
    @PUT("api/vouchers/{id}") suspend fun updateVoucher(@Path("id") id: Int, @Body body: VoucherRequest): VoucherDto
    @DELETE("api/vouchers/{id}") suspend fun deleteVoucher(@Path("id") id: Int): MessageDto

    // ---------- Yêu thích, đặt lại ----------
    @GET("api/favorites") suspend fun favorites(): List<ProductDto>
    @POST("api/favorites/{productId}") suspend fun addFavorite(@Path("productId") productId: Int): MessageDto
    @DELETE("api/favorites/{productId}") suspend fun removeFavorite(@Path("productId") productId: Int): MessageDto
    @POST("api/orders/{id}/reorder") suspend fun reorder(@Path("id") id: Int): MessageDto

    // Upload ảnh từ điện thoại, trả về "uploads/xxx.jpg"
    @Multipart @POST("api/uploads") suspend fun upload(@Part file: MultipartBody.Part): UploadDto

    @GET("api/admin/orders") suspend fun adminOrders(@Query("status") status: String? = null, @Query("type") type: String? = null): List<OrderDto>
    @PUT("api/admin/orders/{id}/status") suspend fun updateOrderStatus(@Path("id") id: Int, @Body body: UpdateStatusRequest): MessageDto
    @GET("api/admin/stats") suspend fun stats(
        @Query("groupBy") groupBy: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): StatsDto
}
