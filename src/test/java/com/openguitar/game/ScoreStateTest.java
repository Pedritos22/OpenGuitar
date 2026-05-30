package com.openguitar.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreStateTest {

    @Test
    void perfectAndGreatGivePointsAndBuildCombo() {
        ScoreState s = new ScoreState();
        s.register(HitJudgment.PERFECT);
        s.register(HitJudgment.GREAT);

        assertEquals(2, s.hits());
        assertEquals(0, s.misses());
        assertEquals(2, s.combo());
        assertEquals(2, s.maxCombo());
        // x1 multiplier dla combo < 10
        assertEquals(300 + 150, s.totalScore());
    }

    @Test
    void missResetsComboButKeepsMaxCombo() {
        ScoreState s = new ScoreState();
        for (int i = 0; i < 5; i++) s.register(HitJudgment.PERFECT);
        s.registerMiss();

        assertEquals(0, s.combo());
        assertEquals(5, s.maxCombo());
        assertEquals(1, s.misses());
        assertEquals(5 * 300, s.totalScore());
    }

    @Test
    void multiplierClimbsEvery10ComboAndCapsAt4x() {
        ScoreState s = new ScoreState();
        assertEquals(1, s.multiplier());
        for (int i = 0; i < 10; i++) s.register(HitJudgment.PERFECT);
        assertEquals(2, s.multiplier(), "10 combo -> x2");
        for (int i = 0; i < 10; i++) s.register(HitJudgment.PERFECT);
        assertEquals(3, s.multiplier(), "20 combo -> x3");
        for (int i = 0; i < 10; i++) s.register(HitJudgment.PERFECT);
        assertEquals(4, s.multiplier(), "30 combo -> x4");
        for (int i = 0; i < 50; i++) s.register(HitJudgment.PERFECT);
        assertEquals(4, s.multiplier(), "cap przy x4 niezależnie od combo");
    }

    @Test
    void multiplierIsAppliedToScore() {
        ScoreState s = new ScoreState();
        for (int i = 0; i < 10; i++) s.register(HitJudgment.PERFECT); // 10x300 = 3000
        // Combo = 10, multiplier = 2, kolejne PERFECT da 600
        int beforeNext = s.totalScore();
        s.register(HitJudgment.PERFECT);
        assertEquals(beforeNext + 300 * 2, s.totalScore());
    }

    @Test
    void registerWithMissDelegatesToRegisterMiss() {
        ScoreState s = new ScoreState();
        s.register(HitJudgment.PERFECT);
        s.register(HitJudgment.MISS);

        assertEquals(0, s.combo());
        assertEquals(1, s.hits());
        assertEquals(1, s.misses());
    }

    @Test
    void toResultBuildsImmutableSnapshot() {
        ScoreState s = new ScoreState();
        s.register(HitJudgment.PERFECT);
        s.register(HitJudgment.GREAT);
        s.registerMiss();

        GameResult r = s.toResult("song-42");
        assertEquals("song-42", r.songId());
        assertEquals(450, r.totalScore());
        assertEquals(2, r.hits());
        assertEquals(1, r.misses());
        assertEquals(2, r.maxCombo());
    }

    @Test
    void hitJudgmentClassifiesByDeltaTime() {
        assertEquals(HitJudgment.PERFECT, HitJudgment.classify(0));
        assertEquals(HitJudgment.PERFECT, HitJudgment.classify(50));
        assertEquals(HitJudgment.GREAT,   HitJudgment.classify(51));
        assertEquals(HitJudgment.GREAT,   HitJudgment.classify(100));
        assertEquals(HitJudgment.MISS,    HitJudgment.classify(101));
    }
}
