package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.openguitar.beatmap.BeatmapTestSupport.assertMedianIoiNear;
import static com.openguitar.beatmap.BeatmapTestSupport.assertSimilarRhythmProfiles;
import static com.openguitar.beatmap.BeatmapTestSupport.countGreedyPairs;
import static com.openguitar.beatmap.BeatmapTestSupport.medianInterOnsetMs;
import static com.openguitar.beatmap.BeatmapTestSupport.onsetTimesMs;
import static org.junit.jupiter.api.Assertions.*;

class BeatmapEngineAudioFormatTest {

    private static final int CLICK_BEAT_MS = 500;
    private static final int CLICK_COUNT = 16;

    @TempDir
    Path tempDir;

    @Test
    void clickTrackWavShouldMatchExpectedCadence() throws Exception {
        Path wav = tempDir.resolve("clicks.wav");
        SyntheticAudio.writeClickTrack(wav, CLICK_COUNT, CLICK_BEAT_MS, 1000.0);

        SongContext ctx = new BeatmapEngine(
                0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN
        ).generateBeatmap(wav, "wav-cadence", "Click Track");

        List<Integer> times = onsetTimesMs(ctx);
        assertTrue(times.size() >= 11, "WAV: za mało onsetów: " + times.size());
        assertTrue(times.size() <= 20, "WAV: za dużo onsetów: " + times.size());
        assertMedianIoiNear(times, CLICK_BEAT_MS, 120, "WAV");
        assertTrue(ctx.bpm() >= 100 && ctx.bpm() <= 140, "BPM: " + ctx.bpm());
    }

    @Test
    void clickTrackMp3ShouldMatchExpectedCadence() throws Exception {
        Path wav = tempDir.resolve("clicks.wav");
        Path mp3 = tempDir.resolve("clicks.mp3");
        SyntheticAudio.writeClickTrack(wav, CLICK_COUNT, CLICK_BEAT_MS, 1000.0);
        TestAudioSupport.encodeMp3(wav, mp3);

        SongContext ctx = new BeatmapEngine(
                0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN
        ).generateBeatmap(mp3, "mp3-cadence", "Click Track");

        List<Integer> times = onsetTimesMs(ctx);
        assertTrue(times.size() >= 11, "MP3: za mało onsetów: " + times.size());
        assertTrue(times.size() <= 20, "MP3: za dużo onsetów: " + times.size());
        assertMedianIoiNear(times, CLICK_BEAT_MS, 120, "MP3");
        assertTrue(ctx.bpm() >= 100 && ctx.bpm() <= 140, "BPM: " + ctx.bpm());
    }

    /**
     * WAV i MP3 tego samego click-tracka powinny dać podobny rytm (mediana IOI, BPM,
     * sparowane onsety), a nie identyczne czasy nuty-po-indeksie — MP3 wprowadza
     * przesunięcie globalne i czasem dodatkowy onset na początku.
     */
    @Test
    void wavAndMp3ClickTracksShouldAgreeOnRhythmNotOnIndex() throws Exception {
        Path wav = tempDir.resolve("clicks.wav");
        Path mp3 = tempDir.resolve("clicks.mp3");
        SyntheticAudio.writeClickTrack(wav, CLICK_COUNT, CLICK_BEAT_MS, 1000.0);
        TestAudioSupport.encodeMp3(wav, mp3);

        BeatmapEngine engine = new BeatmapEngine(
                0.3, 0.08, BeatmapEngine.LaneStrategy.ROUND_ROBIN
        );

        SongContext wavCtx = engine.generateBeatmap(wav, "compare", "Click Track");
        SongContext mp3Ctx = engine.generateBeatmap(mp3, "compare", "Click Track");

        List<Integer> wavTimes = onsetTimesMs(wavCtx);
        List<Integer> mp3Times = onsetTimesMs(mp3Ctx);

        assertTrue(Math.abs(wavTimes.size() - mp3Times.size()) <= 2,
                "Liczba onsetów WAV=" + wavTimes.size() + " MP3=" + mp3Times.size());
        assertTrue(Math.abs(wavCtx.bpm() - mp3Ctx.bpm()) <= 15,
                "BPM WAV=" + wavCtx.bpm() + " MP3=" + mp3Ctx.bpm());

        assertSimilarRhythmProfiles(wavTimes, mp3Times, CLICK_BEAT_MS, 120, 10, 180);

        // Mediana IOI obu formatów powinna być bliska (ten sam materiał źródłowy).
        double wavIoi = medianInterOnsetMs(wavTimes);
        double mp3Ioi = medianInterOnsetMs(mp3Times);
        assertTrue(Math.abs(wavIoi - mp3Ioi) <= 80,
                "Mediana IOI WAV=" + wavIoi + " vs MP3=" + mp3Ioi);
    }

    @Test
    void shouldHandleStereoWavDecodedLikeMp3() throws Exception {
        Path stereo = tempDir.resolve("stereo.wav");
        SyntheticAudio.writeStereoClickTrack(stereo, 12, CLICK_BEAT_MS, 1000.0, 48000);

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
    void shouldAssignRoundRobinLanesInOrder() throws Exception {
        Path wav = tempDir.resolve("rr.wav");
        SyntheticAudio.writeClickTrack(wav, 8, CLICK_BEAT_MS, 1000.0);

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
    void frequencyBandWavAndMp3ShouldAgreeOnRhythmAndPairing() throws Exception {
        Path wav = tempDir.resolve("bands.wav");
        Path mp3 = tempDir.resolve("bands.mp3");
        SyntheticAudio.writeMultiBandTrack(wav, List.of(0, 1, 2, 3, 0, 1, 2, 3), 600);
        TestAudioSupport.encodeMp3(wav, mp3);

        BeatmapEngine engine = new BeatmapEngine(
                0.25, 0.20, BeatmapEngine.LaneStrategy.FREQUENCY_BANDS
        );
        SongContext wavCtx = engine.generateBeatmap(wav, "band-compare", null);
        SongContext mp3Ctx = engine.generateBeatmap(mp3, "band-compare", null);

        List<Integer> wavTimes = onsetTimesMs(wavCtx);
        List<Integer> mp3Times = onsetTimesMs(mp3Ctx);

        assertFalse(wavTimes.isEmpty());
        assertFalse(mp3Times.isEmpty());
        assertTrue(Math.abs(wavTimes.size() - mp3Times.size()) <= 3);
        assertTrue(Math.abs(wavCtx.bpm() - mp3Ctx.bpm()) <= 20);

        assertMedianIoiNear(wavTimes, 600, 150, "WAV pasma");
        assertMedianIoiNear(mp3Times, 600, 150, "MP3 pasma");

        int paired = countGreedyPairs(wavTimes, mp3Times, 200);
        assertTrue(paired >= 5,
                "Za mało sparowanych onsetów dla ścieżki wielopasmowej: " + paired);
    }
}
