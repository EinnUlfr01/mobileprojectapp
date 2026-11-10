using System.Net;
using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace FoodOrder.API.Controllers;

/// <summary>
/// Thanh toán VNPay. Luồng:
/// 1. Đặt đơn chọn VNPay -> API trả về PaymentUrl
/// 2. App mở PaymentUrl trong WebView (trang VNPay sandbox, hoặc trang giả lập nếu chưa có khóa VNPay)
/// 3. Thanh toán xong VNPay chuyển về /api/payments/vnpay-return -> kiểm tra chữ ký, đánh dấu đơn đã thanh toán
/// </summary>
// Tạo link thanh toán VNPay (dùng chung cho OrdersController và PaymentsController)
public class VnPayService(IOptions<VnPayOptions> vnPay, IOptions<JwtOptions> jwt, IHttpContextAccessor http)
{
    public bool IsReal => vnPay.Value.Enabled;

    // Khóa ký: khóa VNPay thật, hoặc khóa riêng cho trang giả lập
    public string Secret => vnPay.Value.Enabled ? vnPay.Value.HashSecret : "MOCK-" + jwt.Value.Key;

    public string CreatePaymentUrl(Order o)
    {
        var ctx = http.HttpContext!;
        // Địa chỉ mà điện thoại đang gọi API (vd http://127.0.0.1:5090) để VNPay chuyển về đúng chỗ
        var host = $"{ctx.Request.Scheme}://{ctx.Request.Host}";
        var returnUrl = $"{host}/api/payments/vnpay-return";
        var ip = ctx.Connection.RemoteIpAddress?.MapToIPv4().ToString() ?? "127.0.0.1";
        var info = $"Thanh toan don hang {o.Code}";
        return vnPay.Value.Enabled
            ? VnPayHelper.CreatePaymentUrl(vnPay.Value.BaseUrl, vnPay.Value.TmnCode, Secret, o.Code, o.Total, info, returnUrl, ip)
            : VnPayHelper.CreatePaymentUrl($"{host}/api/payments/mock", "MOCK", Secret, o.Code, o.Total, info, returnUrl, ip);
    }
}

[ApiController]
[Route("api/payments")]
public class PaymentsController(AppDbContext db, VnPayService vnPay, BankQrService bankQr) : ControllerBase
{
    // Các cách thanh toán đang dùng được (màn đặt hàng gọi để hiện lựa chọn)
    [HttpGet("methods")]
    public List<string> Methods()
    {
        var list = new List<string> { "Tiền mặt", VnPayMethod };
        if (bankQr.Options.Enabled) list.Add(BankQrService.Method);
        return list;
    }

    // Thông tin mã QR chuyển khoản của 1 đơn (khách vãng lai cũng xem được nên tra theo mã đơn)
    [HttpGet("qr/{code}")]
    public async Task<ActionResult<BankQrDto>> Qr(string code)
    {
        if (!bankQr.Options.Enabled) return BadRequest(new MessageDto("Cửa hàng chưa cấu hình tài khoản nhận chuyển khoản"));
        var o = await db.Orders.FirstOrDefaultAsync(x => x.Code == code);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        if (o.PaymentMethod != BankQrService.Method) return BadRequest(new MessageDto("Đơn này không thanh toán bằng chuyển khoản"));
        var opt = bankQr.Options;
        return new BankQrDto(o.Code, bankQr.QrImageUrl(o), opt.BankName, opt.AccountNo, opt.AccountName, o.Total,
            BankQrService.TransferContent(o), o.IsPaid, o.CreatedAt.AddMinutes(opt.ExpireMinutes), opt.AutoCheck);
    }

    // App hỏi định kỳ xem đơn đã nhận tiền chưa (mỗi lần hỏi thì kiểm tra SePay nếu có token)
    [HttpGet("status/{code}")]
    public async Task<ActionResult<PaymentStatusDto>> Status(string code)
    {
        await bankQr.CheckPaymentsAsync(db);
        var o = await db.Orders.AsNoTracking().FirstOrDefaultAsync(x => x.Code == code);
        if (o == null) return NotFound(new MessageDto("Không tìm thấy đơn"));
        return new PaymentStatusDto(o.Code, o.IsPaid, o.Status);
    }

    public const string VnPayMethod = "VNPay";
    private string Secret => vnPay.Secret;

