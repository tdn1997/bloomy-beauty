# Kế hoạch đề tài: Ứng dụng Bán Mỹ Phẩm — Bloomy Beauty

Ngày lập: 07/10/2026.

Trạng thái: đang triển khai. Checkbox được đánh dấu là phần đã thực hiện và kiểm tra: xác thực, danh mục, giỏ/giao hàng, COD cục bộ, hồ sơ và xem/hủy đơn. Checkbox còn trống là việc chưa hoàn tất. Tài liệu này tập trung toàn bộ phạm vi, tiến độ và kế hoạch theo phase trong một file.

Cập nhật theo yêu cầu ngày 07/10/2026: phần đăng ký/đăng nhập dùng SQLite trực tiếp theo [tài liệu Android](https://developer.android.com/training/data-storage/sqlite). Tài khoản và phiên hiện lưu cục bộ trên thiết bị. Firebase trong các phần khác vẫn là phương án dự kiến cho dữ liệu dùng chung, chưa triển khai; trước các phase tiếp theo cần chốt ứng dụng hoàn toàn cục bộ hay có backend. Phiên SQLite không thay thế token Firebase Authentication. Chi tiết phần đã thực hiện và cách chạy ở [mục 16](#16-phần-đăng-nhập-sqlite-đã-triển-khai).

Đọc nhanh: [Phạm vi chức năng](#3-chức-năng) · [Tiến độ 8 tuần](#7-tiến-độ-8-tuần) · [14 phase triển khai](#12-kế-hoạch-chi-tiết-theo-phase) · [Hợp đồng dữ liệu](#13-chi-tiết-dữ-liệu-và-hợp-đồng-xử-lý) · [Ma trận kiểm thử](#14-ma-trận-kiểm-thử) · [Theo dõi tiến độ](#15-theo-dõi-tiến-độ-và-quy-tắc-hoàn-thành).

Kế hoạch đề xuất cho đồ án môn Lập trình di động, nhóm 2–3 thành viên, thời gian 8 tuần. Thời lượng và phân công là giả định lập kế hoạch, chưa phải thông tin đã được xác nhận. Nếu làm cá nhân, nên kéo dài thời gian hoặc giảm phần quản trị.

## 1. Mục tiêu và hiện trạng

Xây dựng ứng dụng Android giúp khách hàng tìm mỹ phẩm, xem thông tin, quản lý giỏ hàng, đặt hàng và theo dõi đơn. Quản trị viên quản lý sản phẩm và xử lý đơn hàng trên cùng ứng dụng, với quyền truy cập riêng.

Dự án dùng Kotlin, Jetpack Compose và Material 3; cấu hình minSdk 28. Hiện đã có xác thực, danh mục/tìm kiếm/chi tiết/yêu thích, giỏ/giao hàng, COD, lịch sử/chi tiết/hủy đơn và sửa hồ sơ/địa chỉ mặc định bằng SQLite. Đây là bản demo cục bộ một thiết bị, chưa kết nối backend dùng chung. Vai trò và chức năng quản trị chưa triển khai. Kế hoạch tận dụng cấu trúc Android hiện có.

Kết quả mong đợi: một bản demo hoạt động xuyên suốt từ chọn sản phẩm đến quản trị viên xử lý đơn, có dữ liệu lưu trữ và kiểm tra quyền truy cập.

## 2. Đối tượng sử dụng và phạm vi

| Đối tượng | Nhu cầu chính |
|---|---|
| Khách chưa đăng nhập | Xem danh mục, tìm kiếm và xem chi tiết sản phẩm |
| Khách hàng đã đăng nhập | Quản lý giỏ hàng, địa chỉ nhận hàng, đặt và theo dõi đơn |
| Quản trị viên | Quản lý sản phẩm, tồn kho và trạng thái đơn |

Phiên bản đầu phục vụ một cửa hàng, dùng tiếng Việt và tiền VND. Chọn COD — thanh toán khi nhận hàng — cho luồng đặt hàng. Giao nhận và thu tiền được mô phỏng trong đồ án; không tích hợp đơn vị vận chuyển.

## 3. Chức năng

### Bắt buộc — phiên bản tối thiểu có thể nghiệm thu

| Nhóm | Nội dung | Tiêu chí hoàn thành |
|---|---|---|
| Tài khoản | Đăng ký, đăng nhập, đăng xuất bằng email và mật khẩu | Xử lý dữ liệu sai; giữ phiên đăng nhập; phân biệt khách hàng và quản trị viên |
| Danh mục sản phẩm | Trang chủ, danh mục, tìm theo tên, lọc danh mục, sắp xếp giá | Hiển thị dữ liệu thật từ kho dữ liệu; có trạng thái tải, lỗi và không có kết quả |
| Chi tiết sản phẩm | Ảnh, tên, thương hiệu, giá, mô tả, thành phần, cách dùng, tồn kho | Có thể chọn sản phẩm và số lượng hợp lệ để thêm vào giỏ |
| Giỏ hàng | Thêm, đổi số lượng, xóa sản phẩm, tính tạm tính | Giỏ được lưu theo tài khoản; cập nhật đúng khi thay đổi số lượng |
| Đặt hàng | Nhập người nhận, số điện thoại, địa chỉ; xem tổng tiền; xác nhận COD | Tạo đơn có mã riêng; kiểm tra lại giá và tồn kho khi xác nhận; không tạo đơn trùng do bấm nhiều lần |
| Đơn hàng của tôi | Danh sách, chi tiết, trạng thái, hủy khi còn chờ xác nhận | Người dùng chỉ xem và hủy đơn của mình theo quy tắc |
| Hồ sơ | Xem và sửa tên, số điện thoại, địa chỉ mặc định | Thông tin còn tồn tại sau khi mở lại ứng dụng |
| Quản trị sản phẩm | Thêm, sửa, ẩn sản phẩm; cập nhật giá và tồn kho | Chỉ quản trị viên được thao tác; sản phẩm đã có trong đơn được ẩn thay vì xóa lịch sử |
| Quản trị đơn hàng | Xem danh sách, chi tiết và chuyển trạng thái | Kiểm tra quyền ở tầng dữ liệu/backend; trạng thái chuyển đúng quy tắc |

Mỗi sản phẩm trong bản đầu tương ứng một mặt hàng có giá và tồn kho riêng. Các màu hoặc dung tích khác nhau có thể tạo thành sản phẩm riêng để tránh mở rộng mô hình biến thể quá sớm.

### Mở rộng khi còn thời gian

- Danh sách yêu thích.
- Đánh giá sản phẩm sau khi đơn được giao.
- Mã giảm giá với điều kiện và thời hạn rõ ràng.
- Thông báo khi trạng thái đơn thay đổi.
- Thống kê số đơn và doanh thu từ đơn đã giao.
- Biến thể sản phẩm theo màu, dung tích.

Thanh toán trực tuyến, chat tư vấn và gợi ý bằng AI thuộc hướng phát triển sau đồ án. Chỉ bổ sung khi luồng bắt buộc đã hoàn thành và kiểm thử đạt.

## 4. Công nghệ và kiến trúc đề xuất

| Thành phần | Lựa chọn đề xuất | Mục đích |
|---|---|---|
| Ứng dụng | Kotlin, Jetpack Compose, Material 3 | Tiếp tục dùng nền tảng của dự án |
| Tổ chức mã | UI → ViewModel → Repository → nguồn dữ liệu | Tách giao diện, trạng thái và truy cập dữ liệu |
| Điều hướng | Navigation Compose | Điều hướng giữa màn hình và khu vực theo vai trò |
| Xử lý bất đồng bộ | Coroutines và Flow | Quản lý tải dữ liệu và cập nhật trạng thái |
| Xác thực — đã triển khai | SQLiteOpenHelper, SQLiteDatabase | Lưu tài khoản email/mật khẩu băm và phiên đăng nhập cục bộ |
| Danh mục, giỏ và địa chỉ — đã triển khai | SQLiteDatabase, transaction | Dữ liệu demo cục bộ; giỏ, yêu thích và địa chỉ tách theo tài khoản |
| Đặt hàng COD — đã triển khai bản cục bộ | SQLite transaction, requestId UUID, fingerprint SHA-256 | Lưu yêu cầu trước khi gửi; tạo đơn/trừ tồn/dọn giỏ cùng giao dịch; gửi lại trả đơn đã lưu |
| Dữ liệu dùng chung | Cloud Firestore | Lưu sản phẩm, hồ sơ, giỏ hàng và đơn |
| Quyền truy cập | Firestore Security Rules và quyền quản trị được cấp từ môi trường tin cậy | Ngăn người dùng sửa dữ liệu hoặc tự nâng quyền |
| Xử lý đơn | Backend tối thiểu, dự kiến callable Cloud Functions | Kiểm tra giá, tồn kho, quyền và giao dịch đặt/hủy đơn |
| Ảnh demo | Ảnh đóng gói trong ứng dụng hoặc URL ảnh được chuẩn bị trước | Có dữ liệu ổn định để trình diễn |

Lựa chọn Firebase cần được xác nhận bằng thử nghiệm kết nối và khả năng triển khai backend ngay tuần 1; kiểm tra yêu cầu tài khoản, hạn mức và chi phí trước khi sử dụng. Nếu học phần yêu cầu backend riêng, giữ các Repository và thay nguồn dữ liệu bằng API tương ứng, đồng thời điều chỉnh tiến độ.

Trong giai đoạn dựng giao diện, dùng dữ liệu mẫu qua Repository để các màn hình có thể phát triển trước khi kết nối dịch vụ. Đơn chỉ được xác nhận thành công sau khi backend đã lưu thành công; khi mất mạng, ứng dụng báo lỗi và cho phép thử lại.

Tài liệu tham khảo cho kiến trúc: [Android — Recommendations for app architecture](https://developer.android.com/topic/architecture/recommendations). Cơ chế kiểm tra truy cập dữ liệu: [Firebase — Writing conditions for Cloud Firestore Security Rules](https://firebase.google.com/docs/firestore/security/rules-conditions).

## 5. Mô hình dữ liệu dự kiến

| Thực thể | Thuộc tính chính |
|---|---|
| User | id, name, email, phone, defaultAddress, role |
| Category | id, name, image, displayOrder |
| Product | id, categoryId, name, brand, priceVnd, imageRefs, description, ingredients, usage, stock, isActive |
| CartItem | userId, productId, quantity |
| Order | id, userId, recipientName, phone, address, subtotalVnd, shippingFeeVnd, totalVnd, paymentMethod, status, createdAt, requestId |
| OrderItem | orderId, productId, productNameSnapshot, unitPriceVndSnapshot, quantity |

Quan hệ chính: một danh mục có nhiều sản phẩm; một người dùng có nhiều mục giỏ hàng và đơn; một đơn có nhiều dòng sản phẩm. Firestore có thể tổ chức các dòng đơn dưới dạng subcollection hoặc dữ liệu nhúng; chốt cách lưu ở tuần 1.

Quy tắc nghiệp vụ cần triển khai:

- Tiền lưu bằng số nguyên VND; `tổng tiền = tổng (đơn giá × số lượng) + phí giao hàng`. Phí giao hàng dùng một mức cố định được cấu hình và hiển thị trước khi xác nhận.
- Số lượng phải lớn hơn 0. Backend đọc lại sản phẩm đang bán, giá và tồn kho để tính đơn; không nhận tổng tiền từ ứng dụng làm nguồn tin cậy.
- Khi tạo đơn thành công, trừ tồn kho trong cùng giao dịch với việc tạo đơn. Khi hủy hợp lệ, hoàn tồn kho đúng một lần.
- Lưu tên và giá tại thời điểm đặt hàng trong dòng đơn, để việc sửa sản phẩm không làm thay đổi lịch sử.
- Mỗi yêu cầu đặt hàng có mã chống trùng. Việc gửi lại cùng yêu cầu không tạo thêm đơn.
- Vai trò quản trị không được sửa qua màn hình hồ sơ hoặc yêu cầu của khách hàng.

Trạng thái đơn: `Chờ xác nhận → Đã xác nhận → Đang giao → Đã giao`. Khách hàng chỉ được hủy ở trạng thái chờ xác nhận; quản trị viên có thể hủy trước khi đơn chuyển sang đang giao. Đơn đã hủy hoặc đã giao không chuyển sang trạng thái khác trong bản đầu.

## 6. Màn hình và luồng sử dụng

Thanh điều hướng chính gồm: Trang chủ, Danh mục, Giỏ hàng, Tài khoản. Đơn hàng được truy cập từ Tài khoản và màn hình đặt hàng thành công.

Các màn hình cần thiết:

1. Đăng nhập / đăng ký.
2. Trang chủ.
3. Danh sách sản phẩm, tìm kiếm và lọc.
4. Chi tiết sản phẩm.
5. Giỏ hàng.
6. Thông tin giao hàng và xác nhận đơn.
7. Đặt hàng thành công.
8. Danh sách và chi tiết đơn hàng.
9. Hồ sơ cá nhân.
10. Danh sách và biểu mẫu sản phẩm dành cho quản trị viên.
11. Danh sách và chi tiết đơn dành cho quản trị viên.

Luồng demo chính: mở ứng dụng → tìm sản phẩm → xem chi tiết → đăng nhập → thêm vào giỏ → nhập địa chỉ → đặt COD → xem đơn → quản trị viên xác nhận và cập nhật trạng thái → khách hàng thấy trạng thái mới.

## 7. Tiến độ 8 tuần

| Tuần | Công việc | Sản phẩm bàn giao / điều kiện hoàn thành |
|---|---|---|
| 1 | Chốt yêu cầu, use case, dữ liệu, quy tắc đơn; thử xác thực, đọc dữ liệu và triển khai backend | Đề cương; sơ đồ dữ liệu; xác nhận phương án công nghệ chạy được |
| 2 | Wireframe, màu sắc, thành phần dùng chung, điều hướng; làm lát cắt đăng nhập → đọc sản phẩm | Bộ màn hình phác thảo và bản chạy kết nối dữ liệu đầu tiên |
| 3 | Trang chủ, danh mục, tìm kiếm, chi tiết; chuẩn bị dữ liệu mỹ phẩm | Luồng duyệt sản phẩm hoàn chỉnh với trạng thái tải/lỗi/rỗng |
| 4 | Giỏ hàng, địa chỉ, backend tạo đơn và cập nhật tồn kho | Đặt COD thành công, kiểm tra giá/tồn kho và chống đơn trùng |
| 5 | Lịch sử đơn, chi tiết, hủy đơn, hồ sơ; lưu thông tin theo tài khoản | Luồng khách hàng hoàn chỉnh, hủy đơn hoàn tồn đúng |
| 6 | Quản trị sản phẩm, tồn kho, đơn và quyền truy cập | Quản trị viên xử lý đơn; tài khoản khách không thực hiện được thao tác quản trị |
| 7 | Kiểm thử nghiệp vụ, phân quyền, lỗi mạng, đặt đồng thời; sửa lỗi và hoàn thiện giao diện | Danh sách ca kiểm thử có kết quả; bản ứng viên nghiệm thu |
| 8 | Kiểm tra bản phát hành, đóng gói APK, báo cáo, slide, video; diễn tập | Bộ sản phẩm nộp và kịch bản demo chạy trên thiết bị đích |

Mốc kiểm soát: cuối tuần 2 có luồng đọc dữ liệu thật; cuối tuần 4 đặt được đơn; cuối tuần 6 hoàn tất toàn bộ chức năng bắt buộc. Hai tuần cuối dành cho kiểm thử và bàn giao.

## 8. Phân công đề xuất cho nhóm 3 người

| Thành viên | Trách nhiệm chính |
|---|---|
| A | Giao diện, điều hướng, danh mục, tìm kiếm và chi tiết sản phẩm |
| B | Xác thực, dữ liệu, phân quyền, backend đơn hàng và tồn kho |
| C | Giỏ hàng, đặt hàng, lịch sử đơn và màn hình quản trị |

Cả nhóm cùng rà soát tích hợp, kiểm thử và viết báo cáo theo phần phụ trách. Thống nhất cấu trúc dữ liệu và hợp đồng Repository ở tuần 1; tích hợp ít nhất mỗi tuần. Với nhóm 2 người, chia theo luồng khách hàng và dữ liệu/quản trị, đồng thời bỏ các chức năng mở rộng.

## 9. Kiểm thử và tiêu chí nghiệm thu

- Có khoảng 20–30 sản phẩm demo thuộc ít nhất 4 danh mục, ví dụ chăm sóc da, trang điểm, chăm sóc tóc và nước hoa; có mặt hàng hết hàng để kiểm thử.
- Thực hiện trọn vẹn luồng đặt COD và cập nhật trạng thái bằng hai tài khoản khác vai trò.
- Giỏ hàng, hồ sơ và đơn vẫn tồn tại sau khi khởi động lại ứng dụng.
- Kiểm thử tính tiền, số lượng không hợp lệ, sản phẩm bị ẩn, hết hàng, thay đổi giá trước khi đặt và hai khách mua mặt hàng chỉ còn một đơn vị.
- Kiểm thử bấm đặt hàng liên tục và gửi lại yêu cầu sau lỗi mạng; không phát sinh đơn hoặc hoàn tồn trùng.
- Khách hàng không đọc đơn của người khác, không sửa giá/tồn kho và không tự cấp quyền quản trị.
- Kiểm thử các chuyển trạng thái hợp lệ và không hợp lệ; đơn đã giao không hủy được.
- Các màn hình dữ liệu có trạng thái tải, rỗng, lỗi và thao tác thử lại khi phù hợp.
- Kiểm tra trên thiết bị hoặc máy ảo thuộc mức Android tối thiểu được hỗ trợ và thiết bị dùng để trình diễn.
- Bản APK nộp chạy ổn định, không gặp lỗi làm dừng ứng dụng trong các luồng nghiệm thu.

Tự động hóa kiểm thử cho tính tiền, quy tắc trạng thái và chống trùng; dùng môi trường Firebase thử nghiệm/emulator để kiểm tra phân quyền và giao dịch. Kiểm tra giao diện và kịch bản demo trên thiết bị.

## 10. Rủi ro và cách xử lý

| Rủi ro | Cách xử lý |
|---|---|
| Dịch vụ/backend không triển khai được | Kiểm chứng ở tuần 1; thống nhất phương án thay thế và điều chỉnh lịch trước khi làm toàn bộ màn hình |
| Phạm vi vượt thời gian | Hoàn thành chức năng bắt buộc trước; chỉ thêm yêu thích, đánh giá, mã giảm giá sau mốc tuần 6 |
| Đơn hàng và tồn kho không đồng nhất | Giao dịch backend, kiểm tra dữ liệu đầu vào và thử tình huống đặt đồng thời |
| Các phần khó tích hợp | Thống nhất hợp đồng dữ liệu sớm; làm một luồng xuyên suốt từ tuần 2; tích hợp hàng tuần |
| Demo thiếu mạng hoặc dữ liệu | Chuẩn bị dữ liệu và tài khoản trước; kiểm tra kết nối; có video dự phòng; không hiển thị đặt thành công khi chưa lưu đơn |

## 11. Hồ sơ bàn giao

- Mã nguồn kèm hướng dẫn cấu hình, chạy ứng dụng và chuẩn bị dữ liệu.
- APK, dữ liệu demo và tài khoản thử nghiệm cho khách hàng/quản trị viên.
- Báo cáo: bài toán, khảo sát yêu cầu, use case, thiết kế dữ liệu, kiến trúc, giao diện, triển khai, kết quả kiểm thử, hạn chế và hướng phát triển.
- Sơ đồ use case, dữ liệu và luồng đặt/hủy đơn.
- Slide thuyết trình và video demo dự kiến 3–5 phút.
- Bảng phân công, tiến độ và kết quả đóng góp của từng thành viên.

Bước triển khai đầu tiên: chốt thời hạn, số thành viên và yêu cầu công nghệ của học phần; sau đó kiểm chứng kết nối dữ liệu/backend và xây dựng lát cắt đăng nhập → danh sách sản phẩm.

## 12. Kế hoạch chi tiết theo phase

Mỗi phase là một giai đoạn có đầu vào, checklist và điều kiện nghiệm thu. Một tuần có thể chứa nhiều phase; thời gian trong bảng là vị trí dự kiến trong lịch 8 tuần, không phải số tuần riêng cho từng phase. Các việc độc lập có thể làm song song sau khi chốt hợp đồng dữ liệu.

| Phase | Nội dung | Thời điểm | Phụ thuộc | Phụ trách đề xuất |
|---|---|---|---|---|
| 01 | Phân tích yêu cầu và nghiệp vụ | Tuần 1 | Không | Cả nhóm |
| 02 | Kiểm chứng công nghệ và môi trường | Tuần 1 | Phạm vi sơ bộ của 01 | B |
| 03 | Thiết kế giao diện và trải nghiệm | Tuần 1–2 | 01 | A, C |
| 04 | Xây dựng nền tảng mã nguồn và dữ liệu | Tuần 2 | 01, 02 | A, B |
| 05 | Tài khoản và phân quyền | Tuần 2 | 04 | B |
| 06 | Danh mục và chi tiết sản phẩm | Tuần 2–3 | 03, 04 | A |
| 07 | Giỏ hàng và thông tin giao hàng | Tuần 4 | 05, 06 | C |
| 08 | Đặt hàng COD và giao dịch tồn kho | Tuần 4 | 05, 07 | B, C |
| 09 | Hồ sơ, lịch sử và hủy đơn | Tuần 5 | 08 | B, C |
| 10 | Quản trị sản phẩm và đơn hàng | Tuần 6 | 05, 06, 08, 09 | A, B, C |
| 11 | Tích hợp toàn bộ và hoàn thiện giao diện | Cuối tuần 6–đầu tuần 7 | 05–10 | Cả nhóm |
| 12 | Kiểm thử và ổn định bản nộp | Tuần 7 | 11 | Cả nhóm |
| 13 | Báo cáo, đóng gói và bảo vệ | Tuần 8 | 12 | Cả nhóm |
| 14 | Chức năng mở rộng | Sau khi 12 đạt, nếu còn thời gian | Các chức năng liên quan | Theo năng lực |

### Phase 01 — Phân tích yêu cầu và nghiệp vụ

**Mục tiêu:** xác định chính xác bản nộp phải làm được gì trước khi xây dựng màn hình.

- [ ] Chốt số thành viên, hạn nộp, yêu cầu của giảng viên và cách đánh giá.
- [ ] Xác nhận nền tảng Android, một cửa hàng, VND và thanh toán COD.
- [ ] Lập use case cho khách, khách hàng và quản trị viên.
- [ ] Mô tả luồng mua hàng, hủy đơn, quản lý sản phẩm và xử lý đơn.
- [ ] Chốt công thức tính tiền, phí giao hàng, quy tắc trừ/hoàn tồn và trạng thái đơn.
- [ ] Chốt thuộc tính sản phẩm, ảnh và cách biểu diễn màu/dung tích trong bản đầu.
- [ ] Phân loại chức năng bắt buộc và mở rộng; phân công người phụ trách.

**Bàn giao:** danh sách yêu cầu, sơ đồ use case, quy tắc nghiệp vụ và danh sách việc đã phân công. Có thể bổ sung nội dung và hình vào báo cáo khi triển khai; file này vẫn là kế hoạch tổng hợp.

**Điều kiện hoàn thành:** nhóm thống nhất một kịch bản nghiệm thu xuyên suốt và biết chức năng nào được phép cắt khi thiếu thời gian.

### Phase 02 — Kiểm chứng công nghệ và môi trường

**Mục tiêu:** phát hiện sớm vấn đề công cụ, kết nối hoặc triển khai dịch vụ.

- [ ] Build dự án hiện tại; chạy màn hình mẫu trên thiết bị dùng để phát triển.
- [ ] Kiểm tra JDK, Android SDK và Gradle tương thích với cấu hình dự án.
- [ ] Tạo môi trường Firebase thử nghiệm; bật xác thực email/mật khẩu.
- [ ] Thử đăng nhập, đọc một sản phẩm mẫu và gọi một backend function đơn giản.
- [ ] Kiểm tra khả năng chạy emulator dịch vụ và triển khai môi trường demo.
- [ ] Kiểm tra yêu cầu tài khoản, hạn mức và chi phí của phương án triển khai đã chọn.
- [ ] Chuẩn bị cách cấu hình cho từng thành viên; giữ thông tin đặc quyền backend ngoài ứng dụng.
- [ ] Chốt phương án dự phòng nếu dịch vụ không triển khai được; cập nhật lịch nếu chuyển backend.

**Bàn giao:** môi trường chạy được, hướng dẫn cấu hình và kết luận chọn công nghệ.

**Điều kiện hoàn thành:** một thiết bị Android giao tiếp thành công với môi trường dữ liệu/backend dự kiến dùng cho bản nộp. Việc chỉ chạy với dữ liệu giả chưa đủ để kết thúc phase này.

### Phase 03 — Thiết kế giao diện và trải nghiệm

**Mục tiêu:** thống nhất giao diện và luồng điều hướng trước khi phát triển nhiều màn hình.

- [ ] Chọn màu chính, màu nền, kiểu chữ, khoảng cách và cách hiển thị tiền.
- [ ] Vẽ wireframe cho toàn bộ màn hình bắt buộc tại mục 6.
- [ ] Thiết kế thẻ sản phẩm, thanh tìm kiếm, bộ lọc, bộ chọn số lượng và phần tổng tiền.
- [ ] Thiết kế trạng thái đang tải, không có dữ liệu, lỗi, hết hàng và ảnh lỗi.
- [ ] Xác định điểm cần đăng nhập và màn hình quay lại sau đăng nhập.
- [ ] Thiết kế xác nhận xóa giỏ, hủy đơn và ẩn sản phẩm.
- [ ] Kiểm tra nội dung dài, bàn phím che biểu mẫu, kích thước màn hình và chữ dễ đọc.

**Bàn giao:** wireframe, sơ đồ điều hướng và danh sách thành phần giao diện dùng chung.

**Điều kiện hoàn thành:** có thể đi qua luồng mua hàng và quản trị trên bản thiết kế; mỗi màn hình có hành động chính và đường quay lại rõ ràng.

### Phase 04 — Xây dựng nền tảng mã nguồn và dữ liệu

**Mục tiêu:** tạo nền tảng để các thành viên phát triển chức năng mà không phụ thuộc chặt vào nhau.

- [ ] Tổ chức mã theo nhóm `core`, `data`, `feature` và phần điều hướng.
- [ ] Định nghĩa model cho người dùng, danh mục, sản phẩm, giỏ và đơn.
- [ ] Định nghĩa các Repository cho tài khoản, sản phẩm, giỏ và đơn.
- [ ] Thiết lập ViewModel, trạng thái màn hình và cách biểu diễn lỗi.
- [ ] Tạo nguồn dữ liệu mẫu để phát triển giao diện; tách biệt với nguồn Firebase.
- [ ] Thiết lập cấu trúc lưu trữ, index cần cho truy vấn đã chọn và Security Rules ban đầu.
- [ ] Chuẩn bị 20–30 sản phẩm, 4 danh mục, trường hợp hết hàng và ảnh dự phòng.
- [ ] Tạo điều hướng chính và thành phần dùng chung từ thiết kế.

**Bàn giao:** khung ứng dụng chạy được, model và Repository thống nhất, dữ liệu demo có thể nạp lại.

**Điều kiện hoàn thành:** màn hình mẫu nhận dữ liệu qua Repository; không truy cập trực tiếp Firestore trong composable. Bộ dữ liệu demo có thể được dựng lại bằng quy trình được ghi rõ.

### Phase 05 — Tài khoản và phân quyền

**Mục tiêu:** bảo đảm mọi thao tác cá nhân và quản trị gắn với đúng người dùng.

- [x] Xây dựng đăng ký, đăng nhập và đăng xuất bằng SQLite.
- [x] Kiểm tra email, mật khẩu, xác nhận mật khẩu và lỗi tài khoản đã tồn tại.
- [x] Lưu tên/email và phiên trong cùng giao dịch đăng ký, tránh tạo tài khoản dở dang.
- [x] Khôi phục phiên đăng nhập từ database khi mở lại ứng dụng.
- [ ] Chặn truy cập giỏ, đặt hàng và đơn cá nhân khi chưa đăng nhập.
- [x] Cấp quyền quản trị từ cấu hình build debug cục bộ; biểu mẫu đăng ký không cấp/sửa quyền. Backend tin cậy cho bản nhiều thiết bị còn chờ triển khai.
- [x] Xóa trạng thái tài khoản trên giao diện khi đăng xuất; phiên mới chỉ chứa tài khoản hiện tại.
- [ ] Tạo ít nhất hai tài khoản khách và một tài khoản quản trị để thử nghiệm.

**Bàn giao:** luồng tài khoản hoạt động và cơ chế phân quyền có kiểm tra ở dữ liệu/backend.

**Điều kiện hoàn thành:** đổi tài khoản không thấy giỏ hoặc đơn của tài khoản trước; khách không thể truy cập dữ liệu quản trị bằng cách gọi yêu cầu trực tiếp.

**Tiến độ thực tế:** phần xác thực cục bộ đã có và được kiểm thử; Phase 05 chưa hoàn thành toàn bộ vì chưa có khu vực quản trị, giỏ hàng và đơn hàng để kiểm tra phân quyền tương ứng.

### Phase 06 — Danh mục và chi tiết sản phẩm

**Mục tiêu:** khách tìm được mỹ phẩm và có đủ thông tin trước khi thêm vào giỏ.

- [x] Trang chủ hiển thị danh mục và bộ sản phẩm demo từ nguồn thực tế.
- [x] Danh sách sản phẩm có ảnh, tên, thương hiệu và giá.
- [x] Tìm kiếm tên không phân biệt hoa/thường, dấu tiếng Việt và đ/d; hỗ trợ nhiều từ khóa.
- [x] Lọc theo danh mục; sắp xếp giá tăng/giảm.
- [x] Bộ dữ liệu demo 8 sản phẩm được tìm/lọc trên thiết bị; phạm vi này chưa dành cho kho hàng lớn.
- [x] Chi tiết hiển thị thành phần nổi bật, cách dùng, mô tả và liên kết nguồn.
- [x] Bổ sung trạng thái tồn kho demo cho chi tiết sản phẩm.
- [x] Sản phẩm hết hàng không cho thêm; sản phẩm bị ẩn không xuất hiện trong danh sách bán.
- [ ] Xử lý ảnh lỗi, sản phẩm không tồn tại, tải lỗi và kết quả rỗng.

**Bàn giao:** luồng trang chủ → danh sách → chi tiết hoạt động với dữ liệu thật.

**Điều kiện hoàn thành:** kết quả tìm/lọc/sắp xếp đúng dữ liệu demo; thay đổi giá hoặc trạng thái sản phẩm được hiển thị sau khi tải lại.

### Phase 07 — Giỏ hàng và thông tin giao hàng

**Mục tiêu:** khách quản lý được các mặt hàng muốn mua và nhập thông tin nhận hàng.

- [x] Thêm sản phẩm vào giỏ theo tài khoản; thêm lại cùng sản phẩm thì cộng số lượng.
- [x] Tăng, giảm số lượng và xóa mục giỏ; kiểm tra số lượng hợp lệ.
- [x] Đọc lại giá và trạng thái bán để hiển thị cảnh báo sản phẩm thay đổi.
- [x] Tính tạm tính và hiển thị tổng số sản phẩm.
- [x] Lưu giỏ và khôi phục khi đăng nhập lại.
- [x] Nhập người nhận, số điện thoại, địa chỉ; dùng lại thông tin giao hàng đã lưu của tài khoản.
- [x] Kiểm tra trường trống và quy tắc số điện thoại cho bản demo: số di động Việt Nam 10 chữ số, hỗ trợ +84/84 và dấu cách/dấu phân cách.
- [x] Chặn tiếp tục khi giỏ rỗng hoặc có mặt hàng không còn hợp lệ.

**Bàn giao:** giỏ hàng lưu theo tài khoản và biểu mẫu giao hàng hợp lệ.

**Điều kiện hoàn thành:** các phép tính đúng; sửa giỏ tài khoản A không ảnh hưởng tài khoản B; không mất dữ liệu vừa nhập khi quay lại từ bước xác nhận.

### Phase 08 — Đặt hàng COD và giao dịch tồn kho

**Mục tiêu:** tạo đơn đúng một lần, đúng giá và không bán vượt tồn kho.

- [x] Áp dụng hợp đồng yêu cầu gồm requestId, tài khoản, sản phẩm/số lượng/giá và địa chỉ cho bản SQLite cục bộ.
- [x] Xây dựng màn hình xác nhận mặt hàng, địa chỉ, phí giao hàng và tổng tiền.
- [x] Repository SQLite kiểm tra phiên hiện tại, số lượng, sản phẩm đang bán và tồn kho trong transaction.
- [x] Repository tính tiền từ database; giá/giỏ/địa chỉ thay đổi phải xem và xác nhận lại.
- [x] Tạo đơn, lưu thông tin sản phẩm tại thời điểm mua và trừ tồn trong cùng giao dịch.
- [x] Sinh và lưu `requestId` trước khi gửi; thử lại cùng yêu cầu dùng lại mã đó.
- [x] Kiểm tra mã chống trùng gắn với tài khoản và nội dung đơn; giỏ/địa chỉ thay đổi được cấp mã mới sau khi xác nhận lại.
- [x] Giới hạn thao tác trong khi gửi; khóa UNIQUE và transaction bảo đảm chống trùng ở tầng dữ liệu.
- [x] Chỉ hiển thị thành công sau khi commit; lưu yêu cầu để tra lại/thử lại cùng mã sau khi mở lại ứng dụng.
- [x] Dọn giỏ trong transaction sau khi đối chiếu đầy đủ; thêm mới sau commit được giữ và gửi lại không dọn giỏ lần nữa.
- [x] Kiểm thử hai tài khoản lập yêu cầu khi còn một đơn vị, rồi xác nhận lần lượt theo phiên đăng nhập; chỉ đơn đầu thành công.
- [ ] Triển khai backend và thử nghiệm nhiều thiết bị dùng chung kho khi mở rộng khỏi bản cục bộ.

**Bàn giao hiện tại:** luồng COD SQLite xuyên suốt, transaction kiểm tra giá/tồn và chống trùng trên một thiết bị. Backend dùng chung vẫn là phần mở rộng kiến trúc.

**Điều kiện hoàn thành:** chỉ một đơn được tạo khi gửi lặp; tồn kho không âm; nếu một mặt hàng không đủ tồn thì cả đơn thất bại và không trừ tồn các mặt hàng còn lại.

### Phase 09 — Hồ sơ, lịch sử và hủy đơn

**Mục tiêu:** khách xem lại giao dịch và quản lý thông tin của mình.

- [x] Hiển thị danh sách đơn theo thời gian mới nhất và chi tiết từng đơn.
- [x] Hiển thị tên/giá đã lưu trong đơn, địa chỉ tại thời điểm đặt và trạng thái hiện tại.
- [x] Sửa tên, số điện thoại, địa chỉ mặc định; giữ nguyên thông tin giao hàng của đơn đã tạo.
- [x] Repository SQLite kiểm tra phiên hiện tại, chủ sở hữu và trạng thái trước khi hủy.
- [x] Chuyển sang đã hủy và hoàn tồn trong cùng giao dịch.
- [x] Gửi lặp hoặc đồng thời yêu cầu hủy không hoàn tồn thêm lần nữa.
- [x] Kiểm tra xung đột với thao tác xác nhận có điều kiện trên trạng thái Chờ xác nhận; chỉ một thao tác thắng. Giao diện quản trị thuộc Phase 10.
- [x] Cho tải lại để xem trạng thái mới; cập nhật kết quả hủy ngay trên chi tiết đơn.

**Bàn giao:** hồ sơ, lịch sử, chi tiết và hủy đơn đúng quyền.

**Điều kiện hoàn thành:** người dùng chỉ thấy đơn của mình; hủy hợp lệ hoàn tồn đúng một lần; trạng thái đã xác nhận không còn cho khách hủy.

### Phase 10 — Quản trị sản phẩm và đơn hàng

**Mục tiêu:** quản trị viên quản lý được toàn bộ dữ liệu phục vụ luồng mua hàng.

- [x] Tạo lối vào quản trị cho tài khoản được cấp quyền.
- [x] Danh sách sản phẩm có tìm kiếm, tồn kho và trạng thái đang bán/đã ẩn.
- [x] Biểu mẫu thêm/sửa kiểm tra tên, danh mục, giá nguyên VND, tồn kho không âm và thông tin ảnh.
- [x] Bản đầu cho chọn ảnh demo hoặc nhập URL đã chuẩn bị; chức năng tải ảnh lên là phần mở rộng.
- [x] Ẩn và mở bán lại sản phẩm; giữ thông tin lịch sử đơn.
- [x] Cập nhật tồn kho qua Repository SQLite cục bộ, xử lý xung đột với giao dịch đặt/hủy đơn; không ghi đè từ một bản dữ liệu cũ.
- [x] Danh sách đơn có lọc trạng thái và xem chi tiết.
- [x] Chuyển trạng thái theo bảng ở mục 13; hủy trước giao hàng theo quyền quản trị.
- [x] Ghi thời điểm và người thực hiện thay đổi trạng thái để kiểm tra.
- [x] Xử lý lỗi mất quyền và dữ liệu thay đổi giữa lúc mở và lưu biểu mẫu.

- [x] Chạy kiểm thử Android, build APK chứa quản trị và lưu ảnh nghiệm thu trên Pixel 6 / Android 16; kết quả cập nhật ở mục 22.
- [ ] Khi mở rộng nhiều thiết bị, chuyển quyền quản trị và kiểm tra xung đột sang backend dùng chung.

**Bàn giao:** phần quản trị sản phẩm, tồn kho và vòng đời đơn cho bản SQLite cục bộ; đã build và nghiệm thu Android trong Phase 11, xem mục 22.

**Điều kiện hoàn thành:** khách không thực hiện được thao tác quản trị qua giao diện hoặc yêu cầu trực tiếp; không sửa được trạng thái kết thúc; cập nhật tồn đồng thời không làm mất thay đổi từ đặt/hủy đơn.

### Phase 11 — Tích hợp toàn bộ và hoàn thiện giao diện

**Mục tiêu:** các chức năng tạo thành một trải nghiệm nhất quán.

- [x] Chạy kịch bản chính bằng khách → quản trị → khách qua các phiên lần lượt trên một thiết bị của bản SQLite.
- [ ] Chạy hai thiết bị dùng chung kho sau khi có backend; đây là phần mở rộng, không áp dụng cho database cục bộ hiện tại.
- [x] Kiểm tra điều hướng sau đăng nhập, đặt thành công, hủy đơn và đăng xuất.
- [x] Đồng nhất định dạng giá, nhãn trạng thái, thông báo lỗi và cách tải lại.
- [x] Kiểm tra giỏ khi sản phẩm đổi giá, bị ẩn hoặc giảm tồn.
- [x] Kiểm tra lưu/khôi phục trạng thái Compose, quay lại, khôi phục yêu cầu COD từ database và bàn phím trong biểu mẫu.
- [x] Kiểm tra tái tạo Activity thật ở giao hàng, xác nhận và sau commit; nghiệm thu bố cục ngang/cỡ chữ lớn trên thiết bị trình diễn thuộc Phase 12.
- [x] Rà soát ảnh có sẵn, nội dung trong khung cuộn, vùng bấm Material và các màn hình trống qua bộ kiểm thử giao diện.
- [x] Đóng phạm vi bản SQLite cục bộ; ghi lỗi tích hợp và người xử lý tại mục 22.4.

**Bàn giao:** bản tích hợp có đầy đủ chức năng bắt buộc và danh sách lỗi cần xử lý.

**Điều kiện hoàn thành:** kịch bản mua hàng và quản trị chạy liên tục; không còn màn hình bắt buộc chỉ dùng dữ liệu giả.

### Phase 12 — Kiểm thử và ổn định bản nộp

**Mục tiêu:** xác nhận tính đúng của nghiệp vụ và độ ổn định trước khi đóng gói.

- [x] Đối chiếu ma trận tại mục 14, ghi ca đạt/một phần/chưa áp dụng theo SQLite tại mục 23.4.
- [x] Viết/chạy kiểm thử phù hợp cho tính tiền, trạng thái, giao dịch và chống trùng (SQLite; mục 23).
- [x] Kiểm thử quyền Repository SQLite với phiên chưa đăng nhập, khách A/B, quản trị và quyền bị thu hồi. Security Rules chưa áp dụng do chưa có backend.
- [ ] Quyền backend/Security Rules: chưa áp dụng trong bản SQLite, giữ cho phạm vi mở rộng.
- [ ] Mất mạng quanh thời điểm backend lưu: chưa áp dụng. Đã kiểm tra SQLite rollback và khôi phục kết quả commit bằng ViewModel mới.
- [x] Thử nhiều yêu cầu cùng phiên, hủy đồng thời, đổi giá và bản nháp tồn cũ trong SQLite. Nhiều thiết bị chưa áp dụng.
- [x] Sửa lỗi phân quyền và tải lại khi mất phiên; kiểm thử liên quan và toàn bộ 105 ca đạt.
- [ ] Nghiệm thu đủ các thiết bị: đã build/cài release QA trên API 36; API 28 và thiết bị trình diễn thực tế còn chờ.
- [x] Lưu kết quả, ảnh minh chứng, checksum APK và các hạn chế tại mục 23.

**Bàn giao:** bản ứng viên phát hành, kết quả kiểm thử và danh sách hạn chế đã biết.

**Điều kiện hoàn thành:** toàn bộ ca bắt buộc đạt; không còn lỗi làm dừng ứng dụng, sai quyền, sai tiền, đơn trùng hoặc sai tồn kho trong các tình huống nghiệm thu.

### Phase 13 — Báo cáo, đóng gói và bảo vệ

**Mục tiêu:** bàn giao bộ sản phẩm có thể chạy lại và trình bày rõ đóng góp của nhóm.

- [ ] Báo cáo kỹ thuật và ảnh đã có (mục 24); trang bìa/thông tin nhóm và mẫu học phần còn chờ hoàn thiện.
- [x] Hoàn thiện 5 sơ đồ Mermaid/SVG theo SQLite v5 và luồng đặt/hủy hiện có.
- [x] Viết hướng dẫn cài/build, tài khoản mẫu, seed, kiểm thử và đóng gói.
- [x] Build/cài APK trình diễn .demo; diễn tập khách → admin → khách trên API 36, kiểm tra chữ ký và dữ liệu cuối.
- [x] Chuẩn bị slide HTML 10 trang và video thực tế 4 phút 10 giây có phụ đề.
- [ ] Điền/xác nhận phân công đóng góp thực tế của nhóm; chưa có thông tin thành viên.
- [x] Chuẩn bị admin/khách công khai của gói .demo, một đơn mẫu Đã giao và tồn còn 29.
- [x] Diễn tập qua ADB: đăng nhập → sản phẩm → COD → quản trị xử lý → khách thấy Đã giao, không có nút hủy.
- [x] Chuẩn bị 11 câu hỏi bảo vệ gắn với mã/kiểm thử và các giới hạn thực tế.
- [x] Đóng gói snapshot mã nguồn/APK/tài liệu, SHA-256 và kiểm tra ZIP; chưa có commit Git.

**Bàn giao:** mã nguồn, APK, hướng dẫn, báo cáo, slide, video và kết quả kiểm thử.

**Điều kiện hoàn thành:** thành viên khác có thể làm theo hướng dẫn để chạy; APK và báo cáo mô tả đúng cùng một phiên bản.

### Phase 14 — Chức năng mở rộng

**Mục tiêu:** bổ sung giá trị khi phần bắt buộc đã ổn định và không ảnh hưởng hạn nộp.

Thực hiện từng chức năng; hoàn thành và kiểm thử chức năng trước rồi mới chọn chức năng tiếp theo.

| Thứ tự đề xuất | Chức năng | Điều kiện nghiệm thu |
|---|---|---|
| 1 | Yêu thích | Danh sách lưu theo tài khoản; thêm/xóa không trùng |
| 2 | Đánh giá | Chỉ người đã mua và nhận hàng được đánh giá; chốt giới hạn đánh giá |
| 3 | Thống kê đơn/doanh thu | Chỉ quản trị; chỉ tính đơn đã giao; xác định rõ khoảng thời gian |
| 4 | Mã giảm giá | Backend kiểm tra hạn dùng, điều kiện và lượt dùng; chống dùng vượt giới hạn |
| 5 | Thông báo | Đúng người nhận; bấm thông báo mở đúng đơn và vẫn kiểm tra quyền |
| 6 | Biến thể/ảnh tải lên | Mỗi biến thể có giá/tồn riêng; ảnh tải lên có kiểm tra quyền và đầu vào |

Thanh toán trực tuyến là hạng mục riêng sau đồ án, cần thiết kế xác nhận giao dịch từ backend, thử nghiệm sandbox và điều chỉnh tiến độ; không thêm vội vào tuần nộp.

**Bàn giao:** chức năng mở rộng đã kiểm thử và tài liệu cập nhật tương ứng.

**Điều kiện hoàn thành:** các ca nghiệm thu bắt buộc vẫn đạt; không để chức năng mở rộng dở dang xuất hiện trong bản nộp.

## 13. Chi tiết dữ liệu và hợp đồng xử lý

### 13.1. Cấu trúc lưu trữ đề xuất

Đây là phương án để chốt tại Phase 01–04, chưa phải cấu trúc đã triển khai.

| Đường dẫn dự kiến | Nội dung | Quyền ghi dự kiến |
|---|---|---|
| `users/{uid}` | Hồ sơ và địa chỉ mặc định | Chủ tài khoản sửa các trường hồ sơ được phép; vai trò do môi trường tin cậy cấp |
| `categories/{categoryId}` | Danh mục và thứ tự hiển thị | Quản trị viên |
| `products/{productId}` | Thông tin, giá, tồn và trạng thái bán | Quản trị được sửa trường cho phép; thay đổi tồn qua backend |
| `users/{uid}/cart/{productId}` | Mã sản phẩm, số lượng, phiên bản cập nhật | Chủ tài khoản, có kiểm tra kiểu dữ liệu và số lượng |
| `orders/{orderId}` | Người mua, địa chỉ, tổng tiền, trạng thái và các dòng hàng | Backend tạo và thay đổi; khách chỉ đọc đơn của mình, quản trị đọc theo quyền |
| `users/{uid}/orderRequests/{requestId}` | Mã đơn kết quả và dấu nhận diện nội dung yêu cầu | Backend; chủ tài khoản có thể đọc kết quả nếu cần |

Dự kiến nhúng danh sách `OrderItem` vào đơn và giới hạn 20 mặt hàng khác nhau mỗi đơn để giới hạn kích thước và đơn giản hóa demo. Nếu yêu cầu thay đổi, đánh giá lại cách lưu trước khi triển khai.

Chọn custom claims làm nguồn quyền quản trị nếu dùng Firebase. Trường `role` trong hồ sơ, nếu giữ, chỉ là dữ liệu đồng bộ từ nguồn tin cậy; không dùng giá trị do khách gửi để quyết định quyền.

Các trường cần bổ sung khi triển khai: `createdAt`, `updatedAt`, `updatedBy`, `stockVersion`, `cancelledAt` và `cancelledBy` khi phù hợp. Backend đặt thời gian cho giao dịch và thay đổi trạng thái.

### 13.2. Hợp đồng nghiệp vụ dự kiến

Các tên dưới đây là tên thao tác đề xuất, không bắt buộc là URL REST.

| Thao tác | Đầu vào chính | Kết quả | Kiểm tra bắt buộc |
|---|---|---|---|
| `createOrder` | requestId, dòng hàng, giá đã xem, địa chỉ, phí giao đã xem | orderId, tổng tiền, trạng thái ban đầu | Đăng nhập, chống trùng, giá/phí hiện tại, tồn, số lượng, địa chỉ |
| `cancelOrder` | orderId | Đơn đã hủy hoặc kết quả hủy đã có | Chủ đơn/quản trị, trạng thái hợp lệ, hoàn tồn một lần |
| `updateOrderStatus` | orderId, trạng thái mong đợi, trạng thái đích | Trạng thái mới | Quyền quản trị, trạng thái hiện tại, chuyển hợp lệ |
| `setProductStock` | productId, tồn mong muốn, stockVersion đã đọc | Tồn và phiên bản mới | Quyền quản trị, số nguyên không âm, từ chối bản dữ liệu cũ |

Backend dùng danh tính từ phiên xác thực; không tin `userId` hay vai trò do ứng dụng truyền lên. Khi giao dịch xung đột, xử lý trên dữ liệu hiện tại; nếu không còn đủ điều kiện thì trả lỗi để tải lại.

Mã lỗi nghiệp vụ dự kiến: `UNAUTHENTICATED`, `FORBIDDEN`, `INVALID_INPUT`, `PRODUCT_UNAVAILABLE`, `OUT_OF_STOCK`, `PRICE_CHANGED`, `ORDER_STATE_CONFLICT`, `REQUEST_CONFLICT`. Ứng dụng ánh xạ sang thông báo tiếng Việt và hành động phù hợp.

`requestId` chỉ được dùng lại cho cùng nội dung đơn. Nếu yêu cầu trước đã thành công, trả lại đơn đó. Nếu cùng mã nhưng nội dung khác, trả `REQUEST_CONFLICT`. Thử lại sau lỗi mạng giữ cùng mã; thay mặt hàng/địa chỉ hoặc xác nhận giá mới phải tạo mã mới sau khi đã xác định kết quả yêu cầu cũ.

### 13.3. Bảng chuyển trạng thái đơn

| Trạng thái hiện tại | Trạng thái tiếp theo | Người thực hiện | Tác động tồn |
|---|---|---|---|
| Chưa có đơn | Chờ xác nhận | Khách qua backend tạo đơn | Trừ tồn một lần |
| Chờ xác nhận | Đã xác nhận | Quản trị | Không đổi |
| Chờ xác nhận | Đã hủy | Chủ đơn hoặc quản trị | Hoàn tồn một lần |
| Đã xác nhận | Đang giao | Quản trị | Không đổi |
| Đã xác nhận | Đã hủy | Quản trị | Hoàn tồn một lần |
| Đang giao | Đã giao | Quản trị | Không đổi |
| Đã giao / Đã hủy | Không có chuyển mới | Không | Không đổi |

Yêu cầu lặp tới một trạng thái đã đạt có thể trả kết quả hiện có mà không ghi tác động lần hai. Các chuyển bỏ qua bước, quay lùi hoặc hủy khi đang giao đều bị từ chối trong bản đầu. Đổi trả thuộc phạm vi phát triển sau.

### 13.4. Tổ chức mã dự kiến

```text
app/src/main/java/com/example/bloomybeauty/
  core/                 # định dạng tiền, kết quả/lỗi, thành phần dùng chung
  data/
    model/              # model lưu trữ và ánh xạ
    repository/         # hợp đồng và triển khai truy cập dữ liệu
    source/             # Firebase và dữ liệu mẫu
  feature/
    auth/
    home/
    catalog/
    product/
    cart/
    checkout/
    orders/
    profile/
    admin/
  navigation/
  ui/theme/             # tiếp tục dùng theme hiện có

backend/                # dự kiến mã xử lý nghiệp vụ nếu dùng Cloud Functions
```

Mỗi feature gồm màn hình, ViewModel và trạng thái màn hình khi cần. Các thư mục trên là cấu trúc dự kiến; không cần tạo thư mục rỗng hoặc nhiều module Gradle chỉ để khớp sơ đồ.

## 14. Ma trận kiểm thử

Thực hiện trên môi trường thử nghiệm có thể khôi phục dữ liệu. Mỗi ca cần ghi người chạy, ngày chạy, kết quả và lỗi liên quan; chưa ghi “đạt” khi chưa thực hiện.

| Mã | Ca kiểm thử | Kết quả mong đợi | Ưu tiên |
|---|---|---|---|
| AUTH-01 | Đăng ký hợp lệ, đăng xuất, đăng nhập lại | Hồ sơ đúng và phiên hoạt động | Bắt buộc |
| AUTH-02 | Email trùng, mật khẩu sai, dữ liệu thiếu | Báo lỗi phù hợp, không tạo dữ liệu sai | Bắt buộc |
| AUTH-03 | Đổi từ khách A sang B | Không còn dữ liệu cá nhân A trên giao diện | Bắt buộc |
| CAT-01 | Tìm kiếm, lọc, sắp xếp | Khớp quy tắc và dữ liệu mẫu | Bắt buộc |
| CAT-02 | Sản phẩm hết hàng, bị ẩn hoặc không tồn tại | Không mua được; có thông báo phù hợp | Bắt buộc |
| CART-01 | Thêm cùng sản phẩm, tăng/giảm, xóa | Số lượng và tạm tính đúng | Bắt buộc |
| CART-02 | Mở lại ứng dụng | Giỏ đúng tài khoản vẫn tồn tại | Bắt buộc |
| ORDER-01 | Đặt nhiều mặt hàng hợp lệ | Một đơn; tiền và tồn đúng | Bắt buộc |
| ORDER-02 | Giá/phí thay đổi trước khi đặt | Yêu cầu xem và xác nhận lại; không tự tạo đơn với giá mới | Bắt buộc |
| ORDER-03 | Một mặt hàng không đủ tồn | Không tạo đơn; không trừ tồn các mặt hàng khác | Bắt buộc |
| ORDER-04 | Hai khách mua mặt hàng tồn 1 | Chỉ một đơn thành công; tồn bằng 0 | Bắt buộc |
| ORDER-05 | Bấm nhiều lần hoặc gửi lại cùng mã | Chỉ một đơn, trừ tồn một lần | Bắt buộc |
| ORDER-06 | Mất mạng sau khi backend lưu | Tra lại/thử lại tìm đúng đơn; không tạo đơn mới | Bắt buộc |
| ORDER-07 | Thêm mục giỏ mới trong lúc đặt | Dọn giỏ không xóa mặt hàng mới hoặc dọn hai lần | Bắt buộc |
| CANCEL-01 | Hủy đơn chờ, gửi lại yêu cầu hủy | Đã hủy, hoàn tồn đúng một lần | Bắt buộc |
| CANCEL-02 | Khách hủy đồng thời quản trị xác nhận | Chỉ kết quả hợp lệ được lưu; tồn khớp trạng thái cuối | Bắt buộc |
| ADMIN-01 | Sửa sản phẩm đã có trong đơn | Đơn cũ giữ tên/giá lúc mua | Bắt buộc |
| ADMIN-02 | Đổi trạng thái hợp lệ và không hợp lệ | Chỉ chuyển đúng bảng trạng thái | Bắt buộc |
| ADMIN-03 | Cập nhật tồn đồng thời có đơn mới | Không ghi đè thay đổi tồn từ giao dịch khác | Bắt buộc |
| SEC-01 | Khách đọc đơn của người khác | Bị từ chối tại dữ liệu/backend | Bắt buộc |
| SEC-02 | Khách sửa quyền, giá, tồn hoặc trạng thái đơn | Bị từ chối cả khi gửi yêu cầu trực tiếp | Bắt buộc |
| UI-01 | Mất mạng, dữ liệu rỗng, ảnh lỗi | Không dừng ứng dụng; có trạng thái và hành động phù hợp | Bắt buộc |
| UI-02 | Xoay màn hình, quay lại, mở bàn phím | Điều hướng và biểu mẫu hoạt động đúng | Bắt buộc |
| RELEASE-01 | Cài mới APK và chạy luồng demo | Hoàn thành bằng bản APK nộp | Bắt buộc |

Tài liệu đối chiếu kỹ thuật: [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions), [callable functions](https://firebase.google.com/docs/functions/callable), [kiểm thử Security Rules](https://firebase.google.com/docs/rules/unit-tests). Các quy tắc đặt/hủy, chống trùng và quyền trong tài liệu này là thiết kế đề xuất của ứng dụng, cần được triển khai và kiểm thử riêng.

## 15. Theo dõi tiến độ và quy tắc hoàn thành

### 15.1. Các mốc nghiệm thu

| Mốc | Hạn dự kiến | Chứng minh bằng |
|---|---|---|
| M1 — Công nghệ khả thi | Cuối tuần 1 | Android đăng nhập/đọc dữ liệu/gọi backend thử |
| M2 — Nền tảng chạy được | Cuối tuần 2 | Đăng nhập → danh sách dữ liệu thật |
| M3 — Duyệt hàng hoàn chỉnh | Cuối tuần 3 | Tìm/lọc → chi tiết → chuẩn bị thêm giỏ |
| M4 — Mua được hàng | Cuối tuần 4 | Đặt COD, lưu đơn và trừ tồn đúng |
| M5 — Khách quản lý được đơn | Cuối tuần 5 | Lịch sử, hồ sơ, hủy và hoàn tồn |
| M6 — Hoàn tất chức năng bắt buộc | Cuối tuần 6 | Quản trị sản phẩm/đơn, luồng hai vai trò |
| M7 — Bản ổn định | Cuối tuần 7 | Ma trận kiểm thử đạt, không còn lỗi nghiêm trọng |
| M8 — Bộ sản phẩm nộp | Cuối tuần 8 | APK, mã nguồn, báo cáo, slide, video |

### 15.2. Quy tắc cho từng đầu việc

- Mỗi việc có một người phụ trách chính, phase, đầu ra và tiêu chí nghiệm thu.
- Trạng thái dùng: Chưa bắt đầu → Đang làm → Chờ kiểm tra → Hoàn thành; thêm Bị chặn khi có phụ thuộc chưa giải quyết.
- Một chức năng chỉ hoàn thành khi giao diện, dữ liệu, kiểm tra lỗi và kiểm tra quyền liên quan đều chạy được.
- Checkbox chỉ đánh dấu sau khi có bằng chứng kiểm tra; dữ liệu mẫu chỉ dùng để nghiệm thu phần giao diện, không thay thế việc kết nối dữ liệu thật.
- Sau mỗi tuần cập nhật công việc đã xong, lỗi đang mở, phụ thuộc và kế hoạch tuần tiếp theo ngay trong file này hoặc công cụ quản lý việc của nhóm.
- Nếu dùng Git, ưu tiên thay đổi nhỏ theo chức năng; ghi rõ tác động dữ liệu và cách kiểm tra khi tích hợp.
- Viết phần báo cáo tương ứng ngay khi hoàn thành phase, tránh dồn toàn bộ việc viết vào tuần cuối.

### 15.3. Ưu tiên xử lý lỗi và điều chỉnh phạm vi

| Mức | Ví dụ | Cách xử lý |
|---|---|---|
| P0 | Lộ/sai quyền, sai tổng tiền, đơn trùng, sai tồn | Dừng bổ sung chức năng; sửa trước mọi việc khác |
| P1 | Không đăng nhập/đặt/hủy được trong luồng hợp lệ; ứng dụng dừng | Sửa trước khi đạt mốc chức năng |
| P2 | Lỗi điều hướng phụ, hiển thị hoặc thông báo gây khó sử dụng | Sửa trong tuần ổn định |
| P3 | Trang trí, hiệu ứng và cải tiến ngoài phạm vi | Làm khi còn thời gian |

Nếu M1 trễ, xử lý phương án dịch vụ trước khi tăng số màn hình. Nếu M4 trễ, bỏ toàn bộ phần mở rộng và ưu tiên đặt/hủy đơn cùng tồn kho. Nếu M6 trễ, rút gọn trang chủ trang trí và bộ lọc phụ; vẫn giữ luồng COD, quản trị tối thiểu, phân quyền và kiểm thử nghiệp vụ. Nếu M7 trễ, tập trung sửa lỗi và bộ hồ sơ nộp.

### 15.4. Các thông tin cần xác nhận khi bắt đầu

- [ ] Số thành viên và năng lực/phần phụ trách của từng người.
- [ ] Ngày nộp, thời lượng thực tế và mốc báo cáo của học phần.
- [ ] Có bắt buộc Java, XML, SQLite hoặc backend riêng hay không.
- [ ] Có cho phép dịch vụ Firebase và điều kiện triển khai backend hay không.
- [ ] Quy định về biểu mẫu báo cáo, số lượng chức năng và cách chấm điểm.
- [ ] Thiết bị, tài khoản, kết nối mạng và cách trình diễn bản nộp.

Các thông tin chưa xác nhận không ngăn việc chuẩn bị yêu cầu và wireframe, nhưng phải chốt trước khi chọn dịch vụ và triển khai các phần phụ thuộc công nghệ.

## 16. Phần đăng nhập SQLite đã triển khai

### 16.1. Phạm vi đã có

- Giao diện Compose tiếng Việt cho đăng nhập, đăng ký và màn hình tài khoản sau đăng nhập.
- Biểu mẫu đăng ký gồm họ tên, email, mật khẩu và nhập lại mật khẩu; đăng ký thành công đồng thời đăng nhập.
- Kiểm tra họ tên 2–80 ký tự, email hợp lệ, mật khẩu 8–128 ký tự và xác nhận khớp.
- Email được bỏ khoảng trắng hai đầu và chuyển chữ thường, để tránh tài khoản trùng vì khác hoa/thường.
- Mật khẩu giữ nguyên khoảng trắng/ký tự người dùng nhập; không chuẩn hóa mật khẩu.
- Hiện/ẩn mật khẩu, trạng thái đang xử lý và thông báo lỗi; ngăn gửi lại trong lúc đang xử lý.
- Phiên được lưu trong SQLite và khôi phục khi mở ứng dụng; đăng xuất chỉ xóa phiên, giữ tài khoản.
- Sau đăng nhập mở trang chủ sản phẩm. Tab Tài khoản hiển thị họ tên, email và nút đăng xuất.

### 16.2. Lưu trữ và xử lý

Database: `bloomy_beauty.db`, schema version hiện tại 4, nằm trong vùng lưu trữ riêng của ứng dụng. Version 1 chứa xác thực; version 2 thêm danh mục/yêu thích; version 3 thêm giỏ/giao hàng/tồn kho; version 4 thêm yêu cầu COD, đơn và chi tiết đơn (mục 19).

| Bảng | Cột | Ý nghĩa |
|---|---|---|
| `users` | `_id`, `name`, `email`, `password_hash`, `password_salt`, `password_iterations` | Tài khoản cục bộ; email có ràng buộc UNIQUE |
| `auth_session` | `_id`, `user_id` | Một phiên hiện tại; `_id` cố định bằng 1; khóa ngoại tới tài khoản |

Mật khẩu được băm bằng PBKDF2-HMAC-SHA256, salt ngẫu nhiên 16 byte, 210.000 vòng và kết quả 256 bit. Database lưu hash/salt/số vòng; không lưu mật khẩu gốc. Mật khẩu trong biểu mẫu chỉ ở bộ nhớ, không đưa vào saved instance state.

Áp dụng SQLite trực tiếp:

- Contract định nghĩa tên bảng/cột và version database.
- `SQLiteOpenHelper` tạo schema và bật kiểm tra khóa ngoại.
- `ContentValues` dùng để ghi dữ liệu; truy vấn dùng `?` và `selectionArgs`.
- Cursor được đóng bằng `use`; database helper được quản lý theo vòng đời tiến trình ứng dụng.
- Repository dùng `Dispatchers.IO` cho truy cập database và băm mật khẩu.
- Đăng ký ghi tài khoản và phiên trong cùng transaction; email trùng không ghi đè tài khoản.
- Khi thay schema phải thêm migration và tăng version; không xóa bảng tài khoản để nâng cấp.

### 16.3. Các file chính

| File | Vai trò |
|---|---|
| `app/src/main/java/com/example/bloomybeauty/data/auth/AuthContract.kt` | Tên database, bảng và cột |
| `app/src/main/java/com/example/bloomybeauty/data/auth/AuthDatabaseHelper.kt` | Tạo và quản lý SQLite |
| `app/src/main/java/com/example/bloomybeauty/data/auth/PasswordHasher.kt` | Tạo và xác minh hash mật khẩu |
| `app/src/main/java/com/example/bloomybeauty/data/auth/AuthModels.kt` | Người dùng, kết quả, lỗi và kiểm tra biểu mẫu |
| `app/src/main/java/com/example/bloomybeauty/data/auth/AuthRepository.kt` | Đăng ký, đăng nhập, khôi phục phiên và đăng xuất |
| `app/src/main/java/com/example/bloomybeauty/feature/auth/AuthViewModel.kt` | Trạng thái và thao tác của màn hình |
| `app/src/main/java/com/example/bloomybeauty/feature/auth/AuthScreen.kt` | Giao diện đăng ký/đăng nhập/tài khoản |
| `app/src/main/java/com/example/bloomybeauty/BloomyBeautyApplication.kt` | Khởi tạo Repository dùng chung |
| `app/src/main/java/com/example/bloomybeauty/MainActivity.kt` | Điểm vào luồng xác thực |

### 16.4. Cách chạy thử

1. Mở dự án bằng Android Studio, đồng bộ Gradle và chạy trên Android API 28 trở lên.
2. Ở màn hình đăng nhập, chọn **Chưa có tài khoản? Đăng ký**.
3. Nhập họ tên, email và mật khẩu hợp lệ; nhập lại mật khẩu rồi chọn **Đăng ký**.
4. Kiểm tra trang chủ xuất hiện; mở tab Tài khoản để xem tên/email.
5. Đóng và mở lại ứng dụng để kiểm tra giữ phiên.
6. Chọn **Đăng xuất**, thử đăng nhập với mật khẩu sai rồi mật khẩu đúng.
7. Thử đăng ký cùng email nhưng đổi chữ hoa/thường để kiểm tra chống trùng.

Không có tài khoản hay mật khẩu mặc định. Tạo tài khoản bằng biểu mẫu; tài khoản chỉ tồn tại trong dữ liệu của ứng dụng trên thiết bị đó. Xóa dữ liệu ứng dụng hoặc gỡ cài đặt sẽ xóa tài khoản cục bộ.

APK debug được tạo tại `app/build/outputs/apk/debug/app-debug.apk`.

### 16.5. Kiểm thử đã thực hiện

Lệnh kiểm tra:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Kết quả tại mốc triển khai đăng nhập ngày 07/10/2026 (kết quả mới nhất ở mục 17):

- Build debug thành công.
- 9 kiểm thử JVM đạt, gồm 8 kiểm thử mới cho kiểm tra đầu vào và băm mật khẩu, cùng 1 kiểm thử mẫu sẵn có.
- 10 kiểm thử trên máy ảo Pixel 6 / Android 16 đạt, gồm 8 kiểm thử Repository SQLite, 1 kiểm thử luồng giao diện và 1 kiểm thử mẫu sẵn có.
- Repository đã kiểm tra mở lại database, email trùng, mật khẩu sai, truy vấn có tham số, hash/salt, đổi tài khoản, đăng ký đồng thời và đăng ký không hợp lệ.
- Kiểm thử giao diện thực hiện đăng ký → xem tài khoản → đăng xuất → đăng nhập bằng dữ liệu SQLite thật.
- Android Lint: 0 lỗi, 37 cảnh báo; chủ yếu phiên bản thư viện/target SDK, tài nguyên mẫu chưa dùng và gợi ý dùng KTX cho transaction.

Báo cáo sinh ra tại `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/` và `app/build/reports/lint-results-debug.html`.

### 16.6. Các phần tiếp theo

1. Chốt kiến trúc dữ liệu cho các phase bán hàng: toàn bộ SQLite cục bộ hoặc backend có xác thực tương ứng.
2. Hoàn thiện tồn kho, trạng thái bán và luồng khách chưa đăng nhập theo yêu cầu; trang chủ sau đăng nhập đã có ở mục 17.
3. Bổ sung hồ sơ đầy đủ, địa chỉ và model/migration tương ứng.
4. Triển khai vai trò quản trị và kiểm tra quyền trên từng thao tác khi các chức năng quản trị xuất hiện.

Không đánh dấu hoàn thành các yêu cầu quyền truy cập giỏ, đơn hay quản trị trước khi những chức năng đó được triển khai và kiểm thử.

## 17. Trang chủ, dữ liệu thực tế và nhập tiếng Việt

### 17.1. Phạm vi triển khai ngày 07/10/2026

- Sau đăng nhập hoặc khôi phục phiên, mở trang chủ với lời chào theo tên tài khoản.
- Bốn tab: Trang chủ, Khám phá, Yêu thích và Tài khoản.
- Banner bộ sưu tập, lưới sản phẩm thích ứng kích thước màn hình, bốn danh mục da/cơ thể/tóc/môi.
- Tám sản phẩm Cocoon có ảnh, tên, dung tích, giá tham khảo, mô tả, thành phần nổi bật và hướng dẫn sử dụng.
- Tìm theo tên/thương hiệu/dung tích, không phân biệt dấu và hoa/thường. Ví dụ `gel bi dao` tìm thấy `Gel bí đao rửa mặt`.
- Lọc danh mục, sắp xếp mặc định/giá tăng/giá giảm, trạng thái đang tải, lỗi kèm thử lại và kết quả rỗng kèm xóa bộ lọc.
- Mở chi tiết, xem ảnh và thông tin, truy cập trang sản phẩm chính thức.
- Lưu/bỏ yêu thích theo từng tài khoản và giữ khi mở lại database.
- Ảnh đóng gói trong ứng dụng, không cần mạng để xem danh mục. Giá không đồng bộ trực tiếp; giao diện ghi rõ ngày tham khảo.

Nguồn và các đường dẫn ảnh được lưu ở [product-sources.md](product-sources.md). Tồn kho demo đã bổ sung ở mục 18; tồn kho này không lấy từ Cocoon. Giao dịch COD cục bộ sử dụng giá trong SQLite đã có ở mục 19; không tạo giao dịch với cửa hàng Cocoon.

### 17.2. SQLite và các file chính

Schema version 2 thêm `categories`, `products`, `favorites`. Bảng yêu thích có khóa chính ghép `(user_id, product_id)` và khóa ngoại tới tài khoản/sản phẩm. Migration 1 → 2 chỉ bổ sung bảng và dữ liệu danh mục; giữ nguyên tài khoản, hash mật khẩu và phiên đăng nhập.

| File/thư mục | Vai trò |
|---|---|
| `data/catalog/CatalogSeed.kt` | Bộ dữ liệu thực tế đã tóm tắt |
| `data/catalog/CatalogSchema.kt` | Schema và khởi tạo danh mục |
| `data/catalog/CatalogRepository.kt` | Đọc SQLite và cập nhật yêu thích bằng transaction |
| `data/catalog/CatalogModels.kt` | Model, quy tắc tìm/lọc/sắp xếp |
| `feature/home/HomeViewModel.kt` | Trạng thái và thao tác trang chủ |
| `feature/home/HomeScreen.kt` | Trang chủ, chi tiết, yêu thích, tài khoản |
| `feature/home/ProductPresentation.kt` | Ánh xạ ảnh và định dạng giá VNĐ |
| `res/drawable-nodpi/product_*.jpg` | 8 ảnh từ CDN chính thức |

### 17.3. Cập nhật nhập tiếng Việt

- Họ tên dùng `TextFieldState` để bộ gõ quản lý trực tiếp văn bản, vùng đang ghép dấu và con trỏ.
- Email, mật khẩu, xác nhận và tìm kiếm giữ `TextFieldValue` đầy đủ; không biến đổi văn bản đang nhập thành một chuỗi mới làm mất vùng composition.
- Chuẩn hóa email chỉ khi gửi biểu mẫu; mật khẩu giữ nguyên ký tự và dấu.
- Kiểm thử gửi chuỗi lệnh IME qua nhiều khung hình: `Ngu` → `Nguy` → `Nguyễn` → `Nguyễn Thị Mỹ`; sau đó chọn đoạn đầu và thay bằng `Trần`, kiểm tra kết quả `Trần Thị Mỹ`.
- Phép thử dùng IME riêng trong APK kiểm thử để tránh Gboard cùng gửi lệnh làm nhiễu. Bộ gõ này không nằm trong APK ứng dụng, và bộ gõ ban đầu được khôi phục sau phép thử.

Kiểm thử xác nhận khả năng giữ dấu và sửa tại vị trí chọn. Người dùng chưa cung cấp ô nhập, bộ gõ hoặc biểu hiện cụ thể của lỗi ban đầu; chưa kết luận được nguyên nhân riêng trên thiết bị của người dùng.

### 17.4. Cách nghiệm thu

1. Cài bản debug mới lên bản cũ để kiểm tra tài khoản và phiên vẫn còn.
2. Đăng ký bằng tên có dấu, sửa một phần tên, rồi kiểm tra tên ở trang chủ/Tài khoản.
3. Tìm `gel bi dao`, mở chi tiết Gel bí đao rửa mặt, kiểm tra ảnh và giá.
4. Lưu yêu thích, mở tab Yêu thích; đóng/mở lại ứng dụng để kiểm tra dữ liệu được giữ.
5. Đổi tài khoản để kiểm tra yêu thích riêng, không dùng chung giữa người dùng.
6. Lọc danh mục, sắp xếp giá, tìm một tên không tồn tại và xóa bộ lọc.
7. Mở liên kết sản phẩm chính thức; kiểm tra thông tin nguồn trong tài liệu.

Kết quả tại mốc trang chủ: build debug thành công, 13 kiểm thử JVM và 16 kiểm thử Android đạt trên Pixel 6 / Android 16. Android Lint có 0 lỗi và 44 cảnh báo, chủ yếu phiên bản thư viện/SDK, tài nguyên mẫu, icon launcher và gợi ý KTX. Kiểm thử mới bao phủ tìm kiếm có/không dấu, lọc/sắp xếp, ảnh đóng gói, yêu thích theo tài khoản, migration giữ tài khoản/phiên, luồng trang chủ → chi tiết → yêu thích → tài khoản và composition tiếng Việt. Báo cáo ở các đường dẫn Gradle ghi tại mục 16.5.

Ảnh nghiệm thu: [Trang chủ](screenshots/home-screen.png) và [Chi tiết sản phẩm](screenshots/product-detail.png). Giỏ hàng và giao hàng ở Phase 07 đã triển khai như mục 18.

## 18. Phase 07 — Giỏ hàng và thông tin giao hàng đã triển khai

### 18.1. Phạm vi và cách dùng

1. Đăng nhập, chọn một sản phẩm và bấm **Thêm vào giỏ** ở màn hình chi tiết.
2. Bấm biểu tượng giỏ ở đầu trang chủ hoặc màn hình chi tiết. Huy hiệu hiển thị tổng số lượng trong giỏ.
3. Tăng/giảm số lượng hoặc xóa mặt hàng; số lượng hợp lệ từ 1 đến 99 và không vượt tồn kho.
4. Kiểm tra tạm tính theo giá hiện tại. Nếu giá đổi từ lúc thêm, xem cảnh báo và bấm **Xác nhận giá mới cho giỏ hàng**.
5. Bấm **Tiếp tục giao hàng**. Repository đọc lại giá, trạng thái và tồn kho trong transaction trước khi chuyển màn hình.
6. Nhập người nhận, số di động và địa chỉ. Lần đầu điền sẵn tên tài khoản; lần sau dùng thông tin giao hàng đã lưu.
7. Bấm **Lưu và kiểm tra thông tin** để kiểm tra lại giỏ và lưu địa chỉ, sau đó xem thông tin đã lưu cùng các mặt hàng/tạm tính.
8. Quay lại để sửa: giữ nguyên giỏ và thông tin đang nhập. Đóng/mở ứng dụng: giỏ và địa chỉ đã lưu vẫn còn trong SQLite.

Tại mốc Phase 07, màn hình cuối mới lưu thông tin giao hàng. Phase 08 hiện đã nối xác nhận COD và tạo đơn ở mục 19. Tồn kho ban đầu là **30 đơn vị/sản phẩm trong dữ liệu demo**, không phản ánh tồn của Cocoon. Thêm vào giỏ không giữ chỗ hay trừ tồn; đặt thành công mới trừ tồn.

### 18.2. Quy tắc dữ liệu

- Schema version 3 bổ sung `products.stock`, `products.active`, `cart_items`, `shipping_details`.
- `cart_items` có khóa chính ghép `(user_id, product_id)`, lưu số lượng và giá đã chấp nhận. Thêm lại cộng số lượng trong transaction để tránh mất cập nhật đồng thời.
- Mọi truy vấn/ghi giỏ và địa chỉ đều có điều kiện `user_id`; xóa mặt hàng của tài khoản khác không ảnh hưởng giỏ hiện tại.
- Sản phẩm ngừng bán không xuất hiện trong danh mục. Mặt hàng đã có trong giỏ vẫn hiển thị cảnh báo để người dùng xóa.
- Hết hàng/vượt tồn chặn tiếp tục. Nếu tồn giảm nhưng vẫn còn hàng, có nút giảm số lượng về tồn hiện tại.
- Giá được đọc từ sản phẩm khi tải giỏ. Giá đã chấp nhận được giữ để phát hiện thay đổi; xác nhận chỉ chấp nhận các giá vừa hiển thị. Nếu giá đổi lần nữa trong lúc xác nhận, trả lỗi và hiển thị lại.
- Tiền VND dùng `Long`, nhân/cộng có kiểm tra tràn; tổng vượt giới hạn bị chặn thay vì thành số âm.
- Địa chỉ đã lưu là thông tin mặc định dùng lại cho tài khoản; bản này chưa có danh sách nhiều địa chỉ.
- Người nhận 2–80 ký tự, địa chỉ 10–300 ký tự. Số điện thoại nhận dạng `0[35789]` và tám chữ số tiếp theo; dạng +84/84 được chuyển về đầu 0 khi lưu. Đây là quy tắc demo, không xác minh thuê bao thực.
- Các ô nhập giữ `TextFieldValue` gồm vùng ghép dấu và con trỏ; bỏ khoảng trắng hai đầu/chuẩn hóa số điện thoại khi kiểm tra hoặc lưu.
- Lưu địa chỉ và kiểm tra giỏ trong cùng transaction; lỗi giá/tồn/trạng thái không ghi đè địa chỉ đã lưu.
- Migration 1 → 3 và 2 → 3 giữ tài khoản, mật khẩu, phiên, sản phẩm đã sửa và yêu thích. Không xóa database để nâng cấp.

### 18.3. Các file chính

| File | Vai trò |
|---|---|
| `data/cart/CartSchema.kt` | Migration bổ sung tồn kho, trạng thái bán, giỏ và địa chỉ |
| `data/cart/CartModels.kt` | Tổng tiền, khả năng tiếp tục, kiểm tra địa chỉ/số điện thoại |
| `data/cart/CartRepository.kt` | Thao tác SQLite theo tài khoản và transaction kiểm tra giỏ |
| `feature/cart/CartViewModel.kt` | Trạng thái giỏ, bản nháp giao hàng, điều hướng ba bước và lỗi |
| `feature/cart/CartScreen.kt` | Giỏ hàng, biểu mẫu giao hàng và kiểm tra thông tin đã lưu |
| `MainActivity.kt`, `feature/home/HomeScreen.kt` | Nối từ trang chủ/chi tiết vào giỏ |

### 18.4. Bằng chứng kiểm thử

- Lần chạy đầy đủ: **18 kiểm thử JVM + 25 kiểm thử Android đạt** trên Pixel 6 / Android 16; build debug thành công.
- Sau khi bổ sung kiểm tra giá đổi lần nữa lúc xác nhận, chạy lại 9 kiểm thử Repository/giao diện giỏ: tất cả đạt.
- Bao phủ tiền nguyên VND và tràn số; kiểm tra thông tin giao hàng; cộng số lượng đồng thời; giữ giỏ/địa chỉ khi mở lại database; phân tách tài khoản; sản phẩm ẩn/hết hàng; số lượng sai; giá thay đổi và xác nhận giá; thay đổi tồn/trạng thái trước khi lưu; migration 2 → 3 giữ tài khoản/phiên/yêu thích/giá đã sửa.
- Luồng giao diện: chi tiết → thêm hai lần → tăng/giảm → giỏ → nhập số điện thoại sai → sửa → lưu giao hàng → quay lại giữ thông tin. Kiểm tra giỏ rỗng và cảnh báo giá chặn nút tiếp tục.
- Ảnh nghiệm thu: [Giỏ hàng](screenshots/cart-screen.png) và [Thông tin giao hàng đã lưu](screenshots/shipping-review.png).

Lệnh chạy đầy đủ:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Báo cáo Gradle ở các đường dẫn mục 16.5; lần chạy có lọc lớp sẽ chỉ hiển thị các lớp được chạy ở lần đó.

### 18.5. Các yêu cầu chuyển sang Phase 08 (đã triển khai bản SQLite ở mục 19)

- Chọn nguồn xử lý đơn phù hợp: transaction SQLite cho bản demo một thiết bị hoặc backend có xác thực cho hệ thống nhiều thiết bị. Các phần Firebase trong kế hoạch vẫn là đề xuất, chưa được nối với phiên SQLite.
- Xây dựng xác nhận COD, quy tắc phí giao hàng, mã yêu cầu chống trùng, bảng đơn/chi tiết đơn và lưu giá/tên tại thời điểm mua.
- Tạo đơn và trừ tồn cùng transaction; kiểm tra lại giá/tồn/trạng thái, bảo đảm tồn không âm.
- Dọn giỏ an toàn sau khi đơn đã lưu; kiểm thử gửi lặp, xung đột giá/tồn và hai tài khoản mua đơn vị cuối.
- Bổ sung lịch sử và chi tiết đơn trước khi chuyển sang xử lý trạng thái/hủy ở Phase 09.

## 19. Phase 08 — Đặt hàng COD cục bộ và xem đơn

### 19.1. Luồng đã triển khai

1. Chọn sản phẩm → Giỏ hàng → Thông tin giao hàng → **Lưu và kiểm tra thông tin**.
2. Xem mặt hàng, người nhận, tạm tính, phí giao hàng và tổng COD. Phí demo **30.000 ₫**, miễn phí khi tạm tính **từ 500.000 ₫**.
3. Bấm **Xác nhận đặt hàng COD**. Khi đang xử lý, nút gửi và quay lại bị khóa.
4. Thành công hiển thị mã dạng `BB-000001`, trạng thái **Chờ xác nhận**, địa chỉ, mặt hàng và tổng tiền.
5. Bấm **Xem đơn hàng của tôi** để mở danh sách. Cũng có thể mở từ tab **Tài khoản → Đơn hàng của tôi**.
6. Chọn mã đơn để xem chi tiết; dùng **Tải lại** để đọc dữ liệu mới.

COD, phí giao hàng và kho đều là mô phỏng đồ án trên thiết bị. Chưa có vận chuyển/thu tiền thật, backend dùng chung hoặc thao tác quản trị xác nhận/giao đơn. Đơn mới luôn là Chờ xác nhận; khách hiện có thể hủy đơn hợp lệ như mục 20.

### 19.2. Transaction, kiểm tra và chống trùng

- `checkout_requests` lưu UUID, tài khoản, payload sản phẩm/số lượng/giá/địa chỉ và fingerprint SHA-256 trước khi bấm xác nhận. Mỗi tài khoản có một yêu cầu đang theo dõi.
- Nội dung không đổi thì giữ mã; sửa giỏ hoặc địa chỉ rồi xác nhận lại thì tạo mã mới. Fingerprint dựa trên nội dung, không dựa trên thứ tự mặt hàng.
- `create` kiểm tra `auth_session.user_id` khớp tài khoản đang yêu cầu; không cho phiên khác tạo đơn của tài khoản cũ.
- Trong transaction, truy vấn đơn theo requestId trước. Nếu đã có, kiểm tra tài khoản/fingerprint rồi trả lại đơn đó, không kiểm tra giỏ đã bị dọn và không trừ tồn/dọn giỏ lần nữa.
- Nếu chưa có đơn, yêu cầu phải khớp bản đã lưu. Đọc lại toàn bộ giỏ, giá, số lượng, trạng thái bán, tồn và địa chỉ từ SQLite; khác màn hình đã xác nhận thì báo lỗi và yêu cầu kiểm tra lại.
- Tính tiền bằng số nguyên VND, có kiểm tra tràn số; phí được tính từ quy tắc ứng dụng, không nhận tổng tiền tùy ý từ giao diện.
- Ghi `orders`, `order_items`, giảm tồn có điều kiện và dọn giỏ trong cùng transaction. Lỗi ở bất kỳ bước nào rollback toàn bộ, giữ giỏ và không trừ tồn một phần.
- Chỉ dọn giỏ khi nội dung hiện tại khớp yêu cầu. Thêm mới sau commit chạy sau transaction và được giữ. Gửi lại mã đã thành công chỉ trả kết quả đã có.
- UNIQUE `orders.request_id` cùng transaction chống trùng cả khi gọi đồng thời; khóa nút là hỗ trợ giao diện.
- Yêu cầu thành công vẫn được giữ đến khi người dùng rời màn hình kết quả. Mở lại ứng dụng rồi mở giỏ sẽ tra lại đơn đã commit hoặc khôi phục bước xác nhận còn hợp lệ.
- Không có kết quả chắc chắn do lỗi SQLite: giữ mã để thử lại; không hiển thị thành công trước khi xác nhận commit.

### 19.3. Lưu trữ và lịch sử

Schema version **4** thêm `checkout_requests`, `orders`, `order_items` và index theo tài khoản/thời gian. Migration 1/2/3 → 4 giữ dữ liệu xác thực, danh mục, yêu thích, giỏ và địa chỉ.

Đơn lưu bản chụp tên/dung tích/ảnh/giá/số lượng và địa chỉ giao hàng, nên sửa tên/giá sản phẩm hoặc địa chỉ mặc định sau đó không thay lịch sử. Danh sách sắp xếp thời gian mới nhất, dùng ID để ổn định thứ tự; truy vấn chi tiết có cả `user_id` và `order_id`.

| File | Vai trò |
|---|---|
| `data/order/OrderModels.kt` | Yêu cầu, fingerprint, quy tắc phí, đơn và lỗi |
| `data/order/OrderSchema.kt` | Schema yêu cầu/đơn/chi tiết và index |
| `data/order/OrderRepository.kt` | Chuẩn bị/khôi phục yêu cầu, transaction tạo đơn, xem lịch sử theo tài khoản |
| `feature/cart/CartViewModel.kt`, `CartScreen.kt` | Xác nhận COD, gửi lại, khôi phục và thành công |
| `feature/order/OrdersScreen.kt` | ViewModel danh sách, màn hình lịch sử và chi tiết |

### 19.4. Nghiệm thu và kiểm thử

- Thử một Gel bí đao: tạm tính 192.000 ₫, phí 30.000 ₫, tổng 222.000 ₫; sau đặt, tồn giảm từ 30 xuống 29 và giỏ rỗng.
- Thử tổng từ 500.000 ₫ để kiểm tra miễn phí giao hàng.
- Gửi cùng yêu cầu 8 lần đồng thời: một đơn, trừ tồn một lần.
- Thêm mặt hàng mới sau commit rồi gửi lại mã cũ: giữ mặt hàng mới trong giỏ.
- Giả lập lỗi ghi ở mặt hàng thứ hai sau khi đã giảm tồn mặt hàng đầu: không lưu đơn và rollback tồn/giỏ; gửi lại cùng mã sau khi bỏ lỗi thành công.
- Kiểm tra thay đổi giá, số lượng, địa chỉ hoặc trạng thái bán trước khi gửi; không tạo đơn theo nội dung chưa được xác nhận.
- Đóng/mở database trước gửi và sau commit: khôi phục đúng requestId và tra lại đúng đơn.
- Hai tài khoản chuẩn bị khi còn một đơn vị rồi xác nhận lần lượt: đơn thứ hai bị chặn thiếu tồn. Bản cục bộ chỉ có một phiên hoạt động; đây không phải kiểm thử nhiều thiết bị có backend chung.
- Tài khoản khác không đọc được chi tiết đơn; không được dùng requestId của đơn đã thuộc tài khoản khác.
- Migration từ version 3 giữ phiên, giỏ, yêu thích, địa chỉ rồi vẫn đặt được đơn.
- Giao diện kiểm tra xác nhận → thành công → danh sách → chi tiết; giá đổi ngay lúc gửi trả về giỏ, chặn tiếp tục đến khi xác nhận giá mới.

Kết quả ngày 07/10/2026: **21 kiểm thử JVM + 36 kiểm thử Android đạt**, build debug thành công. Lint: **0 lỗi, 44 cảnh báo** chủ yếu phiên bản thư viện/SDK, tài nguyên mẫu, icon launcher và gợi ý KTX. Kiểm thử xác thực cũ được bổ sung cuộn đến nút Đăng ký/Đăng nhập trước khi bấm để chạy ổn định khi bàn phím che bớt vùng hiển thị.

Ảnh nghiệm thu: [Xác nhận COD](screenshots/cod-confirmation.png), [Đặt hàng thành công](screenshots/order-success.png), [Chi tiết đơn](screenshots/order-detail.png). APK debug giữ đường dẫn ở mục 16.4; báo cáo Gradle ở mục 16.5.

### 19.5. Bước tiếp theo

Phase 09 đã bổ sung hồ sơ/địa chỉ mặc định và hủy đơn Chờ xác nhận ở mục 20. Tiếp theo triển khai vai trò quản trị và chuyển trạng thái đơn ở Phase 10.

## 20. Phase 09 — Hồ sơ, địa chỉ mặc định và hủy đơn

### 20.1. Hồ sơ và địa chỉ mặc định

- Vào **Tài khoản → Chỉnh sửa hồ sơ** để sửa họ tên. Email hiển thị để nhận diện tài khoản, không chỉnh email/mật khẩu trong màn hình này.
- Bật **Lưu địa chỉ mặc định** để nhập người nhận, số di động và địa chỉ. Người nhận có thể khác tên tài khoản.
- Tắt tùy chọn rồi lưu sẽ xóa địa chỉ mặc định của tài khoản; khi đặt hàng vẫn có thể nhập địa chỉ mới.
- Kiểm tra họ tên/người nhận 2–80 ký tự, địa chỉ 10–300 ký tự và số di động theo quy tắc ở mục 18.2. Chuẩn hóa khi lưu; không sửa chuỗi đang ghép dấu lúc nhập.
- Lưu tên và địa chỉ cùng transaction. Lỗi ghi địa chỉ không làm tên bị đổi một phần.
- Cập nhật tên trên trang chủ/Tài khoản ngay sau lưu; giỏ đọc lại địa chỉ mặc định mới cho lần giao hàng tiếp theo.
- Tên và địa chỉ còn sau khi mở lại database hoặc đăng nhập lại; mật khẩu và email giữ nguyên.
- Đơn đã đặt giữ nguyên bản chụp người nhận/địa chỉ/giá/tên sản phẩm. Đổi địa chỉ mặc định khi còn yêu cầu COD chưa gửi làm yêu cầu cũ không còn khớp và phải xác nhận lại.

### 20.2. Hủy đơn và hoàn tồn

1. Vào **Tài khoản → Đơn hàng của tôi**, mở một đơn **Chờ xác nhận**.
2. Bấm **Hủy đơn**. Hộp thoại có **Giữ đơn** và **Xác nhận hủy**.
3. Sau khi xác nhận, khóa gửi lại/quay lại trong lúc xử lý. Thành công cập nhật **Đã hủy**, ẩn nút hủy và giữ đơn trong lịch sử.
4. Đơn Đã xác nhận/Đang giao/Đã giao không cho khách hủy. Nếu trạng thái đổi khi hộp thoại đang mở, Repository từ chối và giao diện tải lại trạng thái.

Quy tắc transaction:

- Kiểm tra phiên hiện tại và truy vấn bằng cả `user_id`, `order_id`; không hủy đơn của tài khoản khác.
- Đã hủy trả lại kết quả đã có, không hoàn tồn lần nữa; mã đơn đóng vai trò định danh yêu cầu hủy.
- Chỉ chuyển `PENDING → CANCELLED`, cập nhật với điều kiện trạng thái còn `PENDING`.
- Hoàn số lượng của từng dòng đơn vào sản phẩm trong cùng transaction với thay đổi trạng thái. Lỗi ở một dòng rollback toàn bộ.
- Sản phẩm đã bị ẩn vẫn được hoàn tồn nhưng không tự chuyển thành đang bán.
- Kiểm tra tràn số nguyên khi cộng tồn; vượt giới hạn thì giữ nguyên trạng thái/tồn và báo lỗi.
- Gửi lặp, gọi đồng thời hoặc mở lại database sau hủy đều không hoàn tồn hai lần.
- Nếu xác nhận và hủy cùng cạnh tranh với điều kiện `PENDING`, khóa transaction và điều kiện trạng thái bảo đảm chỉ một thao tác thắng. Thao tác xác nhận hiện được mô phỏng bằng SQL trong kiểm thử; màn hình quản trị sẽ có ở Phase 10.
- Hủy không tạo lại giỏ và không thay mặt hàng mới đã thêm. Gửi lại mã tạo đơn đã hủy trả lại đơn Đã hủy, không tạo đơn mới.
- Khôi phục giỏ bỏ yêu cầu COD đã gắn với đơn Đã hủy, tránh hiển thị lại kết quả đặt thành công cũ.

### 20.3. Lưu trữ và các file chính

Giữ schema version **4**: tên nằm trong `users`, địa chỉ mặc định trong `shipping_details`, trạng thái `CANCELLED` đã có trong `orders`. Không cần migration hoặc xóa dữ liệu.

| File | Vai trò |
|---|---|
| `data/profile/ProfileRepository.kt` | Đọc/lưu hồ sơ, kiểm tra phiên/đầu vào và transaction tên/địa chỉ |
| `feature/profile/ProfileScreen.kt` | ViewModel, biểu mẫu giữ vùng ghép dấu/con trỏ, lỗi và trạng thái lưu |
| `data/order/OrderRepository.kt` | Hủy đúng chủ sở hữu/trạng thái, hoàn tồn và chống hoàn lặp |
| `feature/order/OrdersScreen.kt` | Hộp thoại hủy, cập nhật trạng thái và lỗi, khóa thao tác khi xử lý |
| `feature/auth/AuthViewModel.kt` | Cập nhật tên tài khoản sau khi lưu hồ sơ |
| `feature/cart/CartViewModel.kt` | Làm mới địa chỉ mặc định và xử lý yêu cầu đã hủy khi khôi phục |

### 20.4. Kiểm thử và nghiệm thu

- Hồ sơ: tên/địa chỉ/số +84 được lưu và chuẩn hóa, mở lại database còn dữ liệu, đăng nhập bằng mật khẩu cũ vẫn được.
- Đầu vào sai không đổi dữ liệu; giả lập lỗi ghi địa chỉ sau bước đổi tên để kiểm tra rollback tên.
- Phiên khác không đọc/ghi hồ sơ cũ; sửa/xóa địa chỉ mặc định không làm đổi đơn đã đặt.
- Hủy đồng thời 8 lần: trạng thái Đã hủy, tồn trở lại đúng một lần. Mở lại database và gửi lại vẫn giữ kết quả đó.
- Đơn đã xử lý, đơn không tồn tại, sai chủ sở hữu hoặc phiên khác đều bị chặn.
- Giả lập lỗi hoàn tồn ở dòng thứ hai: trạng thái vẫn Chờ xác nhận và mọi tồn giữ nguyên; bỏ lỗi rồi thử lại hoàn tồn thành công.
- Tràn tồn bị chặn; sản phẩm ẩn được hoàn tồn nhưng vẫn ẩn; mặt hàng mới trong giỏ không bị thay đổi.
- Xác nhận có điều kiện chạy đồng thời với hủy: kết quả chỉ Đã xác nhận/tồn đã trừ hoặc Đã hủy/tồn đã hoàn.
- Giao diện: sửa tên → bật địa chỉ → nhập điện thoại sai → sửa và lưu → mở lại hồ sơ → dùng địa chỉ ở giao hàng. Hủy: giữ đơn ở hộp thoại → xác nhận hủy → xem trạng thái mới → mở giỏ không khôi phục đơn đã hủy.
- Giao diện từ chối hủy khi trạng thái đổi sang Đã xác nhận lúc hộp thoại đang mở.

Kết quả ngày 07/10/2026: **21 kiểm thử JVM + 51 kiểm thử Android đạt** trên Pixel 6 / Android 16; build debug thành công. Lint: **0 lỗi, 44 cảnh báo** chủ yếu phiên bản thư viện/SDK, tài nguyên mẫu, icon launcher và gợi ý KTX.

Ảnh nghiệm thu: [Chỉnh sửa hồ sơ](screenshots/profile-screen.png) và [Đơn đã hủy](screenshots/cancelled-order.png). APK và báo cáo Gradle theo đường dẫn mục 16.4–16.5.

### 20.5. Bước tiếp theo — Phase 10

Triển khai tài khoản/quyền quản trị, kiểm tra quyền tại Repository, quản lý sản phẩm/giá/tồn/trạng thái bán, và luồng xác nhận → giao → hoàn tất đơn. Quyền quản trị phải cấp từ nguồn tin cậy, không từ biểu mẫu đăng ký của khách. Mọi chuyển trạng thái cần điều kiện trạng thái hiện tại để giữ đúng quy tắc xung đột đã kiểm tra ở Phase 09.

## 21. Phase 10 — Quản trị sản phẩm, tồn kho và đơn hàng

### 21.1. Quyền quản trị và tài khoản demo

- `users.role` chỉ nhận `CUSTOMER` hoặc `ADMIN`; đăng ký luôn dùng mặc định `CUSTOMER`, không có tham số vai trò trong API đăng ký.
- Lối vào **Tài khoản → Quản trị cửa hàng** chỉ xuất hiện với tài khoản quản trị. Repository kiểm tra lại vai trò của người dùng và phiên `auth_session` trong transaction cho **mọi** lần đọc/lưu sản phẩm và cập nhật đơn; không tin vai trò từ giao diện.
- Đăng nhập, khôi phục phiên và lưu hồ sơ giữ đúng vai trò. Thu hồi quyền chặn cả yêu cầu trực tiếp, xóa dữ liệu quản trị đang hiển thị và thông báo cần đăng nhập lại.
- Tài khoản demo chỉ tự tạo trong build **debug** và database chính, từ cấu hình cục bộ tin cậy. Không tạo tài khoản trong các database kiểm thử. Không nâng quyền cho tài khoản khách đã có cùng email và không đặt lại mật khẩu của admin đã tồn tại.
- Email/mật khẩu demo được lưu tại [cấu hình cục bộ](../.local/admin-demo.properties). File có quyền `0600`, được bỏ qua bởi `.gitignore`; không đưa vào mã nguồn hoặc ảnh báo cáo. Chỉ email, hash PBKDF2-HMAC-SHA256 (210.000 vòng) và salt được đưa vào `BuildConfig` debug, không đưa mật khẩu thuần vào APK.
- Build release để trống cấu hình này và không tự tạo admin. Đây là cơ chế demo trên một thiết bị; bản nhiều thiết bị cần nguồn cấp quyền ở backend.

Để chuẩn bị trên máy mới:

```sh
python3 scripts/create-demo-admin.py
./gradlew :app:assembleDebug --offline
```

Script tạo mật khẩu ngẫu nhiên và không ghi đè cấu hình đã tồn tại. Mở file `.local/admin-demo.properties` để lấy email/mật khẩu, đăng nhập rồi vào Quản trị cửa hàng. Nếu đã có khách trùng email demo, dùng email khác chưa tồn tại trong cấu hình và build lại; không sửa vai trò từ biểu mẫu khách. Thay cấu hình không đổi tài khoản đã lưu trong database.

### 21.2. Quản lý sản phẩm

- Danh sách gồm cả sản phẩm đang bán và đã ẩn; tìm kiếm tên/thương hiệu hỗ trợ tiếng Việt có dấu hoặc không dấu. Hiển thị ảnh, giá VND, tồn kho và trạng thái bán.
- Thêm/sửa tên, thương hiệu, danh mục, dung tích, giá, tồn, mô tả, thành phần, cách dùng, nguồn tham khảo và trạng thái bán. Chọn một trong tám ảnh sản phẩm đã tải ở Phase 06; ảnh tải lên/URL ảnh mới thuộc phần mở rộng.
- Tên 2–160 ký tự, thương hiệu/dung tích tối đa 80 ký tự và không trống; danh mục phải tồn tại. Giá nguyên dương từ 1 đến 1.000.000.000 VND; tồn là số nguyên không âm trong giới hạn `Int`. Mô tả/thành phần/cách dùng 1–4.000 ký tự. Nguồn tùy chọn, nếu nhập phải là HTTPS có tên miền hợp lệ.
- Biểu mẫu giữ `TextFieldValue`, không chuẩn hóa trong lúc ghép dấu. Chỉ trim khi lưu. Không định dạng giá khi người dùng đang nhập.
- Ẩn/mở bán qua công tắc và lưu; không xóa sản phẩm hoặc thay đổi bản chụp tên/giá/ảnh trong đơn đã đặt.
- Mỗi sản phẩm có `revision`. Lưu sửa phải khớp phiên bản lúc mở biểu mẫu; mỗi lần sửa, trừ tồn khi đặt và hoàn tồn khi hủy đều tăng phiên bản. Bản sửa cũ bị từ chối thay vì ghi đè thay đổi mới.
- **Tải lại** đọc dữ liệu hiện tại nhưng giữ nội dung và phiên bản của biểu mẫu đang mở. Khi báo xung đột, quay lại danh sách và mở lại sản phẩm để chỉnh trên bản mới. Phiên bản được giữ cả khi tái tạo giao diện.

### 21.3. Quản lý đơn và lịch sử xử lý

- Xem tất cả đơn, lọc theo trạng thái, mở chi tiết mã đơn/người nhận/địa chỉ/sản phẩm/giá lúc đặt/phí/tổng tiền COD.
- Xác nhận qua hộp thoại trước mỗi cập nhật; khóa thao tác gửi/quay lại trong lúc xử lý.
- `PENDING → CONFIRMED → SHIPPING → DELIVERED`; quản trị viên có thể chuyển `PENDING` hoặc `CONFIRMED` sang `CANCELLED`. Đơn đang giao không hủy; Đã giao/Đã hủy là trạng thái kết thúc.
- Kiểm tra trạng thái được hiển thị với trạng thái hiện tại trong transaction. Bản cũ không thể bỏ bước hoặc đảo trạng thái. Gửi lặp cùng bước đã thực hiện trả về kết quả hiện tại, không ghi lịch sử hay hoàn tồn lần nữa.
- Hủy quản trị hoàn tồn và tăng phiên bản sản phẩm cùng transaction với trạng thái và lịch sử; tràn tồn/lỗi SQLite rollback toàn bộ. Xác nhận/giao/hoàn tất không trừ tồn thêm.
- `order_events` lưu người thực hiện, tên tại thời điểm thao tác, trạng thái trước/sau và thời gian. Tạo đơn và khách hủy từ Phase 09 cũng ghi lịch sử kể từ schema 5. Đơn cũ không được dựng lịch sử giả cho thời gian trước migration.

### 21.4. Lưu trữ và các file chính

Migration **4 → 5** thêm `users.role`, `products.revision`, bảng `order_events` và chỉ mục theo đơn/thời gian. Giữ tài khoản, phiên, danh mục, giỏ, địa chỉ, yêu thích, đơn và các dòng đơn. Các migration từ version 1–3 tiếp tục chạy tuần tự đến version 5, không xóa dữ liệu.

| File | Vai trò |
|---|---|
| `data/admin/AdminSchema.kt` | Migration, lịch sử xử lý, tạo admin debug từ cấu hình tin cậy |
| `data/admin/AdminRepository.kt` | Phân quyền, validation, chống ghi đè sản phẩm, chuyển trạng thái/hoàn tồn |
| `feature/admin/AdminScreen.kt` | ViewModel, danh sách/biểu mẫu sản phẩm, lọc/chi tiết đơn, xác nhận và lỗi |
| `data/auth/AuthModels.kt`, `AuthRepository.kt` | Vai trò trong đăng nhập/khôi phục phiên |
| `data/profile/ProfileRepository.kt` | Giữ vai trò khi lưu hồ sơ |
| `data/order/OrderRepository.kt` | Tăng phiên bản khi thay đổi tồn; ghi lịch sử tạo/hủy bởi khách |
| `scripts/create-demo-admin.py`, `app/build.gradle.kts` | Tạo cấu hình cục bộ và chỉ đưa hash/salt vào build debug |

### 21.5. Kiểm thử và trạng thái nghiệm thu

Ngày 07/10/2026, đã biên dịch toàn bộ mã Kotlin ứng dụng, mã kiểm thử JVM và mã kiểm thử Android bằng Kotlin 2.0.21/Compose compiler từ cache thư viện hiện có. **24 kiểm thử JVM đạt**, gồm 21 kiểm thử trước đó và 3 kiểm thử quy tắc quản trị mới. Kiểm tra trực tiếp bốn câu SQL migration trên SQLite máy phát triển đạt: thêm cột/bảng/chỉ mục, mặc định quyền khách và phiên bản sản phẩm 0, giữ các dòng mẫu và kiểm tra khóa ngoại. Kiểm tra này không thay thế migration trên Android.

Đã bổ sung **12 kiểm thử Repository Android** cho vai trò đăng ký/đăng nhập/khôi phục/lưu hồ sơ, sai phiên, thu hồi quyền, thêm/sửa/ẩn/mở sản phẩm, dữ liệu sản phẩm cũ sau đặt/hủy, validation, vòng đời đơn/lịch sử, hủy đồng thời, trạng thái cũ, rollback khi lỗi ghi lịch sử, tràn tồn và migration 4 → 5. Thêm **3 kiểm thử giao diện Android** cho sửa/ẩn sản phẩm, xác nhận/chuyển/lọc đơn và thu hồi quyền. Các kiểm thử này **đã biên dịch nhưng chưa chạy ở lượt Phase 10 ban đầu**. Trong Phase 11, 15 kiểm thử Android bổ sung đã chạy đạt cùng bộ kiểm thử cũ; xem mục 22.

**Giới hạn ở lượt triển khai Phase 10 ban đầu (đã xử lý trong Phase 11):** Gradle bị sandbox từ chối quyền ghi cache wrapper; thử với cache tạm vẫn bị từ chối mở socket daemon (`Operation not permitted`). Yêu cầu nâng quyền cũng bị chính sách môi trường tự động từ chối. Ở lượt Phase 10 ban đầu chưa chạy được Gradle assemble/lint hoặc kiểm thử Android, APK lúc đó thuộc Phase 09. Trong lượt Phase 11, lệnh Gradle đầy đủ đã chạy được: APK hiện tại chứa Phase 10 và Phase 11, đã có ảnh nghiệm thu quản trị tại mục 22.

Lệnh kiểm tra cần chạy khi môi trường cho phép:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline
```

### 21.6. Bước tiếp theo

Phase 10 đã được build và kiểm thử Android trong Phase 11 ở mục 22. Phần backend dùng chung tiếp tục là phạm vi mở rộng; bản cục bộ nghiệm thu theo các phiên lần lượt trên một thiết bị.

## 22. Phase 11 — Tích hợp luồng khách và quản trị

### 22.1. Điều hướng và vòng đời phiên

- Tách `feature/app/BloomyApp.kt` làm màn hình gốc dùng chung cho `MainActivity` và kiểm thử tích hợp; kiểm thử dùng đúng cách nối các màn hình của ứng dụng, không dựng lại một luồng điều hướng riêng.
- Thay các cờ mở màn hình bằng một màn hình hiện tại (`HOME`, `CART`, `ORDERS`, `PROFILE`, `ADMIN`). Không có nhiều màn hình phụ cùng được đánh dấu mở.
- Dùng `SaveableStateHolder` giữ trạng thái từng màn hình: vào giỏ từ chi tiết sản phẩm rồi quay lại vẫn ở đúng chi tiết; vào hồ sơ từ Tài khoản rồi quay lại vẫn ở Tài khoản.
- ViewModel của danh mục, giỏ, đơn, hồ sơ và quản trị nằm trong `ViewModelStore` của phiên đăng nhập do `AuthViewModel` sở hữu. Store còn qua thay đổi cấu hình, được xóa sau đăng xuất thành công và khi ViewModel xác thực bị hủy. Đăng xuất thất bại vẫn giữ phiên và dữ liệu đang dùng.
- Đăng nhập lại cùng tài khoản tạo ViewModel mới, đọc lại database; không dùng giỏ/giá/trạng thái đơn còn nằm trong bộ nhớ từ lần đăng nhập trước. Tài khoản quản trị không thấy giỏ của khách vừa đăng xuất.
- Khi ứng dụng trở lại foreground, tải lại danh mục và giỏ; tải lại đơn/quản trị nếu đang ở màn hình đó. Không tải đè hồ sơ đang nhập. Đổi giá/tồn/ẩn sản phẩm được cập nhật khi quay lại ứng dụng.
- Tải lại danh mục hủy lần đọc cũ để kết quả đến muộn không ghi đè lần đọc mới. Giỏ ghi nhận yêu cầu tải lại khi đang bận và thực hiện sau khi thao tác hiện tại kết thúc.

### 22.2. Giỏ thay đổi trong lúc kiểm tra đơn

- Nếu đang ở **Kiểm tra thông tin**, tải lại sẽ đối chiếu yêu cầu COD đã lưu với giỏ/địa chỉ hiện tại.
- Giá đổi: trở về giỏ, hiển thị giá cũ/mới và chặn tiếp tục đến khi khách xác nhận giá mới.
- Sản phẩm bị ẩn: trở về giỏ, yêu cầu xóa sản phẩm.
- Tồn giảm dưới số lượng: trở về giỏ, có nút giảm về tồn hiện tại.
- Giỏ/địa chỉ không còn khớp yêu cầu: bỏ yêu cầu khỏi trạng thái giao diện và yêu cầu kiểm tra lại. Repository vẫn kiểm tra lại trong transaction khi đặt; tải lại không tự tạo đơn.
- Từ bước kiểm tra quay lại giao hàng rồi nhập tiếp: tải lại hoặc resume giữ bước giao hàng và bản nháp, không tự nhảy sang yêu cầu cũ.
- Đơn đã commit khôi phục về kết quả đã lưu; không trừ tồn lần nữa. Đơn đã hủy bỏ kết quả đặt thành công cũ.

### 22.3. Giao diện và nhất quán dữ liệu

- Nhãn trạng thái tập trung trong `OrderPresentation.kt`, dùng cho cả khách và quản trị: Chờ xác nhận / Đã xác nhận / Đang giao / Đã giao / Đã hủy.
- Giá tiếp tục dùng chung `formatPrice`, hiển thị số nguyên VND theo định dạng Việt Nam.
- Biểu mẫu quản trị dùng bàn phím số cho giá/tồn và bàn phím URI cho nguồn tham khảo; chuỗi tiếng Việt và vùng ghép dấu giữ nguyên khi nhập.
- Danh sách quản trị hiển thị thông báo khi tìm kiếm không có sản phẩm. Tên trong thẻ có chiều rộng giới hạn để nội dung xuống dòng thay vì đẩy ảnh/giá khỏi khung.
- Các danh sách/biểu mẫu/chi tiết dùng khung cuộn; ảnh thật đóng gói sẵn, không cần mạng để xem danh mục.

### 22.4. Danh sách lỗi tích hợp

| Mã | Vấn đề | Xử lý | Người phụ trách | Trạng thái |
|---|---|---|---|---|
| BB-INT-01 | Nâng database cũ lên version 5 gọi migration quản trị hai lần, lỗi trùng cột `role` | Bỏ lời gọi thừa; chạy migration từ version 1, 2, 3, 4 trên Android | Codex | Đã sửa, kiểm thử đạt |
| BB-INT-02 | Đăng nhập lại cùng tài khoản có thể dùng ViewModel giỏ/danh mục cũ của Activity | Store riêng cho phiên; xóa sau đăng xuất thành công | Codex | Đã sửa, kiểm thử tích hợp đạt |
| BB-INT-03 | Rời trang chi tiết/Tài khoản rồi quay lại làm mất vị trí điều hướng | Một màn hình hiện tại và giữ trạng thái từng màn hình | Codex | Đã sửa, kiểm thử tích hợp đạt |
| BB-INT-04 | Tải lại khi đang xác nhận có thể giữ bước cũ dù giá/tồn/trạng thái bán thay đổi | Đối chiếu lại và đưa về giỏ để sửa/xác nhận | Codex | Đã sửa, kiểm thử giao diện đạt |
| BB-INT-05 | Resume ở bước giao hàng có thể tự chuyển lại sang yêu cầu xác nhận cũ | Chỉ tự khôi phục bước kiểm tra từ bước giỏ; giữ giao hàng/bản nháp | Codex | Đã sửa, kiểm thử giao diện đạt |

### 22.5. Kiểm thử và nghiệm thu

Các ca bổ sung của Phase 11:

1. Khách đặt COD qua giao diện → xem đơn Chờ xác nhận → đăng xuất → admin đăng nhập và xử lý đến Đã giao → khách đăng nhập lại thấy Đã giao, không còn nút hủy; tồn chỉ giảm một lần.
2. Quay lại từ giỏ giữ trang chi tiết; quay lại từ hồ sơ giữ Tài khoản. Đổi sang admin có giỏ riêng; quay lại cùng khách tạo ViewModel mới, giữ giỏ database và phát hiện giá đã đổi.
3. Lưu/khôi phục trạng thái Compose ở giao hàng giữ tên tiếng Việt, điện thoại, địa chỉ và màn hình; chưa tạo đơn. Tải lại sau quay từ kiểm tra về giao hàng không tự nhảy bước.
4. Form sửa tồn quản trị giữ phiên bản cũ qua tải lại/lưu-khôi phục trạng thái; một sửa tồn khác được giữ, bản nháp cũ bị từ chối.
5. Bốn ca giỏ xác nhận: giá đổi cần xác nhận lại, sản phẩm ẩn cần xóa, giảm tồn cần giảm số lượng và resume giữ bản nháp giao hàng.
6. Tái tạo Activity thật ở bước giao hàng, kiểm tra và sau commit: cùng AuthViewModel/CartViewModel còn qua tái tạo; đúng màn hình và bản nháp tiếng Việt, cùng mã yêu cầu, cùng mã đơn, chỉ một đơn và tồn giảm đúng một lần. Ca riêng và bộ kiểm thử đầy đủ đều đạt.

Kết quả cuối ngày 07/10/2026: **24 kiểm thử JVM + 75 kiểm thử Android đạt (99 ca, không bỏ qua)** trên Pixel 6 / Android 16; Gradle assemble debug và lint thành công. Lint: 0 lỗi, 46 cảnh báo và 1 thông tin, chủ yếu phiên bản thư viện/SDK, tài nguyên mẫu, gợi ý KTX và icon launcher.

Báo cáo: JVM ở `app/build/reports/tests/testDebugUnitTest/`, Android ở `app/build/reports/androidTests/connected/debug/`, lint ở `app/build/reports/lint-results-debug.html`.

APK: `app/build/outputs/apk/debug/app-debug.apk` (khoảng 25,89 MiB), hiện chứa cả quản trị và tích hợp phiên/điều hướng. Tài khoản quản trị demo và cách build ở mục 21.1.

Ảnh nghiệm thu: [Danh sách quản trị](screenshots/admin-products.png), [Chi tiết đơn quản trị](screenshots/admin-order-detail.png), [Khách xem đơn đã giao](screenshots/customer-delivered-order.png), [Giao hàng sau tái tạo Activity](screenshots/recreated-SHIPPING.png), [Kiểm tra sau tái tạo](screenshots/recreated-REVIEW.png), [Đơn đã lưu sau tái tạo](screenshots/recreated-SUCCESS.png).

Lệnh nghiệm thu:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline
```

### 22.6. Phạm vi và bước tiếp theo

Bản hiện tại dùng SQLite trên một thiết bị, COD demo và tám ảnh sản phẩm có sẵn. Luồng khách/admin được kiểm tra qua các phiên đăng nhập lần lượt; chưa có backend, đồng bộ kho giữa thiết bị, thanh toán online hoặc tải ảnh lên.

Tiếp tục **Phase 12 — Kiểm thử và ổn định bản nộp**: hoàn thiện ma trận nghiệm thu cho bản cục bộ, kiểm tra thiết bị Android tối thiểu/API 28, bố cục ngang và cỡ chữ lớn, khả năng khôi phục sau khi tiến trình kết thúc, bản release và tài liệu hạn chế. Các ca mạng/Security Rules thuộc backend sẽ ghi rõ chưa áp dụng thay vì đánh dấu đạt.


## 23. Phase 12 — Kiểm thử và ổn định bản cục bộ

### 23.1. Phân quyền ở Repository

Kiểm tra phiên nằm trong cùng transaction với đọc/ghi dữ liệu. `SessionAccess` đối chiếu tài khoản được truyền vào với dòng phiên đang đăng nhập. Giỏ, địa chỉ giao hàng trong giỏ, danh mục kèm yêu thích và lịch sử/chi tiết đơn đều yêu cầu đúng phiên. Khách B không thể truyền ID của A để đọc giỏ/địa chỉ/yêu thích/đơn, sửa giỏ hoặc xóa mã checkout chờ của A. Đăng xuất cũng chặn những lời gọi này. Quyền quản trị tiếp tục được đọc trực tiếp từ database trong mỗi transaction.

Khi phiên không còn hợp lệ, ViewModel danh mục/giỏ/đơn xóa dữ liệu riêng khỏi trạng thái giao diện và báo lỗi; giỏ xóa cả bản nháp địa chỉ và yêu cầu/kết quả checkout. Lần tải lại sau lỗi không ném lỗi phiên chưa xử lý gây dừng ứng dụng. Các kiểm thử cũ được sửa để đăng nhập đúng người trước khi đọc dữ liệu; fixture quản trị kiểm tra snapshot đơn bằng truy vấn nội bộ tin cậy.

Ba kiểm thử `SessionIsolationTest` thử toàn bộ API giỏ/checkout/đơn/yêu thích sau đăng xuất và đổi khách, cùng các API thêm/sửa sản phẩm và chuyển trạng thái bằng quyền khách. Sau mỗi nhóm bị từ chối, đăng nhập chủ sở hữu và đối chiếu giỏ/địa chỉ/yêu thích/yêu cầu/đơn/tồn vẫn nguyên vẹn.

### 23.2. Khôi phục và giao diện

- `ShopColdStartTest`: xóa toàn bộ ViewModelStore, đóng/mở SQLite và tạo AuthViewModel/CartViewModel mới. Phiên và yêu cầu chưa đặt khôi phục đúng mã; sau commit khôi phục đúng đơn; thử lại cùng yêu cầu vẫn một đơn, tồn chỉ giảm một lần và giữ mặt hàng mới thêm vào giỏ. Đây là mô phỏng mất bộ nhớ bằng đối tượng mới; không gọi là kết thúc tiến trình Android thật.
- Ca thu hồi phiên khi đang kiểm tra COD: tải lại về giỏ rỗng, bản nháp rỗng và thông báo phiên; yêu cầu checkout trong database không bị xóa bởi lời gọi bị từ chối.
- `ShopRecreationTest`: Activity ngang thật, mật độ chữ Compose 150%, tìm/mở sản phẩm → thêm giỏ → cuộn đến tiếp tục → nhập điện thoại/địa chỉ → kiểm tra → xác nhận COD. Các trường và hành động chính tiếp cận được qua cuộn. Đây là font scale cung cấp cho Compose trong fixture, chưa thay đổi cài đặt font toàn hệ điều hành.
- Ca tái tạo Activity ở giao hàng/kiểm tra/thành công vẫn giữ bản nháp và mã yêu cầu/đơn như Phase 11.

Ảnh minh chứng: [Màn hình ngang với chữ 150%](screenshots/landscape-font150.png).

### 23.3. Sao lưu và cấu hình release

`backup_rules.xml` loại toàn bộ domain `database` khỏi Auto Backup cho Android cũ. `data_extraction_rules.xml` loại database khỏi cả cloud backup và chuyển thiết bị cho Android 12+. Database chứa hash/salt mật khẩu, phiên đăng nhập và địa chỉ; ứng dụng hiện không có tính năng xuất/khôi phục dữ liệu riêng. Gỡ ứng dụng hoặc xóa dữ liệu sẽ mất tài khoản, giỏ và đơn cục bộ. Cấu hình theo [Android Auto Backup](https://developer.android.com/identity/data/autobackup); chưa thực hiện thử chuyển thiết bị thật.

Release thông thường giữ `applicationId=com.example.bloomybeauty`, `DEBUG=false`, ba giá trị demo admin rỗng và chưa có khóa ký phát hành. Không tự tạo quản trị demo trong release.

Thêm tùy chọn build `-PqaRelease=true` dành riêng cho nghiệm thu: cùng build type release, `applicationId=com.example.bloomybeauty.qa`, phiên bản `1.0-qa`, ký bằng khóa debug của máy phát triển. Gói QA có vùng dữ liệu riêng, cài cạnh bản đang dùng. Không dùng khóa này làm khóa phát hành chính thức. Hướng dẫn ký/build đối chiếu [Android command-line build](https://developer.android.com/build/building-cmdline).

```sh
./gradlew :app:assembleRelease :app:lintRelease -PqaRelease=true --offline
# Sao chép app/build/outputs/apk/release/app-release.apk sang thư mục qa-release
# trước khi build release thông thường để giữ riêng artifact QA.
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease --offline
```

APK QA được giữ tại `app/build/outputs/apk/qa-release/bloomy-beauty-release-qa.apk`; APK release thông thường chưa ký tại `app/build/outputs/apk/release/app-release-unsigned.apk`. APK debug để trình diễn quản trị tại `app/build/outputs/apk/debug/app-debug.apk`. Không đổi cấu hình admin cục bộ hoặc xóa dữ liệu bản chính để kiểm tra release.

### 23.4. Kết quả nghiệm thu

Ngày 07/10/2026, người chạy: Codex. Pixel 6 AVD / Android 16 / API 36. Kết quả cuối: **24 kiểm thử JVM + 81 kiểm thử Android = 105 ca đạt, 0 thất bại, 0 lỗi, 0 bỏ qua**. Sáu ca mới gồm ba ca quyền Repository, hai ca ViewModel mới/thu hồi phiên và một ca ngang/chữ lớn. Build debug, release thông thường và release QA thành công. Lint debug: 0 lỗi, 45 cảnh báo, 1 thông tin; lint release: 0 lỗi, 23 cảnh báo, 1 thông tin. Cảnh báo còn chủ yếu thuộc dependency/target SDK, tài nguyên mẫu, gợi ý KTX và icon.

Báo cáo thực tế: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/`, `app/build/reports/lint-results-debug.html`, `app/build/reports/lint-results-release.html`.

**Kiểm tra APK release QA ngoài instrumentation:** chữ ký v2 xác minh thành công bằng `apksigner`; package `.qa`, min SDK 28, target 35, `DEBUG=false` và cấu hình admin rỗng. Cài APK riêng, đăng ký tài khoản mới → mở sản phẩm → thêm giỏ → buộc dừng tiến trình (`adb shell am force-stop`) → mở lại: khôi phục phiên và giỏ 1 sản phẩm. Lưu địa chỉ/kiểm tra COD → buộc dừng và mở lại → mở giỏ: khôi phục đúng bước kiểm tra và địa chỉ. Xác nhận → lưu đơn `BB-000001`, tổng 222.000 ₫ (192.000 ₫ + phí 30.000 ₫) → buộc dừng và mở lại → mở giỏ: khôi phục đúng kết quả đơn. Xem lịch sử có một mã đơn; mở lại sản phẩm thấy tồn từ 30 xuống 29. Không gọi API backend và không tự tạo admin trong release QA.

Ảnh: [Kiểm tra COD sau cold start](screenshots/release-qa-review.png), [Đơn đã commit sau cold start](screenshots/release-qa-recovered-order.png), [Tồn còn 29](screenshots/release-qa-stock29.png). Ca ViewModel mới kiểm chứng cùng mã yêu cầu và replay; kịch bản `force-stop` kiểm chứng tiến trình Android thật ở trước và sau commit, chưa chèn lệnh dừng chính giữa transaction.

Ma trận mục 14 đối chiếu với **bản SQLite trên một thiết bị**:

| Mã | Kết quả thực tế | Bằng chứng/phạm vi |
|---|---|---|
| AUTH-01 | Đạt | AuthRepositoryTest, AuthFlowTest; đăng ký trên release QA |
| AUTH-02 | Đạt | Validation, email trùng, mật khẩu sai, SQL injection, đăng ký đồng thời |
| AUTH-03 | Đạt | Đổi phiên trong ShopIntegrationTest và từ chối truy cập A bằng phiên B |
| CAT-01 | Đạt | ProductSearchTest và HomeFlowTest: dấu tiếng Việt, lọc, giá tăng/giảm, yêu thích |
| CAT-02 | Đạt | CartRepositoryTest: ẩn/hết hàng/ID không tồn tại không thêm được |
| CART-01 | Đạt | Repository/JVM và CartFlowTest: tăng/giảm/xóa/tính tiền |
| CART-02 | Đạt | Đóng/mở SQLite và force-stop release QA giữ giỏ đúng phiên |
| ORDER-01 | Đạt | Đơn nhiều mặt hàng, snapshot, tổng và trừ tồn trong transaction |
| ORDER-02 | Đạt trong chính sách hiện tại | Đổi giá cần xác nhận; phí cố định 30.000 ₫/miễn từ 500.000 ₫ được kiểm thử, chưa có chỉnh phí runtime |
| ORDER-03 | Đạt | Thiếu tồn ở một dòng hoặc lỗi ghi dòng sau rollback cả đơn/tồn |
| ORDER-04 | Một phần | Hai khách theo phiên lần lượt trên cùng thiết bị tranh tồn 1: khách sau bị chặn; chưa thử hai thiết bị đồng thời |
| ORDER-05 | Đạt | 8 yêu cầu tạo đồng thời cùng phiên; thử lại cùng request giữ một đơn/tồn |
| ORDER-06 | Chưa áp dụng | Không có backend/mạng khi đặt; thay bằng khôi phục SQLite sau commit, không coi là kiểm thử mất phản hồi mạng |
| ORDER-07 | Đạt theo SQLite | Replay/khôi phục yêu cầu cũ giữ mặt hàng mới thêm; ghi giỏ được SQLite tuần tự hóa |
| CANCEL-01 | Đạt | Hủy lặp/đồng thời chỉ hoàn tồn một lần, chặn trạng thái đã xử lý |
| CANCEL-02 | Đạt cơ chế SQL | Hủy chạy đồng thời câu SQL xác nhận có điều kiện: một trạng thái cuối, tồn khớp; không mô phỏng hai phiên UI cùng lúc |
| ADMIN-01 | Đạt | Sửa/ẩn sản phẩm không thay snapshot tên/giá trong đơn cũ |
| ADMIN-02 | Đạt | Repository và giao diện quản trị: chuyển đúng luồng, terminal, lịch sử, trạng thái cũ |
| ADMIN-03 | Đạt | Revision cũ bị từ chối sau đặt/hủy/cập nhật khác, không ghi đè tồn |
| SEC-01 | Đạt Repository | Chưa đăng nhập/B truyền ID A bị từ chối; B xem ID đơn A trả null |
| SEC-02 | Đạt API cục bộ | Khách không thêm/sửa sản phẩm/chuyển trạng thái; đăng ký chỉ CUSTOMER; quyền admin bị thu hồi chặn API |
| UI-01 | Một phần | Danh mục/yêu thích rỗng và 8 ảnh đóng gói đạt; không có tải ảnh/mạng trong app, chưa thử backend/mạng/ảnh remote lỗi |
| UI-02 | Đạt các ca đã chạy | Tiếng Việt ở đăng ký, nhập/giao hàng, quay lại, tái tạo Activity, ngang/chữ Compose 150% |
| RELEASE-01 | Một phần | Release QA cài riêng và hoàn thành COD/cold start; luồng quản trị đã đạt trên debug, bản ký phát hành/API 28/thiết bị nộp còn chờ |

### 23.5. Artifact và hạn chế còn lại

| Artifact | Dung lượng | SHA-256 |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 26,10 MiB | `37cd7c886184bd3a3de40e14520fd8cdf4b1118f6ebe346195f025d23bda7702` |
| `app/build/outputs/apk/qa-release/bloomy-beauty-release-qa.apk` | 19,60 MiB | `d6e5852e517a0f02313a259f9740e8eb1249a836bc1b092c2fd291fb6395c2bc` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 19,59 MiB | `b9a67052545ecaebb90c262ff0adb3789bf0db2d73eda1a1445403cf8546ff07` |

- Chưa có emulator API 28; APK khai báo min SDK 28 và lint không báo lỗi API, nhưng chưa coi Android tối thiểu là đã nghiệm thu. Cần chạy lại trên API 28 và thiết bị trình diễn thực tế.
- QA dùng khóa debug để cài thử, release thường chưa ký. Khóa ký phát hành và việc cấp tài khoản quản trị cho bản release chính thức chưa được thiết lập. Bản debug có cấu hình admin demo cục bộ là bản hiện có để diễn tập đầy đủ khách/quản trị.
- Không có backend, Security Rules, đồng bộ nhiều thiết bị, thanh toán online hoặc ảnh remote. Không suy diễn kiểm thử SQLite thành nghiệm thu các phần đó.
- Loại database khỏi backup đã được build/lint; chưa thử cloud restore/chuyển thiết bị. Database chưa mã hóa; phân quyền Repository không chống người có quyền root hoặc can thiệp trực tiếp dữ liệu ứng dụng.
- Ngôn ngữ giao diện hiện là tiếng Việt; không thay toàn bộ thư viện/target SDK theo cảnh báo phiên bản trong lần nghiệm thu này.

**Trạng thái Phase 12:** đã hoàn thiện sửa quyền, kiểm thử tự động, cold start thật, ngang/chữ lớn, cấu hình backup và kiểm tra release thực hiện được trên API 36. Chưa chốt nghiệm thu toàn bộ vì API 28, thiết bị nộp và bản ký phát hành còn chờ. Phase 13 tiếp theo tập trung báo cáo, sơ đồ theo SQLite, hướng dẫn chạy/build và bộ tài liệu trình diễn; giữ các mục còn chờ trong checklist để không bỏ sót.



## 24. Phase 13 — Bộ tài liệu và APK trình diễn

Ngày chuẩn bị: **08/10/2026**. Bộ này mô tả đúng bản Kotlin/Compose/SQLite schema 5. Phần Firebase/backend ở kế hoạch ban đầu vẫn là phương án mở rộng, không đưa vào báo cáo như chức năng đã chạy.

### 24.1. Hồ sơ đã tạo

| Thành phần | File | Nội dung |
|---|---|---|
| Báo cáo kỹ thuật | [Markdown](bao-cao/bao-cao-de-tai.md), [HTML để đọc/in](bao-cao/bao-cao-de-tai.html) | Bài toán, yêu cầu, kiến trúc, 11 bảng, nghiệp vụ, ảnh, kiểm thử, giới hạn, tham khảo, phụ lục phân công |
| Sơ đồ | [Mermaid và chú giải](bao-cao/so-do.md), `bao-cao/so-do/*.svg` | Use case, kiến trúc, quan hệ SQLite, đặt COD, bảng trạng thái/hủy; SVG dùng ngoại tuyến |
| Hướng dẫn | [Cài/chạy/build](huong-dan-chay.md), `README.md` tại gốc | SDK/JDK/Gradle, các package, tài khoản, seed, test, đóng gói, lỗi thường gặp |
| Slide | [10 trang HTML ngoại tuyến](trinh-dien/slides.html) | Điều hướng ←/→/Home/End, toàn màn hình, in PDF từ trình duyệt |
| Video | [MP4 4 phút 10 giây](trinh-dien/bloomy-beauty-demo.mp4), [phụ đề SRT](trinh-dien/demo.srt) | Ghi màn hình thật trên APK .demo; phụ đề gắn sẵn, không có thuyết minh âm thanh; cắt đoạn chờ/tăng tốc 1,6 lần |
| Kịch bản bảo vệ | [Kịch bản và Q&A](trinh-dien/kich-ban.md) | Các bước 3–5 phút, lời trình bày dự kiến, chuẩn bị thiết bị, 11 câu hỏi/ý trả lời |
| Công cụ tài liệu | `scripts/render-docs.py`, `scripts/render-video.py`, `scripts/package-submission.py` | Sinh HTML/SVG, mã hóa từ bản ghi thô, đóng gói và xác minh hash/ZIP |

Báo cáo/slide để trống tên trường/khoa theo mẫu, lớp, giảng viên, nhóm, họ tên/MSSV và phân công. Chưa có dữ liệu để tự điền hoặc xác nhận đóng góp của từng thành viên. Có thể in báo cáo HTML/slide ra PDF trong trình duyệt; PDF chưa được xuất tự động trong bộ này.

### 24.2. APK và tài khoản trình diễn

Thêm cờ **`-Ppresentation=true` chỉ cho debug**. Script `create-demo-admin.py --presentation` tạo file riêng `.local/presentation-admin.properties`, không ghi đè cấu hình admin riêng. Build demo dùng email/hash/salt của file này, package `com.example.bloomybeauty.demo`, version `1.0-demo`, ký debug. APK gốc và release giữ hành vi cấu hình riêng; release không seed admin.

| Vai trò | Email | Mật khẩu demo | Cách có tài khoản |
|---|---|---|---|
| Admin | `admin@bloomy.demo` | `BloomyDemo123!` | Bootstrap debug khi email chưa tồn tại |
| Khách | `khach@bloomy.demo` | `KhachDemo123!` | Đăng ký qua giao diện trên thiết bị thử; APK không tự tạo khách |

Tám sản phẩm/ảnh và tồn khởi tạo được seed khi tạo database. Không chứa database của thiết bị trong ZIP; thiết bị khác cài APK cần đăng ký khách theo hướng dẫn. Tài khoản admin công khai của gói .demo khác admin ngẫu nhiên trong `.local/admin-demo.properties`. ZIP loại toàn bộ `.local` và `local.properties`.

Lệnh đã chạy:

```sh
python3 scripts/create-demo-admin.py --presentation
./gradlew :app:assembleDebug :app:lintDebug -Ppresentation=true --offline
# Sao chép APK demo sang deliverables/bloomy-beauty-demo.apk trước khi build gói gốc.
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease --offline
```

APK demo: `deliverables/bloomy-beauty-demo.apk` — **25,81 MiB**, SHA-256 `18ee59c5f3d723fd8f46ed0d69a98dda27ba53702614e3a03c9fea38a10c55d4`; `apksigner` xác minh v2 đạt. Package .demo, min SDK 28, target SDK 35 đã đối chiếu trực tiếp metadata APK. Release QA trong bộ bàn giao giữ SHA-256 của mục 23.5.

### 24.3. Diễn tập thực tế và kiểm tra

Trên Pixel 6/API 36 ngày 08/10/2026, cài APK .demo → đăng ký khách → mở cleanser → thêm giỏ → lưu số `0912345678` và địa chỉ mẫu → review 192.000 + 30.000 = 222.000 ₫ → đặt BB-000001 → xem lịch sử → đăng xuất → admin đăng nhập → Quản trị cửa hàng → đơn → PENDING → CONFIRMED → SHIPPING → DELIVERED → đăng xuất → khách đăng nhập lại → xem BB-000001 Đã giao, không có nút Hủy đơn.

Đối chiếu SQLite của riêng gói .demo sau diễn tập bằng truy vấn đọc: **1 đơn, DELIVERED, tổng 222.000 ₫; cleanser tồn 29; 4 dòng audit** (tạo + ba chuyển). `integrity_check=ok`, `foreign_key_check` không có dòng lỗi. Ảnh: [Đơn vừa đặt](screenshots/demo-order-created.png), [Admin xử lý Đã giao](screenshots/demo-admin-delivered.png), [Khách thấy Đã giao](screenshots/demo-customer-delivered.png).

Bộ **105 kiểm thử** là kết quả connected/JVM của Phase 12 trên gói debug gốc; Phase 13 không thay nghiệp vụ Repository/giao diện. Sau thêm cấu hình trình diễn, build/lint debug demo và debug/release gốc đạt; 24 ca JVM còn đạt với kết quả Gradle up-to-date. Không gọi đây là lần chạy 81 connected tests mới trên APK .demo. Lint giữ 0 lỗi, debug 45 cảnh báo + 1 thông tin, release 23 cảnh báo + 1 thông tin.

Đã kiểm tra cú pháp Python/JavaScript, 10 section của slide, 5 SVG hợp lệ XML, đường dẫn ảnh/SVG nội bộ; video H.264 540×1200, duration 250,03 giây, không audio. Video có phụ đề tiếng Việt, ghi thao tác thật; phần ngắt giữa các bản ghi có thể lược bỏ nhịp nhập, kịch bản bảo vệ cung cấp lời đọc riêng.

### 24.4. Đóng gói và tái tạo

- Bộ đầy đủ: `deliverables/bloomy-beauty-ban-giao.zip` gồm APK demo, release QA, mã nguồn zip, tài liệu/ảnh/video, báo cáo kiểm thử, manifest và checksum.
- Mã nguồn: `deliverables/bloomy-beauty-source.zip`; không chứa cache build, đường dẫn SDK, .local, bản ghi thô hoặc video MP4/SRT. Video có trong bộ đầy đủ.
- Nhận diện snapshot: `deliverables/source-manifest.json` ghi SHA-256/byte của từng file. Workspace không có Git nên không invent commit ID.
- `deliverables/SHA256SUMS.txt` ghi hash các artifact/tài liệu trong bộ; ZIP đầy đủ chứa file checksum, không chứa hash tự tham chiếu của chính nó.
- Chạy `python3 scripts/render-docs.py` sau sửa báo cáo/sơ đồ và `python3 scripts/package-submission.py` sau sửa tài liệu/artifact. Script kiểm tra ZIP CRC, đường dẫn loại trừ và hash từng file nguồn; chưa build lại APK thay cho người chạy.

Đã chạy đóng gói và kiểm tra thực tế: **142 file nguồn**, ZIP source khoảng **4,64 MiB**, ZIP đầy đủ khoảng **26,77 MiB**. ZIP CRC đạt; hash từng file nguồn trong ZIP khớp manifest; mọi file trong `SHA256SUMS.txt` khớp file bàn giao. ZIP đầy đủ chứa video/SRT, báo cáo HTML, APK demo và manifest; không có `.local` hoặc `local.properties`.

### 24.5. Việc còn cần trước nộp chính thức

Điền/xác nhận thông tin học phần, thành viên và phân công thực tế; xuất báo cáo/slide theo định dạng yêu cầu; chạy API 28 và thiết bị trình diễn thực tế; chuẩn bị khóa ký phát hành nếu học phần cần APK release chính thức; diễn tập với người trình bày. Không có xác nhận rằng nhóm đã bảo vệ hoặc giảng viên đã nghiệm thu.

Phase 13 đã có bộ tài liệu kỹ thuật và artifact dùng được để trình diễn; các mục còn cần người dùng/thiết bị thực tế được giữ trong checklist. Chưa tự mở rộng Phase 14 (đánh giá, doanh thu, coupon, thông báo, ảnh tải lên) để bản trình diễn hiện tại vẫn khớp báo cáo.
