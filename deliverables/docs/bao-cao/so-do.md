# Sơ đồ triển khai thực tế — SQLite schema 5

Các sơ đồ mô tả bản một thiết bị. Tác nhân khách/admin lần lượt dùng auth_session; không có server hay Firebase trong mã đang chạy. Có SVG ngoại tuyến kèm từng sơ đồ để in/xem không cần thư viện Mermaid.

## 1. Use case

![Use case](so-do/use-case.svg)

```mermaid
flowchart LR
    Guest[Chưa đăng nhập] --> Auth[Đăng ký / Đăng nhập]
    Customer[Khách hàng] --> Browse[Tìm / lọc / xem / yêu thích]
    Customer --> Cart[Giỏ / giao hàng / xác nhận COD]
    Customer --> History[Xem đơn của mình / hủy PENDING]
    Customer --> Profile[Hồ sơ / đăng xuất]
    Admin[Quản trị viên] --> Product[Thêm / sửa / ẩn / mở sản phẩm]
    Admin --> Order[Xem đơn / xử lý trạng thái / lịch sử]
    Admin --> Profile
```

## 2. Kiến trúc

![Kiến trúc](so-do/kien-truc.svg)

```mermaid
flowchart LR
    UI[Compose / BloomyApp] --> VM[ViewModel / StateFlow]
    VM --> Repo[Repository + kiểm tra phiên/quyền]
    Repo --> DB[SQLiteOpenHelper / SQLite v5]
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
    users { int _id PK string email UK string role }
    auth_session { int _id PK int user_id FK }
    categories { string id PK string name }
    products { string id PK string category_id FK long price_vnd int stock long revision boolean active }
    favorites { int user_id PK,FK string product_id PK,FK }
    cart_items { int user_id PK,FK string product_id PK,FK int quantity long accepted_price_vnd }
    shipping_details { int user_id PK,FK string recipient string phone string address }
    checkout_requests { string request_id PK int user_id FK,UK string fingerprint string payload }
    orders { int _id PK int user_id FK string request_id UK string status long total_vnd }
    order_items { int order_id PK,FK string product_id PK,FK string name long unit_price_vnd int quantity }
    order_events { int _id PK int order_id FK int actor_id FK string from_status string to_status long changed_at }
```

`orders.request_id` liên hệ logic với request đã tạo; không khai báo FK tới checkout_requests vì yêu cầu có thể được acknowledge/xóa sau khi khách xem kết quả. `order_items.product_id` có FK tới products nhưng tên/giá/dung tích/ảnh lưu snapshot. SVG chỉ vẽ các đường chính cho dễ đọc; Mermaid trên liệt kê cả quan hệ phụ.

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
    Repo->>DB: Kiểm tra phiên, request/result, giá/tồn/giỏ
    alt Đã commit cùng mã và cùng nội dung
        DB-->>Repo: Đơn cũ
    else Hợp lệ và chưa commit
        Repo->>DB: Trừ tồn + tăng revision + ghi snapshot/audit + dọn giỏ
    else Xung đột / thiếu tồn / lỗi ghi
        Repo->>DB: ROLLBACK
    end
    Repo->>DB: COMMIT nếu thành công
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
