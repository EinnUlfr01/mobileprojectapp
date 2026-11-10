using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Data;

public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<Category> Categories => Set<Category>();
    public DbSet<Product> Products => Set<Product>();
    public DbSet<ProductSize> ProductSizes => Set<ProductSize>();
    public DbSet<CartItem> CartItems => Set<CartItem>();
    public DbSet<Order> Orders => Set<Order>();
    public DbSet<OrderItem> OrderItems => Set<OrderItem>();
    public DbSet<Review> Reviews => Set<Review>();
    public DbSet<Conversation> Conversations => Set<Conversation>();
    public DbSet<ChatMessage> ChatMessages => Set<ChatMessage>();
    public DbSet<Notification> Notifications => Set<Notification>();
    public DbSet<Voucher> Vouchers => Set<Voucher>();
    public DbSet<Favorite> Favorites => Set<Favorite>();
    public DbSet<OrderStatusLog> OrderStatusLogs => Set<OrderStatusLog>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>().ToTable("NguoiDung");
        b.Entity<User>().HasIndex(x => x.Email).IsUnique();
        b.Entity<User>().Property(x => x.Email).HasMaxLength(200);
        b.Entity<User>().Property(x => x.Phone).HasMaxLength(15);
        b.Entity<User>().Property(x => x.Role).HasMaxLength(20);

        b.Entity<Category>().ToTable("DanhMuc");

        b.Entity<Product>().ToTable("SanPham");
        b.Entity<Product>().HasOne(x => x.Category).WithMany(c => c.Products)
            .HasForeignKey(x => x.CategoryId).OnDelete(DeleteBehavior.Restrict);

        b.Entity<ProductSize>().ToTable("SanPhamSize");
        b.Entity<ProductSize>().Property(x => x.Price).HasPrecision(18, 0);
        b.Entity<Product>().HasMany(x => x.Sizes).WithOne()
            .HasForeignKey(x => x.ProductId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<CartItem>().ToTable("GioHang");
        b.Entity<CartItem>().HasOne<User>().WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<CartItem>().HasOne(x => x.Product).WithMany().HasForeignKey(x => x.ProductId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<Order>().ToTable("DonHang");
        b.Entity<Order>().HasIndex(x => x.Code).IsUnique();
        b.Entity<Order>().Property(x => x.Code).HasMaxLength(30);
        b.Entity<Order>().HasOne(x => x.User).WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.SetNull);
        foreach (var p in new[] { nameof(Order.Subtotal), nameof(Order.ShippingFee), nameof(Order.Total) })
            b.Entity<Order>().Property(p).HasPrecision(18, 0);

        b.Entity<OrderItem>().ToTable("ChiTietDonHang");
        b.Entity<OrderItem>().Property(x => x.UnitPrice).HasPrecision(18, 0);
        b.Entity<OrderItem>().Property(x => x.LineTotal).HasPrecision(18, 0);
        b.Entity<Order>().HasMany(x => x.Items).WithOne().HasForeignKey(x => x.OrderId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<OrderItem>().HasOne(x => x.Product).WithMany().HasForeignKey(x => x.ProductId).OnDelete(DeleteBehavior.Restrict);

        b.Entity<Review>().ToTable("DanhGia");
        b.Entity<Review>().HasOne(x => x.User).WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<Review>().HasOne<Product>().WithMany().HasForeignKey(x => x.ProductId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<Review>().HasOne<Order>().WithMany().HasForeignKey(x => x.OrderId).OnDelete(DeleteBehavior.NoAction);
        b.Entity<Review>().HasIndex(x => new { x.UserId, x.ProductId, x.OrderId }).IsUnique();

        // ----- Nâng cao (database cũ được tạo thêm bằng SchemaUpgrade.cs) -----
        b.Entity<Order>().Property(x => x.VoucherCode).HasMaxLength(30);
        b.Entity<Order>().Property(x => x.Discount).HasPrecision(18, 0);
        b.Entity<Order>().Property(x => x.CancelReason).HasMaxLength(300);

        b.Entity<OrderStatusLog>().ToTable("LichSuDonHang");
        b.Entity<OrderStatusLog>().Property(x => x.Status).HasMaxLength(30);
        b.Entity<OrderStatusLog>().Property(x => x.Note).HasMaxLength(300);
        b.Entity<OrderStatusLog>().Property(x => x.ChangedBy).HasMaxLength(200);
        b.Entity<Order>().HasMany(x => x.History).WithOne().HasForeignKey(x => x.OrderId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<Conversation>().ToTable("HoiThoai");
        b.Entity<Conversation>().HasIndex(x => x.CustomerId).IsUnique();
        b.Entity<Conversation>().Property(x => x.LastMessage).HasMaxLength(1000);
        b.Entity<Conversation>().HasOne(x => x.Customer).WithMany().HasForeignKey(x => x.CustomerId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<ChatMessage>().ToTable("TinNhan");
        b.Entity<ChatMessage>().Property(x => x.Content).HasMaxLength(1000);
        b.Entity<ChatMessage>().Property(x => x.SenderName).HasMaxLength(200);
        b.Entity<Conversation>().HasMany(x => x.Messages).WithOne().HasForeignKey(x => x.ConversationId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<ChatMessage>().HasIndex(x => new { x.ConversationId, x.Id });

        b.Entity<Notification>().ToTable("ThongBao");
        b.Entity<Notification>().Property(x => x.Title).HasMaxLength(200);
        b.Entity<Notification>().Property(x => x.Body).HasMaxLength(500);
        b.Entity<Notification>().HasOne<User>().WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<Notification>().HasIndex(x => new { x.UserId, x.Id });

        b.Entity<Voucher>().ToTable("MaGiamGia");
        b.Entity<Voucher>().HasIndex(x => x.Code).IsUnique();
        b.Entity<Voucher>().Property(x => x.Code).HasMaxLength(30);
        b.Entity<Voucher>().Property(x => x.Description).HasMaxLength(300);
        b.Entity<Voucher>().Property(x => x.Type).HasMaxLength(20);
        foreach (var p in new[] { nameof(Voucher.Value), nameof(Voucher.MinOrder), nameof(Voucher.MaxDiscount) })
            b.Entity<Voucher>().Property(p).HasPrecision(18, 0);

        b.Entity<Favorite>().ToTable("YeuThich");
        b.Entity<Favorite>().HasKey(x => new { x.UserId, x.ProductId });
        b.Entity<Favorite>().HasOne<User>().WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<Favorite>().HasOne(x => x.Product).WithMany().HasForeignKey(x => x.ProductId).OnDelete(DeleteBehavior.Cascade);
    }
}
