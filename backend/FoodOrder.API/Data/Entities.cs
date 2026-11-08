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

public static class Roles
{
    public const string User = "User";
    public const string Admin = "Admin";
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
