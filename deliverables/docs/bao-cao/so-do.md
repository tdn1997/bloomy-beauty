# Sơ đồ triển khai thực tế — SQLite schema 11

Các sơ đồ mô tả bản một thiết bị. Tác nhân khách/admin lần lượt dùng auth_session; đơn hàng dùng SQLite cục bộ; Firebase chỉ được bật ở biến thể phù hợp cho ưu đãi và FCM. Có SVG ngoại tuyến kèm từng sơ đồ để in/xem không cần thư viện Mermaid.

## 1. Use case

![Use case](so-do/use-case.svg)

```mermaid
flowchart LR
    Guest[Chưa đăng nhập] --> Auth[Đăng ký / Đăng nhập]
    Customer[Khách hàng] --> Browse[Tìm / lọc / xem / yêu thích]
    Customer --> Cart[Giỏ / giao hàng / xác nhận COD]
    Customer --> History[Xem đơn của mình / hủy PENDING]
    Customer --> Profile[Hồ sơ / đăng xuất]
    Customer --> Care[Lịch chăm sóc / nhắc hẹn]
    Admin[Quản trị viên] --> Product[Thêm / sửa / ẩn / mở sản phẩm]
    Admin --> Order[Xem đơn / xử lý trạng thái / lịch sử]
    Customer --> Review[Đánh giá đơn đã giao]
    Customer --> Address[Sổ địa chỉ / chọn checkout]
    Customer --> Coupon[Mã giảm giá demo]
    Customer --> PDF[Phiếu PDF snapshot]
    Customer --> Inbox[Thông báo đơn / badge]
    Admin --> PDF
    Admin --> Stats[Thống kê theo ngày giao]
    Admin --> Profile
```

## 2. Kiến trúc

![Kiến trúc](so-do/kien-truc.svg)

```mermaid
flowchart LR
    UI[Compose / BloomyApp] --> VM[ViewModel / StateFlow]
    VM --> Repo[Repository + kiểm tra phiên/quyền]
    Repo --> DB[SQLiteOpenHelper / SQLite v11]
    Seed[Seed 8 sản phẩm + ảnh resource] --> DB
    Resource[Ảnh đóng gói] --> UI
```

AuthViewModel sở hữu ViewModelStore của phiên; store giữ qua cấu hình và xóa sau đăng xuất thành công. Repository dùng Dispatchers.IO và transaction. Biểu mẫu hồ sơ không tự tải đè khi resume.

## 3. Quan hệ dữ liệu

![ERD](so-do/du-lieu.svg)

```mermaid
erDiagram
    users ||--o| auth_session : current_session
    users ||--o{ favorites : saves
    users ||--o{ cart_items : owns
    users ||--o| shipping_details : address
    users ||--o| checkout_requests : pending_request
    users ||--o{ orders : places
    users ||--o{ order_events : acts
    categories ||--o{ products : groups
    products ||--o{ favorites : saved_product
    products ||--o{ cart_items : selected_product
    products ||--o{ order_items : purchased_product
    orders ||--|{ order_items : snapshot_lines
    orders ||--|{ order_events : audit
    users ||--o{ addresses : address_book
    users ||--o{ product_reviews : writes
    order_items ||--o| product_reviews : proves_purchase
    users ||--o{ user_notifications : receives
    addresses { int _id PK int user_id FK boolean is_default long revision }
    product_reviews { int _id PK int user_id FK int order_id FK string product_id FK int rating string comment long revision }
    coupon_rules { string code PK string kind long value long min_subtotal long starts_at long ends_at long revision }
    user_notifications { int _id PK int user_id FK string event_id string type long entity_id long created_at long read_at }
    users { int _id PK string email UK string role }
    auth_session { int _id PK int user_id FK }
    categories { string id PK string name }
    products { string id PK string category_id FK long price_vnd int stock long revision boolean active }
    favorites { int user_id PK,FK string product_id PK,FK }
    cart_items { int user_id PK,FK string product_id PK,FK int quantity long accepted_price_vnd }
    shipping_details { int user_id PK,FK string recipient string phone string address }
    checkout_requests { string request_id PK int user_id FK,UK string fingerprint string payload }
    orders { int _id PK int user_id FK string request_id UK string status long delivered_at long subtotal_vnd long discount_vnd long fee_vnd long total_vnd string coupon_code string coupon_rule_snapshot }
    order_items { int order_id PK,FK string product_id PK,FK string name long unit_price_vnd int quantity }
    order_events { int _id PK int order_id FK int actor_id FK string from_status string to_status long changed_at }
```

`orders.request_id` liên hệ logic với request đã tạo; không khai báo FK tới checkout_requests vì yêu cầu có thể được acknowledge/xóa sau khi khách xem kết quả. `order_items.product_id` có FK tới products nhưng tên/giá/dung tích/ảnh lưu snapshot. SVG chỉ vẽ các đường chính cho dễ đọc; Mermaid trên liệt kê cả quan hệ phụ.

