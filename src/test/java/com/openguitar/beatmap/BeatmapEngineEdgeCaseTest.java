package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case'y pipeline'u beatmapy: cisza, puste metadane, domyślne wartości.
 */
class BeatmapEngineEdgeCaseTest {

    @TempDir
    Path tempDir;

    @Test
    void silenceShouldYieldFewOrNoOnsetsAndDefaultBpm() throws Exception {
        Path wav = tempDir.resolve("silence.wav");
        int sampleRate = SyntheticAudio.SAMPLE_RATE;
        int durationMs = 2000;
        int samples = sampleRate * durationMs / 1000;
        short[] pcm = new short[samples]; // same zera
        writeMonoWav(wav, pcm, sampleRate);

        SongContext ctx = new BeatmapEngine().generateBeatmap(wav, "silent", "Silence");

        assertTrue(ctx.notes().size() <= 2,
                "Cisza nie powinna generować wielu onsetów: " + ctx.notes().size());
        assertEquals(120, ctx.bpm(), "Przy <4 onsetach BPM = domyślne 120");
    }

    @Test
    void shouldUseFileNameWhenTitleNull() throws Exception {
        Path wav = tempDir.resolve("my_song.wav");
        SyntheticAudio.writeClickTrack(wav, 4, 500, 1000.0);

        SongContext ctx = new BeatmapEngine().generateBeatmap(wav, "id-1", null);
        assertEquals("my_song", ctx.title());
    }

    @Test
    void shouldGenerateUuidWhenSongIdBlank() throws Exception {
        Path wav = tempDir.resolve("track.wav");
        SyntheticAudio.writeClickTrack(wav, 4, 500, 1000.0);

        SongContext ctx = new BeatmapEngine().generateBeatmap(wav, "  ", null);
        assertDoesNotThrow(() -> UUID.fromString(ctx.songId()));
    }

    @Test
    void veryShortClickShouldStillProduceAtLeastOneOnset() throws Exception {
        Path wav = tempDir.resolve("short.wav");
        SyntheticAudio.writeClickTrack(wav, 2, 500, 1000.0);

        SongContext ctx = new BeatmapEngine(0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN)
                .generateBeatmap(wav, "short", null);

        assertFalse(ctx.notes().isEmpty(), "Nawet 2 kliknięcia powinny dać onset");
        for (Note n : ctx.notes()) {
            assertTrue(n.timeMs() >= 0);
            assertTrue(n.lane() >= 0 && n.lane() <= 3);
        }
    }

    @Test
    void largerMinimumInterOnsetIntervalShouldReduceNoteCount() throws Exception {
        Path wav = tempDir.resolve("clicks.wav");
        SyntheticAudio.writeClickTrack(wav, 16, 500, 1000.0);

        SongContext tight = new BeatmapEngine(0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN)
                .generateBeatmap(wav, "a", null);
        SongContext sparse = new BeatmapEngine(0.3, 0.35, BeatmapEngine.LaneStrategy.ROUND_ROBIN)
                .generateBeatmap(wav, "b", null);

        assertTrue(tight.notes().size() >= sparse.notes().size(),
                "Większy minimalInterOnsetIntervalSec powinien odfiltrować nuty: "
                        + tight.notes().size() + " vs " + sparse.notes().size());
        assertTrue(sparse.notes().size() >= 4, "Nadal oczekujemy kilku onsetów: " + sparse.notes().size());
    }

    private static void writeMonoWav(Path output, short[] pcm, int sampleRate) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (short s : pcm) {
            bytes.write(s & 0xFF);
            bytes.write((s >> 8) & 0xFF);
        }
        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, 1, 2, sampleRate, false);
        try (javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(
                new java.io.ByteArrayInputStream(bytes.toByteArray()), format, pcm.length)) {
            javax.sound.sampled.AudioSystem.write(
                    ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, output.toFile());
        }
    }
}
