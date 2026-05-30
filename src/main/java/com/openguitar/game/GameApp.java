package com.openguitar.game;

import com.openguitar.beatmap.BeatmapLoader;
import com.openguitar.beatmap.Note;
import com.openguitar.beatmap.SongContext;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Punkt wejścia JavaFX.
 *
 * <h3>Argumenty</h3>
 * <pre>
 *   mvn javafx:run                                         # tryb demo (syntetyczna mapa)
 *   mvn javafx:run -Djavafx.args="path/to/beatmap.json"    # rzeczywista beatmapa
 * </pre>
 * Plik audio (z pola {@code audioPath} JSON-a) jest szukany względem
 * katalogu w którym leży plik beatmapy.
 */
public class GameApp extends Application {

    private static final Logger LOG = Logger.getLogger(GameApp.class.getName());

    @Override
    public void start(Stage stage) {
        SongContext context = loadContext(getParameters().getRaw());

        GameScreen screen = new GameScreen(context, result -> {
            LOG.info(() -> "Wynik: " + result);
            // Pokazujemy podsumowanie w konsoli i zamykamy okno - w docelowej grze
            // tu byłoby przejście do ekranu wyników (Scene scoreScene = ...).
            System.out.println("================ KONIEC UTWORU ================");
            System.out.println(result);
            System.out.println("===============================================");
            Platform.runLater(stage::close);
        });

        stage.setScene(screen.getScene());
        stage.setTitle("OpenGuitar - " + context.title());
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> screen.stop());
        stage.show();

        screen.start();
    }

    /**
     * Ładuje {@link SongContext}: z argumentu CLI (ścieżka do JSON) albo
     * generuje proceduralną mapę demonstracyjną, jeśli żaden plik nie został podany.
     */
    private static SongContext loadContext(List<String> args) {
        if (!args.isEmpty()) {
            Path jsonPath = Paths.get(args.get(0)).toAbsolutePath();
            if (Files.isRegularFile(jsonPath)) {
                try {
                    SongContext loaded = new BeatmapLoader().load(jsonPath);
                    // audioPath w JSON jest często względny - rozwiń go względem katalogu beatmapy
                    Path audioAbs = jsonPath.getParent().resolve(loaded.audioPath()).toAbsolutePath();
                    return new SongContext(
                            loaded.songId(), loaded.title(), loaded.bpm(),
                            audioAbs.toString(), loaded.notes()
                    );
                } catch (Exception ex) {
                    LOG.warning("Nie udało się wczytać beatmapy: " + ex.getMessage());
                }
            } else {
                LOG.warning("Plik beatmapy nie istnieje: " + jsonPath);
            }
        }
        LOG.info("Brak beatmapy - uruchamiam tryb demo (proceduralna mapa, bez audio).");
        return demoContext();
    }

    /**
     * Tryb bez audio: 32 nuty co 500ms, naprzemienne ścieżki.
     * Pozwala uruchomić grę i zobaczyć działanie wizualizacji + scoringu
     * bez konieczności posiadania pliku audio.
     */
    private static SongContext demoContext() {
        List<Note> notes = new ArrayList<>();
        int[] pattern = {0, 2, 1, 3, 0, 1, 2, 3, 1, 0, 3, 2};
        int t = 2_000; // pierwsza nuta po 2 sekundach
        int interval = 500;
        for (int i = 0; i < 32; i++) {
            notes.add(new Note(t, pattern[i % pattern.length]));
            t += interval;
        }
        return new SongContext("demo", "Demo (no audio)", 120, "", notes);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
