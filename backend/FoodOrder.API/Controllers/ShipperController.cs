using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// ShipperActivity: shipper xem đơn chờ nhận, nhận đơn, báo đã lấy món, báo giao xong
// Luồng: quán nhận đơn (Đang chuẩn bị) -> shipper nhận đơn -> lấy món (Đang giao) -> giao xong (Hoàn thành)
[ApiController]
[Route("api/shipper")]
[Authorize(Roles = Roles.Shipper)]
public class ShipperController(AppDbContext db) : ControllerBase
{
    private const int MaxActiveOrders = 3; // 1 shipper giao tối đa 3 đơn cùng lúc

    private static DateTime TodayUtc => DateTime.UtcNow.AddHours(7).Date.AddHours(-7);

    private IQueryable<Order> Available() =>
        db.Orders.Where(o => o.Status == OrderStatus.Preparing && o.ShipperId == null && o.Store!.IsActive);

    // Số liệu đầu màn hình: đơn đang chờ, đơn mình đang giao, đã giao hôm nay, thu nhập (phí ship) hôm nay
    [HttpGet("summary")]
    public async Task<ShipperSummaryDto> Summary()
    {
        var uid = User.UserId();
        var doneToday = db.Orders.Where(o => o.ShipperId == uid && o.Status == OrderStatus.Done && o.CreatedAt >= TodayUtc);
        return new ShipperSummaryDto(
            await Available().CountAsync(),
            await db.Orders.CountAsync(o => o.ShipperId == uid && (o.Status == OrderStatus.Preparing || o.Status == OrderStatus.Shipping)),
            await doneToday.CountAsync(),
            await doneToday.SumAsync(o => (decimal?)o.ShippingFee) ?? 0,
            await Available().MaxAsync(o => (int?)o.Id) ?? 0);
    }

    // Tab "Đơn chờ nhận": quán đã chuẩn bị, chưa có shipper
    [HttpGet("available")]
    public async Task<List<OrderDto>> AvailableOrders()
    {
        var list = await Available().WithDetails().OrderBy(o => o.CreatedAt).ToListAsync();
        return list.Select(o => o.ToDto()).ToList();
    }

    // Tab "Đang giao" (done = false) và "Đã giao" (done = true)
    [HttpGet("mine")]
    public async Task<List<OrderDto>> Mine(bool done = false)
    {
        var uid = User.UserId();
        var q = db.Orders.WithDetails().Where(o => o.ShipperId == uid);
        q = done
            ? q.Where(o => o.Status == OrderStatus.Done || o.Status == OrderStatus.Cancelled).OrderByDescending(o => o.CreatedAt).Take(50)
            : q.Where(o => o.Status == OrderStatus.Preparing || o.Status == OrderStatus.Shipping).OrderBy(o => o.CreatedAt);
        var list = await q.ToListAsync();
        return list.Select(o => o.ToDto()).ToList();
    }

    // Nhận đơn
    [HttpPut("orders/{id:int}/accept")]
    public async Task<ActionResult<MessageDto>> Accept(int id)
    {
        var me = await db.Users.FindAsync(User.UserId());
        if (me == null || me.IsLocked) return StatusCode(403, new MessageDto("Tài khoản đã bị khóa"));
        var active = await db.Orders.CountAsync(o => o.ShipperId == me.Id && (o.Status == OrderStatus.Preparing || o.Status == OrderStatus.Shipping));
        if (active >= MaxActiveOrders) return BadRequest(new MessageDto($"Bạn đang giao {active} đơn, giao xong bớt rồi nhận thêm nhé"));
        var o = await db.Orders.FirstOrDefaultAsync(x => x.Id == id);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.Status != OrderStatus.Preparing || o.ShipperId != null)
            return Conflict(new MessageDto("Đơn đã có shipper khác nhận hoặc không còn chờ giao"));
        OrderFlow.AssignShipper(db, o, me, me.FullName);
        await db.SaveChangesAsync();
        return new MessageDto($"Đã nhận đơn #{o.Code}. Tới quán lấy món nhé!");
    }

    // Trả lại đơn (chưa lấy món) để shipper khác nhận
    [HttpPut("orders/{id:int}/release")]
    public async Task<ActionResult<MessageDto>> Release(int id)
    {
        var uid = User.UserId();
        var o = await db.Orders.Include(x => x.Shipper).FirstOrDefaultAsync(x => x.Id == id && x.ShipperId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn của bạn"));
        if (o.Status != OrderStatus.Preparing) return BadRequest(new MessageDto("Đã lấy món thì không trả đơn được"));
        db.OrderStatusLogs.Add(new OrderStatusLog { OrderId = o.Id, Status = o.Status, Note = "Shipper trả đơn, đang tìm shipper khác", ChangedBy = o.Shipper!.FullName });
        o.ShipperId = null;
        await db.SaveChangesAsync();
        return new MessageDto("Đã trả đơn");
    }

    // Đã lấy món ở quán -> Đang giao
    [HttpPut("orders/{id:int}/pickup")]
    public async Task<ActionResult<MessageDto>> PickUp(int id)
    {
        var uid = User.UserId();
        var o = await db.Orders.WithDetails().FirstOrDefaultAsync(x => x.Id == id && x.ShipperId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn của bạn"));
        if (o.Status != OrderStatus.Preparing) return BadRequest(new MessageDto("Đơn không ở trạng thái chờ lấy món"));
        await OrderFlow.ChangeStatusAsync(db, o, OrderStatus.Shipping, "Shipper đã lấy món tại quán", o.Shipper!.FullName);
        await db.SaveChangesAsync();
        return new MessageDto("Đã lấy món, bắt đầu giao");
    }

    // Giao xong -> Hoàn thành (đơn tiền mặt: shipper đã thu tiền)
    [HttpPut("orders/{id:int}/deliver")]
    public async Task<ActionResult<MessageDto>> Deliver(int id)
    {
        var uid = User.UserId();
        var o = await db.Orders.WithDetails().FirstOrDefaultAsync(x => x.Id == id && x.ShipperId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn của bạn"));
        if (o.Status != OrderStatus.Shipping) return BadRequest(new MessageDto("Đơn chưa ở trạng thái đang giao"));
        var note = o.IsPaid ? "Đã giao cho khách" : $"Đã giao cho khách, thu tiền mặt {o.Total:N0}đ";
        await OrderFlow.ChangeStatusAsync(db, o, OrderStatus.Done, note, o.Shipper!.FullName);
        await db.SaveChangesAsync();
        return new MessageDto("Giao hàng thành công");
    }
}
