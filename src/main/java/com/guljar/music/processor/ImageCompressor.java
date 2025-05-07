package com.guljar.music.processor;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.IIOImage;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Base64;

public class ImageCompressor {

    public static byte[] compressImage(MultipartFile file, int targetSizeBytes) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());

        if (image == null) {
            throw new IOException("Invalid image file");
        }

        // Resize width to reduce dimensions — adjust down if needed
        int targetWidth = 400; // A good starting point
        int targetHeight = (int) ((double) image.getHeight() / image.getWidth() * targetWidth);
        Image resizedImage = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(resizedImage, 0, 0, null);
        g2d.dispose();

        float quality = 0.5f; // Start with medium compression
        byte[] imageBytes = null;

        // Try compressing until the byte size is under target (750KB)
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


    // Example usage for a MultipartFile (for Spring controllers)
    public static String handleMultipartFile(MultipartFile file) throws IOException {
        // Compress the image to 400px width (while maintaining aspect ratio)
        byte[] compressedImageBytes = compressImage(file, 400);

        // Convert the compressed image bytes to Base64 string
        return Base64.getEncoder().encodeToString(compressedImageBytes);
    }
}
