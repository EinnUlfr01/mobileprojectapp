using System.Net.Mail;
using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

[ApiController]
[Route("api/admin")]
// Quản trị sàn (Admin) và Chủ cửa hàng (StoreOwner) dùng chung các API này.
// Admin thấy toàn sàn; chủ quán chỉ thấy đơn, món, thống kê của quán mình.
[Authorize(Roles = Roles.AdminOrOwner)]
public class AdminController(AppDbContext db, IPasswordHasher<User> hasher) : ControllerBase
{
    // Giờ Việt Nam để tính "hôm nay", "tháng này"
    private static DateTime VnNow => DateTime.UtcNow.AddHours(7);

    private bool IsAdmin => User.IsInRole(Roles.Admin);

    // Quán của chủ quán đang đăng nhập (admin trả về null = mọi quán)
    private async Task<Store?> MyStore() =>
        IsAdmin ? null : await db.Stores.FirstOrDefaultAsync(s => s.OwnerId == User.UserId());

    // Giới hạn đơn theo quán: admin xem hết, chủ quán chỉ xem đơn quán mình (chưa có quán thì không thấy gì)
    private async Task<IQueryable<Order>> ScopedOrders()
    {
        if (IsAdmin) return db.Orders;
        var store = await MyStore();
        var storeId = store?.Id ?? -1;
        return db.Orders.Where(o => o.StoreId == storeId);
    }

    // AdminDashboardActivity
    [HttpGet("dashboard")]
    public async Task<DashboardDto> Dashboard()
    {
        var todayUtc = VnNow.Date.AddHours(-7);
        var monthUtc = new DateTime(VnNow.Year, VnNow.Month, 1).AddHours(-7);
        var orders = await ScopedOrders();
        var store = await MyStore();
        var products = IsAdmin ? db.Products : db.Products.Where(p => p.StoreId == (store != null ? store.Id : -1));
        return new DashboardDto(
            IsAdmin ? await db.Users.CountAsync(u => u.Role == Roles.User)
                    : await orders.Where(o => o.UserId != null).Select(o => o.UserId).Distinct().CountAsync(),
            await orders.CountAsync(o => o.CreatedAt >= todayUtc),
            await orders.Where(o => o.CreatedAt >= monthUtc && o.Status == OrderStatus.Done).SumAsync(o => (decimal?)o.Total) ?? 0,
            await products.CountAsync(),
            await orders.CountAsync(o => o.Status == OrderStatus.Pending),
            IsAdmin ? await db.Stores.CountAsync() : 0,
            IsAdmin ? await db.Users.CountAsync(u => u.Role == Roles.Shipper) : 0,
            store?.Id, store?.Name, store?.IsOpen ?? false);
    }

    // ---------- ManageUserActivity ----------
    [HttpGet("users")]
    [Authorize(Roles = Roles.Admin)] // chỉ Quản trị viên quản lý tài khoản
    public async Task<List<UserDto>> Users(string? q, string? role)
    {
        var query = db.Users.AsQueryable();
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(u => u.FullName.Contains(q) || u.Email.Contains(q) || u.Phone.Contains(q));
        if (!string.IsNullOrWhiteSpace(role)) query = query.Where(u => u.Role == role);
        var list = await query.OrderBy(u => u.Id).ToListAsync();
        var stores = await db.Stores.Where(s => s.OwnerId != null).ToListAsync();
        return list.Select(u => u.ToDto(stores.FirstOrDefault(s => s.OwnerId == u.Id))).ToList();
    }

    [HttpPost("users")]
    [Authorize(Roles = Roles.Admin)] // chỉ Quản trị viên quản lý tài khoản
    public async Task<ActionResult<UserDto>> CreateUser(AdminUserRequest r)
    {
        var err = await ValidateUser(r, null);
        if (err != null) return BadRequest(new MessageDto(err));
        if (string.IsNullOrEmpty(r.Password) || r.Password.Length < 6) return BadRequest(new MessageDto("Mật khẩu tối thiểu 6 ký tự"));
        var u = new User { FullName = r.FullName.Trim(), Email = r.Email.Trim().ToLower(), Phone = r.Phone, Role = r.Role, DefaultAddress = r.DefaultAddress,
            VehiclePlate = string.IsNullOrWhiteSpace(r.VehiclePlate) ? null : r.VehiclePlate.Trim().ToUpper() };
        u.PasswordHash = hasher.HashPassword(u, r.Password);
        db.Users.Add(u);
        await db.SaveChangesAsync();
        return u.ToDto();
    }

