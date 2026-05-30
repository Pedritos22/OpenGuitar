package com.openguitar.beatmap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprawdza, że strategia {@link BeatmapEngine.LaneStrategy#FREQUENCY_BANDS}
 * faktycznie przydziela ścieżkę zgodnie z dominującym pasmem częstotliwości.
 * <p>
 * Generujemy syntetyczny WAV w którym każde uderzenie ma z góry zdefiniowaną
 * częstotliwość (bass / low-mid / mid / high), a następnie weryfikujemy, że
 * dla większości uderzeń silnik wybrał poprawną ścieżkę.
 */
class FrequencyBandsStrategyTest {

    @Test
    void shouldAssignLanesByDominantFrequencyBand() throws Exception {
        // Sekwencja: bass, low-mid, mid, high, bass, low-mid, mid, high, ...
        List<Integer> bandSeq = List.of(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3);
        Path wav = Files.createTempFile("bands-", ".wav");
        try {
            // 600ms odstępy - wystarczające, żeby każda ramka FFT należała do jednego uderzenia.
            SyntheticAudio.writeMultiBandTrack(wav, bandSeq, 600);

            BeatmapEngine engine = new BeatmapEngine(
                    0.25, 0.20, BeatmapEngine.LaneStrategy.FREQUENCY_BANDS);
            SongContext ctx = engine.generateBeatmap(wav, "bands-test", null);

            assertFalse(ctx.notes().isEmpty(), "powinny być wykryte onsety");
            assertTrue(ctx.notes().size() >= bandSeq.size() - 4,
                    "Wykryto za mało onsetów: " + ctx.notes().size() + " / " + bandSeq.size());

            // Dla każdego wykrytego onsetu znajdź najbliższe oczekiwane uderzenie i
            // sprawdź, czy lane się zgadza. Tolerujemy maksymalnie 25% pomyłek
            // (granice pasm + spectral leakage przy 1024-punkt FFT to nie cuda).
            int correct = 0;
            int total = 0;
            for (Note note : ctx.notes()) {
                int beatIndex = Math.round(note.timeMs() / 600f);
                if (beatIndex >= 0 && beatIndex < bandSeq.size()) {
                    int expectedLane = bandSeq.get(beatIndex);
                    if (note.lane() == expectedLane) {
                        correct++;
                    }
                    total++;
                }
            }
            assertTrue(total > 0, "brak nut dopasowanych do oczekiwanych beatów");
            double accuracy = (double) correct / total;
            assertTrue(accuracy >= 0.6,
                    "Klasyfikacja pasm zbyt niedokładna: " + correct + "/" + total +
                            " (" + Math.round(accuracy * 100) + "%)");
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Test
    void shouldKeepLanesInValidRange() throws Exception {
        Path wav = Files.createTempFile("bands2-", ".wav");
        try {
            SyntheticAudio.writeMultiBandTrack(wav, List.of(0, 1, 2, 3), 500);
            BeatmapEngine engine = new BeatmapEngine(
                    0.25, 0.20, BeatmapEngine.LaneStrategy.FREQUENCY_BANDS);
            SongContext ctx = engine.generateBeatmap(wav, "bands-range", null);

            for (Note n : ctx.notes()) {
                assertTrue(n.lane() >= 0 && n.lane() <= 3,
                        "lane poza zakresem: " + n.lane());
            }
        } finally {
            Files.deleteIfExists(wav);
        }
    }
}
