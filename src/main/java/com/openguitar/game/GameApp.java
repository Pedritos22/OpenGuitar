package com.openguitar.game;

import com.openguitar.beatmap.BeatmapEngine;
import com.openguitar.beatmap.BeatmapLoader;
import com.openguitar.beatmap.SongContext;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punkt wejścia JavaFX. Steruje przepływem: panel startowy → lista utworów → gra,
 * trzyma jeden {@link Stage} przez cały cykl życia aplikacji.
 *
 * <h3>Tryby uruchomienia</h3>
 * <pre>
 *   bez argumentu           -> panel startowy (GRAJ -> lista utworów z songs/)
 *   audio (.mp3/.wav/.aiff) -> bezpośrednio do gry; jeśli brak JSON-a, najpierw generuje
 *   beatmap.json            -> bezpośrednio do gry
 * </pre>
 * Gdy aplikacja została uruchomiona z konkretnym utworem, po jego zakończeniu
 * okno się zamyka. W trybie normalnym po utworze wracamy do listy utworów.
 */
public class GameApp extends Application {

    private static final Logger LOG = Logger.getLogger(GameApp.class.getName());
    private static final Path SONGS_DIR = Paths.get("songs").toAbsolutePath();
    private static final Path STATS_FILE = Paths.get("stats.db").toAbsolutePath();

    private final StatsStore stats = new StatsStore(STATS_FILE);
    private Stage stage;
    /** Czy po zakończeniu utworu wracamy do menu (true), czy zamykamy okno (false). */
    private boolean returnToMenuAfterSong = true;
    /** Aktualnie aktywny GameScreen - trzymamy żeby móc go zatrzymać przy zamykaniu okna. */
    private GameScreen activeGame;
    private volatile boolean shuttingDown;

