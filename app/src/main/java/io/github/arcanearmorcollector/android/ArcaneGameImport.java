package io.github.arcanearmorcollector.android;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Private, user-supplied game file. Never packages or downloads copyrighted SWF data.
 * The picker grants read access to one selected document; no storage permission needed.
 */
final class ArcaneGameImport {
    private static final String FILE_NAME = "arcane.swf";
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 128L * 1024 * 1024;

    private ArcaneGameImport() {}

    static File installedFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    static boolean hasImportedGame(Context context) {
        File file = installedFile(context);
        if (!file.isFile() || file.length() < 20 || file.length() > MAX_FILE_BYTES)
            return false;
        try (InputStream input = new FileInputStream(file)) {
            return validHeader(input, file.length());
        } catch (IOException ignored) {
            return false;
        }
    }

    static void importDocument(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("No file selected.");
        File target = installedFile(context);
        File staging = File.createTempFile("arcane-import-", ".tmp", context.getFilesDir());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(staging)) {
                if (input == null) throw new IOException("Cannot open the selected file.");
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_FILE_BYTES)
                        throw new IOException("The selected file is too large.");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (staging.length() < 20)
                throw new IOException("This is not a valid Flash SWF file.");
            try (InputStream check = new FileInputStream(staging)) {
                if (!validHeader(check, staging.length()))
                    throw new IOException("Please select your original Arcane .swf game file.");
            }
            try {
                Files.move(staging.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(staging.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (staging.exists()) staging.delete();
        }
    }

    private static boolean validHeader(InputStream input, long size) throws IOException {
        byte[] header = new byte[8];
        int filled = 0;
        while (filled < header.length) {
            int count = input.read(header, filled, header.length - filled);
            if (count < 0) return false;
            filled += count;
        }
        if ((header[0] != 'F' && header[0] != 'C') ||
            header[1] != 'W' || header[2] != 'S') return false;
        int version = header[3] & 0xFF;
        long uncompressed = ((long) header[4] & 0xFF) |
            (((long) header[5] & 0xFF) << 8) |
            (((long) header[6] & 0xFF) << 16) |
            (((long) header[7] & 0xFF) << 24);
        return version >= 1 && version <= 40 &&
            uncompressed >= 20 && uncompressed <= MAX_UNCOMPRESSED_BYTES &&
            (header[0] != 'F' || uncompressed == size);
    }
}
