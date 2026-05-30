package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeatmapJsonRoundTripTest {

    @Test
    void shouldSerializeAndDeserializeBeatmap() throws Exception {
        SongContext original = new SongContext(
                "test-song",
                "Test Track",
                128,
                "songs/test.mp3",
                List.of(
                        new Note(500, 0),
                        new Note(1000, 2),
                        new Note(1500, 1),
                        new Note(2000, 3)
                )
        );

        Path tmp = Files.createTempFile("beatmap-", ".json");
        try {
            new BeatmapEngine().save(original, tmp);
            SongContext reloaded = new BeatmapLoader().load(tmp);

            assertEquals(original.songId(), reloaded.songId());
            assertEquals(original.title(), reloaded.title());
            assertEquals(original.bpm(), reloaded.bpm());
            assertEquals(original.audioPath(), reloaded.audioPath());
            assertEquals(original.notes(), reloaded.notes());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void shouldRejectInvalidLane() {
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Note(-1, 0));
    }

    @Test
    void shouldSortNotesByTimeOnLoad() throws Exception {
        SongContext unsorted = new SongContext(
                "x", "X", 100, "x.wav",
                List.of(new Note(2000, 0), new Note(500, 1), new Note(1000, 2))
        );

        Path tmp = Files.createTempFile("beatmap-", ".json");
        try {
            new BeatmapEngine().save(unsorted, tmp);
            SongContext reloaded = new BeatmapLoader().load(tmp);
            assertEquals(500, reloaded.notes().get(0).timeMs());
            assertEquals(1000, reloaded.notes().get(1).timeMs());
            assertEquals(2000, reloaded.notes().get(2).timeMs());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
