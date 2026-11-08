using System.Security.Claims;
using System.Text;
using FoodOrder.API.Data;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;

namespace FoodOrder.API.Common;

public class JwtOptions
{
    public string Key { get; set; } = "";
    public string Issuer { get; set; } = "";
    public string Audience { get; set; } = "";
    public int ExpireMinutes { get; set; } = 10080;
}

public class TokenService(IOptions<JwtOptions> options)
{
    private readonly JwtOptions _o = options.Value;

    public string Create(User u)
    {
        var claims = new[]
        {
            new Claim("sub", u.Id.ToString()),
            new Claim("name", u.FullName),
            new Claim("role", u.Role)
        };
        return new JsonWebTokenHandler().CreateToken(new SecurityTokenDescriptor
        {
            Issuer = _o.Issuer,
            Audience = _o.Audience,
            Expires = DateTime.UtcNow.AddMinutes(_o.ExpireMinutes),
            Subject = new ClaimsIdentity(claims),
            SigningCredentials = new SigningCredentials(
                new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_o.Key)), SecurityAlgorithms.HmacSha256)
        });
    }
}

public static class ClaimsExtensions
{
    // Lấy id user từ token, trả về null nếu là khách vãng lai
    public static int? UserId(this ClaimsPrincipal p)
    {
        var v = p.FindFirst("sub")?.Value;
        return int.TryParse(v, out var id) ? id : null;
    }
}
