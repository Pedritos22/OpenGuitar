package com.openguitar.game;

import com.openguitar.beatmap.SongContext;

import java.nio.file.Path;

/**
 * Wpis biblioteki utworów - jeden plik audio + opcjonalna gotowa beatmapa.
 *
 * @param audioPath   ścieżka do pliku audio (.mp3/.wav/.aiff)
 * @param beatmapPath docelowa/istniejąca ścieżka pliku JSON (siostra audio)
 * @param title       wyświetlana nazwa (nazwa pliku bez rozszerzenia)
 * @param context     załadowana beatmapa, lub {@code null} jeśli JSON nie istnieje / niewczytywalny
 */
public record SongEntry(
        Path audioPath,
        Path beatmapPath,
        String title,
        SongContext context
) {
    public boolean hasBeatmap() {
        return context != null;
    }
}