    [HttpPut("users/{id:int}")]
    [Authorize(Roles = Roles.Admin)] // chỉ Quản trị viên quản lý tài khoản
    public async Task<ActionResult<UserDto>> UpdateUser(int id, AdminUserRequest r)
    {
        var u = await db.Users.FindAsync(id);
        if (u == null) return NotFound(new MessageDto("Không tìm thấy user"));
        var err = await ValidateUser(r, id);
        if (err != null) return BadRequest(new MessageDto(err));
        if (u.Id == User.UserId() && r.Role != Roles.Admin) return BadRequest(new MessageDto("Không thể tự bỏ quyền admin của chính mình"));
        if (u.Role == Roles.StoreOwner && r.Role != Roles.StoreOwner && await db.Stores.AnyAsync(s => s.OwnerId == id))
            return BadRequest(new MessageDto("Tài khoản đang là chủ của một quán. Hãy đổi chủ quán trước khi đổi vai trò"));
        u.FullName = r.FullName.Trim();
        u.Email = r.Email.Trim().ToLower();
        u.Phone = r.Phone;
        u.Role = r.Role;
        u.DefaultAddress = r.DefaultAddress;
        u.VehiclePlate = string.IsNullOrWhiteSpace(r.VehiclePlate) ? null : r.VehiclePlate.Trim().ToUpper();
        if (!string.IsNullOrEmpty(r.Password))
        {
            if (r.Password.Length < 6) return BadRequest(new MessageDto("Mật khẩu tối thiểu 6 ký tự"));
            u.PasswordHash = hasher.HashPassword(u, r.Password);
        }
        await db.SaveChangesAsync();
        return u.ToDto();
    }

    [HttpDelete("users/{id:int}")]
    [Authorize(Roles = Roles.Admin)] // chỉ Quản trị viên quản lý tài khoản
    public async Task<ActionResult<MessageDto>> DeleteUser(int id)
    {
        if (id == User.UserId()) return BadRequest(new MessageDto("Không thể xóa chính mình"));
        var u = await db.Users.FindAsync(id);
        if (u == null) return NotFound(new MessageDto("Không tìm thấy user"));
        // Shipper đã giao đơn thì bỏ liên kết shipper khỏi đơn (khóa ngoại NoAction), đơn vẫn giữ lại
        await db.Orders.Where(o => o.ShipperId == id).ExecuteUpdateAsync(x => x.SetProperty(o => o.ShipperId, (int?)null));
        db.Users.Remove(u); // đơn cũ của user vẫn giữ lại (UserId = null), quán của chủ quán thành "chưa có chủ"
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }

