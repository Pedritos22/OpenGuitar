package com.openguitar.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRecordAggregateAndHistory() {
        Path db = tempDir.resolve("stats.db");
        StatsStore store = new StatsStore(db);
        try {
            GameResult first = new GameResult("song-1", 9000, 30, 2, 24, 6, 30);
            GameResult second = new GameResult("song-1", 12000, 40, 0, 34, 6, 40);
            GameResult other = new GameResult("song-2", 5000, 12, 3, 8, 4, 12);

            store.record(first);
            store.record(second);
            store.record(other);

            assertEquals(2, store.playCount("song-1"));
            assertEquals(1, store.playCount("song-2"));
            assertEquals(3, store.totalPlays());
            assertEquals(2, store.songsPlayed());
            assertEquals(12000, store.bestScoreOverall());
            assertEquals(40, store.bestComboOverall());

            StatsStore.SongStat stat = store.forSong("song-1").orElseThrow();
            assertEquals(2, stat.plays);
            assertEquals(12000, stat.bestScore);
            assertEquals(40, stat.maxCombo);
            assertEquals(40, stat.bestHits);
            assertEquals(34, stat.bestPerfect);
            assertEquals(6, stat.bestGreat);
            assertEquals(Rank.of(second), stat.rank());

            List<StatsStore.PlayRecord> history = store.history("song-1", 10);
            assertEquals(2, history.size());
            assertTrue(history.get(0).playedAt >= history.get(1).playedAt);
            assertEquals(12000, history.get(0).score);
            assertEquals(9000, history.get(1).score);
        } finally {
            store.close();
        }
    }

    @Test
    void shouldHandleEmptyAndInvalidInputGracefully() {
        Path db = tempDir.resolve("stats.db");
        StatsStore store = new StatsStore(db);
        try {
            assertTrue(store.forSong("missing").isEmpty());
            assertEquals(0, store.playCount("missing"));
            assertTrue(store.history("missing", 5).isEmpty());
            assertEquals(0, store.totalPlays());
            assertEquals(0, store.songsPlayed());
            assertEquals(0, store.bestScoreOverall());
            assertEquals(0, store.bestComboOverall());
        } finally {
            store.close();
        }
    }

    @Test
    void rankFallbackShouldBeSafeForUnknownValue() {
        StatsStore.SongStat stat = new StatsStore.SongStat(1, 100, 5, 5, 4, 1, 0.8, "UNKNOWN");
        StatsStore.PlayRecord record = new StatsStore.PlayRecord(100, 5, 0, 4, 1, 5, 0.8, "BROKEN", 123L);

        assertEquals(Rank.E, stat.rank());
        assertEquals(Rank.E, record.rank());
    }
}
