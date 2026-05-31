package com.openguitar.game;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trwały magazyn statystyk gracza oparty o wbudowaną bazę <b>SQLite</b>
 * (sterownik {@code org.xerial:sqlite-jdbc}, plik {@code stats.db}).
 *
 * <p>Dwie tabele:</p>
 * <ul>
 *   <li>{@code song_stats} — agregat per-utwór (rekordy: best score, max combo,
 *       best perfect/great, najlepsza celność i ranga, liczba podejść);</li>
 *   <li>{@code play_history} — po jednym wierszu na każde ukończone podejście
 *       (pełny ślad: wynik, trafienia, celność, ranga, znacznik czasu).</li>
 * </ul>
 *
 * <p>To warstwa <b>aplikacji</b>, a nie silnika gry — czyta gotowy
 * {@link GameResult} po zakończeniu utworu i nie ingeruje w logikę punktacji,
 * detekcję hitów ani DSP. Wszystkie metody są synchronizowane (jedna współdzielona
 * połączenie JDBC), co wystarcza dla wzorca „zapis po utworze, odczyt w menu”.</p>
 */
public final class StatsStore {

    private static final Logger LOG = Logger.getLogger(StatsStore.class.getName());

    /** Agregat statystyk pojedynczego utworu. */
    public static final class SongStat {
        public final int plays;
        public final int bestScore;
        public final int maxCombo;
        public final int bestHits;
        public final int bestPerfect;
        public final int bestGreat;
        public final double bestAccuracy;
        public final String bestRank;

        public SongStat(int plays, int bestScore, int maxCombo, int bestHits,
                        int bestPerfect, int bestGreat, double bestAccuracy, String bestRank) {
            this.plays = plays;
            this.bestScore = bestScore;
            this.maxCombo = maxCombo;
            this.bestHits = bestHits;
            this.bestPerfect = bestPerfect;
            this.bestGreat = bestGreat;
            this.bestAccuracy = bestAccuracy;
            this.bestRank = bestRank;
        }

        /** Najlepsza zdobyta ranga jako {@link Rank} (z bezpiecznym fallbackiem). */
        public Rank rank() {
            return parseRank(bestRank);
        }
    }

    /** Pojedyncze ukończone podejście (wiersz z {@code play_history}). */
    public static final class PlayRecord {
        public final int score;
        public final int hits;
        public final int misses;
        public final int perfect;
        public final int great;
        public final int maxCombo;
        public final double accuracy;
        public final String rank;
        public final long playedAt;

        public PlayRecord(int score, int hits, int misses, int perfect, int great,
                          int maxCombo, double accuracy, String rank, long playedAt) {
            this.score = score;
            this.hits = hits;
            this.misses = misses;
            this.perfect = perfect;
            this.great = great;
            this.maxCombo = maxCombo;
            this.accuracy = accuracy;
            this.rank = rank;
            this.playedAt = playedAt;
        }

        public Rank rank() {
            return parseRank(rank);
        }
    }

    private static Rank parseRank(String name) {
        try {
            return Rank.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Rank.E;
        }
    }

    private Connection conn;

