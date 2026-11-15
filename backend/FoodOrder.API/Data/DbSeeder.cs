using FoodOrder.API.Common;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Data;

public static class DbSeeder
{
    public static async Task SeedAsync(IServiceProvider sp)
    {
        var db = sp.GetRequiredService<AppDbContext>();
        var hasher = sp.GetRequiredService<IPasswordHasher<User>>();

        // Tự tạo database + bảng nếu chưa có (không cần chạy migration)
        await db.Database.EnsureCreatedAsync();
        await SchemaUpgrade.RunAsync(db);     // thêm bảng/cột mới cho database cũ
        await SeedVouchersAsync(db);
        if (await db.Users.AnyAsync())
        {
            await EnsureStoresAsync(db);
            await EnsureAccountsAsync(db, hasher);
            await FillImagesAsync(sp, db);
            return;
        }

        var admin = new User { FullName = "Quản trị viên", Email = "admin@gmail.com", Phone = "0900000000", Role = Roles.Admin, DefaultAddress = "HUFLIT, Q.10, TP.HCM" };
        admin.PasswordHash = hasher.HashPassword(admin, "Admin@123");
        var user = new User { FullName = "Nguyễn Văn A", Email = "user@gmail.com", Phone = "0911111111", Role = Roles.User, DefaultAddress = "12 Nguyễn Trãi, Q.5, TP.HCM" };
        user.PasswordHash = hasher.HashPassword(user, "123456");
        db.Users.AddRange(admin, user);
        await db.SaveChangesAsync();
        var stores = await EnsureStoresAsync(db);

        var data = new (string Cat, (string Name, string Desc, bool IsNew, decimal Price)[] Items)[]
        {
            ("Cơm", new[] {
                ("Cơm tấm sườn bì chả", "Sườn nướng than, bì, chả trứng, mỡ hành", false, 45000m),
                ("Cơm gà xối mỡ", "Đùi gà chiên giòn, cơm chiên nước gà", false, 42000m),
                ("Cơm chiên dương châu", "Cơm chiên lạp xưởng, tôm, trứng, đậu", true, 40000m) }),
            ("Bún/Phở", new[] {
                ("Phở bò tái nạm", "Nước dùng hầm xương 12 tiếng", false, 50000m),
                ("Bún bò Huế", "Giò heo, chả cua, sả ớt", false, 48000m),
                ("Bún thịt nướng", "Thịt nướng, chả giò, nước mắm chua ngọt", true, 40000m) }),
            ("Trà sữa", new[] {
                ("Trà sữa trân châu đường đen", "Trân châu nấu đường đen, sữa tươi", false, 35000m),
                ("Trà đào cam sả", "Đào miếng, cam tươi, sả thơm", true, 32000m),
                ("Trà sữa matcha", "Matcha Nhật, kem cheese", true, 39000m) }),
            ("Ăn nhanh", new[] {
                ("Gà rán sốt cay", "2 miếng gà giòn sốt Hàn Quốc", true, 55000m),
                ("Hamburger bò phô mai", "Bò nướng, phô mai, rau tươi", false, 49000m),
                ("Khoai tây chiên", "Khoai giòn kèm tương cà", false, 25000m) }),
            ("Tráng miệng", new[] {
                ("Chè khúc bạch", "Khúc bạch, nhãn, hạnh nhân", false, 30000m),
                ("Bánh flan caramel", "Flan mềm, caramel đắng nhẹ", true, 18000m) })
        };

        var products = new List<Product>();
        foreach (var (catName, items) in data)
        {
            var cat = new Category { Name = catName };
            db.Categories.Add(cat);
            var store = stores.First(x => x.Name == StoreOfCategory(catName));
            foreach (var (name, desc, isNew, price) in items)
            {
                var p = new Product
                {
                    Category = cat, Store = store, Name = name, Description = desc, IsNew = isNew,
                    Sizes = new List<ProductSize>
                    {
                        new() { Size = "S", Price = price - 5000 },
                        new() { Size = "M", Price = price },
                        new() { Size = "L", Price = price + 10000 }
                    }
                };
                products.Add(p);
                db.Products.Add(p);
            }
        }
        await db.SaveChangesAsync();
        await EnsureAccountsAsync(db, hasher);
        var shipper = await db.Users.FirstAsync(u => u.Role == Roles.Shipper);

        // Vài đơn mẫu để màn thống kê có số liệu (mỗi đơn chỉ gồm món của 1 quán)
        var rnd = new Random(10);
        for (var i = 1; i <= 12; i++)
        {
            var guest = i % 4 == 0;
            var o = new Order
            {
                Code = $"DH{i:0000}",
                UserId = guest ? null : user.Id,
                ReceiverName = guest ? "Khách vãng lai " + i : user.FullName,
                Phone = guest ? "09" + rnd.Next(10000000, 99999999) : user.Phone,
                Address = guest ? "Q.3, TP.HCM" : user.DefaultAddress!,
                PaymentMethod = i % 3 == 0 ? "VNPay" : "Tiền mặt",
                Status = i <= 8 ? OrderStatus.Done : i <= 10 ? OrderStatus.Shipping : i == 11 ? OrderStatus.Preparing : OrderStatus.Pending,
                CreatedAt = DateTime.UtcNow.AddDays(-rnd.Next(0, 60)),
                ShippingFee = 15000
            };
            var orderStore = stores[rnd.Next(stores.Count)];
            o.StoreId = orderStore.Id;
            if (o.Status is OrderStatus.Shipping or OrderStatus.Done) o.ShipperId = shipper.Id;
            foreach (var p in products.Where(x => x.StoreId == orderStore.Id).OrderBy(_ => rnd.Next()).Take(rnd.Next(1, 4)))
            {
                var size = p.Sizes[1];
                var qty = rnd.Next(1, 3);
                o.Items.Add(new OrderItem { ProductId = p.Id, ProductName = p.Name, Size = size.Size, Quantity = qty, UnitPrice = size.Price, LineTotal = size.Price * qty });
                if (o.Status == OrderStatus.Done) p.SoldCount += qty;
            }
            o.Subtotal = o.Items.Sum(x => x.LineTotal);
            o.Total = o.Subtotal + o.ShippingFee;
            o.IsPaid = o.PaymentMethod != "Tiền mặt" || o.Status == OrderStatus.Done;
            // Lịch sử trạng thái mẫu cho dòng thời gian theo dõi đơn
            o.History.Add(new OrderStatusLog { Status = OrderStatus.Pending, Note = "Đặt hàng thành công", ChangedBy = o.ReceiverName, CreatedAt = o.CreatedAt });
            if (o.Status is OrderStatus.Preparing or OrderStatus.Shipping or OrderStatus.Done)
                o.History.Add(new OrderStatusLog { Status = OrderStatus.Preparing, Note = "Quán đã nhận đơn", ChangedBy = orderStore.Name, CreatedAt = o.CreatedAt.AddMinutes(3) });
            if (o.Status is OrderStatus.Shipping or OrderStatus.Done)
            {
                o.History.Add(new OrderStatusLog { Status = OrderStatus.Shipping, Note = "Shipper đã lấy món", ChangedBy = shipper.FullName, CreatedAt = o.CreatedAt.AddMinutes(15) });
            }
            if (o.Status == OrderStatus.Done)
                o.History.Add(new OrderStatusLog { Status = OrderStatus.Done, Note = "Đã giao cho khách", ChangedBy = shipper.FullName, CreatedAt = o.CreatedAt.AddMinutes(40) });
            db.Orders.Add(o);
        }
        await db.SaveChangesAsync();

        // Một vài đánh giá mẫu
        var firstDone = await db.Orders.Include(o => o.Items).Where(o => o.UserId == user.Id && o.Status == OrderStatus.Done).FirstAsync();
        foreach (var it in firstDone.Items)
        {
            db.Reviews.Add(new Review { UserId = user.Id, ProductId = it.ProductId, OrderId = firstDone.Id, Stars = 5, Comment = "Ngon, giao nhanh!" });
            var p = products.First(x => x.Id == it.ProductId);
            p.AvgRating = 5; p.RatingCount = 1;
        }
        await db.SaveChangesAsync();
        await FillImagesAsync(sp, db);
    }

