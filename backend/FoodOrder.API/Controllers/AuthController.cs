using System.Net.Mail;
using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController(AppDbContext db, IPasswordHasher<User> hasher, TokenService tokens) : ControllerBase
{
    // RegisterActivity
    [HttpPost("register")]
    public async Task<ActionResult<LoginResponse>> Register(RegisterRequest r)
    {
        if (string.IsNullOrWhiteSpace(r.FullName)) return BadRequest(new MessageDto("Vui lòng nhập họ tên"));
        if (!MailAddress.TryCreate(r.Email?.Trim(), out _)) return BadRequest(new MessageDto("Email không đúng định dạng"));
        if (!Mapping.IsValidPhone(r.Phone)) return BadRequest(new MessageDto("Số điện thoại phải đủ 10 số"));
        if (string.IsNullOrEmpty(r.Password) || r.Password.Length < 6) return BadRequest(new MessageDto("Mật khẩu tối thiểu 6 ký tự"));

        var email = r.Email!.Trim().ToLower();
        if (await db.Users.AnyAsync(u => u.Email == email)) return Conflict(new MessageDto("Email đã được đăng ký"));
        if (await db.Users.AnyAsync(u => u.Phone == r.Phone)) return Conflict(new MessageDto("Số điện thoại đã được đăng ký"));

        var u = new User { FullName = r.FullName.Trim(), Email = email, Phone = r.Phone, Role = Roles.User };
        u.PasswordHash = hasher.HashPassword(u, r.Password);
        db.Users.Add(u);
        await db.SaveChangesAsync();
        return new LoginResponse(tokens.Create(u), u.ToDto());
    }

    // LoginActivity: đăng nhập bằng email hoặc SĐT
    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login(LoginRequest r)
    {
        var key = (r.EmailOrPhone ?? "").Trim().ToLower();
        var u = await db.Users.FirstOrDefaultAsync(x => x.Email == key || x.Phone == key);
        if (u == null || hasher.VerifyHashedPassword(u, u.PasswordHash, r.Password ?? "") == PasswordVerificationResult.Failed)
            return Unauthorized(new MessageDto("Sai tài khoản hoặc mật khẩu"));
        return new LoginResponse(tokens.Create(u), u.ToDto());
    }
}

// ProfileActivity
[ApiController]
[Route("api/me")]
[Authorize]
public class MeController(AppDbContext db, IPasswordHasher<User> hasher) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<UserDto>> Get()
    {
        var u = await db.Users.FindAsync(User.UserId());
        return u == null ? NotFound() : u.ToDto();
    }

    [HttpPut]
    public async Task<ActionResult<UserDto>> Update(UpdateProfileRequest r)
    {
        var u = await db.Users.FindAsync(User.UserId());
        if (u == null) return NotFound();
        if (string.IsNullOrWhiteSpace(r.FullName)) return BadRequest(new MessageDto("Vui lòng nhập họ tên"));
        if (!Mapping.IsValidPhone(r.Phone)) return BadRequest(new MessageDto("Số điện thoại phải đủ 10 số"));
        if (await db.Users.AnyAsync(x => x.Phone == r.Phone && x.Id != u.Id)) return Conflict(new MessageDto("Số điện thoại đã có người dùng"));
        u.FullName = r.FullName.Trim();
        u.Phone = r.Phone;
        u.DefaultAddress = r.DefaultAddress?.Trim();
        await db.SaveChangesAsync();
        return u.ToDto();
    }

    [HttpPut("password")]
    public async Task<ActionResult<MessageDto>> ChangePassword(ChangePasswordRequest r)
    {
        var u = await db.Users.FindAsync(User.UserId());
        if (u == null) return NotFound();
        if (hasher.VerifyHashedPassword(u, u.PasswordHash, r.OldPassword ?? "") == PasswordVerificationResult.Failed)
            return BadRequest(new MessageDto("Mật khẩu cũ không đúng"));
        if (string.IsNullOrEmpty(r.NewPassword) || r.NewPassword.Length < 6) return BadRequest(new MessageDto("Mật khẩu mới tối thiểu 6 ký tự"));
        u.PasswordHash = hasher.HashPassword(u, r.NewPassword);
        await db.SaveChangesAsync();
        return new MessageDto("Đã đổi mật khẩu");
    }
}
