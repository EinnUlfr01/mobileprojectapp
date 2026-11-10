using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Mã giảm giá: khách xem/kiểm tra mã, Quản trị viên và Quản lý thực đơn tạo/sửa/xóa
[ApiController]
[Route("api/vouchers")]
public class VouchersController(AppDbContext db) : ControllerBase
{
    // Danh sách mã đang dùng được (hiện ở màn đặt hàng)
    [HttpGet]
    public async Task<List<VoucherDto>> Available()
    {
        var now = DateTime.UtcNow;
        var list = await db.Vouchers
            .Where(v => v.IsActive && (v.ExpiresAt == null || v.ExpiresAt > now) && (v.UsageLimit == 0 || v.UsedCount < v.UsageLimit))
            .OrderBy(v => v.MinOrder).ToListAsync();
        return list.Select(v => v.ToDto()).ToList();
    }

    [HttpPost("check")]
    public async Task<ActionResult<VoucherResultDto>> Check(CheckVoucherRequest r)
    {
        var (discount, error) = await Calculate(db, r.Code, r.Subtotal, OrdersController.ShippingFee);
        if (error != null) return BadRequest(new MessageDto(error));
        return new VoucherResultDto(r.Code.Trim().ToUpper(), discount, $"Áp dụng thành công, giảm {discount:N0}đ");
    }

    // Tính số tiền giảm. Trả về (0, lỗi) nếu mã không dùng được
    public static async Task<(decimal Discount, string? Error)> Calculate(AppDbContext db, string? code, decimal subtotal, decimal shippingFee)
    {
        var key = (code ?? "").Trim().ToUpper();
        if (key.Length == 0) return (0, "Vui lòng nhập mã giảm giá");
        var v = await db.Vouchers.FirstOrDefaultAsync(x => x.Code == key);
        if (v == null || !v.IsActive) return (0, "Mã giảm giá không tồn tại");
        if (v.ExpiresAt != null && v.ExpiresAt <= DateTime.UtcNow) return (0, "Mã giảm giá đã hết hạn");
        if (v.UsageLimit > 0 && v.UsedCount >= v.UsageLimit) return (0, "Mã giảm giá đã hết lượt sử dụng");
        if (subtotal < v.MinOrder) return (0, $"Đơn tối thiểu {v.MinOrder:N0}đ để dùng mã này");

        decimal discount = v.Type switch
        {
            VoucherType.Percent => Math.Round(subtotal * v.Value / 100),
            VoucherType.FreeShip => shippingFee,
            _ => v.Value
        };
        if (v.Type == VoucherType.Percent && v.MaxDiscount > 0) discount = Math.Min(discount, v.MaxDiscount);
        discount = Math.Min(discount, subtotal + shippingFee); // không giảm quá tổng tiền
        return (discount, null);
    }

    // ---------- Quản lý (ManageVouchersActivity) ----------
    [HttpGet("all"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<List<VoucherDto>> All()
    {
        var list = await db.Vouchers.OrderByDescending(v => v.Id).ToListAsync();
        return list.Select(v => v.ToDto()).ToList();
    }

    [HttpPost, Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<VoucherDto>> Create(VoucherRequest r)
    {
        var err = await Validate(r, null);
        if (err != null) return BadRequest(new MessageDto(err));
        var v = new Voucher();
        Apply(v, r);
        db.Vouchers.Add(v);
        await db.SaveChangesAsync();
        return v.ToDto();
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<VoucherDto>> Update(int id, VoucherRequest r)
    {
        var v = await db.Vouchers.FindAsync(id);
        if (v == null) return NotFound(new MessageDto("Không tìm thấy mã"));
        var err = await Validate(r, id);
        if (err != null) return BadRequest(new MessageDto(err));
        Apply(v, r);
        await db.SaveChangesAsync();
        return v.ToDto();
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var v = await db.Vouchers.FindAsync(id);
        if (v == null) return NotFound(new MessageDto("Không tìm thấy mã"));
        db.Vouchers.Remove(v); // đơn cũ vẫn giữ mã dạng chữ trong cột VoucherCode
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }

    private static void Apply(Voucher v, VoucherRequest r)
    {
        v.Code = r.Code.Trim().ToUpper();
        v.Description = r.Description?.Trim() ?? "";
        v.Type = r.Type;
        v.Value = r.Value;
        v.MinOrder = r.MinOrder;
        v.MaxDiscount = r.MaxDiscount;
        v.UsageLimit = r.UsageLimit;
        v.ExpiresAt = r.ExpiresAt;
        v.IsActive = r.IsActive;
    }

    private async Task<string?> Validate(VoucherRequest r, int? id)
    {
        var code = (r.Code ?? "").Trim().ToUpper();
        if (code.Length is < 3 or > 30 || !code.All(char.IsLetterOrDigit)) return "Mã gồm 3-30 chữ cái hoặc số, không dấu cách";
        if (!VoucherType.All.Contains(r.Type)) return "Loại mã không hợp lệ";
        if (r.Type == VoucherType.Percent && (r.Value <= 0 || r.Value > 100)) return "Phần trăm giảm từ 1 đến 100";
        if (r.Type == VoucherType.Amount && r.Value <= 0) return "Số tiền giảm phải lớn hơn 0";
        if (r.MinOrder < 0 || r.MaxDiscount < 0 || r.UsageLimit < 0) return "Giá trị không được âm";
        if (await db.Vouchers.AnyAsync(v => v.Code == code && v.Id != id)) return "Mã đã tồn tại";
        return null;
    }
}
