package com.openguitar.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Kopiuje pliki audio do katalogu {@code songs/}. Używany przy drag-and-drop w menu.
 */
public final class SongImporter {

    private static final Set<String> AUDIO_EXTS = Set.of("mp3", "wav", "aiff", "flac");

    private SongImporter() {
    }

    public record ImportResult(int imported, int skipped, List<String> errors) {
        public boolean hasImported() {
            return imported > 0;
        }
    }

    public static ImportResult importAudioFiles(Path songsDir, Collection<Path> sources) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        try {
            Files.createDirectories(songsDir);
        } catch (IOException ex) {
            errors.add(ex.getMessage());
            return new ImportResult(0, sources.size(), errors);
        }
        for (Path src : sources) {
            if (!Files.isRegularFile(src)) {
                skipped++;
                continue;
            }
            if (!isAudio(src)) {
                skipped++;
                continue;
            }
            Path dest = songsDir.resolve(src.getFileName());
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                imported++;
            } catch (IOException ex) {
                errors.add(src.getFileName() + ": " + ex.getMessage());
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    public static boolean isAudio(Path path) {
        String ext = extensionOf(path);
        return ext != null && AUDIO_EXTS.contains(ext);
    }

    public static boolean containsSupportedAudio(Collection<Path> paths) {
        for (Path p : paths) {
            if (Files.isRegularFile(p) && isAudio(p)) {
                return true;
            }
        }
        return false;
    }

    private static String extensionOf(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }
}
