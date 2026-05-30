package com.openguitar.game;

import com.openguitar.beatmap.BeatmapEngine;
import com.openguitar.beatmap.BeatmapLoader;
import com.openguitar.beatmap.SongContext;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punkt wejścia JavaFX. Steruje przepływem między ekranem menu a ekranem gry,
 * trzyma jeden {@link Stage} przez cały cykl życia aplikacji.
 *
 * <h3>Tryby uruchomienia</h3>
 * <pre>
 *   bez argumentu           -> menu (skanuje folder songs/)
 *   audio (.mp3/.wav/.aiff) -> bezpośrednio do gry; jeśli brak JSON-a, najpierw generuje
 *   beatmap.json            -> bezpośrednio do gry
 * </pre>
 * Gdy aplikacja została uruchomiona z konkretnym utworem, po jego zakończeniu
 * okno się zamyka. Gdy z menu - po utworze wracamy do menu.
 */
public class GameApp extends Application {

    private static final Logger LOG = Logger.getLogger(GameApp.class.getName());
    private static final Path SONGS_DIR = Paths.get("songs").toAbsolutePath();

    private Stage stage;
    /** Czy po zakończeniu utworu wracamy do menu (true), czy zamykamy okno (false). */
    private boolean returnToMenuAfterSong = true;
    /** Aktualnie aktywny GameScreen - trzymamy żeby móc go zatrzymać przy zamykaniu okna. */
    private GameScreen activeGame;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setResizable(false);
        stage.setTitle("OpenGuitar");
        stage.setOnCloseRequest(e -> {
            if (activeGame != null) activeGame.stop();
            Platform.exit();
        });

        SongContext fromArgs = loadFromArgs(getParameters().getRaw());
        if (fromArgs != null) {
            returnToMenuAfterSong = false;
            launchGame(fromArgs);
        } else {
            launchMenu();
        }
        stage.show();
    }

    // --------------------------- menu / game switching ---------------------

    private void launchMenu() {
        if (activeGame != null) {
            activeGame.stop();
            activeGame = null;
        }
        MenuScreen menu = new MenuScreen(
                SONGS_DIR,
                this::launchGame,
                Platform::exit
        );
        stage.setScene(menu.getScene());
        stage.setTitle("OpenGuitar");
    }

    private void launchGame(SongContext context) {
        GameScreen screen = new GameScreen(context, this::onSongFinished);
        activeGame = screen;
        stage.setScene(screen.getScene());
        stage.setTitle("OpenGuitar - " + context.title());
        screen.start();
    }

    private void onSongFinished(GameResult result) {
        LOG.info(() -> "Wynik: " + result);
        activeGame = null;

        // UWAGA: ten callback jest wywoływany z wnętrza AnimationTimer (puls renderowania)
        // lub z setOnEndOfMedia. JavaFX zabrania pokazywania modalnych dialogów
        // (Alert.showAndWait) podczas pulsu/layoutu - dlatego odraczamy do następnego
        // pulsu przez Platform.runLater().
        Platform.runLater(() -> {
            showResult(result);
            if (returnToMenuAfterSong) {
                launchMenu();
            } else {
                stage.close();
            }
        });
    }

    private void showResult(GameResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Koniec utworu");
        alert.setHeaderText(result.totalScore() > 0
                ? "Score: " + result.totalScore()
                : "Score: 0  (spróbuj jeszcze raz!)");
        alert.setContentText(String.format(
                "Hits:      %d%nMisses:    %d%nMax combo: %d",
                result.hits(), result.misses(), result.maxCombo()));
        alert.getDialogPane().setStyle(
                "-fx-background-color: #111827;"
              + "-fx-text-fill: white;");
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.showAndWait();
    }

    // --------------------------- CLI args ---------------------------

    /**
     * Próbuje wyczytać {@link SongContext} z argumentów CLI:
     * <ul>
     *   <li>jeśli to plik audio bez sąsiada .json - generuje beatmapę synchronicznie i ładuje;</li>
     *   <li>jeśli to plik audio z sąsiadem .json - ładuje JSON;</li>
     *   <li>jeśli to .json - ładuje bezpośrednio.</li>
     * </ul>
     * Zwraca {@code null} jeśli żaden argument nie został podany lub coś poszło źle.
     */
    private static SongContext loadFromArgs(List<String> args) {
        if (args.isEmpty()) return null;

        Path input = Paths.get(args.get(0)).toAbsolutePath();
        if (!Files.isRegularFile(input)) {
            LOG.warning("Plik z argumentu nie istnieje: " + input);
            return null;
        }
        String name = input.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".json")) {
                return loadJsonAndResolveAudio(input);
            }
            if (isAudioName(name)) {
                Path json = sibling(input, "json");
                if (!Files.isRegularFile(json)) {
                    LOG.info("Generuję beatmapę dla " + input.getFileName());
                    new BeatmapEngine().generateAndSave(input, json,
                            stripExt(name), stripExt(name));
                }
                return loadJsonAndResolveAudio(json);
            }
            LOG.warning("Nieobsługiwany typ pliku: " + name);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Błąd ładowania " + input, ex);
        }
        return null;
    }

    private static SongContext loadJsonAndResolveAudio(Path json) throws Exception {
        SongContext loaded = new BeatmapLoader().load(json);
        // audioPath w JSON jest relatywny do katalogu beatmapy - rozwijamy do absolutnego.
        Path audioAbs = Optional.ofNullable(json.getParent())
                .orElse(Paths.get("."))
                .resolve(loaded.audioPath())
                .toAbsolutePath();
        return new SongContext(
                loaded.songId(), loaded.title(), loaded.bpm(),
                audioAbs.toString(), loaded.notes()
        );
    }

    private static boolean isAudioName(String name) {
        return name.endsWith(".mp3") || name.endsWith(".wav")
                || name.endsWith(".aiff") || name.endsWith(".flac");
    }

    private static Path sibling(Path p, String newExt) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return p.resolveSibling((dot > 0 ? n.substring(0, dot) : n) + "." + newExt);
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
