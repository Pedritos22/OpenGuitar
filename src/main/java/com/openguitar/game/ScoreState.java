package com.openguitar.game;

/**
 * Stanowy obiekt śledzący wynik, combo i mnożnik w trakcie rozgrywki.
 * Czysta logika - bez zależności od UI, łatwo testowalna.
 *
 * <h2>Mnożnik punktów</h2>
 * <pre>
 *   combo  0..9   -> x1
 *   combo 10..19  -> x2
 *   combo 20..29  -> x3
 *   combo 30+     -> x4 (cap)
 * </pre>
 * Trafienie dodaje {@code basePoints * multiplier} do {@code totalScore}.
 * MISS zeruje combo (ale {@code maxCombo} pozostaje).
 */
public final class ScoreState {

    private static final int MAX_MULTIPLIER = 4;
    private static final int COMBO_PER_MULTIPLIER_STEP = 10;

    private int totalScore = 0;
    private int hits = 0;
    private int misses = 0;
    private int perfect = 0;
    private int great = 0;
    private int combo = 0;
    private int maxCombo = 0;

    /** Aktualny mnożnik punktów na podstawie combo. */
    public int multiplier() {
        return Math.min(MAX_MULTIPLIER, 1 + combo / COMBO_PER_MULTIPLIER_STEP);
    }

    /**
     * Rejestruje trafienie. Jeśli judgment to MISS, zachowanie jest takie samo
     * jak {@link #registerMiss()} - dla wygody jednego punktu wejścia.
     */
    public void register(HitJudgment judgment) {
        if (judgment == HitJudgment.MISS) {
            registerMiss();
            return;
        }
        totalScore += judgment.basePoints() * multiplier();
        hits++;
        if (judgment == HitJudgment.PERFECT) {
            perfect++;
        } else if (judgment == HitJudgment.GREAT) {
            great++;
        }
        combo++;
        if (combo > maxCombo) {
            maxCombo = combo;
        }
    }

    /** Rejestruje pominiętą nutę - zeruje combo. */
    public void registerMiss() {
        misses++;
        combo = 0;
    }

    public int totalScore() { return totalScore; }
    public int hits()       { return hits; }
    public int misses()     { return misses; }
    public int perfect()    { return perfect; }
    public int great()      { return great; }
    public int combo()      { return combo; }
    public int maxCombo()   { return maxCombo; }

    /** Buduje finalny {@link GameResult} dla danego utworu. */
    public GameResult toResult(String songId) {
        return new GameResult(songId, totalScore, hits, misses, perfect, great, maxCombo);
    }
}