    // Mã giảm giá mẫu
    private static async Task SeedVouchersAsync(AppDbContext db)
    {
        if (await db.Vouchers.AnyAsync()) return;
        db.Vouchers.AddRange(
            new Voucher { Code = "GIAM20", Description = "Giảm 20% tối đa 30.000đ cho đơn từ 100.000đ", Type = VoucherType.Percent, Value = 20, MinOrder = 100000, MaxDiscount = 30000 },
            new Voucher { Code = "GIAM10K", Description = "Giảm 10.000đ cho đơn từ 50.000đ", Type = VoucherType.Amount, Value = 10000, MinOrder = 50000 },
            new Voucher { Code = "FREESHIP", Description = "Miễn phí giao hàng cho mọi đơn", Type = VoucherType.FreeShip, Value = 0, UsageLimit = 100 });
        await db.SaveChangesAsync();
    }

    // ---------- Quán mẫu của sàn ----------
    // (Tên, Địa chỉ, SĐT, Mô tả, Các danh mục món của quán)
    private static readonly (string Name, string Address, string Phone, string Desc, string[] Categories)[] SampleStores =
    {
        ("Cơm Tấm Sài Gòn", "45 Nguyễn Trãi, Q.5, TP.HCM", "0281234501", "Cơm tấm sườn nướng than hồng, mở cửa 6h - 21h", new[] { "Cơm" }),
        ("Phở Bò Hà Nội", "120 Sư Vạn Hạnh, Q.10, TP.HCM", "0281234502", "Phở bò, bún bò nấu theo vị Bắc", new[] { "Bún/Phở" }),
        ("Trà Sữa Nhà Làm", "88 Ba Tháng Hai, Q.10, TP.HCM", "0281234503", "Trà sữa, trà trái cây và chè tráng miệng", new[] { "Trà sữa", "Tráng miệng" }),
        ("Gà Rán Chicken Plus", "15 Lý Thường Kiệt, Q.10, TP.HCM", "0281234504", "Gà rán, burger, khoai tây chiên", new[] { "Ăn nhanh" })
    };

