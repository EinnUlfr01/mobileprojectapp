using FoodOrder.API.Data;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Common;

public static class Mapping
{
    public static UserDto ToDto(this User u, Store? store = null) => new(u.Id, u.FullName, u.Email, u.Phone, u.DefaultAddress, u.Role,
        u.VehiclePlate, u.IsLocked, store?.Id, store?.Name);

    // Nhớ Include(p => p.Store) để có tên quán
    public static ProductDto ToDto(this Product p) => new(
        p.Id, p.CategoryId, p.Category?.Name ?? "", p.Name, p.Description, p.ImageUrl, p.IsNew,
        p.SoldCount, Math.Round(p.AvgRating, 1), p.RatingCount,
        p.Sizes.Count == 0 ? 0 : p.Sizes.Min(s => s.Price),
        p.Sizes.OrderBy(s => s.Price).Select(s => new SizeDto(s.Size, s.Price)).ToList(),
        p.StoreId, p.Store?.Name ?? "", p.Store?.IsOpen ?? true);

    // Nhớ Include(o => o.Store).Include(o => o.Shipper) để có thông tin quán và shipper
    public static OrderDto ToDto(this Order o, HashSet<int>? reviewedProductIds = null) => new(
        o.Id, o.Code, o.UserId == null, o.ReceiverName, o.Phone, o.Address, o.Note, o.PaymentMethod, o.IsPaid,
        o.Status, o.Subtotal, o.ShippingFee, o.Total, o.CreatedAt, o.Items.Sum(i => i.Quantity),
        o.Items.Select(i => new OrderItemDto(i.ProductId, i.ProductName, i.Size, i.Quantity, i.UnitPrice, i.LineTotal,
            reviewedProductIds?.Contains(i.ProductId) ?? false)).ToList(),
        null, o.VoucherCode, o.Discount, o.CancelReason,
        o.History.OrderBy(h => h.CreatedAt).ThenBy(h => h.Id)
            .Select(h => new OrderLogDto(h.Status, h.Note, h.ChangedBy, h.CreatedAt)).ToList(),
        o.StoreId, o.Store?.Name, o.Store?.Address, o.Store?.Phone,
        o.ShipperId, o.Shipper?.FullName, o.Shipper?.Phone, o.Shipper?.VehiclePlate);

    public static StoreDto ToDto(this Store s, int productCount, double avgRating, int orderCount) => new(
        s.Id, s.Name, s.Address, s.Phone, s.Description, s.ImageUrl, s.IsOpen, s.IsActive,
        s.OwnerId, s.Owner?.FullName, s.Owner?.Email, productCount, Math.Round(avgRating, 1), orderCount,
        s.StoreCategoryId, s.StoreCategory?.Name);

    public static VoucherDto ToDto(this Voucher v) => new(v.Id, v.Code, v.Description, v.Type, v.Value, v.MinOrder,
        v.MaxDiscount, v.UsageLimit, v.UsedCount, v.ExpiresAt, v.IsActive);

    public static bool IsValidPhone(string? phone) =>
        !string.IsNullOrWhiteSpace(phone) && phone.Length == 10 && phone.All(char.IsDigit) && phone[0] == '0';

    // Đơn hàng kèm đủ thông tin để hiển thị (món, quán, shipper)
    public static IQueryable<Order> WithDetails(this IQueryable<Order> q) =>
        q.Include(o => o.Items).Include(o => o.Store).Include(o => o.Shipper);
}
