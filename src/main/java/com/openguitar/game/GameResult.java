package com.openguitar.game;

/**
 * Statystyki rozgrywki zwracane po zakończeniu utworu.
 *
 * @param songId     identyfikator utworu zgrany z {@code SongContext.songId()}
 * @param totalScore suma punktów (z uwzględnieniem mnożnika combo)
 * @param hits       liczba poprawnie trafionych nut (PERFECT + GREAT)
 * @param misses     liczba pominiętych lub spóźnionych nut
 * @param maxCombo   najwyższe zdobyte combo w trakcie utworu
 */
public record GameResult(
        String songId,
        int totalScore,
        int hits,
        int misses,
        int maxCombo
) { }
