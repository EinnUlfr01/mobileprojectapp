using FoodOrder.API.Data;

namespace FoodOrder.API.Common;

public static class Mapping
{
    public static UserDto ToDto(this User u) => new(u.Id, u.FullName, u.Email, u.Phone, u.DefaultAddress, u.Role);

    public static ProductDto ToDto(this Product p) => new(
        p.Id, p.CategoryId, p.Category?.Name ?? "", p.Name, p.Description, p.ImageUrl, p.IsNew,
        p.SoldCount, Math.Round(p.AvgRating, 1), p.RatingCount,
        p.Sizes.Count == 0 ? 0 : p.Sizes.Min(s => s.Price),
        p.Sizes.OrderBy(s => s.Price).Select(s => new SizeDto(s.Size, s.Price)).ToList());

    public static OrderDto ToDto(this Order o, HashSet<int>? reviewedProductIds = null) => new(
        o.Id, o.Code, o.UserId == null, o.ReceiverName, o.Phone, o.Address, o.Note, o.PaymentMethod, o.IsPaid,
        o.Status, o.Subtotal, o.ShippingFee, o.Total, o.CreatedAt, o.Items.Sum(i => i.Quantity),
        o.Items.Select(i => new OrderItemDto(i.ProductId, i.ProductName, i.Size, i.Quantity, i.UnitPrice, i.LineTotal,
            reviewedProductIds?.Contains(i.ProductId) ?? false)).ToList(),
        null, o.VoucherCode, o.Discount);

    public static VoucherDto ToDto(this Voucher v) => new(v.Id, v.Code, v.Description, v.Type, v.Value, v.MinOrder,
        v.MaxDiscount, v.UsageLimit, v.UsedCount, v.ExpiresAt, v.IsActive);

    public static bool IsValidPhone(string? phone) =>
        !string.IsNullOrWhiteSpace(phone) && phone.Length == 10 && phone.All(char.IsDigit) && phone[0] == '0';
}