Các bảng mở rộng có sơ đồ riêng: ![Mở rộng](so-do/mo-rong.svg). `user_notifications.entity_id` là đích logic không có FK tới orders để giữ lịch sử khi đích không còn; event_id dẫn từ audit, không chứa PII. `coupon_rules` không có FK từ đơn vì đơn lưu snapshot và không mất theo chiến dịch.

## 4. Trình tự đặt COD

![Luồng đặt](so-do/dat-cod.svg)

```mermaid
sequenceDiagram
    actor Customer as Khách
    participant UI as Compose/ViewModel
    participant Repo as OrderRepository
    participant DB as SQLite
    Customer->>UI: Lưu giao hàng và kiểm tra
    UI->>Repo: prepare(userId)
    Repo->>DB: Lưu UUID + fingerprint + payload
    Customer->>UI: Xác nhận COD
    UI->>Repo: create(userId, request)
    Repo->>DB: BEGIN TRANSACTION
    Repo->>DB: Kiểm tra phiên, request/result, giá/tồn/giỏ và coupon/quote/fee
    alt Đã commit cùng mã và cùng nội dung
        DB-->>Repo: Đơn cũ
    else Hợp lệ và chưa commit
        Repo->>DB: Trừ tồn + tăng revision + ghi snapshot/audit/inbox + dọn giỏ
    else Xung đột / thiếu tồn / lỗi ghi
        Repo->>DB: ROLLBACK
    end
    Repo->>DB: COMMIT nếu thành công
    Repo->>UI: Broadcast badge / thử notification sau commit
    Repo-->>UI: Đơn hoặc lỗi nghiệp vụ
    UI-->>Customer: Kết quả / hành động sửa giỏ
```

Không tra kết quả bằng mạng; khôi phục dùng request đã lưu trên cùng database. Replay thành công không dọn giỏ vừa thêm lần nữa.

## 5. Hủy và trạng thái

![Trạng thái đơn](so-do/trang-thai.svg)

```mermaid
stateDiagram-v2
    [*] --> PENDING: COD / trừ tồn
    PENDING --> CONFIRMED: Admin xác nhận
    CONFIRMED --> SHIPPING: Admin bắt đầu giao
    SHIPPING --> DELIVERED: Admin giao xong
    PENDING --> CANCELLED: Chủ đơn hoặc admin / hoàn tồn
    CONFIRMED --> CANCELLED: Admin / hoàn tồn
    DELIVERED --> [*]
    CANCELLED --> [*]
```

Hủy kiểm tra phiên, quyền sở hữu/vai trò và trạng thái trong transaction; cập nhật trạng thái có điều kiện, hoàn tồn từng dòng, tăng revision và ghi audit. Gửi lại hủy đã thành công không hoàn tồn lần hai. Khi lỗi ghi lịch sử hoặc tràn tồn, rollback cả trạng thái và tồn. Khách không hủy CONFIRMED/SHIPPING/DELIVERED.

## 6. Lịch chăm sóc bổ sung ở schema 6

```mermaid
erDiagram
    users ||--o{ care_routines : owns
    products o|--o{ care_routines : references
    care_routines {
        integer _id PK
        integer user_id FK
        text product_id FK
        text title
        integer minute_of_day
        integer days_mask
        integer enabled
        integer revision
        integer last_occurrence_at
    }
```

UI → ViewModel → Repository → ContentResolver → CareRoutineProvider → SQLite.
ContentObserver cập nhật danh sách sau commit. AlarmManager → receiver tĩnh → kiểm tra phiên/revision/occurrence → notification. Receiver hệ thống khôi phục lịch; receiver động nhận tín hiệu đổi đơn theo vòng đời.

## 7. Tham khảo tùy chọn: danh tính Cloud B0/B1

Phần này lưu thử nghiệm đã làm; không cần triển khai backend hoặc chạy Cloud để hoàn thành demo môn học.

![Firebase Auth và quyền server](so-do/firebase-auth.svg)

```mermaid
flowchart LR
    Main[MainActivity / nguồn theo build] --> Local[LocalDemo / SQLite v11]
    Main --> Cloud[CloudApp / UID + nonce]
    Cloud --> Ports[CloudAuthGateway / CloudAccounts]
    Ports --> Auth[Firebase Auth Emulator]
    Ports --> Callable[Cloud Functions / ID token]
    Callable --> Auth
    Callable --> Roles[roles UID / server ghi]
    Callable --> Profile[users UID / bootstrap]
```

Cloud dùng package `.cloud` và project `demo-bloomy-commerce`; bản thường/demo/QA còn LocalDemo. UID và token Firebase không lấy từ auth_session hoặc users.role SQLite. Callback phiên cũ bị loại; profile/role Cloud chỉ được đọc đúng chủ qua rules. B2/B3 mới thêm giao dịch và đồng bộ nghiệp vụ; sơ đồ này không mô tả chúng như đã triển khai.
