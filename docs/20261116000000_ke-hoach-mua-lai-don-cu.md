# Kế hoạch Mua lại đơn cũ — 16/11/2026

Trạng thái ngày 16/11/2026: hoàn tất triển khai và kiểm thử phạm vi demo.
Build demo đạt; 71/71 unit test, 57/57 Android test liên quan đạt trên Pixel_6
API 35; lint 0 lỗi. Pixel_6 đã được tạo lại với sự đồng ý của người dùng.
Xem [hướng dẫn sử dụng](20261116000000_mua-lai-don-cu.md) và
[kết quả kiểm thử thực tế](kiem-thu/reorder-2026-11-16/README.md).
Phạm vi: ứng dụng demo bán hàng
trên một thiết bị, dùng SQLite và luồng checkout hiện có; không cần backend.

## 1. Mục tiêu và hiện trạng

Khách mở một đơn cũ, xem các sản phẩm có thể mua lại với giá hiện tại, xác nhận
thêm vào giỏ rồi tiếp tục đặt hàng như bình thường.

- `OrdersScreen.kt` có danh sách/chi tiết đơn, hủy đơn, đánh giá và xuất PDF;
  chưa có thao tác mua lại.
- `OrderRepository` kiểm tra phiên và quyền sở hữu; đơn lưu snapshot tên, giá,
  số lượng để giữ nguyên lịch sử.
- `CartRepository.add()` thêm từng đơn vị và kiểm tra tồn, giá đã chấp nhận,
  giới hạn `MAX_CART_QUANTITY = 99`. Không gọi hàm này lặp theo số lượng để mua lại:
  nhiều transaction có thể để lại giỏ chỉ được cập nhật một phần khi gặp lỗi.
- `CartViewModel.open()/reload()` có khôi phục checkout từ `checkout_requests`;
  cần xử lý trạng thái này khi chuyển từ mua lại sang giỏ.

Nguồn: [OrdersScreen](../app/src/main/java/com/example/bloomybeauty/feature/order/OrdersScreen.kt),
[OrderRepository](../app/src/main/java/com/example/bloomybeauty/data/order/OrderRepository.kt),
[CartRepository](../app/src/main/java/com/example/bloomybeauty/data/cart/CartRepository.kt),
[CartViewModel](../app/src/main/java/com/example/bloomybeauty/feature/cart/CartViewModel.kt).

## 2. Phạm vi phiên bản đầu

Các lựa chọn dưới đây là mặc định đề xuất cho lần triển khai:

1. Nút **Mua lại** trong chi tiết đơn `DELIVERED` hoặc `CANCELLED` của chính khách.
   Chưa đặt nút ở danh sách; đơn đang xử lý không có nút này.
2. Mua lại toàn bộ các dòng còn mua được, có bước xem trước; chưa chọn từng dòng.
3. Gộp vào giỏ hiện tại, giữ các sản phẩm khác. Không xóa hoặc thay toàn bộ giỏ.
4. Chỉ thêm vào giỏ; không tự tạo đơn, thanh toán, trừ kho hoặc gửi thông báo đơn.
5. Không sao chép địa chỉ, coupon, phí giao hàng hoặc giá của đơn cũ. Giữ thông
   tin giao hàng hiện tại; bước checkout tính lại phí và kiểm tra coupon.
6. Dự kiến không đổi SQLite schema. Chỉ dùng các bảng hiện có; nếu triển khai
   phát sinh nhu cầu lưu bền mới thì cập nhật kế hoạch trước khi thêm migration.

## 3. Quy tắc gộp và giá

Với mỗi sản phẩm, đặt `c` là số lượng đã có trong giỏ, `q` là số lượng trong đơn
cũ, `limit = min(tồn kho hiện tại, 99)`.

- Nếu sản phẩm tồn tại, đang bán và `c <= limit`, số lượng thêm là
  `min(q, max(0, limit - c))`; số lượng cuối là `c + số lượng thêm`.
- Thiếu tồn: thêm phần còn đủ, báo rõ số lượng bị giảm. Nếu giỏ đã chạm giới
  hạn, bỏ qua dòng đó và ghi lý do.
- Sản phẩm bị xóa, ngừng bán hoặc hết hàng: bỏ qua; dùng tên snapshot đơn để
  khách nhận biết mục bị bỏ qua.
- Nếu dòng giỏ hiện tại đã vượt tồn/giới hạn, giữ nguyên dòng đó, không cộng
  thêm và yêu cầu khách điều chỉnh trong giỏ. Không âm thầm giảm hàng đã có.
