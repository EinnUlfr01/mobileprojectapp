using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;

namespace FoodOrder.API.Hubs;

// Kênh realtime (SignalR): app giữ kết nối mở, server đẩy tin nhắn mới xuống ngay lập tức.
// - Nhân viên (3 vai trò quản trị) vào nhóm "staff": nhận mọi tin khách gửi
// - Khách hàng vào nhóm "customer-{id}": nhận tin cửa hàng trả lời
// App chỉ nghe sự kiện "message"; gửi tin vẫn qua API /api/chat (ChatController).
[Authorize]
public class ChatHub : Hub
{
    public const string StaffGroup = "staff";
    public static string CustomerGroup(int userId) => $"customer-{userId}";

    public override async Task OnConnectedAsync()
    {
        var uid = Context.User?.FindFirst("sub")?.Value;
        var role = Context.User?.FindFirst("role")?.Value ?? "";
        if (Roles.IsStaff(role))
            await Groups.AddToGroupAsync(Context.ConnectionId, StaffGroup);
        else if (int.TryParse(uid, out var id))
            await Groups.AddToGroupAsync(Context.ConnectionId, CustomerGroup(id));
        await base.OnConnectedAsync();
    }
}
