using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Cửa hàng trên sàn:
// - Khách: xem danh sách quán, xem 1 quán và các món của quán
// - Quản trị sàn: thêm/sửa/khóa quán, gán chủ quán
// - Chủ quán: sửa thông tin quán của mình, bật/tắt nhận đơn
[ApiController]
[Route("api/stores")]
public class StoresController(AppDbContext db) : ControllerBase
{
    // Trang chủ: các quán đang hoạt động
    [HttpGet]
    public async Task<List<StoreDto>> GetAll(string? q, int? storeCategoryId)
    {
        var query = db.Stores.Include(s => s.Owner).Include(s => s.StoreCategory).Where(s => s.IsActive);
        if (storeCategoryId.HasValue) query = query.Where(s => s.StoreCategoryId == storeCategoryId);
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(s => s.Name.Contains(q.Trim()) || s.Address.Contains(q.Trim()));
        var list = await query.OrderByDescending(s => s.IsOpen).ThenBy(s => s.Id).ToListAsync();
        return await ToDtos(list);
    }

    // StoreActivity: thông tin quán + món của quán
    [HttpGet("{id:int}")]
    public async Task<ActionResult<StoreDetailDto>> Get(int id)
    {
        var s = await db.Stores.Include(x => x.Owner).Include(x => x.StoreCategory).FirstOrDefaultAsync(x => x.Id == id);
        if (s == null || (!s.IsActive && !User.IsInRole(Roles.Admin) && s.OwnerId != User.UserId()))
            return NotFound(new MessageDto("Không tìm thấy quán"));
        var products = await db.Products.Include(p => p.Category).Include(p => p.Store).Include(p => p.Sizes)
            .Where(p => p.StoreId == id).OrderByDescending(p => p.SoldCount).ToListAsync();
        return new StoreDetailDto((await ToDtos(new List<Store> { s }))[0], products.Select(p => p.ToDto()).ToList());
    }

    // ---------- Quản trị sàn (ManageStoresActivity) ----------
    [HttpGet("all"), Authorize(Roles = Roles.Admin)]
    public async Task<List<StoreDto>> All(string? q)
    {
        var query = db.Stores.Include(s => s.Owner).Include(s => s.StoreCategory).AsQueryable();
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(s => s.Name.Contains(q.Trim()) || s.Address.Contains(q.Trim()));
        return await ToDtos(await query.OrderBy(s => s.Id).ToListAsync());
    }

