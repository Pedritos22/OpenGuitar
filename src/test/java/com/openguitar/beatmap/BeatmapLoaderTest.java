package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeatmapLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadAndSortNotesFromInputStream() throws Exception {
        SongContext original = new SongContext(
                "stream-song",
                "Stream Song",
                123,
                "audio.wav",
                List.of(
                        new Note(3000, 2),
                        new Note(500, 0),
                        new Note(1500, 1)
                )
        );

        Path json = tempDir.resolve("beatmap.json");
        new BeatmapEngine().save(original, json);

        byte[] raw = Files.readAllBytes(json);
        SongContext loaded = new BeatmapLoader().load(new ByteArrayInputStream(raw));

        assertEquals(original.songId(), loaded.songId());
        assertEquals(original.title(), loaded.title());
        assertEquals(original.bpm(), loaded.bpm());
        assertEquals(original.audioPath(), loaded.audioPath());
        assertEquals(List.of(
                new Note(500, 0),
                new Note(1500, 1),
                new Note(3000, 2)
        ), loaded.notes());
    }

    @Test
    void shouldIgnoreUnknownFieldsWhenLoadingFromFile() throws Exception {
        Path json = tempDir.resolve("extra-fields.json");
        Files.writeString(json, """
                {
                  "songId": "x",
                  "title": "X",
                  "bpm": 140,
                  "audioPath": "x.mp3",
                  "difficulty": "hard",
                  "notes": [
                    {"timeMs": 200, "lane": 1, "velocity": 99},
                    {"timeMs": 100, "lane": 0}
                  ],
                  "custom": {"anything": true}
                }
                """);

        SongContext loaded = new BeatmapLoader().load(json);
        assertEquals("x", loaded.songId());
        assertEquals("X", loaded.title());
        assertEquals(140, loaded.bpm());
        assertEquals("x.mp3", loaded.audioPath());
        assertEquals(List.of(new Note(100, 0), new Note(200, 1)), loaded.notes());
    }

    @Test
    void shouldRejectMissingBeatmapFile() {
        Path missing = tempDir.resolve("missing.json");
        assertThrows(java.io.IOException.class, () -> new BeatmapLoader().load(missing));
    }

    @Test
    void shouldRejectInvalidNoteDataFromJson() throws Exception {
        Path json = tempDir.resolve("invalid.json");
        Files.writeString(json, """
                {
                  "songId": "broken",
                  "title": "Broken",
                  "bpm": 120,
                  "audioPath": "broken.wav",
                  "notes": [
                    {"timeMs": -1, "lane": 0}
                  ]
                }
                """);

        assertThrows(Exception.class, () -> new BeatmapLoader().load(json));
    }

    @Test
    void songContextShouldDefensivelyCopyNotes() {
        List<Note> notes = new ArrayList<>();
        notes.add(new Note(100, 0));
        SongContext ctx = new SongContext("id", "Title", 120, "audio.wav", notes);

        notes.add(new Note(200, 1));
        assertEquals(1, ctx.notes().size());
        assertThrows(UnsupportedOperationException.class, () -> ctx.notes().add(new Note(300, 2)));
    }

    @Test
    void noteShouldValidateBounds() {
        assertThrows(IllegalArgumentException.class, () -> new Note(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 4));
    }
}