    // Khóa / mở khóa tài khoản (shipper vi phạm, chủ quán ngừng hợp tác...)
    [HttpPut("users/{id:int}/lock")]
    [Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> Lock(int id, LockRequest r)
    {
        if (id == User.UserId()) return BadRequest(new MessageDto("Không thể tự khóa chính mình"));
        var u = await db.Users.FindAsync(id);
        if (u == null) return NotFound(new MessageDto("Không tìm thấy user"));
        u.IsLocked = r.IsLocked;
        await db.SaveChangesAsync();
        return new MessageDto(r.IsLocked ? $"Đã khóa {u.FullName}" : $"Đã mở khóa {u.FullName}");
    }

    // ---------- ManageShippersActivity ----------
    [HttpGet("shippers")]
    [Authorize(Roles = Roles.Admin)]
    public async Task<List<ShipperDto>> Shippers(string? q)
    {
        var todayUtc = VnNow.Date.AddHours(-7);
        var query = db.Users.Where(u => u.Role == Roles.Shipper);
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(u => u.FullName.Contains(q) || u.Phone.Contains(q) || (u.VehiclePlate != null && u.VehiclePlate.Contains(q)));
        var list = await query.OrderBy(u => u.Id).ToListAsync();
        var ids = list.Select(u => u.Id).ToList();
        var orders = await db.Orders.Where(o => o.ShipperId != null && ids.Contains(o.ShipperId.Value))
            .Select(o => new { ShipperId = o.ShipperId!.Value, o.Status, o.CreatedAt }).ToListAsync();
        return list.Select(u =>
        {
            var mine = orders.Where(o => o.ShipperId == u.Id).ToList();
            return new ShipperDto(u.Id, u.FullName, u.Email, u.Phone, u.VehiclePlate, u.IsLocked,
                mine.Count(o => o.Status is OrderStatus.Preparing or OrderStatus.Shipping),
                mine.Count(o => o.Status == OrderStatus.Done),
                mine.Count(o => o.Status == OrderStatus.Done && o.CreatedAt >= todayUtc));
        }).ToList();
    }

    private async Task<string?> ValidateUser(AdminUserRequest r, int? id)
    {
        if (string.IsNullOrWhiteSpace(r.FullName)) return "Vui lòng nhập họ tên";
        if (!MailAddress.TryCreate(r.Email?.Trim(), out _)) return "Email không đúng định dạng";
        if (!Mapping.IsValidPhone(r.Phone)) return "Số điện thoại phải đủ 10 số";
        if (!Roles.All.Contains(r.Role)) return "Vai trò không hợp lệ";
        var email = r.Email!.Trim().ToLower();
        if (await db.Users.AnyAsync(u => u.Email == email && u.Id != id)) return "Email đã tồn tại";
        if (await db.Users.AnyAsync(u => u.Phone == r.Phone && u.Id != id)) return "Số điện thoại đã tồn tại";
        return null;
    }

    // ---------- ManageOrderActivity: tab Danh sách đơn ----------
    // type: member | guest ; storeId: admin lọc theo quán
    [HttpGet("orders")]
    public async Task<List<OrderDto>> Orders(string? status, string? type, int? storeId)
    {
        var q = (await ScopedOrders()).WithDetails();
        if (storeId.HasValue && IsAdmin) q = q.Where(o => o.StoreId == storeId);
        if (!string.IsNullOrWhiteSpace(status)) q = q.Where(o => o.Status == status);
        if (type == "member") q = q.Where(o => o.UserId != null);
        if (type == "guest") q = q.Where(o => o.UserId == null);
        var list = await q.OrderByDescending(o => o.CreatedAt).ToListAsync();
        return list.Select(o => o.ToDto()).ToList();
    }

    // Admin: chuyển sang trạng thái bất kỳ.
    // Chủ quán: Nhận đơn (Đang xử lý -> Đang chuẩn bị) hoặc Hủy đơn khi chưa giao.
    [HttpPut("orders/{id:int}/status")]
    public async Task<ActionResult<MessageDto>> UpdateStatus(int id, UpdateStatusRequest r)
    {
        if (!OrderStatus.All.Contains(r.Status)) return BadRequest(new MessageDto("Trạng thái không hợp lệ"));
        var o = await (await ScopedOrders()).WithDetails().FirstOrDefaultAsync(x => x.Id == id);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (!IsAdmin)
        {
            var ok = (o.Status == OrderStatus.Pending && r.Status == OrderStatus.Preparing)
                  || (r.Status == OrderStatus.Cancelled && o.Status is OrderStatus.Pending or OrderStatus.Preparing && o.ShipperId == null);
            if (!ok) return BadRequest(new MessageDto(r.Status == OrderStatus.Cancelled
                ? "Shipper đã nhận đơn nên quán không tự hủy được, hãy liên hệ quản trị sàn"
                : "Quán chỉ nhận đơn hoặc hủy đơn; lấy món và giao hàng do shipper cập nhật"));
        }
        var me = await db.Users.FindAsync(User.UserId());
        var by = IsAdmin ? (me?.FullName ?? "Quản trị sàn") : (o.Store?.Name ?? me?.FullName ?? "Quán");
        var note = r.Note;
        if (r.Status == OrderStatus.Preparing && string.IsNullOrWhiteSpace(note)) note = "Quán đã nhận đơn, đang chuẩn bị món";
        if (r.Status == OrderStatus.Cancelled && string.IsNullOrWhiteSpace(note)) note = IsAdmin ? "Sàn hủy đơn" : "Quán hủy đơn";
        await OrderFlow.ChangeStatusAsync(db, o, r.Status, note, by);
        await db.SaveChangesAsync();
        return new MessageDto("Đã cập nhật trạng thái");
    }

    // Quản trị sàn gán (hoặc bỏ gán) shipper cho đơn
    [HttpPut("orders/{id:int}/shipper")]
    [Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> AssignShipper(int id, AssignShipperRequest r)
    {
        var o = await db.Orders.Include(x => x.Shipper).FirstOrDefaultAsync(x => x.Id == id);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.Status is OrderStatus.Done or OrderStatus.Cancelled) return BadRequest(new MessageDto("Đơn đã kết thúc"));
        var me = await db.Users.FindAsync(User.UserId());
        if (r.ShipperId == null)
        {
            o.ShipperId = null;
            db.OrderStatusLogs.Add(new OrderStatusLog { OrderId = o.Id, Status = o.Status, Note = "Bỏ gán shipper", ChangedBy = me?.FullName ?? "Quản trị sàn" });
            await db.SaveChangesAsync();
            return new MessageDto("Đã bỏ gán shipper");
        }
        var shipper = await db.Users.FindAsync(r.ShipperId);
        if (shipper == null || shipper.Role != Roles.Shipper) return BadRequest(new MessageDto("Shipper không tồn tại"));
        if (shipper.IsLocked) return BadRequest(new MessageDto("Shipper đang bị khóa"));
        OrderFlow.AssignShipper(db, o, shipper, me?.FullName ?? "Quản trị sàn");
        await db.SaveChangesAsync();
        return new MessageDto($"Đã giao đơn cho {shipper.FullName}");
    }

    // Admin kiểm tra tài khoản ngân hàng thấy tiền về rồi thì bấm xác nhận
    [HttpPut("orders/{id:int}/confirm-paid")]
    public async Task<ActionResult<MessageDto>> ConfirmPaid(int id)
    {
        var o = await (await ScopedOrders()).FirstOrDefaultAsync(x => x.Id == id);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.IsPaid) return BadRequest(new MessageDto("Đơn đã được thanh toán"));
        var staff = await db.Users.FindAsync(User.UserId());
        BankQrService.MarkPaid(db, o, $"Đã nhận tiền {o.PaymentMethod.ToLower()} (xác nhận bởi {staff?.FullName ?? "cửa hàng"})");
        await db.SaveChangesAsync();
        return new MessageDto("Đã xác nhận nhận tiền");
    }

