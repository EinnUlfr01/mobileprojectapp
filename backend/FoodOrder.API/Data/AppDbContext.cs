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
    }
}
