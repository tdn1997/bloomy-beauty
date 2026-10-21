# Báo cáo tiến độ lần 1 — Nhóm 4

Ngày báo cáo: 14/10/2026. Đề tài: ứng dụng bán mỹ phẩm Bloomy Beauty.

| Thành viên | Mã số sinh viên |
|---|---|
| Phạm Văn Bình | 25TX610001 |
| Nguyễn Duy Thuận | 25TX810025 |
| Đoàn Thị Mỹ Thiện | 25TX810023 |

Mở `Bao-cao-tien-do-lan-1-Nhom-4.docx` bằng Microsoft Word. Các ảnh PNG được nhúng trực tiếp trong Word, đồng thời giữ bản gốc ở thư mục này. `danh-muc-anh.json` ghi thứ tự, chú thích và SHA-256 của ảnh.

Ảnh chụp mới trên máy giả lập Android API 36, từ APK `deliverables/bloomy-beauty-demo.apk`, package `com.example.bloomybeauty.demo`. Thao tác chỉ dùng tài khoản và đơn hàng demo. Thứ tự trình bày gồm xác thực, mua hàng, hồ sơ, quản trị, xử lý đơn và nhánh hủy đơn chờ. Trạng thái tiến độ: **Đang kiểm thử và hoàn thiện**.

Kết quả 24 JVM và 81 Android trong Word được đối chiếu với báo cáo đã lưu tại `deliverables/ket-qua-kiem-thu`, không phải kết quả chạy lại toàn bộ trong lượt chụp này. Biểu mẫu hồ sơ/thêm sản phẩm chỉ là minh chứng giao diện, không khẳng định đã kiểm tra lưu dữ liệu trong lượt này.

Tạo lại Word khi đã có thư viện `python-docx` và `Pillow`: `python3 scripts/create-progress-report.py`. Kịch bản chụp giao diện: `scripts/capture-progress.py` (cần ADB, máy giả lập và đúng trạng thái điều hướng).
