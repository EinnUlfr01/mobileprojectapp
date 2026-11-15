using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// CartActivity (user đã đăng nhập). Giỏ của khách vãng lai lưu trên máy.
// Giống GrabFood: mỗi giỏ chỉ chứa món của 1 quán (1 đơn = 1 quán = 1 shipper).
[ApiController]
[Route("api/cart")]
[Authorize]
public class CartController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<CartDto> Get()
    {
        var uid = User.UserId()!.Value;
        var items = await db.CartItems.Where(c => c.UserId == uid)
            .Include(c => c.Product!).ThenInclude(p => p.Sizes)
            .Include(c => c.Product!).ThenInclude(p => p.Store)
            .OrderBy(c => c.Id).ToListAsync();
        var dtos = items.Select(c =>
        {
            var price = c.Product!.Sizes.FirstOrDefault(s => s.Size == c.Size)?.Price ?? 0;
            return new CartItemDto(c.Id, c.ProductId, c.Product.Name, c.Product.ImageUrl, c.Size, c.Quantity, price, price * c.Quantity);
        }).ToList();
        var store = items.FirstOrDefault()?.Product?.Store;
        return new CartDto(dtos, dtos.Count, dtos.Sum(x => x.Quantity), dtos.Sum(x => x.LineTotal), store?.Id, store?.Name);
    }

    [HttpPost]
    public async Task<ActionResult<CartDto>> Add(AddCartRequest r)
    {
        var uid = User.UserId()!.Value;
        if (r.Quantity < 1) return BadRequest(new MessageDto("Số lượng phải lớn hơn 0"));
        if (!await db.ProductSizes.AnyAsync(s => s.ProductId == r.ProductId && s.Size == r.Size))
            return BadRequest(new MessageDto("Món hoặc size không tồn tại"));
        var product = await db.Products.Include(p => p.Store).FirstAsync(p => p.Id == r.ProductId);
        if (product.Store is { IsActive: false }) return BadRequest(new MessageDto("Quán đang tạm ngừng hoạt động"));
        // Giỏ đang có món của quán khác -> trả 409 để app hỏi "Xóa giỏ cũ và thêm món này?"
        var otherStore = await db.CartItems.Where(c => c.UserId == uid && c.Product!.StoreId != product.StoreId)
            .Select(c => c.Product!.Store!.Name).FirstOrDefaultAsync();
        if (otherStore != null)
            return Conflict(new MessageDto($"Giỏ hàng đang có món của {otherStore}. Mỗi đơn chỉ đặt món của 1 quán."));
        var existing = await db.CartItems.FirstOrDefaultAsync(c => c.UserId == uid && c.ProductId == r.ProductId && c.Size == r.Size);
        if (existing != null) existing.Quantity += r.Quantity;
        else db.CartItems.Add(new CartItem { UserId = uid, ProductId = r.ProductId, Size = r.Size, Quantity = r.Quantity });
        await db.SaveChangesAsync();
        return await Get();
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<CartDto>> Update(int id, UpdateCartRequest r)
    {
        var uid = User.UserId()!.Value;
        var item = await db.CartItems.FirstOrDefaultAsync(c => c.Id == id && c.UserId == uid);
        if (item == null) return NotFound(new MessageDto("Không tìm thấy món trong giỏ"));
        if (r.Quantity <= 0) db.CartItems.Remove(item);
        else item.Quantity = r.Quantity;
        await db.SaveChangesAsync();
        return await Get();
    }

    // Xóa hết giỏ (khi muốn đổi sang đặt quán khác)
    [HttpDelete]
    public async Task<CartDto> Clear()
    {
        var uid = User.UserId()!.Value;
        db.CartItems.RemoveRange(db.CartItems.Where(c => c.UserId == uid));
        await db.SaveChangesAsync();
        return await Get();
    }

    [HttpDelete("{id:int}")]
    public async Task<ActionResult<CartDto>> Delete(int id)
    {
        var uid = User.UserId()!.Value;
        var item = await db.CartItems.FirstOrDefaultAsync(c => c.Id == id && c.UserId == uid);
        if (item != null)
        {
            db.CartItems.Remove(item);
            await db.SaveChangesAsync();
        }
        return await Get();
    }
}
