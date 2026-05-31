package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeatmapEngineAudioFormatTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateComparableBeatmapsFromWavAndMp3() throws Exception {
        Path wav = tempDir.resolve("clicks.wav");
        Path mp3 = tempDir.resolve("clicks.mp3");
        SyntheticAudio.writeClickTrack(wav, 16, 500, 1000.0);
        TestAudioSupport.encodeMp3(wav, mp3);

        BeatmapEngine engine = new BeatmapEngine(
                0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN
        );

        SongContext wavCtx = engine.generateBeatmap(wav, "compare", "Click Track");
        SongContext mp3Ctx = engine.generateBeatmap(mp3, "compare", "Click Track");

        assertFalse(wavCtx.notes().isEmpty(), "WAV powinien dać onsety");
        assertFalse(mp3Ctx.notes().isEmpty(), "MP3 powinien dać onsety");
        assertTrue(Math.abs(wavCtx.notes().size() - mp3Ctx.notes().size()) <= 2,
                "Liczba nut dla WAV i MP3 powinna być podobna");
        assertTrue(Math.abs(wavCtx.bpm() - mp3Ctx.bpm()) <= 10,
                "BPM dla WAV i MP3 powinno być podobne");

        int compared = Math.min(wavCtx.notes().size(), mp3Ctx.notes().size());
        assertTrue(compared >= 8, "Za mało nut do porównania: " + compared);
        for (int i = 0; i < compared; i++) {
            Note w = wavCtx.notes().get(i);
            Note m = mp3Ctx.notes().get(i);
            assertEquals(i % 4, w.lane());
            assertEquals(i % 4, m.lane());
            assertTrue(Math.abs(w.timeMs() - m.timeMs()) <= 120,
                    "Różnica czasu zbyt duża dla nuty " + i + ": " + w.timeMs() + " vs " + m.timeMs());
        }
    }

    @Test
    void shouldHandleStereoWavDecodedLikeMp3() throws Exception {
        Path stereo = tempDir.resolve("stereo.wav");
        SyntheticAudio.writeStereoClickTrack(stereo, 12, 500, 1000.0, 48000);

        BeatmapEngine engine = new BeatmapEngine(
                0.25, 0.08, BeatmapEngine.LaneStrategy.SEEDED_PSEUDO_RANDOM
        );
        SongContext ctx = engine.generateBeatmap(stereo, "stereo-song", "Stereo");

        assertTrue(ctx.notes().size() >= 8);
        assertTrue(ctx.bpm() >= 100 && ctx.bpm() <= 140);
        for (Note note : ctx.notes()) {
            assertTrue(note.lane() >= 0 && note.lane() <= 3);
        }
    }

    @Test
    void shouldBeDeterministicForSameSongId() throws Exception {
        Path wav = tempDir.resolve("clicks.wav");
        SyntheticAudio.writeClickTrack(wav, 16, 500, 1000.0);

        BeatmapEngine engine = new BeatmapEngine(
                0.3, 0.08, BeatmapEngine.LaneStrategy.SEEDED_PSEUDO_RANDOM
        );
        SongContext a = engine.generateBeatmap(wav, "fixed-id", null);
        SongContext b = engine.generateBeatmap(wav, "fixed-id", null);

        assertEquals(a.notes(), b.notes());
    }

    @Test
    void shouldAssignRoundRobinLanesInOrder() throws Exception {
        Path wav = tempDir.resolve("rr.wav");
        SyntheticAudio.writeClickTrack(wav, 8, 500, 1000.0);

        BeatmapEngine engine = new BeatmapEngine(
                0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN
        );
        SongContext ctx = engine.generateBeatmap(wav, "rr", null);

        for (int i = 0; i < ctx.notes().size(); i++) {
            assertEquals(i % 4, ctx.notes().get(i).lane());
        }
    }

    @Test
    void shouldAssignLanesByDominantFrequencyBand() throws Exception {
        Path wav = tempDir.resolve("bands.wav");
        List<Integer> sequence = List.of(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3);
        SyntheticAudio.writeMultiBandTrack(wav, sequence, 600);

        BeatmapEngine engine = new BeatmapEngine(
                0.25, 0.20, BeatmapEngine.LaneStrategy.FREQUENCY_BANDS
        );
        SongContext ctx = engine.generateBeatmap(wav, "bands", null);

        assertFalse(ctx.notes().isEmpty());
        int checked = 0;
        int correct = 0;
        for (Note note : ctx.notes()) {
            int beatIndex = Math.round(note.timeMs() / 600f);
            if (beatIndex >= 0 && beatIndex < sequence.size()) {
                checked++;
                if (note.lane() == sequence.get(beatIndex)) {
                    correct++;
                }
            }
        }
        assertTrue(checked > 0);
        assertTrue((double) correct / checked >= 0.6,
                "Za słabe mapowanie pasm: " + correct + "/" + checked);
    }

    @Test
    void shouldRejectMissingAudioFile() {
        Path missing = tempDir.resolve("missing.wav");
        BeatmapEngine engine = new BeatmapEngine();
        assertThrows(java.io.IOException.class, () -> engine.generateBeatmap(missing, "x", "X"));
    }

    @Test
    void shouldProduceSimilarOutputForWavAndMp3WhenUsingFrequencyBands() throws Exception {
        Path wav = tempDir.resolve("bands.wav");
        Path mp3 = tempDir.resolve("bands.mp3");
        SyntheticAudio.writeMultiBandTrack(wav, List.of(0, 1, 2, 3, 0, 1, 2, 3), 600);
        TestAudioSupport.encodeMp3(wav, mp3);

        BeatmapEngine engine = new BeatmapEngine(
                0.25, 0.20, BeatmapEngine.LaneStrategy.FREQUENCY_BANDS
        );
        SongContext wavCtx = engine.generateBeatmap(wav, "band-compare", null);
        SongContext mp3Ctx = engine.generateBeatmap(mp3, "band-compare", null);

        assertFalse(wavCtx.notes().isEmpty());
        assertFalse(mp3Ctx.notes().isEmpty());
        assertTrue(Math.abs(wavCtx.notes().size() - mp3Ctx.notes().size()) <= 3);
        assertTrue(Math.abs(wavCtx.bpm() - mp3Ctx.bpm()) <= 15);
    }
}
