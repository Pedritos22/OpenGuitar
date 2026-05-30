package com.openguitar.beatmap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Kompletny kontekst utworu, którego używa silnik gry.
 * Mapowany 1:1 na plik JSON beatmapy.
 *
 * @param songId    unikalny identyfikator utworu (np. UUID lub slug)
 * @param title     tytuł utworu wyświetlany w UI
 * @param bpm       szacowane BPM (tempo) - używane np. do skalowania prędkości notacji
 * @param audioPath ścieżka do pliku audio (względna do katalogu z plikiem JSON lub absolutna)
 * @param notes     posortowana rosnąco po {@code timeMs} lista nut do zagrania
 */
public record SongContext(
        String songId,
        String title,
        int bpm,
        String audioPath,
        List<Note> notes
) {

    /**
     * Konstruktor anotowany dla Jacksona, aby deserializacja działała stabilnie nawet
     * bez modułu jackson-module-parameter-names (z którym nie zawsze jest kompilowany).
     */
    @JsonCreator
    public SongContext(
            @JsonProperty("songId") String songId,
            @JsonProperty("title") String title,
            @JsonProperty("bpm") int bpm,
            @JsonProperty("audioPath") String audioPath,
            @JsonProperty("notes") List<Note> notes
    ) {
        this.songId = Objects.requireNonNull(songId, "songId");
        this.title = Objects.requireNonNull(title, "title");
        this.bpm = bpm;
        this.audioPath = Objects.requireNonNull(audioPath, "audioPath");
        // defensywna kopia + immutable view
        this.notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
