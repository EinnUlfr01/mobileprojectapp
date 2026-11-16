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
        // ================= Sàn nhiều cửa hàng + shipper =================
        """
        IF COL_LENGTH('NguoiDung', 'VehiclePlate') IS NULL
            ALTER TABLE NguoiDung ADD VehiclePlate nvarchar(20) NULL;
        """,
        """
        IF COL_LENGTH('NguoiDung', 'IsLocked') IS NULL
            ALTER TABLE NguoiDung ADD IsLocked bit NOT NULL CONSTRAINT DF_NguoiDung_IsLocked DEFAULT 0;
        """,
        // Vai trò cũ -> vai trò mới: Quản lý thực đơn thành Chủ quán, Nhân viên đơn hàng thành Shipper
        """
        UPDATE NguoiDung SET Role = 'StoreOwner' WHERE Role = 'MenuManager';
        UPDATE NguoiDung SET Role = 'Shipper' WHERE Role = 'OrderStaff';
        """,
        """
        IF OBJECT_ID(N'DanhMucQuanAn', N'U') IS NULL
        CREATE TABLE DanhMucQuanAn (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_DanhMucQuanAn PRIMARY KEY,
            Name nvarchar(200) NOT NULL,
            ImageUrl nvarchar(500) NULL
        );
        """,
        """
        IF OBJECT_ID(N'CuaHang', N'U') IS NULL
        BEGIN
        CREATE TABLE CuaHang (
            Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CuaHang PRIMARY KEY,
            Name nvarchar(200) NOT NULL,
            Address nvarchar(300) NOT NULL,
            Phone nvarchar(15) NOT NULL,
            Description nvarchar(500) NULL,
            ImageUrl nvarchar(max) NULL,
            OwnerId int NULL CONSTRAINT FK_CuaHang_NguoiDung_OwnerId REFERENCES NguoiDung(Id) ON DELETE SET NULL,
            IsOpen bit NOT NULL,
            IsActive bit NOT NULL,
            CreatedAt datetime2 NOT NULL
        );
        CREATE INDEX IX_CuaHang_OwnerId ON CuaHang(OwnerId);
        END
        """,
        """
        IF COL_LENGTH('CuaHang', 'StoreCategoryId') IS NULL
        BEGIN
            ALTER TABLE CuaHang ADD StoreCategoryId int NULL
                CONSTRAINT FK_CuaHang_DanhMucQuanAn_StoreCategoryId REFERENCES DanhMucQuanAn(Id) ON DELETE SET NULL;
            EXEC(N'CREATE INDEX IX_CuaHang_StoreCategoryId ON CuaHang(StoreCategoryId)');
        END
        """,
        // Database từng chạy bản thử nghiệm bảng QuanAn: bỏ cột SanPham.StoreId trỏ sang QuanAn để chuyển sang CuaHang
        """
        IF OBJECT_ID(N'FK_SanPham_QuanAn_StoreId', N'F') IS NOT NULL
        BEGIN
            ALTER TABLE SanPham DROP CONSTRAINT FK_SanPham_QuanAn_StoreId;
            IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SanPham_StoreId' AND object_id = OBJECT_ID('SanPham'))
                DROP INDEX IX_SanPham_StoreId ON SanPham;
            ALTER TABLE SanPham DROP COLUMN StoreId;
        END
        """,
        // Món cũ chưa thuộc quán nào: tạo 4 quán mẫu rồi chia món theo danh mục
        $"""
        IF COL_LENGTH('SanPham', 'StoreId') IS NULL
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM CuaHang)
                INSERT INTO CuaHang (Name, Address, Phone, Description, ImageUrl, OwnerId, IsOpen, IsActive, CreatedAt) VALUES
                {DbSeeder.StoresSql};
            ALTER TABLE SanPham ADD StoreId int NULL;
            EXEC(N'UPDATE p SET StoreId = s.Id FROM SanPham p JOIN DanhMuc c ON c.Id = p.CategoryId
                   JOIN CuaHang s ON s.Name = CASE c.Name {DbSeeder.CategoryStoreCaseSql} END');
            EXEC(N'UPDATE SanPham SET StoreId = (SELECT MIN(Id) FROM CuaHang) WHERE StoreId IS NULL');
            EXEC(N'ALTER TABLE SanPham ALTER COLUMN StoreId int NOT NULL');
            EXEC(N'ALTER TABLE SanPham ADD CONSTRAINT FK_SanPham_CuaHang_StoreId FOREIGN KEY (StoreId) REFERENCES CuaHang(Id)');
            EXEC(N'CREATE INDEX IX_SanPham_StoreId ON SanPham(StoreId)');
        END
        """,
        // Đơn cũ: gán quán theo món đầu tiên trong đơn
        """
        IF COL_LENGTH('DonHang', 'StoreId') IS NULL
        BEGIN
            ALTER TABLE DonHang ADD StoreId int NULL CONSTRAINT FK_DonHang_CuaHang_StoreId REFERENCES CuaHang(Id);
            EXEC(N'UPDATE o SET StoreId = (SELECT TOP 1 p.StoreId FROM ChiTietDonHang i JOIN SanPham p ON p.Id = i.ProductId
                   WHERE i.OrderId = o.Id ORDER BY i.Id) FROM DonHang o');
            EXEC(N'CREATE INDEX IX_DonHang_StoreId ON DonHang(StoreId)');
        END
        """,
        """
        IF COL_LENGTH('DonHang', 'ShipperId') IS NULL
        BEGIN
            ALTER TABLE DonHang ADD ShipperId int NULL CONSTRAINT FK_DonHang_NguoiDung_ShipperId REFERENCES NguoiDung(Id);
            EXEC(N'CREATE INDEX IX_DonHang_ShipperId ON DonHang(ShipperId)');
        END
        """
    };

    public static async Task RunAsync(AppDbContext db)
    {
        foreach (var sql in Scripts)
            await db.Database.ExecuteSqlRawAsync(sql);
    }
}
