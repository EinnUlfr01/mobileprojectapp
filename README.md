# Đề tài 10 – Ứng dụng đặt thức ăn

Sàn giao đồ ăn nhiều quán (kiểu GrabFood): khách đặt món từ nhiều cửa hàng, mỗi đơn của 1 quán,
chủ quán nhận đơn và chuẩn bị món, shipper nhận đơn, lấy món và giao; quản trị sàn quản lý cửa hàng, shipper, người dùng.

Gồm 2 phần:
- `backend/FoodOrder.API`: ASP.NET Core Web API (.NET 10) + SQL Server
- `app`: app Android (Kotlin, XML layout)

## 1. Chạy backend

1. Mở SQL Server (máy đang dùng `Server=localhost`, đăng nhập Windows). Nếu tên server khác, sửa `ConnectionStrings:Default` trong `backend/FoodOrder.API/appsettings.json`.
2. Mở terminal tại `backend/FoodOrder.API` rồi chạy:
   ```
   dotnet run
   ```
3. Lần chạy đầu API tự tạo database `DoAnAppGiaoDoAn`, các bảng và dữ liệu mẫu (4 quán, 5 danh mục, 14 món, 12 đơn, đánh giá).
   Database của bản cũ (1 cửa hàng) được tự nâng cấp: tạo bảng CuaHang, chia món sẵn có vào 4 quán theo danh mục, thêm chủ quán và shipper.
4. Mở http://localhost:5090/scalar để xem và thử API.

Tài khoản mẫu:

| Vai trò | Tài khoản | Được làm gì |
|---|---|---|
| Khách hàng | `user@gmail.com` / `123456` | Đặt món ở nhiều quán, lịch sử đơn, đánh giá, chat hỗ trợ |
| Quản trị sàn | `admin@gmail.com` / `Admin@123` | Quản lý cửa hàng, shipper, người dùng, danh mục, toàn bộ đơn, gán shipper, mã giảm giá, trả lời chat |
| Chủ cửa hàng | `quan1@gmail.com` … `quan4@gmail.com` / `Quan@123` | Thông tin quán, mở/đóng quán, món của quán, nhận/hủy đơn của quán, thống kê quán |
| Shipper | `shipper1@gmail.com`, `shipper2@gmail.com` / `Shipper@123` | Nhận đơn, báo đã lấy món, báo đã giao |

Luồng đơn: **Đang xử lý** (chờ quán xác nhận) → **Đang chuẩn bị** (quán nhận đơn) → **Đang giao** (shipper lấy món) → **Hoàn thành** (shipper giao xong).

API chặn theo vai trò (`[Authorize(Roles = ...)]`), app chỉ hiện các nút của vai trò đang đăng nhập.

### Thanh toán VNPay

- Chưa cấu hình: API dùng trang thanh toán giả lập (bấm "Thanh toán" hoặc "Hủy giao dịch").
- Dùng VNPay sandbox thật: đăng ký tại https://sandbox.vnpayment.vn/devreg/ để nhận `TmnCode` và `HashSecret`,
  copy `appsettings.Local.json.example` thành `appsettings.Local.json`, điền 2 giá trị vào rồi chạy lại API.
  Thẻ test: ngân hàng NCB, số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
- App mở trang thanh toán trong WebView, VNPay chuyển về `/api/payments/vnpay-return`, API kiểm tra chữ ký rồi đánh dấu đơn đã thanh toán.
- Đơn VNPay chưa trả tiền có nút "Thanh toán qua VNPay" trong chi tiết đơn.

### Chuyển khoản QR (thanh toán thật)

- Đặt hàng chọn "Chuyển khoản QR": app hiện mã VietQR có sẵn số tiền và nội dung (= mã đơn). Quét bằng app ngân hàng hoặc MoMo, tiền vào thẳng tài khoản cửa hàng.
- Cấu hình tài khoản nhận tiền trong `appsettings.Local.json` (không đưa lên GitHub):
  ```json
  "BankQr": { "BankId": "970436", "BankName": "Vietcombank", "AccountNo": "SO_TAI_KHOAN", "AccountName": "TEN KHONG DAU", "ExpireMinutes": 15, "SePayApiToken": "" }
  ```
- Xác nhận đã nhận tiền:
  - Thủ công: quản trị sàn hoặc chủ quán vào Quản lý đơn hàng, bấm đơn, chọn "Xác nhận đã nhận tiền".
  - Tự động: đăng ký https://sepay.vn, liên kết tài khoản ngân hàng, tạo API token rồi điền vào `SePayApiToken`. API tự kiểm tra giao dịch mỗi khi app hỏi trạng thái (không cần địa chỉ public).

### Ảnh món

