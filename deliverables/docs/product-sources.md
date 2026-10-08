# Nguồn dữ liệu và ảnh sản phẩm — Bloomy Beauty

Ngày thu thập: 07/10/2026. Nguồn: trang sản phẩm công khai của Cocoon Vietnam. Nội dung trong ứng dụng là tên, dung tích, giá tham khảo và phần mô tả/thành phần/cách dùng được tóm tắt; không nhập đánh giá người dùng, HTML hoặc script từ website.

| Sản phẩm | Giá tham khảo | Trang nguồn | Ảnh đóng gói |
|---|---:|---|---|
| Gel bí đao rửa mặt 140 ml | 192.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/gel-bi-dao-rua-mat-140ml) | `product_cleanser.jpg` |
| Nước tẩy trang bí đao 500 ml | 299.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/nuoc-tay-trang-bi-dao-500ml) | `product_micellar.jpg` |
| Tinh chất bí đao N7 70 ml | 299.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/tinh-chat-bi-dao-70ml) | `product_serum.jpg` |
| Nước bí đao cân bằng da 310 ml | 299.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/nuoc-bi-dao-can-bang-da-310ml) | `product_toner.jpg` |
| Cà phê Đắk Lắk làm sạch da chết cơ thể 200 ml | 133.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/ca-phe-dak-lak-lam-sach-da-chet-co-the-200ml) | `product_body_scrub.jpg` |
| Dầu gội bưởi không sulfate 310 ml | 261.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/dau-goi-buoi-310ml) | `product_shampoo.jpg` |
| Cà phê Đắk Lắk làm sạch da chết môi 5 g | 74.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/ca-phe-dak-lak-lam-sach-da-chet-moi-5g) | `product_lip_scrub.jpg` |
| Son dưỡng dầu dừa Bến Tre 5 g | 32.000 ₫ | [Cocoon](https://www.cocoonvietnam.com/san-pham/son-duong-dau-dua-ben-tre-5g) | `product_lip_balm.jpg` |

Ảnh giữ nguyên bản tải từ CDN của thương hiệu, lưu ở `app/src/main/res/drawable-nodpi/` để ứng dụng dùng ngoại tuyến. Đường dẫn gốc:

- `product_cleanser.jpg`: https://image.cocoonvietnam.com/uploads/z4394607669965_ca1ceaa3a09cb9e3f966f4ac4256dd9a_1_f787014de5.jpg
- `product_micellar.jpg`: https://image.cocoonvietnam.com/uploads/Artboard_6_3ec256ca12.jpg
- `product_serum.jpg`: https://image.cocoonvietnam.com/uploads/gz_Ge_T_Yg_A_3a56f5461a_80dcc4a0fb.jpeg
- `product_toner.jpg`: https://image.cocoonvietnam.com/uploads/z3526520919826_f50cccbc71a7fb1b8ea680592d8528d4_e15f2eeefc.jpg
- `product_body_scrub.jpg`: https://image.cocoonvietnam.com/uploads/z4147355364575_e4b88c65711b8261d9c996e6797b60a1_83f203bec3.jpg
- `product_shampoo.jpg`: https://image.cocoonvietnam.com/uploads/z3955699562722_f08debe9657c26ce2a9a6333b3295060_1476261cc8.jpg
- `product_lip_scrub.jpg`: https://image.cocoonvietnam.com/uploads/CP_Ca_phe_moi_1a1b8ddbf1.jpg
- `product_lip_balm.jpg`: https://image.cocoonvietnam.com/uploads/Son_duong_dau_dua_26498c9936.jpg

Ảnh và thương hiệu thuộc chủ sở hữu tương ứng; việc tải về không chuyển quyền sở hữu hay tạo quan hệ đại diện thương hiệu. Bộ dữ liệu này phục vụ bản demo đồ án. Giá là ảnh chụp dữ liệu tại ngày thu thập, không đồng bộ trực tiếp với cửa hàng. Ứng dụng không lấy tồn kho, số bán, đánh giá hoặc ưu đãi từ website làm dữ liệu giao dịch.

Dữ liệu khởi tạo nằm trong `CatalogSeed.kt`, được ghi vào SQLite khi tạo database hoặc đi qua migration từ 1 lên 2. Schema hiện tại là version 5: version 3 thêm giỏ/giao hàng/tồn kho demo 30 đơn vị/sản phẩm; version 4 thêm đặt hàng COD cục bộ; version 5 thêm vai trò quản trị, revision sản phẩm và lịch sử trạng thái đơn. Tồn kho không lấy từ Cocoon, đơn demo không gửi tới Cocoon. Khi bổ sung/chỉnh dữ liệu seed trong bản tiếp theo, cần migration riêng để cập nhật database đã có mà không mất tài khoản, yêu thích, giỏ và lịch sử đơn.
