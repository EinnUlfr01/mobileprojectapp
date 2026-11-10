using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

[ApiController]
[Route("api/orders")]
public class OrdersController(AppDbContext db, VnPayService vnPay) : ControllerBase
{
    public const decimal ShippingFee = 15000;
    private static readonly string[] PaymentMethods = { "Tiền mặt", PaymentsController.VnPayMethod };

    // CheckoutActivity + GuestCheckoutActivity
    // - User đã đăng nhập: không gửi Items -> lấy từ giỏ hàng trên server rồi xóa giỏ
    // - Khách vãng lai: gửi Items lấy từ giỏ trên máy, chỉ cần tên + SĐT + địa chỉ
    [HttpPost]
    [AllowAnonymous]
    public async Task<ActionResult<OrderDto>> Create(CreateOrderRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.ReceiverName)) return BadRequest(new MessageDto("Vui lòng nhập tên người nhận"));
        if (!Mapping.IsValidPhone(r.Phone)) return BadRequest(new MessageDto("Số điện thoại phải đủ 10 số"));
        if (string.IsNullOrWhiteSpace(r.Address)) return BadRequest(new MessageDto("Vui lòng nhập địa chỉ giao hàng"));
        if (!PaymentMethods.Contains(r.PaymentMethod)) return BadRequest(new MessageDto("Phương thức thanh toán không hợp lệ"));

        var uid = User.UserId();
        List<OrderLineRequest> lines;
        List<CartItem> cartItems = new();
        if (uid != null && (r.Items == null || r.Items.Count == 0))
        {
            cartItems = await db.CartItems.Where(c => c.UserId == uid).ToListAsync();
            lines = cartItems.Select(c => new OrderLineRequest(c.ProductId, c.Size, c.Quantity)).ToList();
        }
        else lines = r.Items ?? new();
        if (lines.Count == 0) return BadRequest(new MessageDto("Giỏ hàng đang trống"));

        var order = new Order
        {
            UserId = uid,
            ReceiverName = r.ReceiverName.Trim(),
            Phone = r.Phone,
            Address = r.Address.Trim(),
            Note = r.Note,
            PaymentMethod = r.PaymentMethod,
            // VNPay: chưa thanh toán cho tới khi VNPay báo thành công
            IsPaid = false,
            ShippingFee = ShippingFee
        };
        foreach (var l in lines)
        {
            if (l.Quantity < 1) return BadRequest(new MessageDto("Số lượng không hợp lệ"));
            var p = await db.Products.Include(x => x.Sizes).FirstOrDefaultAsync(x => x.Id == l.ProductId);
            var size = p?.Sizes.FirstOrDefault(s => s.Size == l.Size);
            if (p == null || size == null) return BadRequest(new MessageDto("Có món không còn bán, vui lòng kiểm tra giỏ"));
            order.Items.Add(new OrderItem
            {
                ProductId = p.Id, ProductName = p.Name, Size = size.Size, Quantity = l.Quantity,
                UnitPrice = size.Price, LineTotal = size.Price * l.Quantity
            });
        }
        order.Subtotal = order.Items.Sum(i => i.LineTotal);

        // Mã giảm giá
        if (!string.IsNullOrWhiteSpace(r.VoucherCode))
        {
            var (discount, error) = await VouchersController.Calculate(db, r.VoucherCode, order.Subtotal, order.ShippingFee);
            if (error != null) return BadRequest(new MessageDto(error));
            order.VoucherCode = r.VoucherCode.Trim().ToUpper();
            order.Discount = discount;
            var v = await db.Vouchers.FirstAsync(x => x.Code == order.VoucherCode);
            v.UsedCount++;
        }
        order.Total = order.Subtotal + order.ShippingFee - order.Discount;
        order.Code = "DH" + DateTime.UtcNow.AddHours(7).ToString("yyMMddHHmmss") + Random.Shared.Next(10, 99);

        order.History.Add(new OrderStatusLog { Status = OrderStatus.Pending, Note = "Đặt hàng thành công", ChangedBy = order.ReceiverName });
        db.Orders.Add(order);
        db.CartItems.RemoveRange(cartItems);
        await db.SaveChangesAsync();
        // Chọn VNPay: trả về link thanh toán để app mở trong WebView
        // Đơn 0đ (voucher giảm hết) thì không cần thanh toán online
        var paymentUrl = order.PaymentMethod == PaymentsController.VnPayMethod && order.Total > 0 ? vnPay.CreatePaymentUrl(order) : null;
        if (uid != null)
        {
            NotificationsController.Add(db, uid.Value, $"Đặt hàng thành công #{order.Code}",
                $"Đơn {order.Total:N0}đ đang chờ cửa hàng xác nhận.", order.Id);
            await db.SaveChangesAsync();
        }
        return order.ToDto() with { PaymentUrl = paymentUrl };
    }

    // OrderHistoryActivity
    [HttpGet("my")]
    [Authorize]
    public async Task<List<OrderDto>> MyOrders(string? status)
    {
        var uid = User.UserId();
        var q = db.Orders.Include(o => o.Items).Where(o => o.UserId == uid);
        if (!string.IsNullOrWhiteSpace(status)) q = q.Where(o => o.Status == status);
        var list = await q.OrderByDescending(o => o.CreatedAt).ToListAsync();
        return list.Select(o => o.ToDto()).ToList();
    }

    // OrderDetailActivity: chủ đơn hoặc admin
    [HttpGet("{id:int}")]
    [Authorize]
    public async Task<ActionResult<OrderDto>> Get(int id)
    {
        var o = await db.Orders.Include(x => x.Items).Include(x => x.History).FirstOrDefaultAsync(x => x.Id == id);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        var uid = User.UserId();
        var isOrderManager = User.IsInRole(Roles.Admin) || User.IsInRole(Roles.OrderStaff);
        if (o.UserId != uid && !isOrderManager) return Forbid();
        var reviewed = uid == null ? new HashSet<int>() :
            (await db.Reviews.Where(r => r.OrderId == id && r.UserId == uid).Select(r => r.ProductId).ToListAsync()).ToHashSet();
        return o.ToDto(reviewed);
    }

    // Thanh toán lại đơn VNPay chưa trả tiền (OrderDetailActivity)
    [HttpPost("{id:int}/pay")]
    [Authorize]
    public async Task<ActionResult<PaymentLinkDto>> Pay(int id)
    {
        var uid = User.UserId();
        var o = await db.Orders.FirstOrDefaultAsync(x => x.Id == id && x.UserId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.IsPaid) return BadRequest(new MessageDto("Đơn đã được thanh toán"));
        if (o.Status == OrderStatus.Cancelled) return BadRequest(new MessageDto("Đơn đã hủy"));
        o.PaymentMethod = PaymentsController.VnPayMethod;
        await db.SaveChangesAsync();
        return new PaymentLinkDto(vnPay.CreatePaymentUrl(o));
    }

    // Đặt lại: cho toàn bộ món của đơn cũ vào giỏ hàng (món/size không còn bán thì bỏ qua)
    [HttpPost("{id:int}/reorder")]
    [Authorize]
    public async Task<ActionResult<MessageDto>> Reorder(int id)
    {
        var uid = User.UserId()!.Value;
        var o = await db.Orders.Include(x => x.Items).FirstOrDefaultAsync(x => x.Id == id && x.UserId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        int added = 0, skipped = 0;
        foreach (var it in o.Items)
        {
            if (!await db.ProductSizes.AnyAsync(s => s.ProductId == it.ProductId && s.Size == it.Size)) { skipped++; continue; }
            var c = await db.CartItems.FirstOrDefaultAsync(x => x.UserId == uid && x.ProductId == it.ProductId && x.Size == it.Size);
            if (c != null) c.Quantity += it.Quantity;
            else db.CartItems.Add(new CartItem { UserId = uid, ProductId = it.ProductId, Size = it.Size, Quantity = it.Quantity });
            added++;
        }
        await db.SaveChangesAsync();
        if (added == 0) return BadRequest(new MessageDto("Các món trong đơn này không còn bán"));
        return new MessageDto(skipped == 0 ? "Đã thêm các món vào giỏ hàng" : $"Đã thêm {added} món, {skipped} món không còn bán");
    }

    // Người dùng tự hủy khi đơn còn "Đang xử lý"
    [HttpPut("{id:int}/cancel")]
    [Authorize]
    public async Task<ActionResult<MessageDto>> Cancel(int id, CancelRequest r)
    {
        var uid = User.UserId();
        var o = await db.Orders.FirstOrDefaultAsync(x => x.Id == id && x.UserId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.Status != OrderStatus.Pending) return BadRequest(new MessageDto("Chỉ hủy được đơn đang xử lý"));
        var reason = string.IsNullOrWhiteSpace(r?.Reason) ? "Khách hủy đơn" : r.Reason.Trim();
        if (reason.Length > 300) reason = reason[..300];
        o.Status = OrderStatus.Cancelled;
        o.CancelReason = reason;
        db.OrderStatusLogs.Add(new OrderStatusLog { OrderId = o.Id, Status = OrderStatus.Cancelled, Note = reason, ChangedBy = "Khách hàng" });
        await db.SaveChangesAsync();
        return new MessageDto("Đã hủy đơn");
    }
}

