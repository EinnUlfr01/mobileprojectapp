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

| Vai trò | Tài khoản | Được làm gì |
|---|---|---|
| Thành viên | `user@gmail.com` / `123456` | Đặt món, lịch sử đơn, đánh giá |
| Quản trị viên | `admin@gmail.com` / `Admin@123` | Toàn quyền, quản lý người dùng và phân quyền |
| Quản lý thực đơn | `menu@gmail.com` / `Menu@123` | Danh mục, món ăn, upload ảnh |
| Nhân viên đơn hàng | `donhang@gmail.com` / `Donhang@123` | Xử lý đơn, thống kê |

API chặn theo vai trò (`[Authorize(Roles = ...)]`), app chỉ hiện các nút của vai trò đang đăng nhập.

### Thanh toán VNPay

- Chưa cấu hình: API dùng trang thanh toán giả lập (bấm "Thanh toán" hoặc "Hủy giao dịch").
- Dùng VNPay sandbox thật: đăng ký tại https://sandbox.vnpayment.vn/devreg/ để nhận `TmnCode` và `HashSecret`,
  copy `appsettings.Local.json.example` thành `appsettings.Local.json`, điền 2 giá trị vào rồi chạy lại API.
  Thẻ test: ngân hàng NCB, số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
- App mở trang thanh toán trong WebView, VNPay chuyển về `/api/payments/vnpay-return`, API kiểm tra chữ ký rồi đánh dấu đơn đã thanh toán.
- Đơn VNPay chưa trả tiền có nút "Thanh toán qua VNPay" trong chi tiết đơn.

### Ảnh món

- Admin / Quản lý thực đơn bấm "Chọn ảnh từ điện thoại" khi thêm/sửa món hoặc danh mục, ảnh lưu ở `wwwroot/uploads`.
- Hoặc chép ảnh vào `wwwroot/images` theo tên trong `DANH_SACH_TEN_ANH.txt` rồi chạy lại API.

Muốn tạo lại dữ liệu từ đầu: xóa database `DoAnAppGiaoDoAn` trong SSMS rồi chạy lại.

## 2. Chạy app Android

1. Mở thư mục `DoAnAppGiaodoan` bằng Android Studio, bấm **Sync Project with Gradle Files**.
2. Giữ backend đang chạy. Mở terminal mới, nối cổng 5090 của máy ảo về máy tính (làm lại mỗi lần mở máy ảo):
   ```
   D:\Android\SDK\platform-tools\adb.exe reverse tcp:5090 tcp:5090
   ```
3. Bấm **Run**. App gọi API qua `http://127.0.0.1:5090/` (cấu hình trong `app/.../data/ApiClient.kt`).
   Điện thoại thật cắm cáp USB cũng dùng lệnh adb reverse như trên.

## Chức năng nâng cao

| Chức năng | Mô tả | Màn hình |
|---|---|---|
| Chat trực tuyến | Khách nhắn tin với cửa hàng, mọi vai trò quản trị trả lời. App tự lấy tin mới mỗi 3 giây | ChatActivity (khách + nhân viên), ChatListActivity (nhân viên) |
| Thông báo | Admin đổi trạng thái đơn, thanh toán VNPay xong → khách nhận thông báo trên điện thoại. Nhân viên được báo khi có đơn mới, tin nhắn mới | NotificationsActivity, chuông + chấm đỏ ở trang chủ |
| Mã giảm giá | Giảm %, giảm tiền, freeship; có đơn tối thiểu, giảm tối đa, số lượt, hạn dùng. Mẫu: `GIAM20`, `GIAM10K`, `FREESHIP` | CheckoutActivity, ManageVouchersActivity |
| Món yêu thích | Bấm trái tim ở chi tiết món | FavoritesActivity (GridView) |
| Đặt lại đơn cũ | Cho toàn bộ món của đơn cũ vào giỏ | OrderDetailActivity |
| Hỏi cửa hàng về đơn | Mở chat điền sẵn mã đơn | OrderDetailActivity |

Thông báo chỉ chạy khi app đang mở (app hỏi server mỗi 8 giây). Android 13+ cần cho phép quyền thông báo khi app hỏi.

Database cũ được tự thêm bảng/cột mới khi chạy API (`Data/SchemaUpgrade.cs`), không cần xóa database.

## 3. Các bảng CSDL

NguoiDung, DanhMuc, SanPham, SanPhamSize, GioHang, DonHang (user_id cho phép null = khách vãng lai), ChiTietDonHang, DanhGia,
HoiThoai, TinNhan (chat), ThongBao, MaGiamGia, YeuThich.

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
