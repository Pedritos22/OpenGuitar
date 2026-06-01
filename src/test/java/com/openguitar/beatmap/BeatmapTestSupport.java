package com.openguitar.beatmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wspólne asercje dla testów {@link BeatmapEngine} — rytm (IOI), parowanie onsetów
 * między formatami, tolerancje zamiast sztywnego porównania nuty-po-indeksie.
 */
final class BeatmapTestSupport {

    private BeatmapTestSupport() {}

    static List<Integer> onsetTimesMs(SongContext ctx) {
        return ctx.notes().stream().map(Note::timeMs).sorted().toList();
    }

    /** Mediana odstępów między kolejnymi onsetami (ms). Pusta lub 1 nuta → {@code -1}. */
    static double medianInterOnsetMs(List<Integer> sortedTimesMs) {
        if (sortedTimesMs.size() < 2) {
            return -1;
        }
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < sortedTimesMs.size(); i++) {
            gaps.add((double) (sortedTimesMs.get(i) - sortedTimesMs.get(i - 1)));
        }
        Collections.sort(gaps);
        int mid = gaps.size() / 2;
        if (gaps.size() % 2 == 0) {
            return (gaps.get(mid - 1) + gaps.get(mid)) / 2.0;
        }
        return gaps.get(mid);
    }

    /**
     * Liczy pary onsetów (najbliższy sąsiad), które mieszczą się w {@code maxDeltaMs}.
     * Każdy onset z {@code b} może być użyty co najwyżej raz — odpornie na przesunięcie
     * globalne (np. opóźnienie enkodera MP3) i brakującą pierwszą nutę w jednym formacie.
     */
    static int countGreedyPairs(List<Integer> sortedA, List<Integer> sortedB, int maxDeltaMs) {
        if (sortedA.isEmpty() || sortedB.isEmpty()) {
            return 0;
        }
        boolean[] usedB = new boolean[sortedB.size()];
        int pairs = 0;
        for (int ta : sortedA) {
            int bestIdx = -1;
            int bestDelta = Integer.MAX_VALUE;
            for (int j = 0; j < sortedB.size(); j++) {
                if (usedB[j]) {
                    continue;
                }
                int delta = Math.abs(ta - sortedB.get(j));
                if (delta <= maxDeltaMs && delta < bestDelta) {
                    bestDelta = delta;
                    bestIdx = j;
                }
            }
            if (bestIdx >= 0) {
                usedB[bestIdx] = true;
                pairs++;
            }
        }
        return pairs;
    }

    static void assertMedianIoiNear(List<Integer> times, int expectedMs, int toleranceMs, String label) {
        double median = medianInterOnsetMs(times);
        assertTrue(median >= 0,
                label + ": za mało onsetów do policzenia IOI (" + times.size() + ")");
        assertTrue(Math.abs(median - expectedMs) <= toleranceMs,
                label + ": mediana IOI=" + median + " ms, oczekiwano ~" + expectedMs
                        + " ±" + toleranceMs);
    }

    static void assertSimilarRhythmProfiles(
            List<Integer> wavTimes,
            List<Integer> mp3Times,
            int expectedBeatMs,
            int beatToleranceMs,
            int minPairs,
            int pairToleranceMs
    ) {
        assertFalse(wavTimes.isEmpty(), "WAV: brak onsetów");
        assertFalse(mp3Times.isEmpty(), "MP3: brak onsetów");
        assertMedianIoiNear(wavTimes, expectedBeatMs, beatToleranceMs, "WAV");
        assertMedianIoiNear(mp3Times, expectedBeatMs, beatToleranceMs, "MP3");

        int paired = countGreedyPairs(wavTimes, mp3Times, pairToleranceMs);
        assertTrue(paired >= minPairs,
                "Za mało sparowanych onsetów WAV↔MP3: " + paired + " (min " + minPairs + ")");
    }
}
