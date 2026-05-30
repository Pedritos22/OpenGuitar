package com.openguitar.game;

/**
 * Klasyfikacja trafienia w zależności od odchylenia czasowego (|dt| w ms)
 * między momentem naciśnięcia klawisza a idealnym czasem nuty.
 *
 * <pre>
 *   |dt| <=  50ms -> PERFECT  (300 pkt)
 *   |dt| <= 100ms -> GREAT    (150 pkt)
 *   |dt| >  100ms -> brak trafienia (klawisz "gubi się")
 *   nuta minęła hit-line + 100ms bez naciśnięcia -> MISS (combo break, 0 pkt)
 * </pre>
 *
 * Zakres ±100 ms odpowiada wymaganiu zadania ("HIT vs MISS"), ale wewnętrzny
 * podział na PERFECT/GREAT pozwala nagradzać precyzję wyższym wynikiem.
 */
public enum HitJudgment {
    PERFECT(300, 50),
    GREAT(150, 100),
    MISS(0, Integer.MAX_VALUE);

    private final int basePoints;
    private final int windowMs;

    HitJudgment(int basePoints, int windowMs) {
        this.basePoints = basePoints;
        this.windowMs = windowMs;
    }

    public int basePoints() { return basePoints; }
    public int windowMs()   { return windowMs; }

    /** Maksymalne odchylenie ms uznawane jeszcze za trafienie (poza tym -> MISS). */
    public static int hitWindowMs() {
        return GREAT.windowMs;
    }

    /** Klasyfikuje |dt| w milisekundach na judgment. */
    public static HitJudgment classify(int absDtMs) {
        if (absDtMs <= PERFECT.windowMs) return PERFECT;
        if (absDtMs <= GREAT.windowMs)   return GREAT;
        return MISS;
    }
}
