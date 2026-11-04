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
 * arena scene scripts are on root frames 3-5 and 11-18, not just frame 20.
 * Frame markers use ActionGetURL FSCommands read by Ruffle. Also registers
 * a periodic flush on the game's existing _root.savefile SharedObject, which
 * otherwise may not persist its updated data in a WebView force-stop.
 * If patching fails, use the unmodified game.
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
                    patched.write(marker(COMMAND, isArenaFrame(frame) ? "arena" : "menu"));
                    patched.write(marker(FRAME_COMMAND, Integer.toString(frame)));
                    markedFrame = true;
                    notified++;
                }
                if (tag == 1) { // ShowFrame
                    // The frame-1 script has already initialized
                    // _root.savefile = SharedObject.getLocal("Arcane8").
                    // Schedule a flush without replacing or modifying the
                    // game's original save data or calling it on each frame.
                    if (frame == 1) patched.write(saveFlushTimer());
                    // Flush again whenever a main timeline scene changes.
                    // Arcane updates its savefile.data in several menu and
                    // result frames without explicitly calling flush().
                    patched.write(saveFlushOnce());
                    if (!markedFrame) {
                        patched.write(marker(COMMAND, isArenaFrame(frame) ? "arena" : "menu"));
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

    private static boolean isArenaFrame(int frame) {
        // Root scripts on these frames call the game's startall gameplay
        // setup; frame 20 contains inter-level ad code, not arena combat.
        return (frame >= 3 && frame <= 5) || (frame >= 11 && frame <= 18);
    }

    private static void pushString(ByteArrayOutputStream action, String value)
            throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int length = bytes.length + 2; // type 0 + bytes + null terminator
        action.write(0x96);
        action.write(length & 255);
        action.write((length >>> 8) & 255);
        action.write(0);
        action.write(bytes);
        action.write(0);
    }

    private static void pushInt(ByteArrayOutputStream action, int value)
            throws IOException {
        action.write(0x96);
        action.write(5);
        action.write(0);
        action.write(7); // ActionPush integer
        for (int i = 0; i < 4; i++) action.write((value >>> (8 * i)) & 255);
    }

    private static byte[] saveFlushOnce() throws IOException {
        // ActionScript: _root.savefile.flush();
        ByteArrayOutputStream action = new ByteArrayOutputStream();
        pushInt(action, 0); // no arguments
        pushString(action, "_root");
        action.write(0x1c); // GetVariable
        pushString(action, "savefile");
        action.write(0x4e); // GetMember
        pushString(action, "flush");
        action.write(0x52); // CallMethod
        action.write(0x17); // Pop return value
        action.write(0);    // End
        return doActionTag(action.toByteArray());
    }

    private static byte[] doActionTag(byte[] code) throws IOException {
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        int header = (12 << 6) | Math.min(code.length, 63);
        tag.write(header & 255);
        tag.write((header >>> 8) & 255);
        if (code.length >= 63) {
            for (int i = 0; i < 4; i++)
                tag.write((code.length >>> (8 * i)) & 255);
        }
        tag.write(code);
        return tag.toByteArray();
    }

    private static byte[] saveFlushTimer() throws IOException {
        // ActionScript 2:
        //   setInterval(_root.savefile, "flush", 5000);
        // ActionCallFunction takes the arguments in reverse push order.
        // It calls the game's existing SharedObject method rather than
        // recreating the object or rewriting its data from JavaScript.
        ByteArrayOutputStream action = new ByteArrayOutputStream();
        pushInt(action, 5000);
        pushString(action, "flush");
        pushString(action, "_root");
        action.write(0x1c); // GetVariable
        pushString(action, "savefile");
        action.write(0x4e); // GetMember
        pushInt(action, 3);
        pushString(action, "setInterval");
        action.write(0x3d); // CallFunction
        action.write(0x17); // Pop timer id
        action.write(0);    // End
        return doActionTag(action.toByteArray());
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
