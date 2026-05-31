package com.openguitar.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trwały magazyn statystyk gracza (per-utwór: liczba podejść, najlepszy wynik,
 * najwyższe combo, najwięcej trafień). Zapisywany do prostego pliku JSON.
 *
 * <p>To warstwa <b>aplikacji</b>, a nie silnika gry — czyta gotowy
 * {@link GameResult} po zakończeniu utworu i nie ingeruje w logikę punktacji,
 * detekcję hitów ani DSP.</p>
 */
public final class StatsStore {

    private static final Logger LOG = Logger.getLogger(StatsStore.class.getName());

    /** Statystyka pojedynczego utworu. Pola publiczne → prosta (de)serializacja Jacksona. */
    public static final class SongStat {
        public int plays;
        public int bestScore;
        public int maxCombo;
        public int bestHits;

        public SongStat() {}

        public SongStat(int plays, int bestScore, int maxCombo, int bestHits) {
            this.plays = plays;
            this.bestScore = bestScore;
            this.maxCombo = maxCombo;
            this.bestHits = bestHits;
        }
    }

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, SongStat> stats;

    public StatsStore(Path file) {
        this.file = file;
        this.stats = load();
    }

    /** Rejestruje wynik utworu (aktualizuje rekordy) i zapisuje na dysk. */
    public synchronized void record(GameResult r) {
        if (r == null || r.songId() == null || r.songId().isBlank()) {
            return;
        }
        SongStat prev = stats.get(r.songId());
        SongStat next = new SongStat(
                (prev != null ? prev.plays : 0) + 1,
                Math.max(prev != null ? prev.bestScore : 0, r.totalScore()),
                Math.max(prev != null ? prev.maxCombo : 0, r.maxCombo()),
                Math.max(prev != null ? prev.bestHits : 0, r.hits()));
        stats.put(r.songId(), next);
        save();
    }

    public synchronized Optional<SongStat> forSong(String songId) {
        return Optional.ofNullable(songId == null ? null : stats.get(songId));
    }

    public synchronized int totalPlays() {
        return stats.values().stream().mapToInt(s -> s.plays).sum();
    }

    public synchronized int songsPlayed() {
        return (int) stats.values().stream().filter(s -> s.plays > 0).count();
    }

    public synchronized int bestScoreOverall() {
        return stats.values().stream().mapToInt(s -> s.bestScore).max().orElse(0);
    }

    public synchronized int bestComboOverall() {
        return stats.values().stream().mapToInt(s -> s.maxCombo).max().orElse(0);
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private Map<String, SongStat> load() {
        try {
            if (Files.isRegularFile(file)) {
                return mapper.readValue(file.toFile(), new TypeReference<HashMap<String, SongStat>>() {});
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się wczytać statystyk z " + file + " — start z pustymi.", ex);
        }
        return new HashMap<>();
    }

    private void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            mapper.writeValue(file.toFile(), stats);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się zapisać statystyk do " + file, ex);
        }
    }
}