    // VNPay chuyển trình duyệt về đây sau khi thanh toán
    [HttpGet("vnpay-return")]
    public async Task<ContentResult> VnPayReturn()
    {
        var q = Request.Query;
        if (!VnPayHelper.ValidateSignature(Secret, q))
            return Page("Sai chữ ký", "Dữ liệu thanh toán không hợp lệ.", false);

        var code = q["vnp_TxnRef"].ToString();
        var o = await db.Orders.FirstOrDefaultAsync(x => x.Code == code);
        if (o == null) return Page("Không tìm thấy đơn", $"Mã đơn {code} không tồn tại.", false);

        var success = q["vnp_ResponseCode"].ToString() == "00" && q["vnp_TransactionStatus"].ToString() == "00";
        var amountOk = long.TryParse(q["vnp_Amount"].ToString(), out var amount) && amount == (long)Math.Round(o.Total * 100);
        if (success && amountOk)
        {
            if (!o.IsPaid)
            {
                o.IsPaid = true;
                if (o.UserId != null)
                    NotificationsController.Add(db, o.UserId.Value, $"Thanh toán thành công #{o.Code}",
                        $"Đã nhận {o.Total:N0}đ qua VNPay.", o.Id);
                await db.SaveChangesAsync();
            }
            return Page("Thanh toán thành công", $"Đơn #{o.Code} đã thanh toán {o.Total:N0}đ qua VNPay.", true);
        }
        return Page("Thanh toán chưa thành công", $"Đơn #{o.Code} vẫn được giữ. Bạn có thể thanh toán lại hoặc trả tiền mặt khi nhận hàng.", false);
    }

    // Trang thanh toán giả lập (chỉ dùng khi chưa có TmnCode/HashSecret VNPay)
    [HttpGet("mock")]
    public ContentResult Mock()
    {
        var q = Request.Query;
        if (vnPay.IsReal || !VnPayHelper.ValidateSignature(Secret, q))
            return Page("Không hợp lệ", "Link thanh toán không hợp lệ.", false);

        string ResultLink(string responseCode)
        {
            var values = new Dictionary<string, string>
            {
                ["vnp_TxnRef"] = q["vnp_TxnRef"].ToString(),
                ["vnp_Amount"] = q["vnp_Amount"].ToString(),
                ["vnp_ResponseCode"] = responseCode,
                ["vnp_TransactionStatus"] = responseCode,
                ["vnp_TransactionNo"] = DateTime.UtcNow.Ticks.ToString(),
                ["vnp_BankCode"] = "NCB"
            };
            return q["vnp_ReturnUrl"].ToString() + "?" + VnPayHelper.SignedQuery(Secret, values);
        }

        var amount = long.Parse(q["vnp_Amount"].ToString()) / 100;
        var html = $"""
            <h2 style="color:#005BAA;margin:0 0 4px">VNPAY</h2>
            <div style="color:#666;font-size:13px;margin-bottom:20px">Cổng thanh toán giả lập (chưa cấu hình khóa VNPay sandbox)</div>
            <div style="border:1px solid #ddd;border-radius:12px;padding:16px;margin-bottom:20px">
              <div>Đơn hàng: <b>{WebUtility.HtmlEncode(q["vnp_TxnRef"].ToString())}</b></div>
              <div>Nội dung: {WebUtility.HtmlEncode(q["vnp_OrderInfo"].ToString())}</div>
              <div style="font-size:24px;font-weight:bold;color:#D7263D;margin-top:8px">{amount:N0} VND</div>
            </div>
            <a href="{WebUtility.HtmlEncode(ResultLink("00"))}" style="{Btn("#005BAA", "#fff")}">Thanh toán</a>
            <a href="{WebUtility.HtmlEncode(ResultLink("24"))}" style="{Btn("#fff", "#333")}border:1px solid #ccc">Hủy giao dịch</a>
            """;
        return Html("VNPAY", html);
    }

    private static string Btn(string bg, string color) =>
        $"display:block;text-align:center;padding:14px;border-radius:10px;margin-bottom:10px;text-decoration:none;font-weight:bold;background:{bg};color:{color};";

    private static ContentResult Page(string title, string message, bool ok) =>
        Html(title, $"""
            <div style="text-align:center;margin-top:60px">
              <div style="font-size:56px;color:{(ok ? "#1E6B47" : "#B42318")}">{(ok ? "✓" : "✕")}</div>
              <h2>{WebUtility.HtmlEncode(title)}</h2>
              <p style="color:#555">{WebUtility.HtmlEncode(message)}</p>
              <p style="color:#999;font-size:13px">Đang quay lại ứng dụng…</p>
            </div>
            """);

    private static ContentResult Html(string title, string body) => new()
    {
        ContentType = "text/html; charset=utf-8",
        Content = $"""
            <!doctype html><html lang="vi"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><title>{WebUtility.HtmlEncode(title)}</title></head>
            <body style="font-family:sans-serif;padding:20px;max-width:480px;margin:auto">{body}</body></html>
            """
    };
}
