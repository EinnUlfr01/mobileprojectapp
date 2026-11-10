using System.Net.Http.Headers;
using System.Text.Json;
using FoodOrder.API.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace FoodOrder.API.Common;

/// <summary>
/// Chuyển khoản bằng mã VietQR: quét bằng app ngân hàng (Vietcombank, MB, ...) hoặc MoMo, tiền vào thẳng tài khoản cửa hàng.
/// Thông tin tài khoản đặt trong appsettings.Local.json (không đưa lên GitHub).
/// </summary>
public class BankQrOptions
{
    public string BankId { get; set; } = "";       // mã BIN ngân hàng theo VietQR, vd Vietcombank = 970436
    public string BankName { get; set; } = "";
    public string AccountNo { get; set; } = "";
    public string AccountName { get; set; } = "";
    public int ExpireMinutes { get; set; } = 15;

    /// <summary>API token SePay (tùy chọn). Có token thì server tự kiểm tra tiền về, không cần admin bấm xác nhận</summary>
    public string SePayApiToken { get; set; } = "";

    public bool Enabled => !string.IsNullOrWhiteSpace(BankId) && !string.IsNullOrWhiteSpace(AccountNo);
    public bool AutoCheck => !string.IsNullOrWhiteSpace(SePayApiToken);
}

/// <summary>Tạo mã QR và tự kiểm tra tiền về qua API giao dịch của SePay</summary>
public class BankQrService(IOptions<BankQrOptions> options, IHttpClientFactory httpFactory, ILogger<BankQrService> logger)
{
    public const string Method = "Chuyển khoản QR";
    private readonly BankQrOptions _o = options.Value;
    private static DateTime _lastCheck = DateTime.MinValue;
    private static readonly SemaphoreSlim Lock = new(1, 1);

    public BankQrOptions Options => _o;

    // Nội dung chuyển khoản = mã đơn (chỉ chữ và số để ngân hàng không cắt mất)
    public static string TransferContent(Order o) => o.Code;

    // Ảnh mã VietQR chuẩn NAPAS (img.vietqr.io), đã điền sẵn số tiền + nội dung
    public string QrImageUrl(Order o) =>
        $"https://img.vietqr.io/image/{_o.BankId}-{_o.AccountNo}-compact2.png" +
        $"?amount={(long)o.Total}&addInfo={Uri.EscapeDataString(TransferContent(o))}&accountName={Uri.EscapeDataString(_o.AccountName)}";

    /// <summary>
    /// Hỏi SePay các giao dịch tiền vào gần đây, đơn nào có nội dung chứa mã đơn và đủ tiền thì đánh dấu đã thanh toán.
    /// Không cần địa chỉ public như webhook, chạy được khi API chạy trên laptop. Giới hạn 1 lần / 5 giây.
    /// </summary>
    public async Task CheckPaymentsAsync(AppDbContext db)
    {
        if (!_o.AutoCheck) return;
        if (!await Lock.WaitAsync(0)) return;
        try
        {
            if (DateTime.UtcNow - _lastCheck < TimeSpan.FromSeconds(5)) return;
            _lastCheck = DateTime.UtcNow;

            var pending = await db.Orders
                .Where(o => o.PaymentMethod == Method && !o.IsPaid && o.Status != OrderStatus.Cancelled
                            && o.CreatedAt > DateTime.UtcNow.AddDays(-2))
                .ToListAsync();
            if (pending.Count == 0) return;

            var http = httpFactory.CreateClient();
            http.Timeout = TimeSpan.FromSeconds(10);
            var req = new HttpRequestMessage(HttpMethod.Get,
                $"https://my.sepay.vn/userapi/transactions/list?account_number={Uri.EscapeDataString(_o.AccountNo)}&limit=50");
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _o.SePayApiToken);
            using var res = await http.SendAsync(req);
            if (!res.IsSuccessStatusCode)
            {
                logger.LogWarning("SePay trả lỗi {Status}", (int)res.StatusCode);
                return;
            }
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
            if (!doc.RootElement.TryGetProperty("transactions", out var list)) return;

            foreach (var t in list.EnumerateArray())
            {
                var content = t.TryGetProperty("transaction_content", out var c) ? c.GetString() ?? "" : "";
                var amountIn = t.TryGetProperty("amount_in", out var a) ? ParseDecimal(a) : 0;
                if (amountIn <= 0) continue;
                var normalized = content.Replace(" ", "").ToUpperInvariant();
                foreach (var o in pending.Where(o => !o.IsPaid))
                {
                    if (normalized.Contains(TransferContent(o).ToUpperInvariant()) && amountIn >= o.Total)
                    {
                        MarkPaid(db, o, "Đã nhận tiền chuyển khoản (SePay tự xác nhận)");
                        logger.LogInformation("Đơn {Code} đã nhận {Amount}đ qua chuyển khoản", o.Code, amountIn);
                    }
                }
            }
            await db.SaveChangesAsync();
        }
        catch (Exception e)
        {
            logger.LogWarning(e, "Không kiểm tra được giao dịch SePay");
        }
        finally
        {
            Lock.Release();
        }
    }

    private static decimal ParseDecimal(JsonElement e) => e.ValueKind switch
    {
        JsonValueKind.Number => e.GetDecimal(),
        JsonValueKind.String => decimal.TryParse(e.GetString(), System.Globalization.NumberStyles.Any,
            System.Globalization.CultureInfo.InvariantCulture, out var d) ? d : 0,
        _ => 0
    };

    // Đánh dấu đã thanh toán + ghi lịch sử + báo cho khách (dùng chung cho SePay và admin bấm xác nhận)
    public static void MarkPaid(AppDbContext db, Order o, string note)
    {
        if (o.IsPaid) return;
        o.IsPaid = true;
        db.OrderStatusLogs.Add(new OrderStatusLog { OrderId = o.Id, Status = o.Status, Note = note, ChangedBy = "Hệ thống thanh toán" });
        if (o.UserId != null)
            db.Notifications.Add(new Notification
            {
                UserId = o.UserId.Value, OrderId = o.Id,
                Title = $"Thanh toán thành công #{o.Code}",
                Body = $"Cửa hàng đã nhận {o.Total:N0}đ chuyển khoản."
            });
    }
}
