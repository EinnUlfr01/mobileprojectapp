using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Món yêu thích của thành viên
[ApiController]
[Route("api/favorites")]
[Authorize]
public class FavoritesController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<List<ProductDto>> Get()
    {
        var uid = User.UserId();
        var ids = await db.Favorites.Where(f => f.UserId == uid).OrderByDescending(f => f.CreatedAt)
            .Select(f => f.ProductId).ToListAsync();
        var products = await db.Products.Include(p => p.Category).Include(p => p.Sizes)
            .Where(p => ids.Contains(p.Id)).ToListAsync();
        return products.OrderBy(p => ids.IndexOf(p.Id)).Select(p => p.ToDto()).ToList();
    }

    [HttpPost("{productId:int}")]
    public async Task<ActionResult<MessageDto>> Add(int productId)
    {
        var uid = User.UserId()!.Value;
        if (!await db.Products.AnyAsync(p => p.Id == productId)) return NotFound(new MessageDto("Không tìm thấy món"));
        if (!await db.Favorites.AnyAsync(f => f.UserId == uid && f.ProductId == productId))
        {
            db.Favorites.Add(new Favorite { UserId = uid, ProductId = productId });
            await db.SaveChangesAsync();
        }
        return new MessageDto("Đã thêm vào yêu thích");
    }

    [HttpDelete("{productId:int}")]
    public async Task<MessageDto> Remove(int productId)
    {
        var uid = User.UserId();
        var f = await db.Favorites.FirstOrDefaultAsync(x => x.UserId == uid && x.ProductId == productId);
        if (f != null)
        {
            db.Favorites.Remove(f);
            await db.SaveChangesAsync();
        }
        return new MessageDto("Đã bỏ yêu thích");
    }
}
