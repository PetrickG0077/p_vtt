package com.petrick.vtt.feature.asset.library;

import java.util.Collections;
import java.util.List;

/**
 * Resultado de um scan da biblioteca de assets.
 */
public record AssetLibraryScanResult(
        List<AssetLibraryEntry> entries
) {

    public AssetLibraryScanResult {
        if (entries == null) {
            entries = List.of();
        }

        entries = Collections.unmodifiableList(entries);
    }

    public int totalCount() {
        return entries.size();
    }

    public long imageCount() {
        return entries.stream()
                .filter(entry -> entry.fileType() == AssetLibraryFileType.IMAGE)
                .count();
    }

    public long documentCount() {
        return entries.stream()
                .filter(entry -> entry.fileType() == AssetLibraryFileType.DOCUMENT)
                .count();
    }

    public long unknownCount() {
        return entries.stream()
                .filter(entry -> entry.fileType() == AssetLibraryFileType.UNKNOWN)
                .count();
    }

    public long animatedImageCount() {
        return entries.stream()
                .filter(entry -> entry.fileType() == AssetLibraryFileType.ANIMATED_IMAGE)
                .count();
    }
}