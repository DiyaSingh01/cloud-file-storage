package com.filestorage.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtil {

    private FileUtil() {}

    /**
     * Format file size in human-readable form.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Compute MD5 hex checksum from byte array.
     */
    public static String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /**
     * Build a unique S3 key for a file.
     * Format: owner/yyyy/MM/uuid_filename
     */
    public static String buildS3Key(String owner, String originalFileName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sanitized = originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return owner + "/" + timestamp + "_" + sanitized;
    }

    /**
     * Extract file extension from a filename.
     */
    public static String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
