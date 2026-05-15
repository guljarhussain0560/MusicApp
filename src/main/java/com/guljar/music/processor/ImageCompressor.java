package com.guljar.music.processor;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Base64;

/**
 * Compresses and resizes uploaded images before sending to Groq Vision API.
 * Groq has a 4MB limit for base64-encoded images, so this ensures
 * we stay well under that threshold while preserving enough detail
 * for accurate mood/scene analysis.
 */
@Component
public class ImageCompressor {

    /** Target max base64 size ~ 3MB (leaving headroom under 4MB Groq limit) */
    private static final int MAX_BASE64_BYTES = 3 * 1024 * 1024;

    /** Max width for resizing — enough for scene/mood recognition */
    private static final int TARGET_WIDTH = 512;

    /**
     * Compress a MultipartFile and return the base64-encoded JPEG string.
     * This is the main entry point used by ImageProcessor.
     */
    public String compressToBase64(MultipartFile file) throws IOException {
        byte[] compressedBytes = compressImage(file, MAX_BASE64_BYTES);
        return Base64.getEncoder().encodeToString(compressedBytes);
    }

    /**
     * Detect the MIME type for the data URI prefix (e.g., "image/jpeg").
     */
    public String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && (contentType.contains("png") || contentType.contains("PNG"))) {
            return "image/png";
        }
        return "image/jpeg";
    }

    public static byte[] compressImage(MultipartFile file, int targetSizeBytes) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());

        if (image == null) {
            throw new IOException("Invalid image file");
        }

        // Resize width to reduce dimensions — adjust down if needed
        int targetWidth = TARGET_WIDTH;
        int targetHeight = (int) ((double) image.getHeight() / image.getWidth() * targetWidth);
        Image resizedImage = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(resizedImage, 0, 0, null);
        g2d.dispose();

        float quality = 0.7f; // Start with good quality
        byte[] imageBytes = null;

        // Try compressing until the byte size is under target
        for (; quality >= 0.1f; quality -= 0.05f) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();

            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            writer.setOutput(ImageIO.createImageOutputStream(byteArrayOutputStream));
            writer.write(null, new IIOImage(outputImage, null, null), param);
            writer.dispose();

            imageBytes = byteArrayOutputStream.toByteArray();

            // Approximate base64 size: 4/3 of original byte length
            int base64Size = (int) (imageBytes.length * 1.37);
            if (base64Size <= targetSizeBytes) {
                break;
            }
        }

        return imageBytes;
    }
}

