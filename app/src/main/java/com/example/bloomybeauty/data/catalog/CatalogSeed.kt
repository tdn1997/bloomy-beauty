package com.example.bloomybeauty.data.catalog

// Curated from official product pages on 2026-10-07. Provenance: docs/product-sources.md.
object CatalogSeed {
    val categories = listOf(
        Category("skin", "Chăm sóc da"), Category("body", "Cơ thể"),
        Category("hair", "Chăm sóc tóc"), Category("lips", "Dưỡng môi"),
    )

    private const val SOURCE = "https://www.cocoonvietnam.com/san-pham/"
    val products = listOf(
        Product(
            "cleanser", "Gel bí đao rửa mặt", "skin", "140 ml", 192_000, "product_cleanser",
            "Gel làm sạch bụi bẩn và dầu thừa, dành cho da dầu và da hỗn hợp thiên dầu.",
            "Bí đao, rau má, tràm trà, BHA, vitamin B3, B5 và betaine.",
            "Massage một lượng vừa đủ trên da ướt, sau đó rửa lại với nước. Tránh vùng mắt.",
            SOURCE + "gel-bi-dao-rua-mat-140ml",
        ),
        Product(
            "micellar", "Nước tẩy trang bí đao", "skin", "500 ml", 299_000, "product_micellar",
            "Nước tẩy trang công nghệ micellar giúp làm sạch lớp trang điểm, bụi bẩn và dầu thừa.",
            "Bí đao, rau má, tinh dầu tràm trà và NatraGem S150.",
            "Làm ướt bông tẩy trang rồi lau nhẹ trên mặt để lấy đi lớp trang điểm và bụi bẩn.",
            SOURCE + "nuoc-tay-trang-bi-dao-500ml",
        ),
        Product(
            "serum", "Tinh chất bí đao N7", "skin", "70 ml", 299_000, "product_serum",
            "Tinh chất chăm sóc da dầu với niacinamide, hỗ trợ dưỡng ẩm và cải thiện bề mặt da.",
            "Bí đao, 7% niacinamide, rau má và tinh dầu tràm trà.",
            "Thoa lượng vừa đủ lên da sạch và massage nhẹ. Tham khảo hướng dẫn trên bao bì trước khi dùng.",
            SOURCE + "tinh-chat-bi-dao-70ml",
        ),
        Product(
            "toner", "Nước bí đao cân bằng da", "skin", "310 ml", 299_000, "product_toner",
            "Nước cân bằng da không chứa cồn, bổ sung các thành phần dưỡng ẩm cho bước chăm sóc sau rửa mặt.",
            "Bí đao, rau má, tràm trà, vitamin B3, HA và cam thảo.",
            "Dùng trên da sạch sau bước rửa mặt, thoa nhẹ nhàng để sản phẩm thấm đều.",
            SOURCE + "nuoc-bi-dao-can-bang-da-310ml",
        ),
        Product(
            "body_scrub", "Cà phê Đắk Lắk làm sạch da chết cơ thể", "body", "200 ml", 133_000, "product_body_scrub",
            "Sản phẩm làm sạch tế bào chết cơ thể từ hạt cà phê xay và bơ ca cao, giúp da mềm mại.",
            "Hạt cà phê Đắk Lắk và bơ ca cao Tiền Giang.",
            "Massage trên da cơ thể đã làm ướt rồi tắm sạch. Tránh vùng mắt; dùng 2–3 lần mỗi tuần theo hướng dẫn hãng.",
            SOURCE + "ca-phe-dak-lak-lam-sach-da-chet-co-the-200ml",
        ),
        Product(
            "shampoo", "Dầu gội bưởi không sulfate", "hair", "310 ml", 261_000, "product_shampoo",
            "Dầu gội làm sạch tóc với công thức không sulfate và các thành phần dưỡng ẩm.",
            "Tinh dầu bưởi, vitamin B5, Xylishine và axit amin.",
            "Tạo bọt trên tóc ướt, massage nhẹ từ gốc đến ngọn rồi xả sạch với nước. Tránh tiếp xúc với mắt.",
            SOURCE + "dau-goi-buoi-310ml",
        ),
        Product(
            "lip_scrub", "Cà phê Đắk Lắk làm sạch da chết môi", "lips", "5 g", 74_000, "product_lip_scrub",
            "Sản phẩm dạng thỏi với hạt cà phê mịn, hỗ trợ lấy đi da chết và làm mềm môi.",
            "Cà phê xay mịn, dầu mắc-ca và bơ hạt mỡ.",
            "Làm ẩm môi, thoa nhẹ qua lại khoảng 30 giây rồi lau sạch bằng khăn mềm.",
            SOURCE + "ca-phe-dak-lak-lam-sach-da-chet-moi-5g",
        ),
        Product(
            "lip_balm", "Son dưỡng dầu dừa Bến Tre", "lips", "5 g", 32_000, "product_lip_balm",
            "Son dưỡng hỗ trợ giữ môi mềm mại với dầu dừa, bơ hạt mỡ và vitamin E.",
            "Dầu dừa Bến Tre, bơ hạt mỡ và vitamin E.",
            "Thoa trực tiếp lên môi; có thể dùng trước son màu để bổ sung độ ẩm.",
            SOURCE + "son-duong-dau-dua-ben-tre-5g",
        ),
    )
}
