# Kế hoạch Chia sẻ sản phẩm bằng ảnh — 16/11/2026

Phạm vi: tạo một ảnh PNG từ dữ liệu sản phẩm local rồi mở Android Sharesheet.
Trạng thái: hoàn tất triển khai và kiểm thử ngày 16/11/2026. Không thay schema 11.

## Luồng và quy tắc

1. Trang chi tiết có nút **Chia sẻ ảnh sản phẩm**. Tạo ảnh trong nền, khóa nhấn
   lặp, hiển thị tiến trình và lỗi nếu có.
2. Ảnh thẻ gồm logo chữ Bloomy Beauty, ảnh đóng gói trong app, tên, thương hiệu,
   dung tích, giá tham khảo, tình trạng hàng, mô tả tóm tắt và thời điểm tạo.
   Ảnh rộng 1080 px, chiều cao theo nội dung; giới hạn dòng để tránh ảnh quá lớn.
3. Mở xem trước; **Hủy** xóa ảnh chưa chia sẻ, **Chia sẻ ảnh** mở bảng chọn của
   Android. Người dùng tự chọn ứng dụng nhận; không tự gửi hoặc thông báo đã gửi.
4. Không đưa tài khoản, giỏ, địa chỉ, đơn hoặc nút UI vào ảnh. Không cần server,
   URL sản phẩm hay deep link để tạo ảnh. Không xin quyền đọc/ghi bộ nhớ chung.
5. Đọc lại sản phẩm từ catalog theo phiên trước/sau xuất và trước mở chia sẻ.
   Không còn phiên/ngừng bán, dữ liệu đổi hoặc ảnh hết hạn thì phải tạo lại.
   Hết hàng vẫn cho chia sẻ, có nhãn tình trạng tại lúc tạo ảnh.
6. File ở cache riêng, tên UUID; FileProvider riêng chỉ mở đúng thư mục ảnh.
   Intent `image/png`, `EXTRA_STREAM`, `ClipData` và quyền đọc tạm; không cấp ghi.
7. Giữ file đã mở Sharesheet cho ứng dụng nhận đọc bất đồng bộ. Dọn ảnh từ 24 giờ
   ở lần tạo tiếp theo; đổi phiên/đăng xuất xóa cache và thu hồi quyền URI còn có.
8. Khi đổi sản phẩm hoặc đóng màn hình, hủy coroutine và dọn ảnh chưa chia sẻ.
   Xoay máy không tự chia sẻ hoặc lưu bitmap vào saved state; tạo lại nếu cần.

## Điểm triển khai

- `ProductImageRenderer`: Canvas/StaticLayout dựng PNG có xuống dòng tiếng Việt,
  lấy mẫu ảnh nguồn để hạn chế bộ nhớ và xử lý ảnh không hợp lệ bằng hình thay thế.
- `ProductImageExporter`: nguồn catalog hiện tại, file tạm, kiểm tra phiên/dữ
  liệu, tạo intent và dọn cache. `ProductImageFileProvider` có paths riêng.
- `ProductShareActions`: UI tạo/xem trước/hủy/chia sẻ. Nối exporter từ Application
  qua MainActivity, ShopRepositories, HomeScreen vào trang chi tiết.
- Provider PDF và các nghiệp vụ mua hàng hiện có giữ chức năng như trước.

## Kiểm thử và bàn giao

- PNG đọc qua ContentResolver, kích thước/nội dung có ảnh; MIME và grant chỉ đọc;
  từ chối file ngoài thư mục, phiên cũ, sản phẩm mất/ngừng bán, giá đổi, ảnh hết hạn.
- Xác nhận nhiều file không trùng; hủy/dọn cache; lỗi output và chuỗi dài tiếng Việt.
- UI thật: mở chi tiết, tạo preview, hủy, mở native Sharesheet rồi Back; không
  chọn ứng dụng nhận/gửi dữ liệu trong lượt kiểm thử. Thay giá và recreate activity.
- Hồi quy FileProvider PDF, HomeFlow và tích hợp So sánh → giỏ trên Pixel_6 API 35.
- Ghi kết quả và ảnh thật tại `docs/kiem-thu/product-share-2026-11-16/README.md`.

[Hướng dẫn](20261116000000_chia-se-san-pham.md).

[Kết quả và ảnh minh chứng](kiem-thu/product-share-2026-11-16/README.md).
