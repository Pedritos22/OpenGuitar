package com.openguitar.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankTest {

    @Test
    void shouldAssignRankFromAccuracyThresholds() {
        assertEquals(Rank.S, Rank.of(result(96, 0, 4)));
        assertEquals(Rank.A, Rank.of(result(88, 0, 12)));
        assertEquals(Rank.B, Rank.of(result(78, 0, 22)));
        assertEquals(Rank.C, Rank.of(result(65, 0, 35)));
        assertEquals(Rank.D, Rank.of(result(50, 0, 50)));
        assertEquals(Rank.F, Rank.of(result(40, 0, 60)));
    }

    @Test
    void fullComboCanUpgradeToSAtNinetyPercent() {
        assertEquals(Rank.S, Rank.of(result(91, 0, 0)));
        assertEquals(Rank.A, Rank.of(result(91, 0, 9)));
    }

    @Test
    void nullOrEmptyResultShouldBeRankF() {
        assertEquals(Rank.F, Rank.of(null));
        assertEquals(Rank.F, Rank.of(new GameResult("x", 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void fromAccuracyShouldMirrorOfLogic() {
        assertEquals(Rank.S, Rank.fromAccuracy(0.95, false));
        assertEquals(Rank.S, Rank.fromAccuracy(0.90, true));
        assertEquals(Rank.F, Rank.fromAccuracy(0.10, false));
    }

    @Test
    void gameResultAccuracyAndFullCombo() {
        GameResult r = new GameResult("song", 1000, 10, 0, 8, 2, 10);
        assertEquals(10, r.totalNotes());
        double expected = (8 + 2 * Rank.GREAT_WEIGHT) / 10.0;
        assertEquals(expected, r.accuracy(), 1e-9);
        assertEquals(true, r.fullCombo());
        assertEquals(Rank.S, r.rank());
    }

    private static GameResult result(int perfect, int great, int misses) {
        int hits = perfect + great;
        return new GameResult("id", 0, hits, misses, perfect, great, hits);
    }
}
