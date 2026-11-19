package daiso.product.controller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.servlet.http.Part;

final class ReviewPhotoUpload {
    private static final long MAX_BATCH = 5 * 1024 * 1024;
    private static final long MAX_INPUT = 2 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private static final int MAX_SIDE = 1600;

    static List<byte[]> read(Collection<Part> parts, int maximum) throws IOException {
        List<Part> photos = new ArrayList<>();
        long bytes = 0;
        for (Part part : parts) {
            if (!"photos".equals(part.getName()) || part.getSubmittedFileName() == null
                    || part.getSubmittedFileName().isEmpty()) continue;
            bytes += part.getSize();
            if (part.getSize() < 1 || part.getSize() > MAX_INPUT || bytes > MAX_BATCH || photos.size() >= maximum) {
                throw new IllegalArgumentException("사진은 최대 10장, 장당 2MB, 한 번에 총 5MB까지 첨부할 수 있습니다.");
            }
            photos.add(part);
        }
        List<byte[]> images = new ArrayList<>(photos.size());
        for (Part photo : photos) images.add(normalize(photo));
        return images;
    }

    private static byte[] normalize(Part part) throws IOException {
        try (InputStream raw = part.getInputStream(); MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(raw)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!"JPEG".equals(format) && !"PNG".equals(format)) throw invalid();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) throw invalid();
                int scale = (Math.max(width, height) + MAX_SIDE - 1) / MAX_SIDE;
                ImageReadParam options = reader.getDefaultReadParam();
                options.setSourceSubsampling(scale, scale, 0, 0);
                BufferedImage decoded = reader.read(0, options);
                BufferedImage rgb = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(rgb, "jpeg", output)) throw invalid();
                    return output.toByteArray();
                } finally {
                    rgb.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException malformed) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("정상적인 JPG 또는 PNG 사진을 첨부해 주세요. 사진당 최대 1,600만 화소입니다.");
    }
}
