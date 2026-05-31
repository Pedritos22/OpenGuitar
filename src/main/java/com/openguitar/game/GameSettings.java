package com.openguitar.game;

import javafx.scene.input.KeyCode;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trwała konfiguracja użytkownika (warstwa widoku/wejścia — nie dotyka logiki gry).
 *
 * <p>Przechowywana w pliku {@code settings.properties} w katalogu roboczym. Trzyma:</p>
 * <ul>
 *   <li>przypisanie klawiszy do czterech ścieżek (domyślnie D / F / J / K),</li>
 *   <li>długość odliczania przed startem utworu (0 = wyłączone),</li>
 *   <li>przełącznik animowanych komunikatów trafień (PERFECT/GREAT/MISS).</li>
 * </ul>
 *
 * Singleton — wczytywany leniwie, zapisywany jawnie przez {@link #save()}.
 */
public final class GameSettings {

    private static final Logger LOG = Logger.getLogger(GameSettings.class.getName());

    private static final Path FILE = Paths.get("settings.properties").toAbsolutePath();

    /** Liczba ścieżek = liczba konfigurowalnych klawiszy. */
    public static final int LANES = GameScreen.LANES;

    public static final int COUNTDOWN_MIN = 0;
    public static final int COUNTDOWN_MAX = 5;

    private static final KeyCode[] DEFAULT_KEYS = {
            KeyCode.D, KeyCode.F, KeyCode.J, KeyCode.K
    };

    private static GameSettings instance;

    private final KeyCode[] laneKeys = DEFAULT_KEYS.clone();
    private int countdownSeconds = 3;
    private boolean showHitPopups = true;

    private GameSettings() {}

    /** Globalna instancja (wczytywana z dysku przy pierwszym użyciu). */
    public static synchronized GameSettings get() {
        if (instance == null) {
            instance = new GameSettings();
            instance.load();
        }
        return instance;
    }

    // ── gettery ──────────────────────────────────────────────────────────────

    /** Klawisz przypisany do danej ścieżki (kopia chroni wewnętrzny stan). */
    public KeyCode laneKey(int lane) {
        return laneKeys[Math.floorMod(lane, laneKeys.length)];
    }

    public KeyCode[] laneKeys() {
        return laneKeys.clone();
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public boolean showHitPopups() {
        return showHitPopups;
    }

    // ── settery (bez zapisu — wołaj save() po edycji) ─────────────────────────

    /**
     * Przypisuje klawisz do ścieżki. Jeśli klawisz jest już użyty na innej ścieżce,
     * następuje zamiana (swap), żeby nigdy nie było dwóch ścieżek na tym samym klawiszu.
     */
    public void setLaneKey(int lane, KeyCode key) {
        if (lane < 0 || lane >= laneKeys.length || key == null) {
            return;
        }
        for (int i = 0; i < laneKeys.length; i++) {
            if (i != lane && laneKeys[i] == key) {
                laneKeys[i] = laneKeys[lane]; // swap, by uniknąć duplikatu
                break;
            }
        }
        laneKeys[lane] = key;
    }

    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(COUNTDOWN_MIN, Math.min(COUNTDOWN_MAX, seconds));
    }

    public void setShowHitPopups(boolean show) {
        showHitPopups = show;
    }

    // ── trwałość ──────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się wczytać ustawień — używam domyślnych.", ex);
            return;
        }
        for (int i = 0; i < laneKeys.length; i++) {
            KeyCode parsed = parseKey(p.getProperty("lane." + i));
            if (parsed != null) {
                laneKeys[i] = parsed;
            }
        }
        countdownSeconds = parseInt(p.getProperty("countdown.seconds"), countdownSeconds,
                COUNTDOWN_MIN, COUNTDOWN_MAX);
        showHitPopups = Boolean.parseBoolean(
                p.getProperty("popups.hits", Boolean.toString(showHitPopups)));
        dedupeKeys();
    }

    /** Zapisuje bieżące ustawienia do pliku. */
    public void save() {
        Properties p = new Properties();
        for (int i = 0; i < laneKeys.length; i++) {
            p.setProperty("lane." + i, laneKeys[i].name());
        }
        p.setProperty("countdown.seconds", Integer.toString(countdownSeconds));
        p.setProperty("popups.hits", Boolean.toString(showHitPopups));
        try (OutputStream out = Files.newOutputStream(FILE)) {
            p.store(out, "OpenGuitar — ustawienia użytkownika");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się zapisać ustawień do " + FILE, ex);
        }
    }

    private static KeyCode parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return KeyCode.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            LOG.warning(() -> "Nieznany kod klawisza w ustawieniach: " + raw);
            return null;
        }
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Naprawia ewentualne duplikaty po ręcznej edycji pliku — przywraca domyślne kolizje. */
    private void dedupeKeys() {
        for (int i = 0; i < laneKeys.length; i++) {
            for (int j = i + 1; j < laneKeys.length; j++) {
                if (laneKeys[i] == laneKeys[j]) {
                    laneKeys[j] = DEFAULT_KEYS[j];
                }
            }
        }
    }
}
