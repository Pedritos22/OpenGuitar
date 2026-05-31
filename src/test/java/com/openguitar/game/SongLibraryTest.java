package com.openguitar.game;

import com.openguitar.beatmap.BeatmapEngine;
import com.openguitar.beatmap.Note;
import com.openguitar.beatmap.SongContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SongLibraryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldScanOnlySupportedAudioFilesAndSortCaseInsensitively() throws Exception {
        Path z = tempDir.resolve("zeta.WAV");
        Path a = tempDir.resolve("Alpha.mp3");
        Path b = tempDir.resolve("beta.aiff");
        Path c = tempDir.resolve("ignore.txt");
        Files.writeString(z, "dummy");
        Files.writeString(a, "dummy");
        Files.writeString(b, "dummy");
        Files.writeString(c, "dummy");

        List<SongEntry> entries = new SongLibrary(tempDir).scan();

        assertEquals(3, entries.size());
        assertEquals("Alpha", entries.get(0).title());
        assertEquals("beta", entries.get(1).title());
        assertEquals("zeta", entries.get(2).title());
    }

    @Test
    void shouldAttachBeatmapWhenJsonExists() throws Exception {
        Path audio = tempDir.resolve("song.mp3");
        Files.writeString(audio, "dummy");
        Path json = tempDir.resolve("song.json");

        SongContext ctx = new SongContext(
                "song-1",
                "Song One",
                128,
                "song.mp3",
                List.of(new Note(100, 0), new Note(500, 2))
        );
        new BeatmapEngine().save(ctx, json);

        List<SongEntry> entries = new SongLibrary(tempDir).scan();
        assertEquals(1, entries.size());
        SongEntry entry = entries.get(0);

        assertTrue(entry.hasBeatmap());
        assertNotNull(entry.context());
        assertEquals(ctx.songId(), entry.context().songId());
        assertEquals(json, entry.beatmapPath());
    }

    @Test
    void shouldNotFailWholeScanWhenOneJsonIsBroken() throws Exception {
        Path good = tempDir.resolve("good.wav");
        Path bad = tempDir.resolve("bad.mp3");
        Files.writeString(good, "dummy");
        Files.writeString(bad, "dummy");
        Files.writeString(tempDir.resolve("bad.json"), "{ this is not valid json }");

        List<SongEntry> entries = new SongLibrary(tempDir).scan();
        assertEquals(2, entries.size());

        SongEntry badEntry = entries.stream()
                .filter(e -> e.audioPath().getFileName().toString().equals("bad.mp3"))
                .findFirst()
                .orElseThrow();
        assertFalse(badEntry.hasBeatmap());
        assertNull(badEntry.context());
    }

    @Test
    void shouldReturnEmptyListForMissingDirectory() throws Exception {
        Path missing = tempDir.resolve("missing-dir");
        List<SongEntry> entries = new SongLibrary(missing).scan();
        assertTrue(entries.isEmpty());
    }
}
