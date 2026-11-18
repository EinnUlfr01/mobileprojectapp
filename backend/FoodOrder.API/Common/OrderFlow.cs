using FoodOrder.API.Controllers;
using FoodOrder.API.Data;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Common;

// Luồng trạng thái đơn của sàn giao đồ ăn:
//   Đang xử lý (chờ quán xác nhận) -> Đang chuẩn bị (quán nhận đơn, chờ shipper)
//   -> Đang giao (shipper đã lấy món) -> Hoàn thành (shipper giao xong)
//   Đơn có thể bị hủy khi chưa giao.
// Mọi thay đổi đều: ghi LichSuDonHang, gửi ThongBao cho khách, cộng/trừ số lượng đã bán.
public static class OrderFlow
{
    public static async Task ChangeStatusAsync(AppDbContext db, Order o, string status, string? note, string changedBy)
    {
        if (o.Status == status) return;
        note = string.IsNullOrWhiteSpace(note) ? null : note.Trim();
        if (note?.Length > 300) note = note[..300];

        // Cộng/trừ số lượng đã bán khi đơn vào/ra trạng thái Hoàn thành
        var wasDone = o.Status == OrderStatus.Done;
        var isDone = status == OrderStatus.Done;
        if (wasDone != isDone)
        {
            if (o.Items.Count == 0) await db.Entry(o).Collection(x => x.Items).LoadAsync();
            var sign = isDone ? 1 : -1;
            foreach (var it in o.Items)
            {
                var p = await db.Products.FindAsync(it.ProductId);
                if (p != null) p.SoldCount = Math.Max(0, p.SoldCount + sign * it.Quantity);
            }
        }

        o.Status = status;
        if (isDone) o.IsPaid = true; // giao xong = đã thu tiền (tiền mặt shipper thu hộ)
        if (status == OrderStatus.Cancelled) o.CancelReason = note ?? "Đơn bị hủy";
        db.OrderStatusLogs.Add(new OrderStatusLog { OrderId = o.Id, Status = status, Note = note, ChangedBy = changedBy });

        // Báo cho khách thành viên (khách vãng lai không có tài khoản nên quán/shipper gọi điện)
        if (o.UserId != null)
        {
            if (o.ShipperId != null && o.Shipper == null) await db.Entry(o).Reference(x => x.Shipper).LoadAsync();
            if (o.StoreId != null && o.Store == null) await db.Entry(o).Reference(x => x.Store).LoadAsync();
            var storeName = o.Store?.Name ?? "Quán";
            var body = status switch
            {
                OrderStatus.Preparing => $"{storeName} đã nhận đơn và đang chuẩn bị món.",
                OrderStatus.Shipping => $"Shipper {o.Shipper?.FullName} ({o.Shipper?.Phone}) đã lấy món và đang giao tới bạn.",
                OrderStatus.Done => "Chúc bạn ngon miệng! Hãy đánh giá món để quán phục vụ tốt hơn.",
                OrderStatus.Cancelled => $"Đơn đã bị hủy: {o.CancelReason}. Liên hệ hỗ trợ qua mục Chat nếu cần.",
                _ => "Đơn đang chờ quán xác nhận."
            };
            NotificationsController.Add(db, o.UserId.Value, $"Đơn #{o.Code}: {status}", body, o.Id);
        }
    }

    // Shipper nhận đơn
    public static void AssignShipper(AppDbContext db, Order o, User shipper, string changedBy)
    {
        o.ShipperId = shipper.Id;
        o.Shipper = shipper;
        db.OrderStatusLogs.Add(new OrderStatusLog
        {
            OrderId = o.Id, Status = o.Status, ChangedBy = changedBy,
            Note = $"Shipper {shipper.FullName} · {shipper.Phone}" + (shipper.VehiclePlate != null ? $" · {shipper.VehiclePlate}" : "") + " nhận giao đơn"
        });
        if (o.UserId != null)
            NotificationsController.Add(db, o.UserId.Value, $"Đã có shipper cho đơn #{o.Code}",
                $"Shipper {shipper.FullName} ({shipper.Phone}) sẽ tới quán lấy món cho bạn.", o.Id);
    }
}
