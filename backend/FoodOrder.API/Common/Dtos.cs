namespace FoodOrder.API.Common;

public record MessageDto(string Message);

// ---------- Auth ----------
public record RegisterRequest(string FullName, string Email, string Phone, string Password);
public record LoginRequest(string EmailOrPhone, string Password);
public record UserDto(int Id, string FullName, string Email, string Phone, string? DefaultAddress, string Role);
public record LoginResponse(string Token, UserDto User);
public record UpdateProfileRequest(string FullName, string Phone, string? DefaultAddress);
public record ChangePasswordRequest(string OldPassword, string NewPassword);

// ---------- Danh mục, món ----------
public record CategoryDto(int Id, string Name, string? ImageUrl, int ProductCount);
public record CategoryRequest(string Name, string? ImageUrl);
public record SizeDto(string Size, decimal Price);
public record ProductDto(int Id, int CategoryId, string CategoryName, string Name, string? Description,
    string? ImageUrl, bool IsNew, int SoldCount, double AvgRating, int RatingCount, decimal MinPrice, List<SizeDto> Sizes);
public record ReviewDto(int Id, string UserName, int Stars, string? Comment, DateTime CreatedAt);
public record ProductDetailDto(ProductDto Product, List<ReviewDto> Reviews, bool IsFavorite = false);
public record ProductRequest(int CategoryId, string Name, string? Description, string? ImageUrl, bool IsNew, List<SizeDto> Sizes);

// ---------- Giỏ hàng ----------
public record CartItemDto(int Id, int ProductId, string ProductName, string? ImageUrl, string Size, int Quantity, decimal UnitPrice, decimal LineTotal);
public record CartDto(List<CartItemDto> Items, int ItemCount, int TotalQuantity, decimal Subtotal);
public record AddCartRequest(int ProductId, string Size, int Quantity);
public record UpdateCartRequest(int Quantity);

// ---------- Đơn hàng ----------
public record OrderLineRequest(int ProductId, string Size, int Quantity);
public record CreateOrderRequest(string ReceiverName, string Phone, string Address, string? Note,
    string PaymentMethod, List<OrderLineRequest>? Items, string? VoucherCode = null);
public record OrderItemDto(int ProductId, string ProductName, string Size, int Quantity, decimal UnitPrice, decimal LineTotal, bool Reviewed);
public record OrderDto(int Id, string Code, bool IsGuest, string ReceiverName, string Phone, string Address, string? Note,
    string PaymentMethod, bool IsPaid, string Status, decimal Subtotal, decimal ShippingFee, decimal Total,
    DateTime CreatedAt, int ItemCount, List<OrderItemDto> Items, string? PaymentUrl = null,
    string? VoucherCode = null, decimal Discount = 0, string? CancelReason = null, List<OrderLogDto>? History = null);
public record PaymentLinkDto(string PaymentUrl);
public record UpdateStatusRequest(string Status, string? Note = null);
public record CancelRequest(string? Reason);
public record OrderLogDto(string Status, string? Note, string ChangedBy, DateTime CreatedAt);
public record CreateReviewRequest(int OrderId, int ProductId, int Stars, string? Comment);

// ---------- Admin ----------
public record DashboardDto(int TotalUsers, int OrdersToday, decimal RevenueThisMonth, int TotalProducts, int PendingOrders);
public record AdminUserRequest(string FullName, string Email, string Phone, string? Password, string Role, string? DefaultAddress);
public record StatRowDto(string Label, int OrderCount, int Quantity, decimal Revenue);
public record StatsDto(List<StatRowDto> Rows, List<StatRowDto> TopProducts, decimal TotalRevenue, int TotalOrders);

// ---------- Chat ----------
public record ChatMessageDto(int Id, bool IsFromStaff, string SenderName, string Content, DateTime CreatedAt);
public record ChatThreadDto(int ConversationId, string CustomerName, List<ChatMessageDto> Messages);
public record SendMessageRequest(string Content);
public record ConversationDto(int Id, int CustomerId, string CustomerName, string CustomerPhone, string? LastMessage,
    DateTime LastMessageAt, int Unread);
public record CountDto(int Count);

// ---------- Thông báo ----------
public record NotificationDto(int Id, string Title, string Body, int? OrderId, bool IsRead, DateTime CreatedAt);
public record StaffAlertsDto(int PendingOrders, int LatestOrderId, int ChatUnread);

// ---------- Mã giảm giá ----------
public record VoucherDto(int Id, string Code, string Description, string Type, decimal Value, decimal MinOrder,
    decimal MaxDiscount, int UsageLimit, int UsedCount, DateTime? ExpiresAt, bool IsActive);
public record VoucherRequest(string Code, string Description, string Type, decimal Value, decimal MinOrder,
    decimal MaxDiscount, int UsageLimit, DateTime? ExpiresAt, bool IsActive);
public record CheckVoucherRequest(string Code, decimal Subtotal);
public record VoucherResultDto(string Code, decimal Discount, string Message);
