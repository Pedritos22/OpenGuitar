package com.openguitar.game;

import com.openguitar.beatmap.BeatmapLoader;
import com.openguitar.beatmap.SongContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Skaner katalogu z utworami. Dla każdego pliku audio sprawdza czy obok
 * istnieje już wygenerowana beatmapa (.json) i ładuje ją jeśli się da.
 */
public final class SongLibrary {

    private static final Logger LOG = Logger.getLogger(SongLibrary.class.getName());
    private static final Set<String> AUDIO_EXTS = Set.of("mp3", "wav", "aiff", "flac");

    private final Path songsDir;

    public SongLibrary(Path songsDir) {
        this.songsDir = songsDir;
    }

    /** Lista wpisów posortowana alfabetycznie po nazwie pliku (case-insensitive). */
    public List<SongEntry> scan() throws IOException {
        if (!Files.isDirectory(songsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(songsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SongLibrary::isAudio)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .map(SongLibrary::toEntry)
                    .toList();
        }
    }

    private static boolean isAudio(Path p) {
        String ext = extensionOf(p);
        return ext != null && AUDIO_EXTS.contains(ext);
    }

    private static SongEntry toEntry(Path audio) {
        Path json = withExtension(audio, "json");
        SongContext ctx = null;
        if (Files.isRegularFile(json)) {
            try {
                ctx = new BeatmapLoader().load(json);
            } catch (Exception ex) {
                // Nie wybuchamy całego skanowania jeśli jeden JSON jest popsuty -
                // pokażemy go jako "NO BEATMAP" i pozwolimy na regenerację.
                LOG.log(Level.WARNING, "Nie udało się wczytać beatmapy " + json, ex);
            }
        }
        String title = stripExtension(audio.getFileName().toString());
        return new SongEntry(audio, json, title, ctx);
    }

    /** "song.mp3" -> "song.json"; bez kropki. */
    private static Path withExtension(Path p, String newExt) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        return p.resolveSibling(base + "." + newExt);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private static String extensionOf(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase();
    }
}
