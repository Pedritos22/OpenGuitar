package com.openguitar.beatmap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pojedyncza nuta na beatmapie.
 *
 * @param timeMs moment uderzenia od początku utworu w milisekundach
 * @param lane   numer ścieżki [0..3] na której nuta ma się pojawić
 */
public record Note(int timeMs, int lane) {

    @JsonCreator
    public Note(
            @JsonProperty("timeMs") int timeMs,
            @JsonProperty("lane") int lane
    ) {
        if (timeMs < 0) {
            throw new IllegalArgumentException("timeMs musi być >= 0, otrzymano " + timeMs);
        }
        if (lane < 0 || lane > 3) {
            throw new IllegalArgumentException("lane musi być w zakresie [0..3], otrzymano " + lane);
        }
        this.timeMs = timeMs;
        this.lane = lane;
    }
}
