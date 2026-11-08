# Báo cáo tiến độ lần 2 — Nhóm 4

Ngày báo cáo: **07/11/2026**. Đề tài: ứng dụng bán mỹ phẩm Bloomy Beauty. Trạng thái: **Đang kiểm thử và hoàn thiện**.

Mở [Báo cáo Word lần 2](Bao-cao-tien-do-lan-2-Nhom-4.docx). File kế thừa style, bố cục trang và danh sách thành viên từ [báo cáo lần 1 ngày 14/10/2026](../tien-do-lan-1/Bao-cao-tien-do-lan-1-Nhom-4.docx), giữ cấu trúc bốn phần chính.

| Thành viên | Mã số sinh viên |
|---|---|
| Phạm Văn Bình | 25TX610001 |
| Nguyễn Duy Thuận | 25TX810025 |
| Đoàn Thị Mỹ Thiện | 25TX810023 |

Nội dung bổ sung: đối chiếu lần 1/lần 2; schema 5 → 6; lịch chăm sóc, Content Provider, nhắc hẹn và Broadcast Receiver; trạng thái Firestore/FCM theo biến thể; kết quả kiểm thử và các việc còn lại. Không tự gán phân công hoặc tỷ lệ đóng góp.

Ảnh: 7 ảnh từ [đợt kiểm thử mới nhất trên Pixel_6](../../kiem-thu/pixel6-care-2026-11-07/README.md), cùng 2 ảnh Firestore từ đợt 21/10/2026; cả 9 ảnh được nhúng trực tiếp trong Word và giữ bản PNG ở thư mục này. [Danh mục ảnh](danh-muc-anh.json) lưu nguồn, chú thích, kích thước và SHA-256. Ảnh Firestore được ghi rõ là minh chứng lịch sử, không phải ảnh Pixel_6 mới. Không chụp lại hoặc chạy lại kiểm thử trong lượt tạo/cập nhật Word.

Số liệu: 46 JVM đạt; trên Pixel_6 gói demo, 98 Android đạt, 7 bỏ qua theo điều kiện trong 105 ca. Nhóm F1–F3 là tập có giao với bộ toàn app, không cộng dồn. Kết quả nhiều API/Doze ở đợt trước được ghi riêng; không gọi là chạy lại trên Pixel_6. Thiết bị thật để đợt sau theo yêu cầu.

Tái tạo khi đã cài `python-docx` và `Pillow`:

```sh
python3 scripts/create-progress-report-2.py
```

Script chỉ tạo/cập nhật thư mục lần 2, không sửa bản Word lần 1.

## Phần Firebase bổ sung

Mục 2.1 mô tả cấu hình theo biến thể, schema hai collection, luồng snapshot listener → Flow → ViewModel → UI, cache/lỗi/kết nối lại, rules/IAM, token và payload FCM, quyền notification, kết quả kiểm thử và giới hạn checkout. Mục 2.2 nhúng hai ảnh Firestore lịch sử có ngày và nguồn rõ ràng. Không khẳng định đã chạy Firebase live hoặc gửi FCM trong lượt cập nhật báo cáo này.
