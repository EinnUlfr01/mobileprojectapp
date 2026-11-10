using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Thông báo cho khách hàng (đơn đổi trạng thái, thanh toán...)
[ApiController]
[Route("api/notifications")]
[Authorize]
public class NotificationsController(AppDbContext db) : ControllerBase
{
    // afterId > 0: chỉ lấy thông báo mới hơn (app dùng để bắn thông báo lên điện thoại)
    [HttpGet]
    public async Task<List<NotificationDto>> Get(int afterId = 0, int take = 50)
    {
        var uid = User.UserId();
        return await db.Notifications.Where(n => n.UserId == uid && n.Id > afterId)
            .OrderByDescending(n => n.Id).Take(Math.Clamp(take, 1, 100))
            .Select(n => new NotificationDto(n.Id, n.Title, n.Body, n.OrderId, n.IsRead, n.CreatedAt))
            .ToListAsync();
    }

    [HttpGet("unread")]
    public async Task<CountDto> Unread()
    {
        var uid = User.UserId();
        return new CountDto(await db.Notifications.CountAsync(n => n.UserId == uid && !n.IsRead));
    }

    [HttpPut("read-all")]
    public async Task<MessageDto> ReadAll()
    {
        var uid = User.UserId();
        var list = await db.Notifications.Where(n => n.UserId == uid && !n.IsRead).ToListAsync();
        list.ForEach(n => n.IsRead = true);
        await db.SaveChangesAsync();
        return new MessageDto("Đã đánh dấu đã đọc");
    }

    // Dùng chung: tạo thông báo cho 1 user
    public static void Add(AppDbContext db, int userId, string title, string body, int? orderId) =>
        db.Notifications.Add(new Notification { UserId = userId, Title = title, Body = body, OrderId = orderId });
}
