package daiso.product.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.ServletContext;
import javax.servlet.http.Part;

public final class ProductImageStorage {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private final Path directory;

    // 웹앱 배포 경로와 분리된 상품 사진 저장 위치
    public ProductImageStorage(ServletContext context) {
        String configured = context.getInitParameter("productImageDirectory");
        directory = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), "daisomall-uploads", "products")
                : Path.of(configured);
    }

    // 실제 이미지 형식과 크기를 확인하고 새 파일명으로 저장
    public String save(Part part) throws IOException {
        if (part.getSize() <= 0 || part.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("사진은 5MB 이하의 JPG 또는 PNG 파일을 선택해 주세요.");
        }
        BufferedImage image;
        String extension;
        try (InputStream input = part.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new IllegalArgumentException("사진 파일을 읽을 수 없습니다.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("JPG 또는 PNG 사진 파일을 선택해 주세요.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) {
                    throw new IllegalArgumentException("JPG 또는 PNG 사진 파일을 선택해 주세요.");
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IllegalArgumentException("사진의 가로와 세로는 각각 4,096픽셀 이하여야 합니다.");
                }
                image = reader.read(0);
                extension = "PNG".equalsIgnoreCase(format) ? "png" : "jpg";
            } finally {
                reader.dispose();
            }
        }

        Files.createDirectories(directory);
        String filename = UUID.randomUUID() + "." + extension;
        Path target = directory.resolve(filename);
        Path pending = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            if (!ImageIO.write(image, extension, pending.toFile())) throw new IOException("사진을 저장할 수 없습니다.");
            Files.move(pending, target);
            return "product-images/" + filename;
        } finally {
            Files.deleteIfExists(pending);
            image.flush();
        }
    }

    // 업로드 경로의 파일명만 허용해 저장 폴더 밖 접근 방지
    public static boolean isImagePath(String value) {
        return value != null && value.matches("product-images/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.(?:jpg|png)");
    }

    public Path resolve(String imagePath) {
        if (!isImagePath(imagePath)) throw new IllegalArgumentException("잘못된 상품 이미지 경로입니다.");
        return directory.resolve(imagePath.substring("product-images/".length()));
    }

    // DB 저장에 실패한 신규 업로드만 정리
    public void discard(String imagePath) throws IOException {
        Files.deleteIfExists(resolve(imagePath));
    }
}
