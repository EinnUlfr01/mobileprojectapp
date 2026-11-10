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
// Mọi vai trò quản trị vào được; từng chức năng giới hạn thêm bên dưới
[Authorize(Roles = Roles.AllStaff)]
public class AdminController(AppDbContext db, IPasswordHasher<User> hasher) : ControllerBase
{
    // Giờ Việt Nam để tính "hôm nay", "tháng này"
    private static DateTime VnNow => DateTime.UtcNow.AddHours(7);

    // AdminDashboardActivity
    [HttpGet("dashboard")]
    public async Task<DashboardDto> Dashboard()
    {
        var todayUtc = VnNow.Date.AddHours(-7);
        var monthUtc = new DateTime(VnNow.Year, VnNow.Month, 1).AddHours(-7);
        return new DashboardDto(
            await db.Users.CountAsync(u => u.Role == Roles.User),
            await db.Orders.CountAsync(o => o.CreatedAt >= todayUtc),
            await db.Orders.Where(o => o.CreatedAt >= monthUtc && o.Status == OrderStatus.Done).SumAsync(o => (decimal?)o.Total) ?? 0,
            await db.Products.CountAsync(),
            await db.Orders.CountAsync(o => o.Status == OrderStatus.Pending));
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
        return list.Select(u => u.ToDto()).ToList();
    }

    [HttpPost("users")]
    [Authorize(Roles = Roles.Admin)] // chỉ Quản trị viên quản lý tài khoản
    public async Task<ActionResult<UserDto>> CreateUser(AdminUserRequest r)
    {
        var err = await ValidateUser(r, null);
        if (err != null) return BadRequest(new MessageDto(err));
        if (string.IsNullOrEmpty(r.Password) || r.Password.Length < 6) return BadRequest(new MessageDto("Mật khẩu tối thiểu 6 ký tự"));
        var u = new User { FullName = r.FullName.Trim(), Email = r.Email.Trim().ToLower(), Phone = r.Phone, Role = r.Role, DefaultAddress = r.DefaultAddress };
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
        u.FullName = r.FullName.Trim();
        u.Email = r.Email.Trim().ToLower();
        u.Phone = r.Phone;
        u.Role = r.Role;
        u.DefaultAddress = r.DefaultAddress;
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
        db.Users.Remove(u); // đơn cũ của user vẫn giữ lại (UserId = null)
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
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
    // type: member | guest
    [HttpGet("orders")]
    [Authorize(Roles = Roles.OrderManagers)]
    public async Task<List<OrderDto>> Orders(string? status, string? type)
    {
        var q = db.Orders.Include(o => o.Items).AsQueryable();
        if (!string.IsNullOrWhiteSpace(status)) q = q.Where(o => o.Status == status);
        if (type == "member") q = q.Where(o => o.UserId != null);
        if (type == "guest") q = q.Where(o => o.UserId == null);
        var list = await q.OrderByDescending(o => o.CreatedAt).ToListAsync();
        return list.Select(o => o.ToDto()).ToList();
    }

    [HttpPut("orders/{id:int}/status")]
    [Authorize(Roles = Roles.OrderManagers)]
    public async Task<ActionResult<MessageDto>> UpdateStatus(int id, UpdateStatusRequest r)
    {
        if (!OrderStatus.All.Contains(r.Status)) return BadRequest(new MessageDto("Trạng thái không hợp lệ"));
        var o = await db.Orders.Include(x => x.Items).FirstOrDefaultAsync(x => x.Id == id);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));

        // Cộng/trừ số lượng đã bán khi đơn vào/ra trạng thái Hoàn thành
        var wasDone = o.Status == OrderStatus.Done;
        var isDone = r.Status == OrderStatus.Done;
        if (wasDone != isDone)
        {
            var sign = isDone ? 1 : -1;
            foreach (var it in o.Items)
            {
                var p = await db.Products.FindAsync(it.ProductId);
                if (p != null) p.SoldCount = Math.Max(0, p.SoldCount + sign * it.Quantity);
            }
        }
        var changed = o.Status != r.Status;
        o.Status = r.Status;
        if (isDone) o.IsPaid = true;
        // Báo cho khách thành viên (khách vãng lai không có tài khoản nên quán gọi điện)
        if (changed && o.UserId != null)
        {
            var body = r.Status switch
            {
                OrderStatus.Shipping => "Shipper đang trên đường giao món tới bạn.",
                OrderStatus.Done => "Chúc bạn ngon miệng! Hãy đánh giá món để cửa hàng phục vụ tốt hơn.",
                OrderStatus.Cancelled => "Đơn đã bị hủy. Liên hệ cửa hàng qua mục Chat nếu cần hỗ trợ.",
                _ => "Cửa hàng đang chuẩn bị món của bạn."
            };
            NotificationsController.Add(db, o.UserId.Value, $"Đơn #{o.Code}: {r.Status}", body, o.Id);
        }
        await db.SaveChangesAsync();
        return new MessageDto("Đã cập nhật trạng thái");
    }

    // App nhân viên hỏi định kỳ để bắn thông báo khi có đơn mới / tin nhắn mới
    [HttpGet("alerts")]
    public async Task<StaffAlertsDto> Alerts() => new(
        await db.Orders.CountAsync(o => o.Status == OrderStatus.Pending),
        await db.Orders.MaxAsync(o => (int?)o.Id) ?? 0,
        await db.Conversations.SumAsync(c => (int?)c.UnreadByStaff) ?? 0);

    // ---------- ManageOrderActivity: tab Thống kê ----------
    // groupBy: day | month | user | category ; from/to dạng yyyy-MM-dd (giờ VN). Chỉ tính đơn Hoàn thành.
    [HttpGet("stats")]
    [Authorize(Roles = Roles.OrderManagers)]
    public async Task<StatsDto> Stats(string groupBy = "day", DateTime? from = null, DateTime? to = null)
    {
        var fromVn = (from ?? VnNow.Date.AddDays(-29)).Date;
        var toVn = (to ?? VnNow.Date).Date.AddDays(1);
        var orders = await db.Orders.Include(o => o.Items).ThenInclude(i => i.Product!).ThenInclude(p => p.Category)
            .Include(o => o.User)
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
                _ => o => (o.CreatedAt.AddHours(7).ToString("yyyyMMdd"), o.CreatedAt.AddHours(7).ToString("dd/MM"))
            };
            var grouped = orders.GroupBy(key).Select(g => new
            {
                g.Key.Sort,
                Row = new StatRowDto(g.Key.Label, g.Count(), g.Sum(o => o.Items.Sum(i => i.Quantity)), g.Sum(o => o.Total))
            });
            rows = groupBy == "user"
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