- Quản trị sàn / chủ quán bấm "Chọn ảnh từ điện thoại" khi thêm/sửa món, quán hoặc danh mục, ảnh lưu ở `wwwroot/uploads`.
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
| Sàn nhiều cửa hàng | Trang chủ có danh sách quán, mỗi quán có thực đơn riêng, mở/đóng cửa. Giỏ hàng chỉ chứa món của 1 quán (đổi quán thì hỏi xóa giỏ cũ) | MainActivity, StoreActivity, CartActivity |
| Quản lý cửa hàng | Quản trị sàn thêm quán, gán chủ quán, tạm khóa quán; chủ quán sửa thông tin và bật/tắt nhận đơn | ManageStoresActivity, StoreEditActivity |
| Shipper | Shipper xem đơn chờ nhận, nhận đơn (tối đa 3 đơn cùng lúc), báo đã lấy món, báo đã giao (thu tiền mặt), gọi khách/quán, chỉ đường | ShipperActivity |
| Quản lý shipper | Quản trị sàn thêm/sửa shipper, biển số xe, khóa tài khoản, xem số đơn đang giao/đã giao, gán shipper cho đơn | ManageShippersActivity, ManageOrdersActivity |
| Chat trực tuyến | Khách nhắn tin với bộ phận hỗ trợ của sàn, quản trị sàn trả lời. Realtime bằng SignalR (`/hubs/chat`): gửi là bên kia thấy ngay + bật thông báo. Mất kết nối thì tự chuyển sang hỏi server mỗi 3 giây | ChatActivity (khách + nhân viên), ChatListActivity (nhân viên) |
| Thông báo | Admin đổi trạng thái đơn, thanh toán VNPay xong → khách nhận thông báo trên điện thoại. Nhân viên được báo khi có đơn mới, tin nhắn mới | NotificationsActivity, chuông + chấm đỏ ở trang chủ |
| Mã giảm giá | Giảm %, giảm tiền, freeship; có đơn tối thiểu, giảm tối đa, số lượt, hạn dùng. Mẫu: `GIAM20`, `GIAM10K`, `FREESHIP` | CheckoutActivity, ManageVouchersActivity |
| Món yêu thích | Bấm trái tim ở chi tiết món | FavoritesActivity (GridView) |
| Theo dõi đơn hàng | Dòng thời gian Đã đặt → Quán chuẩn bị → Shipper giao → Giao thành công, có giờ, người cập nhật, tên quán và shipper | OrderDetailActivity |
| Hủy đơn có lý do | Khách và cửa hàng chọn lý do khi hủy, lý do hiện trên dòng thời gian và trong thông báo | OrderDetailActivity, ManageOrdersActivity |
| Chat: đang nhập… | Bên kia gõ phím thì hiện "đang nhập…" realtime | ChatActivity |
| Chat: trả lời nhanh | Câu mẫu cho nhân viên (đã nhận đơn, đang giao…) và câu hỏi nhanh cho khách | ChatActivity |
| Biểu đồ doanh thu | Biểu đồ cột 7 ngày tự vẽ bằng Canvas (không dùng thư viện) | AdminDashboardActivity (BarChartView) |
| Gợi ý món | "Có thể bạn cũng thích": món bán chạy cùng danh mục | ProductDetailActivity |
| Lịch sử tìm kiếm | Tìm kiếm gần đây (lưu trên máy) + từ khóa phổ biến | SearchActivity |
| Đặt lại đơn cũ | Cho toàn bộ món của đơn cũ vào giỏ | OrderDetailActivity |
| Hỏi hỗ trợ về đơn | Mở chat điền sẵn mã đơn | OrderDetailActivity |

Thông báo chỉ chạy khi app đang mở (app hỏi server mỗi 8 giây). Android 13+ cần cho phép quyền thông báo khi app hỏi.

Database cũ được tự thêm bảng/cột mới khi chạy API (`Data/SchemaUpgrade.cs`), không cần xóa database.

## 3. Các bảng CSDL

15 bảng: NguoiDung (vai trò, biển số xe shipper, khóa tài khoản), **CuaHang** (quán, chủ quán, mở/đóng, khóa),
DanhMuc, SanPham (thuộc 1 quán), SanPhamSize, GioHang, DonHang (UserId null = khách vãng lai, StoreId = quán, ShipperId = shipper),
ChiTietDonHang, DanhGia, HoiThoai, TinNhan (chat), ThongBao, MaGiamGia, YeuThich, LichSuDonHang (theo dõi đơn).

## 4. Activity và loại danh sách

| Nhóm | Activity | Danh sách |
|---|---|---|
| Chung | SplashActivity, LoginActivity, RegisterActivity | |
| User + Khách | MainActivity (trang chủ) | RecyclerView ngang (quán, danh mục, bán chạy), RecyclerView dọc (món mới) |
| | StoreActivity (1 quán) | RecyclerView (thực đơn của quán) |
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
| | ManageStoresActivity, ManageShippersActivity | ListView + Dialog |
| | StoreEditActivity | Spinner (chủ quán), Switch |
| Shipper | ShipperActivity | TabLayout + ListView |

Khách vãng lai dùng chung activity với user, code kiểm tra `Session.isLoggedIn` để đổi giao diện: giỏ hàng lưu trên máy, đặt hàng chỉ cần tên + SĐT + địa chỉ + phương thức thanh toán, không được đánh giá và xem lịch sử đơn.

Thanh toán MoMo/ZaloPay/VNPay là giả lập (bấm "Thanh toán" là đơn được đánh dấu đã trả).