    [HttpPost, Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<StoreDto>> Create(StoreRequest r)
    {
        var err = await Validate(r, null);
        if (err != null) return BadRequest(new MessageDto(err));
        var s = new Store();
        Apply(s, r, isAdmin: true);
        db.Stores.Add(s);
        await db.SaveChangesAsync();
        await db.Entry(s).Reference(x => x.Owner).LoadAsync();
        await db.Entry(s).Reference(x => x.StoreCategory).LoadAsync();
        return (await ToDtos(new List<Store> { s }))[0];
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<StoreDto>> Update(int id, StoreRequest r)
    {
        var s = await db.Stores.FindAsync(id);
        if (s == null) return NotFound(new MessageDto("Không tìm thấy quán"));
        var err = await Validate(r, id);
        if (err != null) return BadRequest(new MessageDto(err));
        Apply(s, r, isAdmin: true);
        await db.SaveChangesAsync();
        await db.Entry(s).Reference(x => x.Owner).LoadAsync();
        await db.Entry(s).Reference(x => x.StoreCategory).LoadAsync();
        return (await ToDtos(new List<Store> { s }))[0];
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var s = await db.Stores.FindAsync(id);
        if (s == null) return NotFound(new MessageDto("Không tìm thấy quán"));
        if (await db.Products.AnyAsync(p => p.StoreId == id) || await db.Orders.AnyAsync(o => o.StoreId == id))
            return BadRequest(new MessageDto("Quán đã có món hoặc đơn hàng nên không xóa được. Hãy tạm khóa quán."));
        db.Stores.Remove(s);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa quán");
    }

    // ---------- Chủ quán (StoreEditActivity) ----------
    [HttpGet("mine"), Authorize(Roles = Roles.StoreOwner)]
    public async Task<ActionResult<StoreDto>> Mine()
    {
        var s = await db.Stores.Include(x => x.Owner).Include(x => x.StoreCategory).FirstOrDefaultAsync(x => x.OwnerId == User.UserId());
        if (s == null) return NotFound(new MessageDto("Tài khoản chưa được gán cửa hàng, liên hệ quản trị sàn"));
        return (await ToDtos(new List<Store> { s }))[0];
    }

    [HttpPut("mine"), Authorize(Roles = Roles.StoreOwner)]
    public async Task<ActionResult<StoreDto>> UpdateMine(StoreRequest r)
    {
        var s = await db.Stores.Include(x => x.Owner).Include(x => x.StoreCategory).FirstOrDefaultAsync(x => x.OwnerId == User.UserId());
        if (s == null) return NotFound(new MessageDto("Tài khoản chưa được gán cửa hàng"));
        var err = await Validate(r with { OwnerId = s.OwnerId }, s.Id);
        if (err != null) return BadRequest(new MessageDto(err));
        Apply(s, r, isAdmin: false);
        await db.SaveChangesAsync();
        await db.Entry(s).Reference(x => x.StoreCategory).LoadAsync();
        return (await ToDtos(new List<Store> { s }))[0];
    }

    // Bật/tắt nhận đơn (quán đóng cửa thì khách không đặt được)
    [HttpPut("mine/open"), Authorize(Roles = Roles.StoreOwner)]
    public async Task<ActionResult<MessageDto>> SetOpen(OpenRequest r)
    {
        var s = await db.Stores.FirstOrDefaultAsync(x => x.OwnerId == User.UserId());
        if (s == null) return NotFound(new MessageDto("Tài khoản chưa được gán cửa hàng"));
        s.IsOpen = r.IsOpen;
        await db.SaveChangesAsync();
        return new MessageDto(r.IsOpen ? "Quán đang mở, nhận đơn bình thường" : "Quán đã tạm đóng, khách không đặt được");
    }

    private static void Apply(Store s, StoreRequest r, bool isAdmin)
    {
        s.Name = r.Name.Trim();
        s.Address = r.Address.Trim();
        s.Phone = r.Phone.Trim();
        s.Description = string.IsNullOrWhiteSpace(r.Description) ? null : r.Description.Trim();
        s.ImageUrl = string.IsNullOrWhiteSpace(r.ImageUrl) ? null : r.ImageUrl.Trim();
        s.IsOpen = r.IsOpen;
        s.StoreCategoryId = r.StoreCategoryId;
        if (isAdmin)
        {
            s.IsActive = r.IsActive;
            s.OwnerId = r.OwnerId;
        }
    }

    private async Task<string?> Validate(StoreRequest r, int? id)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return "Vui lòng nhập tên quán";
        if (string.IsNullOrWhiteSpace(r.Address)) return "Vui lòng nhập địa chỉ quán";
        if (string.IsNullOrWhiteSpace(r.Phone) || r.Phone.Trim().Length < 8 || !r.Phone.Trim().All(char.IsDigit))
            return "Số điện thoại quán không hợp lệ";
        if (r.StoreCategoryId != null && !await db.StoreCategories.AnyAsync(c => c.Id == r.StoreCategoryId))
            return "Loại quán không tồn tại";
        if (r.OwnerId != null)
        {
            var owner = await db.Users.FindAsync(r.OwnerId);
            if (owner == null || owner.Role != Roles.StoreOwner) return "Chủ quán phải là tài khoản có vai trò Chủ cửa hàng";
            if (await db.Stores.AnyAsync(s => s.OwnerId == r.OwnerId && s.Id != id)) return "Chủ quán này đang quản lý quán khác";
        }
        return null;
    }

    // Kèm số món, điểm đánh giá trung bình các món, số đơn đã hoàn thành
    private async Task<List<StoreDto>> ToDtos(List<Store> stores)
    {
        var ids = stores.Select(s => s.Id).ToList();
        // Lấy gọn vài cột rồi gom nhóm trong bộ nhớ (ít quán nên nhẹ)
        var productRows = await db.Products.Where(p => ids.Contains(p.StoreId))
            .Select(p => new { p.StoreId, p.RatingCount, p.AvgRating }).ToListAsync();
        var orderCounts = await db.Orders.Where(o => o.StoreId != null && ids.Contains(o.StoreId.Value) && o.Status == OrderStatus.Done)
            .GroupBy(o => o.StoreId!.Value).Select(g => new { StoreId = g.Key, Count = g.Count() }).ToListAsync();
        return stores.Select(s =>
        {
            var ps = productRows.Where(x => x.StoreId == s.Id).ToList();
            var rated = ps.Where(x => x.RatingCount > 0).Select(x => x.AvgRating).ToList();
            var avg = rated.Count == 0 ? 0 : rated.Average();
            return s.ToDto(ps.Count, avg, orderCounts.FirstOrDefault(x => x.StoreId == s.Id)?.Count ?? 0);
        }).ToList();
    }
}

// Loại quán (bảng DanhMucQuanAn): ai cũng xem được, quản trị sàn thêm/sửa/xóa
[ApiController]
[Route("api/store-categories")]
public class StoreCategoriesController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<List<StoreCategoryDto>> GetAll() =>
        await db.StoreCategories.OrderBy(c => c.Id)
            .Select(c => new StoreCategoryDto(c.Id, c.Name, c.ImageUrl, c.Stores.Count(s => s.IsActive)))
            .ToListAsync();

    [HttpPost, Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<StoreCategoryDto>> Create(StoreCategoryRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên loại quán"));
        var c = new StoreCategory { Name = r.Name.Trim(), ImageUrl = r.ImageUrl };
        db.StoreCategories.Add(c);
        await db.SaveChangesAsync();
        return new StoreCategoryDto(c.Id, c.Name, c.ImageUrl, 0);
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> Update(int id, StoreCategoryRequest r)
    {
        var c = await db.StoreCategories.FindAsync(id);
        if (c == null) return NotFound(new MessageDto("Không tìm thấy loại quán"));
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên loại quán"));
        c.Name = r.Name.Trim();
        c.ImageUrl = r.ImageUrl;
        await db.SaveChangesAsync();
        return new MessageDto("Đã cập nhật");
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var c = await db.StoreCategories.FindAsync(id);
        if (c == null) return NotFound(new MessageDto("Không tìm thấy loại quán"));
        if (await db.Stores.AnyAsync(s => s.StoreCategoryId == id))
            return BadRequest(new MessageDto("Loại quán còn quán, hãy chuyển quán sang loại khác trước"));
        db.StoreCategories.Remove(c);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }
}
