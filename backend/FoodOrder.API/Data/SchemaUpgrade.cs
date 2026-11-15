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
        IF COL_LENGTH('DonHang', 'CancelReason') IS NULL
            ALTER TABLE DonHang ADD CancelReason nvarchar(300) NULL;
        """,
        """
        IF OBJECT_ID(N'LichSuDonHang', N'U') IS NULL
        BEGIN
        CREATE TABLE LichSuDonHang (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_LichSuDonHang PRIMARY KEY,
            OrderId int NOT NULL CONSTRAINT FK_LichSuDonHang_DonHang_OrderId REFERENCES DonHang(Id) ON DELETE CASCADE,
            Status nvarchar(30) NOT NULL,
            Note nvarchar(300) NULL,
            ChangedBy nvarchar(200) NOT NULL,
            CreatedAt datetime2 NOT NULL
        );
        CREATE INDEX IX_LichSuDonHang_OrderId ON LichSuDonHang(OrderId);
        END
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
        """,
        """
        IF OBJECT_ID(N'DanhMucQuanAn', N'U') IS NULL
        BEGIN
        CREATE TABLE DanhMucQuanAn (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_DanhMucQuanAn PRIMARY KEY,
            Name nvarchar(200) NOT NULL,
            ImageUrl nvarchar(500) NULL
        );
        END
        """,
        """
        IF OBJECT_ID(N'QuanAn', N'U') IS NULL
        BEGIN
        CREATE TABLE QuanAn (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_QuanAn PRIMARY KEY,
            StoreCategoryId int NOT NULL CONSTRAINT FK_QuanAn_DanhMucQuanAn_StoreCategoryId REFERENCES DanhMucQuanAn(Id) ON DELETE NO ACTION,
            Name nvarchar(200) NOT NULL,
            Description nvarchar(1000) NULL,
            ImageUrl nvarchar(500) NULL,
            Address nvarchar(300) NULL,
            Phone nvarchar(20) NULL,
            Rating float NOT NULL DEFAULT 5.0,
            SoldCount int NOT NULL DEFAULT 0,
            CreatedAt datetime2 NOT NULL
        );
        END
        """,
        """
        IF COL_LENGTH('SanPham', 'StoreId') IS NULL
        BEGIN
            ALTER TABLE SanPham ADD StoreId int NULL CONSTRAINT FK_SanPham_QuanAn_StoreId REFERENCES QuanAn(Id) ON DELETE SET NULL;
        END
        """
    };

    public static async Task RunAsync(AppDbContext db)
    {
        foreach (var sql in Scripts)
            await db.Database.ExecuteSqlRawAsync(sql);
    }
}