    public static string StoreOfCategory(string category) =>
        SampleStores.FirstOrDefault(s => s.Categories.Contains(category)).Name ?? SampleStores[0].Name;

    // Dùng trong SchemaUpgrade để chuyển database cũ (chỉ 1 cửa hàng) sang nhiều quán
    public static readonly string StoresSql = string.Join(",\n", SampleStores.Select(s =>
        $"(N'{s.Name}', N'{s.Address}', '{s.Phone}', N'{s.Desc}', NULL, NULL, 1, 1, SYSUTCDATETIME())"));

    public static readonly string CategoryStoreCaseSql = string.Join(" ", SampleStores.SelectMany(s =>
        s.Categories.Select(c => $"WHEN N''{c}'' THEN N''{s.Name}''"))) + $" ELSE N''{SampleStores[0].Name}''";

    private static async Task<List<Store>> EnsureStoresAsync(AppDbContext db)
    {
        if (!await db.Stores.AnyAsync())
        {
            foreach (var s in SampleStores)
                db.Stores.Add(new Store { Name = s.Name, Address = s.Address, Phone = s.Phone, Description = s.Desc });
            await db.SaveChangesAsync();
        }
        return await db.Stores.OrderBy(s => s.Id).ToListAsync();
    }

    // Tài khoản mẫu: mỗi quán 1 chủ quán (quan1..quan4@gmail.com) và 2 shipper (thêm vào cả database đã có sẵn)
    private static async Task EnsureAccountsAsync(AppDbContext db, IPasswordHasher<User> hasher)
    {
        var stores = await db.Stores.OrderBy(s => s.Id).ToListAsync();
        for (var i = 0; i < stores.Count && i < SampleStores.Length; i++)
        {
            var store = stores[i];
            if (store.OwnerId != null) continue;
            var email = $"quan{i + 1}@gmail.com";
            var owner = await db.Users.FirstOrDefaultAsync(u => u.Email == email);
            if (owner == null)
            {
                owner = new User { FullName = "Chủ quán " + store.Name, Email = email, Phone = $"09000000{11 + i}", Role = Roles.StoreOwner };
                owner.PasswordHash = hasher.HashPassword(owner, "Quan@123");
                db.Users.Add(owner);
                await db.SaveChangesAsync();
            }
            store.OwnerId = owner.Id;
        }

        var shippers = new[]
        {
            (Name: "Trần Văn Bình", Email: "shipper1@gmail.com", Phone: "0900000021", Plate: "59X1-123.45"),
            (Name: "Lê Thị Cúc", Email: "shipper2@gmail.com", Phone: "0900000022", Plate: "59X2-678.90")
        };
        foreach (var s in shippers)
        {
            if (await db.Users.AnyAsync(u => u.Email == s.Email || u.Phone == s.Phone)) continue;
            var u = new User { FullName = s.Name, Email = s.Email, Phone = s.Phone, Role = Roles.Shipper, VehiclePlate = s.Plate };
            u.PasswordHash = hasher.HashPassword(u, "Shipper@123");
            db.Users.Add(u);
        }
        await db.SaveChangesAsync();
    }

    // Gắn ảnh trong wwwroot/images cho món/danh mục chưa có ảnh.
    // Tên file = tên món bỏ dấu, nối bằng gạch ngang. Ví dụ "Phở bò tái nạm" -> pho-bo-tai-nam.jpg
    // Chép ảnh vào thư mục rồi chạy lại API là app có ảnh.
    public static async Task FillImagesAsync(IServiceProvider sp, AppDbContext db)
    {
        var env = sp.GetRequiredService<IWebHostEnvironment>();
        var dir = Path.Combine(env.ContentRootPath, "wwwroot", "images");
        if (!Directory.Exists(dir)) return;

        string? Find(string name)
        {
            var slug = Slug(name);
            foreach (var ext in new[] { ".jpg", ".jpeg", ".png", ".webp" })
                if (File.Exists(Path.Combine(dir, slug + ext))) return "images/" + slug + ext;
            return null;
        }

        foreach (var p in await db.Products.Where(x => x.ImageUrl == null || x.ImageUrl == "").ToListAsync())
            p.ImageUrl = Find(p.Name);
        foreach (var c in await db.Categories.Where(x => x.ImageUrl == null || x.ImageUrl == "").ToListAsync())
            c.ImageUrl = Find(c.Name);
        await db.SaveChangesAsync();
    }

    public static string Slug(string s)
    {
        var normalized = s.ToLowerInvariant().Replace('đ', 'd').Normalize(System.Text.NormalizationForm.FormD);
        var sb = new System.Text.StringBuilder();
        foreach (var ch in normalized)
        {
            if (System.Globalization.CharUnicodeInfo.GetUnicodeCategory(ch) == System.Globalization.UnicodeCategory.NonSpacingMark) continue;
            sb.Append(char.IsLetterOrDigit(ch) ? ch : '-');
        }
        return System.Text.RegularExpressions.Regex.Replace(sb.ToString(), "-+", "-").Trim('-');
    }
}
