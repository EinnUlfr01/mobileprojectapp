# Đề tài 10 – Ứng dụng đặt thức ăn

Gồm 2 phần:
- `backend/FoodOrder.API`: ASP.NET Core Web API (.NET 10) + SQL Server
- `app`: app Android (Kotlin, XML layout)

## 1. Chạy backend

1. Mở SQL Server (máy đang dùng `Server=localhost`, đăng nhập Windows). Nếu tên server khác, sửa `ConnectionStrings:Default` trong `backend/FoodOrder.API/appsettings.json`.
2. Mở terminal tại `backend/FoodOrder.API` rồi chạy:
   ```
   dotnet run
   ```
3. Lần chạy đầu API tự tạo database `DoAnAppGiaoDoAn`, các bảng và dữ liệu mẫu (5 danh mục, 14 món, 12 đơn, đánh giá).
4. Mở http://localhost:5090/scalar để xem và thử API.

Tài khoản mẫu:
- User: `user@gmail.com` / `123456`
- Admin: `admin@gmail.com` / `Admin@123`

Muốn tạo lại dữ liệu từ đầu: xóa database `DoAnAppGiaoDoAn` trong SSMS rồi chạy lại.

## 2. Chạy app Android

1. Mở thư mục `DoAnAppGiaodoan` bằng Android Studio, bấm **Sync Project with Gradle Files**.
2. Giữ backend đang chạy. Mở terminal mới, nối cổng 5090 của máy ảo về máy tính (làm lại mỗi lần mở máy ảo):
   ```
   D:\Android\SDK\platform-tools\adb.exe reverse tcp:5090 tcp:5090
   ```
3. Bấm **Run**. App gọi API qua `http://127.0.0.1:5090/` (cấu hình trong `app/.../data/ApiClient.kt`).
   Điện thoại thật cắm cáp USB cũng dùng lệnh adb reverse như trên.

## 3. Các bảng CSDL

NguoiDung, DanhMuc, SanPham, SanPhamSize, GioHang, DonHang (user_id cho phép null = khách vãng lai), ChiTietDonHang, DanhGia.

## 4. Activity và loại danh sách

| Nhóm | Activity | Danh sách |
|---|---|---|
| Chung | SplashActivity, LoginActivity, RegisterActivity | |
| User + Khách | MainActivity (trang chủ) | RecyclerView ngang (danh mục, bán chạy), RecyclerView dọc (món mới) |
| | SearchActivity | ListView |
| | CategoryProductsActivity | GridView |
| | ProductDetailActivity | RecyclerView (đánh giá) |
| | CartActivity | RecyclerView (+/−, xóa) |
| | CheckoutActivity, OrderSuccessActivity (khách) | |
| User | OrderHistoryActivity | ListView |
| | OrderDetailActivity | ListView |
| | ReviewActivity, ProfileActivity | |
| Admin | AdminDashboardActivity | GridView |
| | ManageUsersActivity, ManageCategoriesActivity | ListView + Dialog |
| | ManageProductsActivity | RecyclerView |
| | AddEditProductActivity | Spinner |
| | ManageOrdersActivity | TabLayout + ListView + thống kê |

Khách vãng lai dùng chung activity với user, code kiểm tra `Session.isLoggedIn` để đổi giao diện: giỏ hàng lưu trên máy, đặt hàng chỉ cần tên + SĐT + địa chỉ + phương thức thanh toán, không được đánh giá và xem lịch sử đơn.

Thanh toán MoMo/ZaloPay/VNPay là giả lập (bấm "Thanh toán" là đơn được đánh dấu đã trả).
