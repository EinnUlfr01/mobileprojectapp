using System.Text;
using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Scalar.AspNetCore;

var builder = WebApplication.CreateBuilder(args);

// Cấu hình riêng trên máy (khóa VNPay sandbox...): không đưa lên GitHub
builder.Configuration.AddJsonFile("appsettings.Local.json", optional: true, reloadOnChange: true);

builder.Services.AddDbContext<AppDbContext>(o =>
    o.UseSqlServer(builder.Configuration.GetConnectionString("Default")));

// ---------- JWT ----------
var jwtSection = builder.Configuration.GetSection("Jwt");
builder.Services.Configure<JwtOptions>(jwtSection);
var jwt = jwtSection.Get<JwtOptions>()!;
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(o =>
    {
        o.MapInboundClaims = false;
        o.TokenValidationParameters = new TokenValidationParameters
        {
            ValidIssuer = jwt.Issuer,
            ValidAudience = jwt.Audience,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt.Key)),
            NameClaimType = "name",
            RoleClaimType = "role"
        };
        // SignalR qua WebSocket có thể gửi token trên URL (?access_token=...)
        o.Events = new JwtBearerEvents
        {
            OnMessageReceived = ctx =>
            {
                var token = ctx.Request.Query["access_token"].ToString();
                if (!string.IsNullOrEmpty(token) && ctx.HttpContext.Request.Path.StartsWithSegments("/hubs"))
                    ctx.Token = token;
                return Task.CompletedTask;
            }
        };
    });
builder.Services.AddAuthorization();

builder.Services.AddSingleton<IPasswordHasher<User>, PasswordHasher<User>>();
builder.Services.AddSingleton<TokenService>();
builder.Services.Configure<VnPayOptions>(builder.Configuration.GetSection("VnPay"));
builder.Services.Configure<BankQrOptions>(builder.Configuration.GetSection("BankQr"));
builder.Services.AddHttpClient();
builder.Services.AddScoped<BankQrService>();
builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<FoodOrder.API.Controllers.VnPayService>();
builder.Services.AddControllers();
builder.Services.AddSignalR(); // chat realtime
builder.Services.AddOpenApi();

var app = builder.Build();

// Tạo DB DoAnAppGiaoDoAn + dữ liệu mẫu (admin@gmail.com / Admin@123, user@gmail.com / 123456)
using (var scope = app.Services.CreateScope())
{
    await DbSeeder.SeedAsync(scope.ServiceProvider);
}

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
    app.MapScalarApiReference(); // mở http://localhost:5090/scalar để thử API
}

app.UseStaticFiles(); // phục vụ ảnh trong wwwroot/images
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapHub<FoodOrder.API.Hubs.ChatHub>("/hubs/chat");
app.MapGet("/", () => "FoodOrder API đang chạy. Xem tài liệu tại /scalar");

app.Run();
