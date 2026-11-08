package com.huflit.doanappgiaodoan.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @GET("api/admin/orders") suspend fun adminOrders(@Query("status") status: String? = null, @Query("type") type: String? = null): List<OrderDto>
    @PUT("api/admin/orders/{id}/status") suspend fun updateOrderStatus(@Path("id") id: Int, @Body body: UpdateStatusRequest): MessageDto
    @GET("api/admin/stats") suspend fun stats(
        @Query("groupBy") groupBy: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): StatsDto
}
