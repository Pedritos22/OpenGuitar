package com.openguitar.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SongImporterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCopySupportedAudioFilesIntoSongsDir() throws Exception {
        Path songsDir = tempDir.resolve("songs");
        Path srcMp3 = tempDir.resolve("track.mp3");
        Path srcWav = tempDir.resolve("other.WAV");
        Path srcTxt = tempDir.resolve("notes.txt");
        Files.writeString(srcMp3, "dummy");
        Files.writeString(srcWav, "dummy");
        Files.writeString(srcTxt, "dummy");

        SongImporter.ImportResult result = SongImporter.importAudioFiles(
                songsDir, List.of(srcMp3, srcWav, srcTxt));

        assertEquals(2, result.imported());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().isEmpty());
        assertTrue(Files.isRegularFile(songsDir.resolve("track.mp3")));
        assertTrue(Files.isRegularFile(songsDir.resolve("other.WAV")));
    }

    @Test
    void shouldReplaceExistingFileWithSameName() throws Exception {
        Path songsDir = tempDir.resolve("songs");
        Files.createDirectories(songsDir);
        Path src = tempDir.resolve("song.mp3");
        Files.writeString(src, "new-content");
        Files.writeString(songsDir.resolve("song.mp3"), "old-content");

        SongImporter.ImportResult result = SongImporter.importAudioFiles(songsDir, List.of(src));

        assertEquals(1, result.imported());
        assertEquals("new-content", Files.readString(songsDir.resolve("song.mp3")));
    }

    @Test
    void shouldDetectSupportedAudioInMixedFileList() throws Exception {
        Path mp3 = tempDir.resolve("a.mp3");
        Path txt = tempDir.resolve("b.txt");
        Files.writeString(mp3, "dummy");
        Files.writeString(txt, "dummy");

        assertTrue(SongImporter.containsSupportedAudio(List.of(mp3, txt)));
        assertFalse(SongImporter.containsSupportedAudio(List.of(txt)));
    }
}
