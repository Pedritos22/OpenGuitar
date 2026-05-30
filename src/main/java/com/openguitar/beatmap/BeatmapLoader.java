package com.openguitar.beatmap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wczytuje plik JSON beatmapy i mapuje go z powrotem na obiekt {@link SongContext},
 * z którego korzysta silnik gry.
 * <p>
 * Klasa jest świadomie minimalna i bezstanowa: nie trzyma cache'u, każde wywołanie
 * to świeży odczyt z dysku. Walidacja wykonywana w konstruktorach {@link Note}
 * i {@link SongContext} łapie typowe problemy (zła ścieżka lane, ujemny czas).
 */
public final class BeatmapLoader {

    private final ObjectMapper mapper;

    public BeatmapLoader() {
        this.mapper = new ObjectMapper()
                // Ignorowanie nieznanych pól zapewnia kompatybilność forward,
                // gdy w przyszłości dodamy do JSON-a metadane (np. difficulty).
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Wczytuje beatmapę z pliku. */
    public SongContext load(Path jsonFile) throws IOException {
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("Plik beatmapy nie istnieje: " + jsonFile);
        }
        SongContext ctx = mapper.readValue(jsonFile.toFile(), SongContext.class);
        return normalize(ctx);
    }

    /** Wczytuje beatmapę z dowolnego strumienia (np. zasobu z classpath). */
    public SongContext load(InputStream input) throws IOException {
        SongContext ctx = mapper.readValue(input, SongContext.class);
        return normalize(ctx);
    }

    /**
     * Sortuje listę nut po {@code timeMs} - silnik gry zakłada chronologiczną
     * kolejność, a polegać na zewnętrznych edytorach beatmap nie warto.
     */
    private SongContext normalize(SongContext ctx) {
        List<Note> sorted = new ArrayList<>(ctx.notes());
        sorted.sort(Comparator.comparingInt(Note::timeMs));
        return new SongContext(
                ctx.songId(),
                ctx.title(),
                ctx.bpm(),
                ctx.audioPath(),
                sorted
        );
    }
}