- Dùng giá catalog hiện tại. Xem trước hiển thị giá cũ → giá hiện tại khi thay
  đổi và tạm tính phần thêm; đây chưa phải tổng tiền COD.
- Nếu sản phẩm trùng giỏ có `accepted_price_vnd` khác giá catalog, yêu cầu khách
  vào giỏ xác nhận giá bằng luồng hiện có rồi xem trước mua lại lần nữa. Không
  âm thầm chấp nhận lại giá cho lượng hàng đang có.
- Không có dòng nào thêm được: hiển thị lý do từng dòng, vô hiệu hóa xác nhận,
  không thay đổi giỏ hoặc checkout.
- Tính tiền bằng `Long`, kiểm tra tràn; chỉ dùng số lượng hợp lệ.

Ví dụ: đơn cũ có 3 sữa rửa mặt; giỏ có 1; kho còn 2 → thêm 1, giỏ thành 2,
báo chỉ thêm được 1/3 sản phẩm do tồn kho.

## 4. Luồng giao diện

1. Khách vào **Đơn hàng của tôi → Chi tiết → Mua lại**.
2. Tải bản xem trước từ database: tên, số lượng yêu cầu/thêm được, giá hiện tại,
   thay đổi giá, lý do bỏ qua và thông báo sẽ gộp vào giỏ.
3. Cho chọn **Hủy** hoặc **Thêm vào giỏ**. Khóa thao tác trong lúc xử lý.
4. Xác nhận thành công: chuyển đến bước danh sách giỏ, cập nhật badge và hiển thị
   kết quả thực tế, ví dụ “Đã thêm 2 sản phẩm; 1 sản phẩm đã ngừng bán”.
5. Nếu dữ liệu thay đổi sau xem trước, tải lại bản xem trước và yêu cầu xác nhận
   lại. Không thêm theo giá/số lượng mà khách chưa nhìn thấy.
6. Nếu lỗi lưu trữ: giữ nguyên giỏ, báo lỗi và cho thử lại. Nếu đổi phiên:
   đóng bản xem trước, không ghi dữ liệu sang tài khoản mới.

Quay lại màn hình hoặc xoay thiết bị không tự thực thi thao tác thêm. Sau khi
ứng dụng bị đóng, khách mở lại bản xem trước từ dữ liệu mới.

## 5. Thiết kế triển khai

### Dữ liệu và transaction

- Thêm `data/order/ReorderModels.kt` và `ReorderRepository.kt`, dùng chung
  `AuthDatabaseHelper`. API dự kiến: `preview(userId, orderId)` và
  `confirm(userId, preview)`; kết quả gồm các dòng thêm/giảm/bỏ qua cùng lý do.
- Mọi đọc/ghi kiểm tra `SessionAccess.matches`, quyền sở hữu đơn và trạng thái
  đủ điều kiện tại repository. Không tin danh sách dòng hoặc giá do UI truyền vào.
- `confirm` đọc lại đơn, catalog và giỏ trong **một transaction**, tính lại kết
  quả và so với dữ liệu đã xem trước. Preview mang dấu vết dữ liệu liên quan
  (chủ đơn, dòng đơn, giỏ, giá, trạng thái bán, tồn) để phát hiện thay đổi.
- Chỉ ghi khi kết quả còn khớp. Các dòng bỏ qua theo quy tắc là kết quả nghiệp
  vụ đã thông báo; lỗi ghi bất kỳ dòng nào phải rollback toàn bộ phần thêm.
- Không trừ tồn; `OrderRepository.create()` vẫn kiểm tra và trừ tồn khi đặt đơn.
- Chặn nhấn đúp ở ViewModel. Preview đã dùng không được tự tái sử dụng; gọi lại
  cùng preview sau commit bị từ chối do giỏ đã đổi. Sau lỗi không rõ kết quả,
  tải lại giỏ và preview trước khi cho xác nhận lần nữa.

### Checkout đang dở và điều hướng

- Nếu có checkout đã tạo đơn nhưng chưa được xác nhận đã xem, yêu cầu khách
  hoàn tất màn kết quả hiện có trước khi mua lại. Không tự xóa request đã commit.
- Với checkout nháp chưa tạo đơn: khi thực sự thêm hàng thành công, xóa request
  nháp cũ trong cùng transaction. Không mang coupon của request đó sang đơn mới.
- Thêm điểm vào rõ ràng cho `CartViewModel` sau mua lại: hủy trạng thái review
  cũ, tải giỏ, về `CartStep.ITEMS`, xóa request/coupon trong UI nhưng giữ nháp
  địa chỉ hiện tại. Không chỉ gọi `cart.open()` rồi mặc định việc recovery đúng.