    // App quản trị / chủ quán hỏi định kỳ để bắn thông báo khi có đơn mới / tin nhắn mới
    [HttpGet("alerts")]
    public async Task<StaffAlertsDto> Alerts()
    {
        var orders = await ScopedOrders();
        return new StaffAlertsDto(
            await orders.CountAsync(o => o.Status == OrderStatus.Pending),
            await orders.MaxAsync(o => (int?)o.Id) ?? 0,
            IsAdmin ? await db.Conversations.SumAsync(c => (int?)c.UnreadByStaff) ?? 0 : 0);
    }

    // ---------- ManageOrderActivity: tab Thống kê ----------
    // groupBy: day | month | user | category | store ; from/to dạng yyyy-MM-dd (giờ VN). Chỉ tính đơn Hoàn thành.
    // Chủ quán chỉ thấy số liệu quán mình.
    [HttpGet("stats")]
    public async Task<StatsDto> Stats(string groupBy = "day", DateTime? from = null, DateTime? to = null)
    {
        var fromVn = (from ?? VnNow.Date.AddDays(-29)).Date;
        var toVn = (to ?? VnNow.Date).Date.AddDays(1);
        var orders = await (await ScopedOrders()).Include(o => o.Items).ThenInclude(i => i.Product!).ThenInclude(p => p.Category)
            .Include(o => o.User).Include(o => o.Store)
            .Where(o => o.Status == OrderStatus.Done && o.CreatedAt >= fromVn.AddHours(-7) && o.CreatedAt < toVn.AddHours(-7))
            .ToListAsync();

        List<StatRowDto> rows;
        if (groupBy == "category")
        {
            rows = orders.SelectMany(o => o.Items.Select(i => (o.Id, i)))
                .GroupBy(x => x.i.Product?.Category?.Name ?? "Khác")
                .Select(g => new StatRowDto(g.Key, g.Select(x => x.Id).Distinct().Count(), g.Sum(x => x.i.Quantity), g.Sum(x => x.i.LineTotal)))
                .OrderByDescending(r => r.Revenue).ToList();
        }
        else
        {
            // Khóa sắp xếp (yyyyMMdd / yyyyMM) và nhãn hiển thị
            Func<Order, (string Sort, string Label)> key = groupBy switch
            {
                "month" => o => (o.CreatedAt.AddHours(7).ToString("yyyyMM"), o.CreatedAt.AddHours(7).ToString("MM/yyyy")),
                "user" => o => ("", o.User != null ? o.User.FullName : "Khách vãng lai"),
                "store" => o => ("", o.Store != null ? o.Store.Name : "Không rõ quán"),
                _ => o => (o.CreatedAt.AddHours(7).ToString("yyyyMMdd"), o.CreatedAt.AddHours(7).ToString("dd/MM"))
            };
            var grouped = orders.GroupBy(key).Select(g => new
            {
                g.Key.Sort,
                Row = new StatRowDto(g.Key.Label, g.Count(), g.Sum(o => o.Items.Sum(i => i.Quantity)), g.Sum(o => o.Total))
            });
            rows = groupBy is "user" or "store"
                ? grouped.Select(x => x.Row).OrderByDescending(r => r.Revenue).ToList()
                : grouped.OrderBy(x => x.Sort).Select(x => x.Row).ToList();
        }

        var top = orders.SelectMany(o => o.Items)
            .GroupBy(i => i.ProductName)
            .Select(g => new StatRowDto(g.Key, 0, g.Sum(i => i.Quantity), g.Sum(i => i.LineTotal)))
            .OrderByDescending(r => r.Quantity).Take(5).ToList();

        return new StatsDto(rows, top, orders.Sum(o => o.Total), orders.Count);
    }
}