    public StatsStore(Path file) {
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            initSchema();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Nie udało się otworzyć bazy statystyk " + file
                    + " — statystyki będą wyłączone.", ex);
            this.conn = null;
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS song_stats (
                        song_id       TEXT PRIMARY KEY,
                        plays         INTEGER NOT NULL DEFAULT 0,
                        best_score    INTEGER NOT NULL DEFAULT 0,
                        max_combo     INTEGER NOT NULL DEFAULT 0,
                        best_hits     INTEGER NOT NULL DEFAULT 0,
                        best_perfect  INTEGER NOT NULL DEFAULT 0,
                        best_great    INTEGER NOT NULL DEFAULT 0,
                        best_accuracy REAL    NOT NULL DEFAULT 0,
                        best_rank     TEXT    NOT NULL DEFAULT 'E',
                        updated_at    INTEGER NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS play_history (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        song_id   TEXT    NOT NULL,
                        score     INTEGER NOT NULL,
                        hits      INTEGER NOT NULL,
                        misses    INTEGER NOT NULL,
                        perfect   INTEGER NOT NULL,
                        great     INTEGER NOT NULL,
                        max_combo INTEGER NOT NULL,
                        accuracy  REAL    NOT NULL,
                        rank      TEXT    NOT NULL,
                        played_at INTEGER NOT NULL
                    )""");
            st.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_song ON play_history(song_id)");
        }
    }

    /** Rejestruje wynik utworu: dopisuje wiersz historii i aktualizuje agregat. */
    public synchronized void record(GameResult r) {
        if (conn == null || r == null || r.songId() == null || r.songId().isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Rank rank = r.rank();
        double acc = r.accuracy();
        try {
            insertHistory(r, rank, acc, now);
            upsertAggregate(r, rank, acc, now);
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Nie udało się zapisać statystyk dla " + r.songId(), ex);
        }
    }

    private void insertHistory(GameResult r, Rank rank, double acc, long now) throws SQLException {
        String sql = "INSERT INTO play_history"
                + "(song_id, score, hits, misses, perfect, great, max_combo, accuracy, rank, played_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.songId());
            ps.setInt(2, r.totalScore());
            ps.setInt(3, r.hits());
            ps.setInt(4, r.misses());
            ps.setInt(5, r.perfect());
            ps.setInt(6, r.great());
            ps.setInt(7, r.maxCombo());
            ps.setDouble(8, acc);
            ps.setString(9, rank.name());
            ps.setLong(10, now);
            ps.executeUpdate();
        }
    }

    private void upsertAggregate(GameResult r, Rank rank, double acc, long now) throws SQLException {
        SongStat prev = querySong(r.songId());
        int plays = (prev != null ? prev.plays : 0) + 1;
        int bestScore = Math.max(prev != null ? prev.bestScore : 0, r.totalScore());
        int maxCombo = Math.max(prev != null ? prev.maxCombo : 0, r.maxCombo());
        int bestHits = Math.max(prev != null ? prev.bestHits : 0, r.hits());
        int bestPerfect = Math.max(prev != null ? prev.bestPerfect : 0, r.perfect());
        int bestGreat = Math.max(prev != null ? prev.bestGreat : 0, r.great());
        double bestAcc = Math.max(prev != null ? prev.bestAccuracy : 0.0, acc);
        // Najlepsza ranga = ta o najniższym porządku (S=0 jest najlepsze).
        Rank bestRank = (prev != null && prev.rank().ordinal() < rank.ordinal())
                ? prev.rank() : rank;

        String sql = "INSERT INTO song_stats"
                + "(song_id, plays, best_score, max_combo, best_hits, best_perfect, best_great,"
                + " best_accuracy, best_rank, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)"
                + " ON CONFLICT(song_id) DO UPDATE SET"
                + " plays=excluded.plays, best_score=excluded.best_score,"
                + " max_combo=excluded.max_combo, best_hits=excluded.best_hits,"
                + " best_perfect=excluded.best_perfect, best_great=excluded.best_great,"
                + " best_accuracy=excluded.best_accuracy, best_rank=excluded.best_rank,"
                + " updated_at=excluded.updated_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.songId());
            ps.setInt(2, plays);
            ps.setInt(3, bestScore);
            ps.setInt(4, maxCombo);
            ps.setInt(5, bestHits);
            ps.setInt(6, bestPerfect);
            ps.setInt(7, bestGreat);
            ps.setDouble(8, bestAcc);
            ps.setString(9, bestRank.name());
            ps.setLong(10, now);
            ps.executeUpdate();
        }
    }

    public synchronized Optional<SongStat> forSong(String songId) {
        if (conn == null || songId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(querySong(songId));
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Odczyt statystyk nie powiódł się dla " + songId, ex);
            return Optional.empty();
        }
    }

    private SongStat querySong(String songId) throws SQLException {
        String sql = "SELECT plays, best_score, max_combo, best_hits, best_perfect,"
                + " best_great, best_accuracy, best_rank FROM song_stats WHERE song_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, songId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SongStat(
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getDouble(7), rs.getString(8));
            }
        }
    }

    /** Liczba podejść do danego utworu (0 jeśli nigdy nie grany). */
    public synchronized int playCount(String songId) {
        return forSong(songId).map(s -> s.plays).orElse(0);
    }

    /** Ostatnie podejścia do utworu (najnowsze najpierw), maksymalnie {@code limit}. */
    public synchronized List<PlayRecord> history(String songId, int limit) {
        List<PlayRecord> out = new ArrayList<>();
        if (conn == null || songId == null) {
            return out;
        }
        String sql = "SELECT score, hits, misses, perfect, great, max_combo, accuracy, rank, played_at"
                + " FROM play_history WHERE song_id=? ORDER BY played_at DESC, id DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, songId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PlayRecord(
                            rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                            rs.getInt(5), rs.getInt(6), rs.getDouble(7), rs.getString(8),
                            rs.getLong(9)));
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Odczyt historii nie powiódł się dla " + songId, ex);
        }
        return out;
    }

    // ── agregaty globalne ──────────────────────────────────────────────────────

    public synchronized int totalPlays() {
        return scalarInt("SELECT COALESCE(SUM(plays),0) FROM song_stats");
    }

    public synchronized int songsPlayed() {
        return scalarInt("SELECT COUNT(*) FROM song_stats WHERE plays > 0");
    }

    public synchronized int bestScoreOverall() {
        return scalarInt("SELECT COALESCE(MAX(best_score),0) FROM song_stats");
    }

    public synchronized int bestComboOverall() {
        return scalarInt("SELECT COALESCE(MAX(max_combo),0) FROM song_stats");
    }

    private int scalarInt(String sql) {
        if (conn == null) {
            return 0;
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Zapytanie agregujące nie powiodło się: " + sql, ex);
            return 0;
        }
    }

    /** Zamyka połączenie z bazą (wywołać przy zamykaniu aplikacji). */
    public synchronized void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ex) {
                LOG.log(Level.WARNING, "Błąd zamykania bazy statystyk", ex);
            }
            conn = null;
        }
    }
}
