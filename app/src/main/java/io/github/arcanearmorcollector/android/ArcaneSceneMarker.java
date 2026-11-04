package io.github.arcanearmorcollector.android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Adds host-only scene notifications to Arcane's local SWF in memory.
 *
 * The user's original SWF remains unmodified and git-ignored. It is NOT
 * embedded in source control or distributed by this file. Root timeline
 * frame 20 is the arena; the other root frames are menus, preparation or
 * transitions. These are ActionGetURL "FSCommand:" events read by Ruffle's
 * onFSCommand callback. If patching fails, use the unmodified game.
 */
final class ArcaneSceneMarker {
    private static final byte[] COMMAND =
        "FSCommand:arcanePhase".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static final byte[] FRAME_COMMAND =
        "FSCommand:arcaneFrame".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private ArcaneSceneMarker() {}

    static byte[] patch(InputStream source) throws IOException {
        ByteArrayOutputStream rawBuffer = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int read;
        while ((read = source.read(block)) != -1) rawBuffer.write(block, 0, read);
        byte[] original = rawBuffer.toByteArray();
        if (original.length < 20 || original[1] != 'W' || original[2] != 'S' ||
            (original[0] != 'C' && original[0] != 'F')) return original;

        try {
            ByteArrayOutputStream plainBuffer = new ByteArrayOutputStream();
            if (original[0] == 'C') {
                plainBuffer.write('F');
                plainBuffer.write(original, 1, 7);
                try (InflaterInputStream inflate = new InflaterInputStream(
                        new ByteArrayInputStream(original, 8, original.length - 8))) {
                    while ((read = inflate.read(block)) != -1)
                        plainBuffer.write(block, 0, read);
                }
            } else {
                plainBuffer.write(original);
            }
            byte[] plain = plainBuffer.toByteArray();
            if (contains(plain, COMMAND)) return original; // idempotent
            int bitCount = (plain[8] & 0xff) >>> 3;
            int frameHeader = 8 + (5 + 4 * bitCount + 7) / 8 + 4;
            if (frameHeader >= plain.length) return original;

            ByteArrayOutputStream patched = new ByteArrayOutputStream(plain.length + 1024);
            patched.write(plain, 0, frameHeader);
            int pos = frameHeader;
            int frame = 1, notified = 0;
            boolean markedFrame = false;
            while (pos + 2 <= plain.length) {
                int start = pos;
                int record = ushort(plain, pos);
                pos += 2;
                int tag = record >>> 6;
                long length = record & 63;
                if (length == 63) {
                    if (pos + 4 > plain.length) return original;
                    length = uint(plain, pos);
                    pos += 4;
                }
                if (length > Integer.MAX_VALUE || pos + length > plain.length)
                    return original;
                // Put FSCommands BEFORE original DoAction. On some scenes,
                // the game's first DoAction transfers control to a different
                // frame, so a marker placed after it never executes.
                if (tag == 12 && !markedFrame) {
                    patched.write(marker(COMMAND, frame == 20 ? "arena" : "menu"));
                    patched.write(marker(FRAME_COMMAND, Integer.toString(frame)));
                    markedFrame = true;
                    notified++;
                }
                if (tag == 1) { // ShowFrame
                    if (!markedFrame) {
                        patched.write(marker(COMMAND, frame == 20 ? "arena" : "menu"));
                        patched.write(marker(FRAME_COMMAND, Integer.toString(frame)));
                        notified++;
                    }
                    markedFrame = false;
                    frame++;
                }
                int end = pos + (int) length;
                patched.write(plain, start, end - start);
                pos = end;
                if (tag == 0) break;
            }
            if (notified != 25) return original; // game-specific safety check
            byte[] patchedPlain = patched.toByteArray();
            writeUint(patchedPlain, 4, patchedPlain.length);

            if (original[0] == 'F') return patchedPlain;
            ByteArrayOutputStream compressed = new ByteArrayOutputStream(original.length + 1024);
            compressed.write('C');
            compressed.write(patchedPlain, 1, 7);
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(compressed)) {
                deflate.write(patchedPlain, 8, patchedPlain.length - 8);
            }
            return compressed.toByteArray();
        } catch (RuntimeException | IOException error) {
            return original; // never prevent the user's original game from loading
        }
    }

    private static byte[] marker(byte[] command, String phase) throws IOException {
        // AVM1 ActionGetURL("FSCommand:arcanePhase/arcaneFrame", value), ActionEnd.
        ByteArrayOutputStream action = new ByteArrayOutputStream();
        int payloadLength = command.length + 1 + phase.length() + 1;
        action.write(0x83);
        action.write(payloadLength & 255);
        action.write(payloadLength >>> 8);
        action.write(command);
        action.write(0);
        action.write(phase.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        action.write(0);
        action.write(0);
        byte[] bytes = action.toByteArray();
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        int header = (12 << 6) | bytes.length;
        tag.write(header & 255);
        tag.write(header >>> 8);
        tag.write(bytes);
        return tag.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (haystack[i+j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private static int ushort(byte[] b, int index) {
        return (b[index] & 255) | ((b[index+1] & 255) << 8);
    }

    private static long uint(byte[] b, int index) {
        return (long) (b[index] & 255) |
            ((long) (b[index+1] & 255) << 8) |
            ((long) (b[index+2] & 255) << 16) |
            ((long) (b[index+3] & 255) << 24);
    }

    private static void writeUint(byte[] b, int index, long value) {
        for (int i = 0; i < 4; i++) b[index+i] = (byte) (value >>> (8*i));
    }
}
