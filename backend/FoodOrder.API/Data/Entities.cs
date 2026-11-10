namespace FoodOrder.API.Data;

// Bảng NguoiDung: tài khoản user và admin
public class User
{
    public int Id { get; set; }
    public string FullName { get; set; } = "";
    public string Email { get; set; } = "";
    public string Phone { get; set; } = "";
    public string PasswordHash { get; set; } = "";
    public string? DefaultAddress { get; set; }
    public string Role { get; set; } = Roles.User;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// Vai trò: 1 vai trò khách hàng + 3 vai trò quản trị
public static class Roles
{
    public const string User = "User";                 // Khách hàng thành viên
    public const string Admin = "Admin";               // Quản trị viên: toàn quyền
    public const string MenuManager = "MenuManager";   // Quản lý thực đơn: danh mục, món ăn
    public const string OrderStaff = "OrderStaff";     // Nhân viên đơn hàng: xử lý đơn, thống kê

    // Dùng trong [Authorize(Roles = ...)]
    public const string AllStaff = Admin + "," + MenuManager + "," + OrderStaff;
    public const string MenuManagers = Admin + "," + MenuManager;
    public const string OrderManagers = Admin + "," + OrderStaff;

    public static readonly string[] All = { User, Admin, MenuManager, OrderStaff };
    public static bool IsStaff(string role) => role is Admin or MenuManager or OrderStaff;
}

// Bảng DanhMuc
public class Category
{
    public int Id { get; set; }
    public string Name { get; set; } = "";
    public string? ImageUrl { get; set; }
    public List<Product> Products { get; set; } = new();
}

// Bảng SanPham
public class Product
{
    public int Id { get; set; }
    public int CategoryId { get; set; }
    public Category? Category { get; set; }
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public string? ImageUrl { get; set; }
    public bool IsNew { get; set; }
    public int SoldCount { get; set; }
    public double AvgRating { get; set; }
    public int RatingCount { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public List<ProductSize> Sizes { get; set; } = new();
}

// Bảng SanPhamSize: mỗi món có giá S/M/L
public class ProductSize
{
    public int Id { get; set; }
    public int ProductId { get; set; }
    public string Size { get; set; } = "M";
    public decimal Price { get; set; }
}

// Bảng GioHang (chỉ user đã đăng nhập; khách lưu giỏ trên máy)
public class CartItem
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public int ProductId { get; set; }
    public Product? Product { get; set; }
    public string Size { get; set; } = "M";
    public int Quantity { get; set; }
}

public static class OrderStatus
{
    public const string Pending = "Đang xử lý";
    public const string Shipping = "Đang giao";
    public const string Done = "Hoàn thành";
    public const string Cancelled = "Đã hủy";
    public static readonly string[] All = { Pending, Shipping, Done, Cancelled };
}

// Bảng DonHang: UserId = null nghĩa là khách vãng lai
public class Order
{
    public int Id { get; set; }
    public string Code { get; set; } = "";
    public int? UserId { get; set; }
    public User? User { get; set; }
    public string ReceiverName { get; set; } = "";
    public string Phone { get; set; } = "";
    public string Address { get; set; } = "";
    public string? Note { get; set; }
    public string PaymentMethod { get; set; } = "Tiền mặt";
    public bool IsPaid { get; set; }
    public string Status { get; set; } = OrderStatus.Pending;
    public decimal Subtotal { get; set; }
    public decimal ShippingFee { get; set; }
    public decimal Total { get; set; }
    public string? VoucherCode { get; set; }   // mã giảm giá đã dùng
    public decimal Discount { get; set; }      // số tiền được giảm (gồm cả giảm phí ship)
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public List<OrderItem> Items { get; set; } = new();
}

// Bảng ChiTietDonHang
public class OrderItem
{
    public int Id { get; set; }
    public int OrderId { get; set; }
    public int ProductId { get; set; }
    public Product? Product { get; set; }
    public string ProductName { get; set; } = "";
    public string Size { get; set; } = "M";
    public int Quantity { get; set; }
    public decimal UnitPrice { get; set; }
    public decimal LineTotal { get; set; }
}

// Bảng DanhGia
public class Review
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public User? User { get; set; }
    public int ProductId { get; set; }
    public int OrderId { get; set; }
    public int Stars { get; set; }
    public string? Comment { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// ================= Chức năng nâng cao =================

// Bảng HoiThoai: mỗi khách hàng có 1 cuộc trò chuyện với cửa hàng
public class Conversation
{
    public int Id { get; set; }
    public int CustomerId { get; set; }
    public User? Customer { get; set; }
    public string? LastMessage { get; set; }
    public DateTime LastMessageAt { get; set; } = DateTime.UtcNow;
    public int UnreadByStaff { get; set; }      // số tin khách gửi mà cửa hàng chưa đọc
    public int UnreadByCustomer { get; set; }   // số tin cửa hàng gửi mà khách chưa đọc
    public List<ChatMessage> Messages { get; set; } = new();
}

// Bảng TinNhan
public class ChatMessage
{
    public int Id { get; set; }
    public int ConversationId { get; set; }
    public int SenderId { get; set; }           // không ràng buộc khóa ngoại để xóa nhân viên vẫn giữ lịch sử
    public string SenderName { get; set; } = "";
    public bool IsFromStaff { get; set; }
    public string Content { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// Bảng ThongBao: báo cho khách khi đơn đổi trạng thái
public class Notification
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public string Title { get; set; } = "";
    public string Body { get; set; } = "";
    public int? OrderId { get; set; }
    public bool IsRead { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// Bảng MaGiamGia
public static class VoucherType
{
    public const string Percent = "Percent";    // giảm % tạm tính, có mức giảm tối đa
    public const string Amount = "Amount";      // giảm số tiền cố định
    public const string FreeShip = "FreeShip";  // miễn phí giao hàng
    public static readonly string[] All = { Percent, Amount, FreeShip };
}

public class Voucher
{
    public int Id { get; set; }
    public string Code { get; set; } = "";
    public string Description { get; set; } = "";
    public string Type { get; set; } = VoucherType.Amount;
    public decimal Value { get; set; }          // % hoặc số tiền
    public decimal MinOrder { get; set; }       // tạm tính tối thiểu
    public decimal MaxDiscount { get; set; }    // giảm tối đa (0 = không giới hạn), dùng cho Percent
    public int UsageLimit { get; set; }         // số lượt dùng tối đa (0 = không giới hạn)
    public int UsedCount { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public bool IsActive { get; set; } = true;
}

// Bảng YeuThich
public class Favorite
{
    public int UserId { get; set; }
    public int ProductId { get; set; }
    public Product? Product { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
