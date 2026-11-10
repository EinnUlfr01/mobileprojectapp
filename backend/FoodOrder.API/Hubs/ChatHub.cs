using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Hubs;

// Kênh realtime (SignalR): app giữ kết nối mở, server đẩy tin nhắn mới xuống ngay lập tức.
// - Nhân viên (3 vai trò quản trị) vào nhóm "staff": nhận mọi tin khách gửi
// - Khách hàng vào nhóm "customer-{id}": nhận tin cửa hàng trả lời
// Sự kiện server -> app: "message" (tin mới), "typing" (bên kia đang nhập)
// Gửi tin vẫn qua API /api/chat (ChatController); app chỉ gọi hub để báo "đang nhập".
[Authorize]
public class ChatHub(AppDbContext db) : Hub
{
    public const string StaffGroup = "staff";
    public static string CustomerGroup(int userId) => $"customer-{userId}";

    private int? UserId => int.TryParse(Context.User?.FindFirst("sub")?.Value, out var id) ? id : null;
    private bool IsStaff => Roles.IsStaff(Context.User?.FindFirst("role")?.Value ?? "");

    public override async Task OnConnectedAsync()
    {
        if (IsStaff)
            await Groups.AddToGroupAsync(Context.ConnectionId, StaffGroup);
        else if (UserId is int id)
            await Groups.AddToGroupAsync(Context.ConnectionId, CustomerGroup(id));
        await base.OnConnectedAsync();
    }

    // App gọi khi người dùng đang gõ phím (tối đa 1 lần / 2 giây)
    // Nhân viên truyền id cuộc trò chuyện; khách truyền 0 (server tự tìm cuộc trò chuyện của khách)
    public async Task Typing(int conversationId)
    {
        if (IsStaff)
        {
            var c = await db.Conversations.FindAsync(conversationId);
            if (c != null)
                await Clients.Group(CustomerGroup(c.CustomerId)).SendAsync("typing", c.Id, true);
        }
        else if (UserId is int uid)
        {
            var c = await db.Conversations.FirstOrDefaultAsync(x => x.CustomerId == uid);
            if (c != null)
                await Clients.Group(StaffGroup).SendAsync("typing", c.Id, false);
        }
    }
}
