using FoodOrder.API.Common;
using FoodOrder.API.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Controllers;

// Chat trực tuyến giữa khách hàng và cửa hàng.
// App gọi lại mỗi vài giây với afterId = id tin cuối cùng đã có để lấy tin mới (cơ chế polling).
[ApiController]
[Route("api/chat")]
[Authorize]
public class ChatController(AppDbContext db) : ControllerBase
{
    private const int MaxLength = 1000;

    private static ChatMessageDto ToDto(ChatMessage m) => new(m.Id, m.IsFromStaff, m.SenderName, m.Content, m.CreatedAt);

    private async Task<User?> Me() => await db.Users.FindAsync(User.UserId());

    private async Task<Conversation> GetOrCreateConversation(int customerId)
    {
        var c = await db.Conversations.FirstOrDefaultAsync(x => x.CustomerId == customerId);
        if (c != null) return c;
        c = new Conversation { CustomerId = customerId };
        db.Conversations.Add(c);
        await db.SaveChangesAsync();
        return c;
    }

    private async Task<List<ChatMessageDto>> Messages(int conversationId, int afterId)
    {
        var q = db.ChatMessages.Where(m => m.ConversationId == conversationId && m.Id > afterId).OrderBy(m => m.Id);
        // Lần đầu (afterId = 0) chỉ lấy 100 tin gần nhất
        var list = afterId == 0
            ? (await q.OrderByDescending(m => m.Id).Take(100).ToListAsync()).OrderBy(m => m.Id).ToList()
            : await q.ToListAsync();
        return list.Select(ToDto).ToList();
    }

    // ===================== Phía khách hàng =====================

    [HttpGet("me")]
    public async Task<ActionResult<ChatThreadDto>> MyThread(int afterId = 0)
    {
        var me = await Me();
        if (me == null) return Unauthorized();
        var c = await GetOrCreateConversation(me.Id);
        var msgs = await Messages(c.Id, afterId);
        if (c.UnreadByCustomer > 0)
        {
            c.UnreadByCustomer = 0; // khách đang xem -> đã đọc
            await db.SaveChangesAsync();
        }
        return new ChatThreadDto(c.Id, me.FullName, msgs);
    }

    [HttpPost("me")]
    public async Task<ActionResult<ChatMessageDto>> SendToShop(SendMessageRequest r)
    {
        var me = await Me();
        if (me == null) return Unauthorized();
        var content = (r.Content ?? "").Trim();
        if (content.Length == 0) return BadRequest(new MessageDto("Tin nhắn trống"));
        if (content.Length > MaxLength) return BadRequest(new MessageDto("Tin nhắn tối đa 1000 ký tự"));

        var c = await GetOrCreateConversation(me.Id);
        var m = new ChatMessage { ConversationId = c.Id, SenderId = me.Id, SenderName = me.FullName, IsFromStaff = false, Content = content };
        db.ChatMessages.Add(m);
        c.LastMessage = content;
        c.LastMessageAt = m.CreatedAt;
        c.UnreadByStaff++;
        await db.SaveChangesAsync();
        return ToDto(m);
    }

    // Số tin cửa hàng gửi mà khách chưa đọc (khách) / tổng tin khách gửi chưa trả lời (nhân viên)
    [HttpGet("unread")]
    public async Task<CountDto> Unread()
    {
        var uid = User.UserId();
        var isStaff = User.IsInRole(Roles.Admin) || User.IsInRole(Roles.MenuManager) || User.IsInRole(Roles.OrderStaff);
        var count = isStaff
            ? await db.Conversations.SumAsync(c => (int?)c.UnreadByStaff) ?? 0
            : await db.Conversations.Where(c => c.CustomerId == uid).Select(c => (int?)c.UnreadByCustomer).FirstOrDefaultAsync() ?? 0;
        return new CountDto(count);
    }

    // ===================== Phía cửa hàng (mọi vai trò quản trị) =====================

    [HttpGet("conversations")]
    [Authorize(Roles = Roles.AllStaff)]
    public async Task<List<ConversationDto>> Conversations(string? q)
    {
        var query = db.Conversations.Include(c => c.Customer).Where(c => c.LastMessage != null);
        if (!string.IsNullOrWhiteSpace(q))
            query = query.Where(c => c.Customer!.FullName.Contains(q) || c.Customer.Phone.Contains(q));
        var list = await query.OrderByDescending(c => c.UnreadByStaff > 0).ThenByDescending(c => c.LastMessageAt).ToListAsync();
        return list.Select(c => new ConversationDto(c.Id, c.CustomerId, c.Customer?.FullName ?? "(đã xóa)",
            c.Customer?.Phone ?? "", c.LastMessage, c.LastMessageAt, c.UnreadByStaff)).ToList();
    }

    [HttpGet("conversations/{id:int}")]
    [Authorize(Roles = Roles.AllStaff)]
    public async Task<ActionResult<ChatThreadDto>> Thread(int id, int afterId = 0)
    {
        var c = await db.Conversations.Include(x => x.Customer).FirstOrDefaultAsync(x => x.Id == id);
        if (c == null) return NotFound(new MessageDto("Không tìm thấy cuộc trò chuyện"));
        var msgs = await Messages(c.Id, afterId);
        if (c.UnreadByStaff > 0)
        {
            c.UnreadByStaff = 0;
            await db.SaveChangesAsync();
        }
        return new ChatThreadDto(c.Id, c.Customer?.FullName ?? "(đã xóa)", msgs);
    }

    [HttpPost("conversations/{id:int}")]
    [Authorize(Roles = Roles.AllStaff)]
    public async Task<ActionResult<ChatMessageDto>> Reply(int id, SendMessageRequest r)
    {
        var me = await Me();
        if (me == null) return Unauthorized();
        var c = await db.Conversations.FindAsync(id);
        if (c == null) return NotFound(new MessageDto("Không tìm thấy cuộc trò chuyện"));
        var content = (r.Content ?? "").Trim();
        if (content.Length == 0) return BadRequest(new MessageDto("Tin nhắn trống"));
        if (content.Length > MaxLength) return BadRequest(new MessageDto("Tin nhắn tối đa 1000 ký tự"));

        // Khách thấy tên "Cửa hàng" kèm tên nhân viên trả lời
        var m = new ChatMessage { ConversationId = c.Id, SenderId = me.Id, SenderName = "Cửa hàng · " + me.FullName, IsFromStaff = true, Content = content };
        db.ChatMessages.Add(m);
        c.LastMessage = content;
        c.LastMessageAt = m.CreatedAt;
        c.UnreadByCustomer++;
        c.UnreadByStaff = 0;
        await db.SaveChangesAsync();
        return ToDto(m);
    }
}
