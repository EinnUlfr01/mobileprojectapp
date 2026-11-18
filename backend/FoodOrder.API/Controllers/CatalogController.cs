using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Danh mục món dùng chung cho cả sàn: ai cũng xem được, quản trị sàn mới thêm/sửa/xóa
[ApiController]
[Route("api/categories")]
public class CategoriesController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<List<CategoryDto>> GetAll() =>
        await db.Categories.OrderBy(c => c.Id)
            .Select(c => new CategoryDto(c.Id, c.Name, c.ImageUrl, c.Products.Count))
            .ToListAsync();

    [HttpPost, Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<CategoryDto>> Create(CategoryRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên danh mục"));
        var c = new Category { Name = r.Name.Trim(), ImageUrl = r.ImageUrl };
        db.Categories.Add(c);
        await db.SaveChangesAsync();
        return new CategoryDto(c.Id, c.Name, c.ImageUrl, 0);
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> Update(int id, CategoryRequest r)
    {
        var c = await db.Categories.FindAsync(id);
        if (c == null) return NotFound(new MessageDto("Không tìm thấy danh mục"));
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên danh mục"));
        c.Name = r.Name.Trim();
        c.ImageUrl = r.ImageUrl;
        await db.SaveChangesAsync();
        return new MessageDto("Đã cập nhật");
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.Admin)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var c = await db.Categories.FindAsync(id);
        if (c == null) return NotFound(new MessageDto("Không tìm thấy danh mục"));
        if (await db.Products.AnyAsync(p => p.CategoryId == id))
            return BadRequest(new MessageDto("Danh mục còn món, hãy xóa hoặc chuyển món trước"));
        db.Categories.Remove(c);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }
}

[ApiController]
[Route("api/products")]
public class ProductsController(AppDbContext db) : ControllerBase
{
    // Trang chủ, tìm kiếm, món theo danh mục, món của 1 quán
    // sort: bestseller | new | price_asc | price_desc
    [HttpGet]
    public async Task<List<ProductDto>> GetAll(int? categoryId, string? q, string? sort, bool? isNew, int? take, int? storeId)
    {
        var query = db.Products.Include(p => p.Category).Include(p => p.Store).Include(p => p.Sizes).AsQueryable();
        if (storeId.HasValue) query = query.Where(p => p.StoreId == storeId);
        else query = query.Where(p => p.Store!.IsActive); // quán bị sàn khóa thì ẩn món
        if (categoryId.HasValue) query = query.Where(p => p.CategoryId == categoryId);
        if (isNew == true) query = query.Where(p => p.IsNew);
        if (!string.IsNullOrWhiteSpace(q))
        {
            var key = q.Trim();
            query = query.Where(p => p.Name.Contains(key) || p.Store!.Name.Contains(key));
        }
        query = sort switch
        {
            "bestseller" => query.OrderByDescending(p => p.SoldCount),
            "new" => query.OrderByDescending(p => p.CreatedAt).ThenByDescending(p => p.Id),
            "price_asc" => query.OrderBy(p => p.Sizes.Min(s => s.Price)),
            "price_desc" => query.OrderByDescending(p => p.Sizes.Min(s => s.Price)),
            _ => query.OrderBy(p => p.Id)
        };
        if (take is > 0) query = query.Take(take.Value);
        var list = await query.ToListAsync();
        return list.Select(p => p.ToDto()).ToList();
    }

    // ProductDetailActivity: chi tiết + đánh giá
    [HttpGet("{id:int}")]
    public async Task<ActionResult<ProductDetailDto>> Get(int id)
    {
        var p = await db.Products.Include(x => x.Category).Include(x => x.Store).Include(x => x.Sizes).FirstOrDefaultAsync(x => x.Id == id);
        if (p == null) return NotFound(new MessageDto("Không tìm thấy món"));
        var reviews = await db.Reviews.Where(r => r.ProductId == id).OrderByDescending(r => r.CreatedAt)
            .Select(r => new ReviewDto(r.Id, r.User!.FullName, r.Stars, r.Comment, r.CreatedAt))
            .ToListAsync();
        // Đăng nhập rồi thì cho biết món này có trong danh sách yêu thích không
        var uid = User.UserId();
        var isFavorite = uid != null && await db.Favorites.AnyAsync(f => f.UserId == uid && f.ProductId == id);
        return new ProductDetailDto(p.ToDto(), reviews, isFavorite);
    }

