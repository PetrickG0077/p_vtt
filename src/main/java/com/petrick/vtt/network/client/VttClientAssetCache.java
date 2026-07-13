package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public final class VttClientAssetCache {
    private static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_FILES = 4096;
    private static final Map<String, IncomingFile> INCOMING = new HashMap<>();
    private static int expectedFiles;
    private static int completedFiles;

    private VttClientAssetCache() {}

    public static void begin(int fileCount) {
        expectedFiles = Math.max(0, Math.min(fileCount, MAX_FILES));
        completedFiles = 0;
        INCOMING.clear();
        clearCache();
        VTT.LOGGER.info("Starting VTT server asset sync: {} files", expectedFiles);
    }

    public static void accept(VttAssetChunkPayload payload) {
        if (payload == null || payload.data() == null || payload.data().length > VttAssetChunkPayload.MAX_CHUNK_BYTES) return;
        if (!payload.category().equals("assets") && !payload.category().equals("tokens")) return;
        if (payload.chunkCount() <= 0 || payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.chunkCount()) return;

        String key = payload.category() + ":" + payload.relativePath();
        IncomingFile incoming = INCOMING.computeIfAbsent(key,
                ignored -> new IncomingFile(payload.sha256(), payload.chunkCount()));
        if (!incoming.accept(payload)) {
            INCOMING.remove(key);
            return;
        }
        if (!incoming.complete()) return;

        INCOMING.remove(key);
        byte[] bytes = incoming.bytes();
        if (!sha256(bytes).equalsIgnoreCase(payload.sha256())) {
            VTT.LOGGER.warn("Rejected VTT asset with invalid hash: {}", payload.relativePath());
            return;
        }
        try {
            Path target = safeTarget(payload.category(), payload.relativePath());
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            completedFiles++;
            VTT.LOGGER.info("Cached VTT server asset: {}/{}", payload.category(), payload.relativePath());
        } catch (IOException | IllegalArgumentException exception) {
            VTT.LOGGER.error("Failed to cache VTT server asset: {}", payload.relativePath(), exception);
        }
    }

    public static void finish() {
        INCOMING.clear();
        VTT.LOGGER.info("Finished VTT server asset sync: {}/{} files", completedFiles, expectedFiles);
        VTT.getApplication().getActiveSession().reloadSyncedServerAssets();
    }

    private static Path cacheRoot() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/vtt_assets/cache/server").toAbsolutePath().normalize();
    }

    private static Path safeTarget(String category, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid asset path");
        }
        Path categoryRoot = cacheRoot().resolve(category).normalize();
        Path target = categoryRoot.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(categoryRoot)) throw new IllegalArgumentException("Asset path escapes cache");
        return target;
    }

    private static void clearCache() {
        Path root = cacheRoot();
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to clear old VTT server asset cache", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class IncomingFile {
        private final String sha256;
        private final int chunkCount;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int nextChunk;

        private IncomingFile(String sha256, int chunkCount) {
            this.sha256 = sha256;
            this.chunkCount = chunkCount;
        }

        private boolean accept(VttAssetChunkPayload payload) {
            if (!sha256.equals(payload.sha256()) || chunkCount != payload.chunkCount()
                    || payload.chunkIndex() != nextChunk || output.size() + payload.data().length > MAX_FILE_BYTES) return false;
            output.writeBytes(payload.data());
            nextChunk++;
            return true;
        }

        private boolean complete() { return nextChunk == chunkCount; }
        private byte[] bytes() { return output.toByteArray(); }
    }
}