- Wiring repository ở `ShopRepositories`/nơi khởi tạo và callback ở `BloomyApp`;
  trạng thái mua lại theo tài khoản, không dùng chung cờ thành công cũ.

### Điểm sửa dự kiến

| Khu vực | Nội dung |
|---|---|
| `data/order/ReorderModels.kt`, `ReorderRepository.kt` | Preview, kết quả và transaction |
| `data/cart/CartRepository.kt` | Tái sử dụng hoặc tách helper gộp hàng trong transaction nếu cần |
| `feature/order/OrdersScreen.kt` | Nút mua lại và màn/hộp thoại xem trước; tách ViewModel riêng nếu giúp giữ mã gọn |
| `feature/cart/CartViewModel.kt` | Điểm vào giỏ sau mua lại, làm sạch checkout UI cũ |
| `feature/app/BloomyApp.kt`, nơi tạo repositories | Wiring, điều hướng, đồng bộ giỏ |

## 6. Các bước thực hiện

1. Tạo model preview/kết quả, quy tắc tính số lượng và lỗi có thông báo rõ ràng.
2. Triển khai repository preview/confirm, kiểm tra phiên/chủ đơn và transaction;
   bổ sung kiểm thử SQLite trước khi nối UI.
3. Nối nút, xem trước, xác nhận và điều hướng; xử lý checkout recovery.
4. Chạy kiểm thử tích hợp và hồi quy liên quan; chụp minh chứng trên Pixel_6
   hiện có. Không tạo AVD khác; thiết bị thật để sau.
5. Viết hướng dẫn sử dụng và hồ sơ kiểm thử thực tế; cập nhật README. Chỉ cập
   nhật APK/bộ bàn giao khi thực hiện bước đóng gói, không ghi là đã hoàn tất
   khi mới có kế hoạch.

## 7. Kiểm thử và tiêu chí nghiệm thu

| Nhóm | Trường hợp bắt buộc |
|---|---|
| Luồng cơ bản | Giỏ rỗng; gộp sản phẩm trùng; giữ sản phẩm khác; giữ dữ liệu sau mở lại DB |
| Khả dụng | Thiếu tồn; hết hàng; ngừng bán; bị xóa; giới hạn 99; giỏ đã vượt tồn; không còn dòng hợp lệ |
| Giá | Tăng/giảm giá so với đơn; dòng giỏ chưa chấp nhận giá mới; không tái dùng giảm giá/phí cũ |
| Quyền | Đơn tài khoản khác; ID không tồn tại; phiên cũ; đổi tài khoản giữa preview và confirm; đơn chưa đủ trạng thái |
| Tính nhất quán | Giá/tồn/giỏ thay đổi sau preview; rollback lỗi ghi; nhấn đúp; dùng lại preview đã commit |
| Checkout | Có nháp review; có đơn đã commit chờ acknowledge; coupon cũ không tự khôi phục; checkout mới tính lại đúng |
| UI | Hủy không đổi giỏ; loading/lỗi/thử lại; mở đúng giỏ; badge cập nhật; xoay màn hình không cộng lại |
| Lịch sử | Snapshot/PDF/đánh giá đơn cũ không đổi; thêm giỏ không đổi tồn, số đơn, doanh thu hoặc tạo thông báo đơn |

- Unit test phần tính toán khi tách được hàm thuần; Android test cho SQLite và
  transaction; Compose/integration test cho luồng mua lại → giỏ → tạo đơn mới.
- Hồi quy `CartRepositoryTest`, `OrderRepositoryTest`, `CartFlowTest`,
  `CartRefreshTest` và `CommerceExtensionsIntegrationTest`; chạy unit test,
  build và lint phù hợp với biến thể demo.
- Nghiệm thu khi khách xác nhận đúng những thay đổi đã xem trước, giỏ cập nhật
  đúng một lần, đơn mới đi qua checkout hiện có và mọi ca bắt buộc đạt.
- Kết quả build/unit/lint và Android đã lưu trong hồ sơ ngày 16/11/2026.
  Có 8 ảnh minh chứng từ luồng thực thi trên Pixel_6 mới, gồm xem trước, gộp giỏ,
  đặt đơn mới, thiếu tồn/đổi giá và xác nhận lại dữ liệu thay đổi.

Các tính năng khác được lưu tại [Danh sách tính năng phát triển sau](20261116000000_tinh-nang-phat-trien-sau.md).
