package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integracyjny test pełnego pipeline'u: syntetyczny WAV -&gt; BeatmapEngine -&gt; JSON -&gt; Loader.
 * Nie wymaga żadnych zewnętrznych plików audio - sam generuje sygnał o znanym tempie.
 */
class BeatmapEngineIntegrationTest {

    @Test
    void shouldDetectOnsetsInClickTrack() throws Exception {
        Path wav = Files.createTempFile("clicks-", ".wav");
        Path json = Files.createTempFile("beatmap-", ".json");
        try {
            // 16 kliknięć co 500ms = 120 BPM, łącznie ~8 sekund.
            SyntheticAudio.writeClickTrack(wav, 16, 500, 1000.0);

            BeatmapEngine engine = new BeatmapEngine();
            SongContext ctx = engine.generateAndSave(wav, json, "click-test", "Click Track");

            // Podstawowe asercje strukturalne.
            assertEquals("click-test", ctx.songId());
            assertEquals("Click Track", ctx.title());
            assertEquals(wav.getFileName().toString(), ctx.audioPath());

            // Detektor powinien znaleźć większość kliknięć (z tolerancją - 70%).
            assertTrue(ctx.notes().size() >= 11,
                    "Oczekiwano >= 11 onsetów, otrzymano " + ctx.notes().size());
            assertTrue(ctx.notes().size() <= 20,
                    "Onsetów jest za dużo - prawdopodobnie false positive: " + ctx.notes().size());

            // BPM mediana powinna być w okolicy 120 (mediana IOI = 500ms -> 120 BPM).
            assertTrue(ctx.bpm() >= 100 && ctx.bpm() <= 140,
                    "BPM poza zakresem [100,140]: " + ctx.bpm());

            // Round-trip JSON.
            SongContext reloaded = new BeatmapLoader().load(json);
            assertEquals(ctx.notes(), reloaded.notes());
        } finally {
            Files.deleteIfExists(wav);
            Files.deleteIfExists(json);
        }
    }

    @Test
    void shouldAvoidConsecutiveSameLaneInSeededRandomStrategy() throws Exception {
        Path wav = Files.createTempFile("clicks2-", ".wav");
        try {
            SyntheticAudio.writeClickTrack(wav, 24, 400, 1000.0);

            BeatmapEngine engine = new BeatmapEngine(
                    0.3, 0.08, BeatmapEngine.LaneStrategy.SEEDED_PSEUDO_RANDOM);
            SongContext ctx = engine.generateBeatmap(wav, "test", null);

            List<Note> notes = ctx.notes();
            assertFalse(notes.isEmpty(), "powinny być wykryte onsety");

            // Heurystyka playability: brak dwóch identycznych ścieżek z rzędu.
            for (int i = 1; i < notes.size(); i++) {
                assertNotEquals(notes.get(i - 1).lane(), notes.get(i).lane(),
                        "Dwie nuty z rzędu na tej samej ścieżce na pozycji " + i);
            }
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Test
    void shouldDetectOnsetsInStereo48kHzTrack() throws Exception {
        // Symuluje plik MP3 po dekompresji: stereo, 48 kHz - dokładnie ścieżka kodu
        // (decode + downmix do mono) którą napotyka realny MP3 przepuszczany
        // przez mp3spi → AudioSystem.getAudioInputStream(targetFormat, ...).
        Path wav = Files.createTempFile("stereo48-", ".wav");
        Path json = Files.createTempFile("stereo-bm-", ".json");
        try {
            SyntheticAudio.writeStereoClickTrack(wav, 12, 500, 1000.0, 48000);

            BeatmapEngine engine = new BeatmapEngine();
            SongContext ctx = engine.generateAndSave(wav, json, "stereo48", "Stereo 48k");

            assertTrue(ctx.notes().size() >= 8,
                    "Stereo 48 kHz powinno generować onsety (regresja na konwersję PCM): "
                            + ctx.notes().size());
            assertTrue(ctx.bpm() >= 100 && ctx.bpm() <= 140,
                    "BPM dla 120 BPM target: " + ctx.bpm());
        } finally {
            Files.deleteIfExists(wav);
            Files.deleteIfExists(json);
        }
    }

    @Test
    void shouldBeDeterministicForSameSongId() throws Exception {
        Path wav = Files.createTempFile("clicks3-", ".wav");
        try {
            SyntheticAudio.writeClickTrack(wav, 16, 500, 1000.0);
            BeatmapEngine engine = new BeatmapEngine();

            SongContext a = engine.generateBeatmap(wav, "fixed-id", null);
            SongContext b = engine.generateBeatmap(wav, "fixed-id", null);

            assertEquals(a.notes(), b.notes(),
                    "Beatmapa dla tego samego songId powinna być deterministyczna");
        } finally {
            Files.deleteIfExists(wav);
        }
    }
}
