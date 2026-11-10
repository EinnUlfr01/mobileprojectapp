using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace FoodOrder.API.Controllers;

// Upload ảnh món / danh mục từ điện thoại. Ảnh lưu ở wwwroot/uploads, trả về đường dẫn "uploads/xxx.jpg"
[ApiController]
[Route("api/uploads")]
[Authorize(Roles = Roles.MenuManagers)]
public class UploadsController(IWebHostEnvironment env) : ControllerBase
{
    private static readonly string[] AllowedExt = { ".jpg", ".jpeg", ".png", ".webp" };
    private const long MaxBytes = 5 * 1024 * 1024; // 5 MB

    public record UploadDto(string Url);

    [HttpPost]
    [RequestSizeLimit(MaxBytes + 1024 * 1024)]
    public async Task<ActionResult<UploadDto>> Upload(IFormFile file)
    {
        if (file == null || file.Length == 0) return BadRequest(new MessageDto("Chưa chọn ảnh"));
        if (file.Length > MaxBytes) return BadRequest(new MessageDto("Ảnh tối đa 5MB"));
        var ext = Path.GetExtension(file.FileName).ToLowerInvariant();
        if (string.IsNullOrEmpty(ext)) ext = ".jpg";
        if (!AllowedExt.Contains(ext)) return BadRequest(new MessageDto("Chỉ nhận ảnh .jpg, .png, .webp"));

        var dir = Path.Combine(env.ContentRootPath, "wwwroot", "uploads");
        Directory.CreateDirectory(dir);
        var name = $"{Guid.NewGuid():N}{ext}";
        await using (var stream = System.IO.File.Create(Path.Combine(dir, name)))
            await file.CopyToAsync(stream);
        return new UploadDto("uploads/" + name);
    }
}