// ReviewActivity: chỉ user, chỉ món trong đơn đã hoàn thành
[ApiController]
[Route("api/reviews")]
[Authorize]
public class ReviewsController(AppDbContext db) : ControllerBase
{
    [HttpPost]
    public async Task<ActionResult<MessageDto>> Create(CreateReviewRequest r)
    {
        var uid = User.UserId()!.Value;
        if (r.Stars is < 1 or > 5) return BadRequest(new MessageDto("Số sao từ 1 đến 5"));
        var o = await db.Orders.Include(x => x.Items).FirstOrDefaultAsync(x => x.Id == r.OrderId && x.UserId == uid);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.Status != OrderStatus.Done) return BadRequest(new MessageDto("Chỉ đánh giá được đơn đã hoàn thành"));
        if (o.Items.All(i => i.ProductId != r.ProductId)) return BadRequest(new MessageDto("Món này không có trong đơn"));
        if (await db.Reviews.AnyAsync(x => x.UserId == uid && x.OrderId == r.OrderId && x.ProductId == r.ProductId))
            return Conflict(new MessageDto("Bạn đã đánh giá món này rồi"));

        db.Reviews.Add(new Review { UserId = uid, OrderId = r.OrderId, ProductId = r.ProductId, Stars = r.Stars, Comment = r.Comment?.Trim() });
        await db.SaveChangesAsync();

        // Cập nhật rating trung bình của món
        var p = await db.Products.FindAsync(r.ProductId);
        var stars = await db.Reviews.Where(x => x.ProductId == r.ProductId).Select(x => x.Stars).ToListAsync();
        p!.RatingCount = stars.Count;
        p.AvgRating = stars.Average();
        await db.SaveChangesAsync();
        return new MessageDto("Cảm ơn bạn đã đánh giá");
    }
}
