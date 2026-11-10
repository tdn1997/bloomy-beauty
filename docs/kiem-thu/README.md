# Hồ sơ kiểm thử

Git giữ mã nguồn kiểm thử, tài liệu Markdown và ảnh minh chứng dùng trong báo cáo. Log chạy, XML/JUnit/lint, JSON kết quả/môi trường, PDF xuất thử và các đầu ra build không được commit; `.gitignore` giữ chúng ở máy chạy kiểm thử.

Toàn bộ `deliverables/` là bộ bàn giao được tạo lại bằng `scripts/package-submission.py`, gồm APK, ZIP, bản sao tài liệu và kết quả kiểm thử; không theo dõi trong Git. Báo cáo gốc và ảnh nằm trong `docs/bao-cao/`, `docs/screenshots/` và các thư mục minh chứng tại đây.

Các liên kết tới log/JSON/XML trong hồ sơ từng đợt chỉ mở được khi có kết quả chạy trên máy hoặc bộ bàn giao đi kèm. Bản clone Git vẫn có phần tổng hợp và ảnh; không tự tạo số liệu thay thế cho các lượt lịch sử.

Chạy lại các kiểm thử liên quan trên AVD Pixel_6 hiện có bằng `scripts/run-pixel6-tests.py` sau khi build/cài APK và APK instrumentation. Mỗi hồ sơ ghi các lớp test và phạm vi đã chạy. Script tạo báo cáo Word có thể cần log/metadata cục bộ của chính đợt đó; file Word đã tạo được giữ trong `docs/bao-cao/`.