    // AddEditProductActivity: quản trị sàn chọn quán, chủ quán chỉ thêm món cho quán của mình
    [HttpPost, Authorize(Roles = Roles.AdminOrOwner)]
    public async Task<ActionResult<ProductDto>> Create(ProductRequest r)
    {
        var (storeId, storeErr) = await ResolveStore(r.StoreId);
        if (storeErr != null) return BadRequest(new MessageDto(storeErr));
        var err = await Validate(r);
        if (err != null) return BadRequest(new MessageDto(err));
        var p = new Product
        {
            CategoryId = r.CategoryId, StoreId = storeId, Name = r.Name.Trim(), Description = r.Description, ImageUrl = r.ImageUrl, IsNew = r.IsNew,
            Sizes = r.Sizes.Select(s => new ProductSize { Size = s.Size, Price = s.Price }).ToList()
        };
        db.Products.Add(p);
        await db.SaveChangesAsync();
        await db.Entry(p).Reference(x => x.Category).LoadAsync();
        await db.Entry(p).Reference(x => x.Store).LoadAsync();
        return p.ToDto();
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.AdminOrOwner)]
    public async Task<ActionResult<ProductDto>> Update(int id, ProductRequest r)
    {
        var p = await db.Products.Include(x => x.Sizes).Include(x => x.Category).FirstOrDefaultAsync(x => x.Id == id);
        if (p == null) return NotFound(new MessageDto("Không tìm thấy món"));
        if (!await CanEdit(p)) return Forbid();
        var err = await Validate(r);
        if (err != null) return BadRequest(new MessageDto(err));
        if (User.IsInRole(Roles.Admin) && r.StoreId is int newStore && newStore != p.StoreId)
        {
            if (!await db.Stores.AnyAsync(s => s.Id == newStore)) return BadRequest(new MessageDto("Cửa hàng không tồn tại"));
            p.StoreId = newStore;
        }
        p.CategoryId = r.CategoryId;
        p.Name = r.Name.Trim();
        p.Description = r.Description;
        p.ImageUrl = r.ImageUrl;
        p.IsNew = r.IsNew;
        db.ProductSizes.RemoveRange(p.Sizes);
        p.Sizes = r.Sizes.Select(s => new ProductSize { Size = s.Size, Price = s.Price }).ToList();
        await db.SaveChangesAsync();
        await db.Entry(p).Reference(x => x.Category).LoadAsync();
        await db.Entry(p).Reference(x => x.Store).LoadAsync();
        return p.ToDto();
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.AdminOrOwner)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var p = await db.Products.FindAsync(id);
        if (p == null) return NotFound(new MessageDto("Không tìm thấy món"));
        if (!await CanEdit(p)) return Forbid();
        if (await db.OrderItems.AnyAsync(i => i.ProductId == id))
            return BadRequest(new MessageDto("Món đã có trong đơn hàng nên không xóa được"));
        db.Products.Remove(p);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }

    // Chủ quán: luôn là quán của mình. Quản trị sàn: quán được chọn trong form.
    private async Task<(int StoreId, string? Error)> ResolveStore(int? requested)
    {
        if (User.IsInRole(Roles.Admin))
        {
            if (requested == null) return (0, "Vui lòng chọn cửa hàng");
            return await db.Stores.AnyAsync(s => s.Id == requested) ? (requested.Value, null) : (0, "Cửa hàng không tồn tại");
        }
        var uid = User.UserId();
        var mine = await db.Stores.Where(s => s.OwnerId == uid).Select(s => (int?)s.Id).FirstOrDefaultAsync();
        return mine == null ? (0, "Tài khoản chưa được gán cửa hàng, liên hệ quản trị sàn") : (mine.Value, null);
    }

    private async Task<bool> CanEdit(Product p)
    {
        if (User.IsInRole(Roles.Admin)) return true;
        var uid = User.UserId();
        return await db.Stores.AnyAsync(s => s.Id == p.StoreId && s.OwnerId == uid);
    }

    private async Task<string?> Validate(ProductRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return "Vui lòng nhập tên món";
        if (!await db.Categories.AnyAsync(c => c.Id == r.CategoryId)) return "Danh mục không tồn tại";
        if (r.Sizes == null || r.Sizes.Count == 0) return "Cần ít nhất 1 size có giá";
        if (r.Sizes.Any(s => s.Price <= 0)) return "Giá phải lớn hơn 0";
        if (r.Sizes.Select(s => s.Size).Distinct().Count() != r.Sizes.Count) return "Size bị trùng";
        return null;
    }
}
