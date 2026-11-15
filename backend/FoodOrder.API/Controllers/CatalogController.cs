using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Danh mục quán ăn: ai cũng xem được, admin mới thêm/sửa/xóa
[ApiController]
[Route("api/store-categories")]
public class StoreCategoriesController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<List<StoreCategoryDto>> GetAll() =>
        await db.StoreCategories.OrderBy(c => c.Id)
            .Select(c => new StoreCategoryDto(c.Id, c.Name, c.ImageUrl, c.Stores.Count))
            .ToListAsync();

    [HttpPost, Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<StoreCategoryDto>> Create(StoreCategoryRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên danh mục quán"));
        var sc = new StoreCategory { Name = r.Name.Trim(), ImageUrl = r.ImageUrl };
        db.StoreCategories.Add(sc);
        await db.SaveChangesAsync();
        return new StoreCategoryDto(sc.Id, sc.Name, sc.ImageUrl, 0);
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<MessageDto>> Update(int id, StoreCategoryRequest r)
    {
        var sc = await db.StoreCategories.FindAsync(id);
        if (sc == null) return NotFound(new MessageDto("Không tìm thấy danh mục quán"));
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên danh mục quán"));
        sc.Name = r.Name.Trim();
        sc.ImageUrl = r.ImageUrl;
        await db.SaveChangesAsync();
        return new MessageDto("Đã cập nhật");
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var sc = await db.StoreCategories.FindAsync(id);
        if (sc == null) return NotFound(new MessageDto("Không tìm thấy danh mục quán"));
        if (await db.Stores.AnyAsync(s => s.StoreCategoryId == id))
            return BadRequest(new MessageDto("Danh mục còn quán ăn, hãy xóa hoặc chuyển quán trước"));
        db.StoreCategories.Remove(sc);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }
}

// Quán ăn: ai cũng xem được, admin mới thêm/sửa/xóa
[ApiController]
[Route("api/stores")]
public class StoresController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<List<StoreDto>> GetAll(int? storeCategoryId, string? q, string? sort, int? take)
    {
        var query = db.Stores.Include(s => s.StoreCategory).Include(s => s.Products).AsQueryable();
        if (storeCategoryId.HasValue) query = query.Where(s => s.StoreCategoryId == storeCategoryId);
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(s => s.Name.Contains(q.Trim()));
        query = sort switch
        {
            "rating" => query.OrderByDescending(s => s.Rating),
            "bestseller" => query.OrderByDescending(s => s.SoldCount),
            "new" => query.OrderByDescending(s => s.CreatedAt).ThenByDescending(s => s.Id),
            _ => query.OrderBy(s => s.Id)
        };
        if (take is > 0) query = query.Take(take.Value);
        var list = await query.ToListAsync();
        return list.Select(s => s.ToDto()).ToList();
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<StoreDetailDto>> Get(int id)
    {
        var store = await db.Stores.Include(s => s.StoreCategory).Include(s => s.Products).FirstOrDefaultAsync(s => s.Id == id);
        if (store == null) return NotFound(new MessageDto("Không tìm thấy quán ăn"));
        var products = await db.Products.Include(p => p.Category).Include(p => p.Store).Include(p => p.Sizes)
            .Where(p => p.StoreId == id).OrderBy(p => p.Id).ToListAsync();
        return new StoreDetailDto(store.ToDto(), products.Select(p => p.ToDto()).ToList());
    }

    [HttpPost, Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<StoreDto>> Create(StoreRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên quán ăn"));
        if (!await db.StoreCategories.AnyAsync(c => c.Id == r.StoreCategoryId)) return BadRequest(new MessageDto("Danh mục quán không tồn tại"));
        var store = new Store
        {
            StoreCategoryId = r.StoreCategoryId, Name = r.Name.Trim(), Description = r.Description,
            ImageUrl = r.ImageUrl, Address = r.Address, Phone = r.Phone
        };
        db.Stores.Add(store);
        await db.SaveChangesAsync();
        await db.Entry(store).Reference(x => x.StoreCategory).LoadAsync();
        return store.ToDto();
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<StoreDto>> Update(int id, StoreRequest r)
    {
        var store = await db.Stores.Include(s => s.StoreCategory).Include(s => s.Products).FirstOrDefaultAsync(s => s.Id == id);
        if (store == null) return NotFound(new MessageDto("Không tìm thấy quán ăn"));
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên quán ăn"));
        if (!await db.StoreCategories.AnyAsync(c => c.Id == r.StoreCategoryId)) return BadRequest(new MessageDto("Danh mục quán không tồn tại"));
        store.StoreCategoryId = r.StoreCategoryId;
        store.Name = r.Name.Trim();
        store.Description = r.Description;
        store.ImageUrl = r.ImageUrl;
        store.Address = r.Address;
        store.Phone = r.Phone;
        await db.SaveChangesAsync();
        return store.ToDto();
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var store = await db.Stores.FindAsync(id);
        if (store == null) return NotFound(new MessageDto("Không tìm thấy quán ăn"));
        db.Stores.Remove(store);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }
}

// Danh mục: ai cũng xem được, admin mới thêm/sửa/xóa
[ApiController]
[Route("api/categories")]
public class CategoriesController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<List<CategoryDto>> GetAll() =>
        await db.Categories.OrderBy(c => c.Id)
            .Select(c => new CategoryDto(c.Id, c.Name, c.ImageUrl, c.Products.Count))
            .ToListAsync();

    [HttpPost, Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<CategoryDto>> Create(CategoryRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return BadRequest(new MessageDto("Vui lòng nhập tên danh mục"));
        var c = new Category { Name = r.Name.Trim(), ImageUrl = r.ImageUrl };
        db.Categories.Add(c);
        await db.SaveChangesAsync();
        return new CategoryDto(c.Id, c.Name, c.ImageUrl, 0);
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
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

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
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
    // Trang chủ, tìm kiếm, món theo danh mục, món theo quán ăn
    // sort: bestseller | new | price_asc | price_desc
    [HttpGet]
    public async Task<List<ProductDto>> GetAll(int? categoryId, int? storeId, string? q, string? sort, bool? isNew, int? take)
    {
        var query = db.Products.Include(p => p.Category).Include(p => p.Store).Include(p => p.Sizes).AsQueryable();
        if (categoryId.HasValue) query = query.Where(p => p.CategoryId == categoryId);
        if (storeId.HasValue) query = query.Where(p => p.StoreId == storeId);
        if (isNew == true) query = query.Where(p => p.IsNew);
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(p => p.Name.Contains(q.Trim()));
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

    // AddEditProductActivity
    [HttpPost, Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<ProductDto>> Create(ProductRequest r)
    {
        var err = await Validate(r);
        if (err != null) return BadRequest(new MessageDto(err));
        var p = new Product
        {
            CategoryId = r.CategoryId, StoreId = r.StoreId, Name = r.Name.Trim(), Description = r.Description, ImageUrl = r.ImageUrl, IsNew = r.IsNew,
            Sizes = r.Sizes.Select(s => new ProductSize { Size = s.Size, Price = s.Price }).ToList()
        };
        db.Products.Add(p);
        await db.SaveChangesAsync();
        await db.Entry(p).Reference(x => x.Category).LoadAsync();
        if (p.StoreId.HasValue) await db.Entry(p).Reference(x => x.Store).LoadAsync();
        return p.ToDto();
    }

    [HttpPut("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<ProductDto>> Update(int id, ProductRequest r)
    {
        var p = await db.Products.Include(x => x.Sizes).Include(x => x.Category).Include(x => x.Store).FirstOrDefaultAsync(x => x.Id == id);
        if (p == null) return NotFound(new MessageDto("Không tìm thấy món"));
        var err = await Validate(r);
        if (err != null) return BadRequest(new MessageDto(err));
        p.CategoryId = r.CategoryId;
        p.StoreId = r.StoreId;
        p.Name = r.Name.Trim();
        p.Description = r.Description;
        p.ImageUrl = r.ImageUrl;
        p.IsNew = r.IsNew;
        db.ProductSizes.RemoveRange(p.Sizes);
        p.Sizes = r.Sizes.Select(s => new ProductSize { Size = s.Size, Price = s.Price }).ToList();
        await db.SaveChangesAsync();
        await db.Entry(p).Reference(x => x.Category).LoadAsync();
        if (p.StoreId.HasValue) await db.Entry(p).Reference(x => x.Store).LoadAsync();
        return p.ToDto();
    }

    [HttpDelete("{id:int}"), Authorize(Roles = Roles.MenuManagers)]
    public async Task<ActionResult<MessageDto>> Delete(int id)
    {
        var p = await db.Products.FindAsync(id);
        if (p == null) return NotFound(new MessageDto("Không tìm thấy món"));
        if (await db.OrderItems.AnyAsync(i => i.ProductId == id))
            return BadRequest(new MessageDto("Món đã có trong đơn hàng nên không xóa được"));
        db.Products.Remove(p);
        await db.SaveChangesAsync();
        return new MessageDto("Đã xóa");
    }

    private async Task<string?> Validate(ProductRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.Name)) return "Vui lòng nhập tên món";
        if (!await db.Categories.AnyAsync(c => c.Id == r.CategoryId)) return "Danh mục món không tồn tại";
        if (r.StoreId.HasValue && !await db.Stores.AnyAsync(s => s.Id == r.StoreId.Value)) return "Quán ăn không tồn tại";
        if (r.Sizes == null || r.Sizes.Count == 0) return "Cần ít nhất 1 size có giá";
        if (r.Sizes.Any(s => s.Price <= 0)) return "Giá phải lớn hơn 0";
        if (r.Sizes.Select(s => s.Size).Distinct().Count() != r.Sizes.Count) return "Size bị trùng";
        return null;
    }
}