    @Override
    public void start(Stage stage) {
        com.openguitar.game.view.PersonaFonts.init(); // ładujemy fonty P3R raz, przed budową scen
        I18n.setLocaleTag(GameSettings.get().localeTag());
        this.stage = stage;
        stage.setResizable(true);
        stage.setTitle(I18n.get("app.title"));
        // F11 przełącza pełny ekran; ESC ma być wolny dla pauzy (nie wychodzi z fullscreen).
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint(I18n.get("app.fullscreen.hint"));
        stage.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                e.consume();
            }
        });
        stage.setOnCloseRequest(e -> {
            e.consume();
            shutdownApplication();
        });
        stage.focusedProperty().addListener((obs, wasFocused, focused) -> {
            SoundManager.get().setWindowFocused(focused);
            if (activeGame != null) {
                activeGame.setWindowFocused(focused);
            }
        });

        SongContext fromArgs = loadFromArgs(getParameters().getRaw());
        if (fromArgs != null) {
            returnToMenuAfterSong = false;
            launchGame(fromArgs);
        } else {
            launchTitle();
        }
        GameLog.event(LOG, "app", "start() — okno gotowe");
        stage.show();
        if (GameSettings.get().fullscreenOnStart()) {
            stage.setFullScreen(true);
            GameLog.fine(LOG, "app", "start() — pełny ekran z ustawień");
        }
    }

    // --------------------------- menu / game switching ---------------------

    private void launchTitle() {
        GameLog.event(LOG, "app", "launchTitle() — panel startowy");
        if (activeGame != null) {
            GameLog.event(LOG, "app", "launchTitle() — zatrzymuję poprzedni GameScreen");
            activeGame.stop();
            activeGame = null;
        }
        TitleScreen title = new TitleScreen(this::launchMenu, this::shutdownApplication);
        Scene titleScene = title.getScene();
        showScene(titleScene, "OpenGuitar");
        SoundManager.get().playTitleMusic();
        GameLog.event(LOG, "app", "launchTitle() — scena tytułowa ustawiona, muzyka 1 start");
        Platform.runLater(() -> {
            if (stage.getScene() == titleScene) {
                titleScene.getRoot().requestFocus();
                GameLog.fine(LOG, "app", "launchTitle() — focus na panelu startowym");
            }
        });
    }

    private void launchMenu() {
        GameLog.event(LOG, "app", "launchMenu() — lista utworów");
        if (activeGame != null) {
            GameLog.event(LOG, "app", "launchMenu() — zatrzymuję poprzedni GameScreen");
            activeGame.stop();
            activeGame = null;
        }
        MenuScreen menu = new MenuScreen(
                SONGS_DIR,
                this::launchGame,
                this::launchTitle,
                stats
        );
        Scene menuScene = menu.getScene();
        showScene(menuScene, "OpenGuitar");
        SoundManager.get().playMenuMusic();
        GameLog.event(LOG, "app", "launchMenu() — scena menu ustawiona, muzyka 2 start");
        Platform.runLater(() -> {
            if (stage.getScene() == menuScene) {
                menuScene.getRoot().requestFocus();
                GameLog.fine(LOG, "app", "launchMenu() — focus na menu");
            }
        });
    }

    private void launchGame(SongContext context) {
        GameLog.event(LOG, "app", "launchGame() — \"" + context.title() + "\" nut="
                + context.notes().size() + " audio=" + context.audioPath());
        if (activeGame != null) {
            GameLog.event(LOG, "app", "launchGame() — zatrzymuję poprzedni GameScreen");
            activeGame.stop();
            activeGame = null;
        }
        SoundManager.get().enterGameplay();
        GameScreen screen = new GameScreen(context, this::onSongFinished, this::launchMenu);
        activeGame = screen;
        screen.setWindowFocused(stage.isFocused());
        showScene(screen.getScene(), "OpenGuitar - " + context.title());
        screen.start();
        GameLog.event(LOG, "app", "launchGame() — GameScreen.start() wywołane");
    }

    /**
     * Podmienia scenę zachowując tryb pełnoekranowy. JavaFX (zwłaszcza na macOS)
     * potrafi wyjść z fullscreen przy {@code setScene} — ponownie go wymuszamy,
     * dzięki czemu po kliknięciu „Graj” gra zostaje na pełnym ekranie aż do
     * świadomego wyjścia (F11).
     */
    private void showScene(Scene scene, String title) {
        boolean wasFullScreen = stage.isFullScreen();
        GameLog.event(LOG, "app", "showScene() — tytuł=\"" + title + "\" fullscreen="
                + wasFullScreen + " → " + scene.getClass().getSimpleName());
        stage.setScene(scene);
        stage.setTitle(title);
        if (wasFullScreen && !stage.isFullScreen()) {
            stage.setFullScreen(true);
            GameLog.fine(LOG, "app", "showScene() — przywrócono fullscreen po setScene");
        }
    }

    @Override
    public void stop() {
        shutdownResources();
    }

    /** Bezpieczne zamknięcie — Maven/javafx:run inaczej raportuje kod 143 (SIGTERM). */
    private void shutdownApplication() {
        if (shuttingDown) {
            GameLog.fine(LOG, "app", "shutdownApplication() — już w trakcie zamykania");
            return;
        }
        shuttingDown = true;
        GameLog.event(LOG, "app", "shutdownApplication()");
        shutdownResources();
        Platform.exit();
        System.exit(0);
    }

    private void shutdownResources() {
        GameLog.event(LOG, "app", "shutdownResources() — activeGame="
                + (activeGame != null ? "tak" : "nie"));
        try {
            if (activeGame != null) {
                activeGame.stop();
                activeGame = null;
            }
        } catch (Exception ex) {
            GameLog.warn(LOG, "app", "shutdownResources() — błąd zatrzymania gry", ex);
        }
        try {
            stats.close();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Błąd przy zamykaniu stats.db", ex);
        }
        SoundManager.get().dispose();
    }

    private void onSongFinished(GameResult result) {
        GameLog.event(LOG, "app", "onSongFinished() — wynik=" + result
                + " returnToMenu=" + returnToMenuAfterSong);
        activeGame = null;
        stats.record(result);
        Platform.runLater(() -> {
            if (returnToMenuAfterSong) {
                launchMenu();
            } else {
                GameLog.event(LOG, "app", "onSongFinished() — zamykam okno (tryb CLI)");
                stage.close();
            }
        });
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
        GameLog.event(LOG, "app", "main() — start JVM");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                GameLog.error(LOG, "app", "nieobsłużony wyjątek w wątku " + thread.getName(), error));
        launch(args);
    }
}
