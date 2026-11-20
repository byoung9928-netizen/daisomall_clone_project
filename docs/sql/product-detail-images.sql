CREATE TABLE product_detail_images (
    image_id BIGINT NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(100) NOT NULL,
    display_order TINYINT UNSIGNED NOT NULL,
    image_path VARCHAR(100) NOT NULL,
    PRIMARY KEY (image_id),
    UNIQUE KEY uq_product_detail_image_order (product_id, display_order),
    CONSTRAINT fk_product_detail_images_product FOREIGN KEY (product_id)
        REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT chk_product_detail_image_order CHECK (display_order BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
