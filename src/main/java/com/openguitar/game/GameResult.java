package com.openguitar.game;

/**
 * Statystyki rozgrywki zwracane po zakończeniu utworu.
 *
 * @param songId     identyfikator utworu zgrany z {@code SongContext.songId()}
 * @param totalScore suma punktów (z uwzględnieniem mnożnika combo)
 * @param hits       liczba poprawnie trafionych nut (PERFECT + GREAT)
 * @param misses     liczba pominiętych lub spóźnionych nut
 * @param perfect    liczba trafień PERFECT (|dt| ≤ 50 ms)
 * @param great      liczba trafień GREAT (50 &lt; |dt| ≤ 100 ms)
 * @param maxCombo   najwyższe zdobyte combo w trakcie utworu
 */
public record GameResult(
        String songId,
        int totalScore,
        int hits,
        int misses,
        int perfect,
        int great,
        int maxCombo
) {

    /** Łączna liczba nut, które weszły w grę (trafione + pominięte). */
    public int totalNotes() {
        return hits + misses;
    }

    /**
     * Celność w zakresie 0..1, ważona jakością trafień: PERFECT liczy się w pełni,
     * GREAT z wagą {@link Rank#GREAT_WEIGHT}. Brak nut → 0.
     */
    public double accuracy() {
        int total = totalNotes();
        if (total == 0) {
            return 0.0;
        }
        return (perfect + great * Rank.GREAT_WEIGHT) / total;
    }

    /** Czy utwór zaliczono bez ani jednego MISS-a (i z jakimkolwiek trafieniem). */
    public boolean fullCombo() {
        return misses == 0 && hits > 0;
    }

    /** Ranga utworu wyliczona z celności i full combo. */
    public Rank rank() {
        return Rank.of(this);
    }
}
