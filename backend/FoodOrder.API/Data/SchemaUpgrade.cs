using Microsoft.EntityFrameworkCore;

namespace FoodOrder.API.Data;

// EnsureCreated chỉ tạo bảng khi database chưa có.
// Database đã tạo từ bản trước thì chạy các lệnh dưới đây để thêm bảng/cột mới (chạy nhiều lần không sao).
public static class SchemaUpgrade
{
    private static readonly string[] Scripts =
    {
        // Cột mã giảm giá trong DonHang
        """
        IF COL_LENGTH('DonHang', 'VoucherCode') IS NULL
            ALTER TABLE DonHang ADD VoucherCode nvarchar(30) NULL;
        """,
        """
        IF COL_LENGTH('DonHang', 'Discount') IS NULL
            ALTER TABLE DonHang ADD Discount decimal(18,0) NOT NULL CONSTRAINT DF_DonHang_Discount DEFAULT 0;
        """,
        """
        IF OBJECT_ID(N'HoiThoai', N'U') IS NULL
        BEGIN
        CREATE TABLE HoiThoai (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_HoiThoai PRIMARY KEY,
            CustomerId int NOT NULL CONSTRAINT FK_HoiThoai_NguoiDung_CustomerId REFERENCES NguoiDung(Id) ON DELETE CASCADE,
            LastMessage nvarchar(1000) NULL,
            LastMessageAt datetime2 NOT NULL,
            UnreadByStaff int NOT NULL,
            UnreadByCustomer int NOT NULL,
            CONSTRAINT IX_HoiThoai_CustomerId UNIQUE (CustomerId)
        );
        END
        """,
        """
        IF OBJECT_ID(N'TinNhan', N'U') IS NULL
        BEGIN
        CREATE TABLE TinNhan (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_TinNhan PRIMARY KEY,
            ConversationId int NOT NULL CONSTRAINT FK_TinNhan_HoiThoai_ConversationId REFERENCES HoiThoai(Id) ON DELETE CASCADE,
            SenderId int NOT NULL,
            SenderName nvarchar(200) NOT NULL,
            IsFromStaff bit NOT NULL,
            Content nvarchar(1000) NOT NULL,
            CreatedAt datetime2 NOT NULL
        );
        CREATE INDEX IX_TinNhan_ConversationId_Id ON TinNhan(ConversationId, Id);
        END
        """,
        """
        IF OBJECT_ID(N'ThongBao', N'U') IS NULL
        BEGIN
        CREATE TABLE ThongBao (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_ThongBao PRIMARY KEY,
            UserId int NOT NULL CONSTRAINT FK_ThongBao_NguoiDung_UserId REFERENCES NguoiDung(Id) ON DELETE CASCADE,
            Title nvarchar(200) NOT NULL,
            Body nvarchar(500) NOT NULL,
            OrderId int NULL,
            IsRead bit NOT NULL,
            CreatedAt datetime2 NOT NULL
        );
        CREATE INDEX IX_ThongBao_UserId_Id ON ThongBao(UserId, Id);
        END
        """,
        """
        IF OBJECT_ID(N'MaGiamGia', N'U') IS NULL
        BEGIN
        CREATE TABLE MaGiamGia (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_MaGiamGia PRIMARY KEY,
            Code nvarchar(30) NOT NULL CONSTRAINT IX_MaGiamGia_Code UNIQUE,
            Description nvarchar(300) NOT NULL,
            Type nvarchar(20) NOT NULL,
            Value decimal(18,0) NOT NULL,
            MinOrder decimal(18,0) NOT NULL,
            MaxDiscount decimal(18,0) NOT NULL,
            UsageLimit int NOT NULL,
            UsedCount int NOT NULL,
            ExpiresAt datetime2 NULL,
            IsActive bit NOT NULL
        );
        END
        """,
        """
        IF OBJECT_ID(N'YeuThich', N'U') IS NULL
        BEGIN
        CREATE TABLE YeuThich (
            UserId int NOT NULL CONSTRAINT FK_YeuThich_NguoiDung_UserId REFERENCES NguoiDung(Id) ON DELETE CASCADE,
            ProductId int NOT NULL CONSTRAINT FK_YeuThich_SanPham_ProductId REFERENCES SanPham(Id) ON DELETE CASCADE,
            CreatedAt datetime2 NOT NULL,
            CONSTRAINT PK_YeuThich PRIMARY KEY (UserId, ProductId)
        );
        END
        """
    };

    public static async Task RunAsync(AppDbContext db)
    {
        foreach (var sql in Scripts)
            await db.Database.ExecuteSqlRawAsync(sql);
    }
}
