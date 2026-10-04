package com.filestorage;

import com.filestorage.util.FileUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {

    @Test
    @DisplayName("Should format bytes correctly")
    void formatFileSize_Bytes() {
        assertEquals("512 B", FileUtil.formatFileSize(512));
    }

    @Test
    @DisplayName("Should format kilobytes correctly")
    void formatFileSize_Kilobytes() {
        assertEquals("1.00 KB", FileUtil.formatFileSize(1024));
    }

    @Test
    @DisplayName("Should format megabytes correctly")
    void formatFileSize_Megabytes() {
        assertEquals("10.00 MB", FileUtil.formatFileSize(10 * 1024 * 1024));
    }

    @Test
    @DisplayName("Should format gigabytes correctly")
    void formatFileSize_Gigabytes() {
        assertEquals("1.00 GB", FileUtil.formatFileSize(1024L * 1024 * 1024));
    }

    @Test
    @DisplayName("Should compute MD5 checksum")
    void computeMd5_ShouldReturn32HexChars() {
        String checksum = FileUtil.computeMd5("hello world".getBytes());
        assertNotNull(checksum);
        assertEquals(32, checksum.length());
        assertTrue(checksum.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("Same content should always produce same MD5")
    void computeMd5_Deterministic() {
        byte[] content = "test content".getBytes();
        assertEquals(FileUtil.computeMd5(content), FileUtil.computeMd5(content));
    }

    @Test
    @DisplayName("Should build S3 key with owner prefix")
    void buildS3Key_ShouldContainOwner() {
        String key = FileUtil.buildS3Key("john", "report.pdf");
        assertTrue(key.startsWith("john/"));
        assertTrue(key.endsWith("report.pdf"));
    }

    @Test
    @DisplayName("Should sanitize special characters in filename")
    void buildS3Key_ShouldSanitizeFilename() {
        String key = FileUtil.buildS3Key("alice", "my file (1).jpg");
        assertFalse(key.contains(" "));
        assertFalse(key.contains("("));
        assertFalse(key.contains(")"));
    }

    @Test
    @DisplayName("Should extract file extension")
    void getExtension_ShouldReturnExtension() {
        assertEquals("pdf", FileUtil.getExtension("document.pdf"));
        assertEquals("jpg", FileUtil.getExtension("photo.JPG"));
        assertEquals("", FileUtil.getExtension("noextension"));
    }
}
