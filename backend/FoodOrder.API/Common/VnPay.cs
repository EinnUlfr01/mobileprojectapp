using System.Globalization;
using System.Net;
using System.Security.Cryptography;
using System.Text;

namespace FoodOrder.API.Common;

/// <summary>
/// Cấu hình VNPay Sandbox. Đăng ký miễn phí tại https://sandbox.vnpayment.vn/devreg/ để nhận TmnCode và HashSecret,
/// rồi điền vào appsettings.Local.json (file này không đưa lên GitHub).
/// Chưa điền thì API dùng trang thanh toán giả lập có giao diện tương tự để demo.
/// </summary>
public class VnPayOptions
{
    public string TmnCode { get; set; } = "";
    public string HashSecret { get; set; } = "";
    public string BaseUrl { get; set; } = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    public bool Enabled => !string.IsNullOrWhiteSpace(TmnCode) && !string.IsNullOrWhiteSpace(HashSecret);
}

/// <summary>
/// Tạo link thanh toán và kiểm tra chữ ký VNPay (phiên bản 2.1.0).
/// Chữ ký = HMAC-SHA512(HashSecret, chuỗi tham số vnp_* sắp xếp theo tên và đã URL-encode).
/// </summary>
public static class VnPayHelper
{
    private static readonly TimeZoneInfo VietnamTime = FindVietnamTimeZone();

    public static string CreatePaymentUrl(string baseUrl, string tmnCode, string secret, string txnRef, decimal amount,
        string orderInfo, string returnUrl, string ipAddress)
    {
        var local = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, VietnamTime);
        var data = new SortedDictionary<string, string>(StringComparer.Ordinal)
        {
            ["vnp_Version"] = "2.1.0",
            ["vnp_Command"] = "pay",
            ["vnp_TmnCode"] = tmnCode,
            ["vnp_Amount"] = ((long)Math.Round(amount * 100)).ToString(CultureInfo.InvariantCulture),
            ["vnp_CurrCode"] = "VND",
            ["vnp_TxnRef"] = txnRef,
            ["vnp_OrderInfo"] = orderInfo,
            ["vnp_OrderType"] = "other",
            ["vnp_Locale"] = "vn",
            ["vnp_ReturnUrl"] = returnUrl,
            ["vnp_IpAddr"] = ipAddress,
            ["vnp_CreateDate"] = local.ToString("yyyyMMddHHmmss", CultureInfo.InvariantCulture),
            ["vnp_ExpireDate"] = local.AddMinutes(15).ToString("yyyyMMddHHmmss", CultureInfo.InvariantCulture)
        };
        var query = BuildQuery(data);
        return $"{baseUrl}?{query}&vnp_SecureHash={Sign(secret, query)}";
    }

    /// <summary>Tạo chuỗi tham số kèm chữ ký (dùng cho trang giả lập trả kết quả về)</summary>
    public static string SignedQuery(string secret, IDictionary<string, string> values)
    {
        var data = new SortedDictionary<string, string>(values, StringComparer.Ordinal);
        var query = BuildQuery(data);
        return $"{query}&vnp_SecureHash={Sign(secret, query)}";
    }

    /// <summary>Kiểm tra chữ ký của dữ liệu VNPay gửi về</summary>
    public static bool ValidateSignature(string secret, IQueryCollection query)
    {
        var data = new SortedDictionary<string, string>(StringComparer.Ordinal);
        foreach (var pair in query)
        {
            var value = pair.Value.ToString();
            if (pair.Key.StartsWith("vnp_", StringComparison.Ordinal) &&
                pair.Key != "vnp_SecureHash" && pair.Key != "vnp_SecureHashType" &&
                !string.IsNullOrEmpty(value))
                data[pair.Key] = value;
        }
        var expected = Sign(secret, BuildQuery(data));
        return string.Equals(expected, query["vnp_SecureHash"].ToString(), StringComparison.OrdinalIgnoreCase);
    }

    private static string BuildQuery(SortedDictionary<string, string> data) =>
        string.Join("&", data
            .Where(kv => !string.IsNullOrEmpty(kv.Value))
            .Select(kv => $"{WebUtility.UrlEncode(kv.Key)}={WebUtility.UrlEncode(kv.Value)}"));

    private static string Sign(string secret, string data)
    {
        using var hmac = new HMACSHA512(Encoding.UTF8.GetBytes(secret));
        return Convert.ToHexString(hmac.ComputeHash(Encoding.UTF8.GetBytes(data))).ToLowerInvariant();
    }

    // Windows dùng "SE Asia Standard Time", Linux/macOS dùng "Asia/Ho_Chi_Minh"
    private static TimeZoneInfo FindVietnamTimeZone()
    {
        foreach (var id in new[] { "Asia/Ho_Chi_Minh", "SE Asia Standard Time" })
        {
            try { return TimeZoneInfo.FindSystemTimeZoneById(id); }
            catch (TimeZoneNotFoundException) { }
            catch (InvalidTimeZoneException) { }
        }
        return TimeZoneInfo.CreateCustomTimeZone("UTC+07", TimeSpan.FromHours(7), "UTC+07", "UTC+07");
    }
}
