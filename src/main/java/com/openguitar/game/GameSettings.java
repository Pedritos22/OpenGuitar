package com.openguitar.game;

import javafx.scene.input.KeyCode;

import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ustawienia użytkownika — plik {@code settings.properties}, zapis przez {@link #save()}.
 * Obejmuje: klawisze ścieżek, odliczanie, popupy trafień/combo, głośności (lobby, utwór, UI SFX),
 * dźwięki trafień, czas na reakcję ({@link #noteLookAheadMs()}), offset nut, odliczanie po pauzie,
 * pełny ekran przy starcie.
 */
public final class GameSettings {

    private static final Logger LOG = Logger.getLogger(GameSettings.class.getName());

    private static final Path DEFAULT_FILE = AppPaths.settingsFile();
    /** Nadpisywany w testach — domyślnie {@link #DEFAULT_FILE}. */
    private static Path storageFile = DEFAULT_FILE;

    /** Liczba ścieżek = liczba konfigurowalnych klawiszy. */
    public static final int LANES = GameScreen.LANES;

    public static final int COUNTDOWN_MIN = 0;
    public static final int COUNTDOWN_MAX = 5;

    public static final int VOLUME_MIN = 0;
    public static final int VOLUME_MAX = 100;

    /** Zakres czasu pojawiania się nut przed hit-line (ms). */
    public static final int REACTION_TIME_MIN_MS = 50;
    public static final int REACTION_TIME_MAX_MS = 2_200;
    public static final int REACTION_TIME_STEP_MS = 50;
    public static final int REACTION_TIME_DEFAULT_MS = 1_650;
    /** Stare presety pozostają wyłącznie do migracji wcześniejszych ustawień. */
    public static final int[] REACTION_TIME_MS = {2_200, 1_650, 1_200};
    public static final int REACTION_TIME_DEFAULT = 1;

    /** Przesunięcie nut względem audio (ms): dodatnie opóźnia nuty dla opóźnionego odsłuchu. */
    public static final int NOTE_OFFSET_MIN_MS = -500;
    public static final int NOTE_OFFSET_MAX_MS = 500;
    public static final int NOTE_OFFSET_STEP_MS = 10;

    /** Obsługiwane języki UI (dodaj {@code messages_xx.properties} w {@code i18n/}). */
    public static final String[] LOCALE_TAGS = {"pl", "en", "de", "es", "fr", "it"};
    public static final String LOCALE_DEFAULT = "pl";

    private static final KeyCode[] DEFAULT_KEYS = {
            KeyCode.D, KeyCode.F, KeyCode.J, KeyCode.K
    };

    private static GameSettings instance;

    private final KeyCode[] laneKeys = DEFAULT_KEYS.clone();
    private int countdownSeconds = 3;
    private boolean showHitPopups = true;
    /** Głośność muzyki menu / wyników (0–100). */
    private int lobbyMusicVolume = 100;
    /** Głośność odtwarzanego utworu w grze (0–100). */
    private int songMusicVolume = 100;
    /** Dźwięki trafień (PERFECT/GREAT/MISS/combo) podczas gry. */
    private boolean gameplayHitSfx = true;
    /** Głośność krótkich efektów UI (kliknięcia menu, nawigacja). */
    private int uiSfxVolume = 72;
    /** Dokładny czas na reakcję w ms. */
    private int reactionTimeMs = REACTION_TIME_DEFAULT_MS;
    /** Przesunięcie nut względem zegara audio w ms. */
    private int noteOffsetMs = 0;
    /** Komunikaty combo, mnożnika i zerwania combo nad torami. */
    private boolean showComboPopups = true;
    /** Odliczanie po wznowieniu z pauzy (gdy countdown &gt; 0). */
    private boolean countdownOnResume = true;
    /** Uruchom grę w trybie pełnoekranowym. */
    private boolean fullscreenOnStart = false;
    /** Wycisz całą aplikację, gdy jej okno nie ma fokusu. */
    private boolean muteWhenUnfocused = true;
    /** Tag języka UI (BCP 47), np. {@code pl} lub {@code en}. */
    private String localeTag = LOCALE_DEFAULT;

    /** Discord Api Settings */
    /** App Id z discord developers */
    private long discordAppId = 1516739980096180264L;
    /** Link do githuba gry */
    private String githubLink = "https://github.com/Pedritos22/OpenGuitar";


    private GameSettings() {}

    /** Globalna instancja (wczytywana z dysku przy pierwszym użyciu). */
    public static synchronized GameSettings get() {
        if (instance == null) {
            instance = new GameSettings();
            instance.load();
        }
        return instance;
    }

    /**
     * Resetuje singleton i opcjonalnie wskazuje inny plik (tylko testy w tym pakiecie).
     * {@code path == null} przywraca domyślny {@code settings.properties}.
     */
    static synchronized void resetForTests(Path path) {
        instance = null;
        storageFile = path == null ? DEFAULT_FILE : path.toAbsolutePath();
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

    public int lobbyMusicVolume() {
        return lobbyMusicVolume;
    }

    public int songMusicVolume() {
        return songMusicVolume;
    }

    public boolean gameplayHitSfx() {
        return gameplayHitSfx;
    }

    public int uiSfxVolume() {
        return uiSfxVolume;
    }

    public int reactionTimePreset() {
        int closest = 0;
        for (int i = 1; i < REACTION_TIME_MS.length; i++) {
            if (Math.abs(REACTION_TIME_MS[i] - reactionTimeMs)
                    < Math.abs(REACTION_TIME_MS[closest] - reactionTimeMs)) {
                closest = i;
            }
        }
        return closest;
    }

    /** Czas pojawiania się nut w ms przed hit-line (z presetu czasu na reakcję). */
    public int noteLookAheadMs() {
        return reactionTimeMs;
    }

    /** Etykieta czasu pojawiania się nut, np. {@code "2.2 s"}. */
    public String reactionTimeLabel() {
        return I18n.format("settings.seconds",
                String.format(Locale.ROOT, "%.2f", reactionTimeMs / 1000.0));
    }

    public int noteOffsetMs() {
        return noteOffsetMs;
    }

    public String noteOffsetLabel() {
        return I18n.format("settings.milliseconds", noteOffsetMs);
    }

    public boolean showComboPopups() {
        return showComboPopups;
    }

    public boolean countdownOnResume() {
        return countdownOnResume;
    }

    public boolean fullscreenOnStart() {
        return fullscreenOnStart;
    }

    public boolean muteWhenUnfocused() {
        return muteWhenUnfocused;
    }

    public String localeTag() {
        return localeTag;
    }

    /** Etykieta bieżącego języka w UI ustawień. */
    public String localeLabel() {
        return I18n.get("locale.name." + localeTag);
    }

    public double lobbyMusicVolumeScale() {
        return volumeScale(lobbyMusicVolume);
    }

    public double songMusicVolumeScale() {
        return volumeScale(songMusicVolume);
    }

    public double uiSfxVolumeScale() {
        return volumeScale(uiSfxVolume);
    }

    private static double volumeScale(int percent) {
        return Math.max(VOLUME_MIN, Math.min(VOLUME_MAX, percent)) / 100.0;
    }

    public long discordAppId() {
        return discordAppId;
    }

    public String githubLink() {
        return githubLink;
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

    public void setLobbyMusicVolume(int percent) {
        lobbyMusicVolume = clampVolume(percent);
    }

    public void setSongMusicVolume(int percent) {
        songMusicVolume = clampVolume(percent);
    }

    public void setGameplayHitSfx(boolean enabled) {
        gameplayHitSfx = enabled;
    }

    public void setUiSfxVolume(int percent) {
        uiSfxVolume = clampVolume(percent);
    }

    public void setReactionTimePreset(int preset) {
        int clamped = Math.max(0, Math.min(REACTION_TIME_MS.length - 1, preset));
        setReactionTimeMs(REACTION_TIME_MS[clamped]);
    }

    public void adjustReactionTimePreset(int delta) {
        setReactionTimePreset(reactionTimePreset() + delta);
    }

    public void setReactionTimeMs(int milliseconds) {
        reactionTimeMs = Math.max(REACTION_TIME_MIN_MS, Math.min(REACTION_TIME_MAX_MS, milliseconds));
    }

    public void setNoteOffsetMs(int milliseconds) {
        noteOffsetMs = Math.max(NOTE_OFFSET_MIN_MS, Math.min(NOTE_OFFSET_MAX_MS, milliseconds));
    }

    public void setShowComboPopups(boolean show) {
        showComboPopups = show;
    }

    public void setCountdownOnResume(boolean enabled) {
        countdownOnResume = enabled;
    }

    public void setFullscreenOnStart(boolean enabled) {
        fullscreenOnStart = enabled;
    }

    public void setMuteWhenUnfocused(boolean enabled) {
        muteWhenUnfocused = enabled;
    }

    public void setLocaleTag(String tag) {
        localeTag = normalizeLocaleTag(tag);
        I18n.setLocaleTag(localeTag);
    }

    public void resetToDefaults() {
        System.arraycopy(DEFAULT_KEYS, 0, laneKeys, 0, laneKeys.length);
        countdownSeconds = 3;
        showHitPopups = true;
        lobbyMusicVolume = 100;
        songMusicVolume = 100;
        gameplayHitSfx = true;
        uiSfxVolume = 72;
        reactionTimeMs = REACTION_TIME_DEFAULT_MS;
        noteOffsetMs = 0;
        showComboPopups = true;
        countdownOnResume = true;
        fullscreenOnStart = false;
        muteWhenUnfocused = true;
    }

    public void cycleLocale(int delta) {
        int idx = localeIndex(localeTag);
        int next = Math.floorMod(idx + delta, LOCALE_TAGS.length);
        setLocaleTag(LOCALE_TAGS[next]);
    }

    static String normalizeLocaleTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return LOCALE_DEFAULT;
        }
        String t = tag.trim().toLowerCase(Locale.ROOT);
        for (String supported : LOCALE_TAGS) {
            if (supported.equals(t)) {
                return t;
            }
        }
        return LOCALE_DEFAULT;
    }

    private static int localeIndex(String tag) {
        for (int i = 0; i < LOCALE_TAGS.length; i++) {
            if (LOCALE_TAGS[i].equals(tag)) {
                return i;
            }
        }
        return 0;
    }

    public void adjustLobbyMusicVolume(int delta) {
        setLobbyMusicVolume(lobbyMusicVolume + delta);
    }

    public void adjustSongMusicVolume(int delta) {
        setSongMusicVolume(songMusicVolume + delta);
    }

    private static int clampVolume(int percent) {
        return Math.max(VOLUME_MIN, Math.min(VOLUME_MAX, percent));
    }

    // ── trwałość ──────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.isRegularFile(storageFile)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(storageFile)) {
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
        lobbyMusicVolume = parseInt(p.getProperty("audio.lobby.volume"), lobbyMusicVolume,
                VOLUME_MIN, VOLUME_MAX);
        songMusicVolume = parseInt(p.getProperty("audio.song.volume"), songMusicVolume,
                VOLUME_MIN, VOLUME_MAX);
        gameplayHitSfx = Boolean.parseBoolean(
                p.getProperty("audio.gameplay.sfx", Boolean.toString(gameplayHitSfx)));
        uiSfxVolume = parseInt(p.getProperty("audio.ui.sfx.volume"), uiSfxVolume,
                VOLUME_MIN, VOLUME_MAX);
        setNoteOffsetMs(parseInt(p.getProperty("gameplay.note.offset.ms"), noteOffsetMs,
                NOTE_OFFSET_MIN_MS, NOTE_OFFSET_MAX_MS));
        String reactionMsRaw = p.getProperty("gameplay.reaction.time.ms");
        if (reactionMsRaw != null) {
            setReactionTimeMs(parseInt(reactionMsRaw, reactionTimeMs,
                    REACTION_TIME_MIN_MS, REACTION_TIME_MAX_MS));
        } else {
            String legacyPreset = p.getProperty("gameplay.reaction.time");
            if (legacyPreset == null) {
                legacyPreset = p.getProperty("gameplay.note.speed");
            }
            setReactionTimePreset(parseInt(legacyPreset, REACTION_TIME_DEFAULT,
                    0, REACTION_TIME_MS.length - 1));
        }
        showComboPopups = Boolean.parseBoolean(
                p.getProperty("popups.combo", Boolean.toString(showComboPopups)));
        countdownOnResume = Boolean.parseBoolean(
                p.getProperty("gameplay.countdown.resume", Boolean.toString(countdownOnResume)));
        fullscreenOnStart = Boolean.parseBoolean(
                p.getProperty("display.fullscreen.start", Boolean.toString(fullscreenOnStart)));
        muteWhenUnfocused = Boolean.parseBoolean(
                p.getProperty("audio.mute.unfocused", Boolean.toString(muteWhenUnfocused)));
        setLocaleTag(p.getProperty("display.locale", localeTag));
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
        p.setProperty("audio.lobby.volume", Integer.toString(lobbyMusicVolume));
        p.setProperty("audio.song.volume", Integer.toString(songMusicVolume));
        p.setProperty("audio.gameplay.sfx", Boolean.toString(gameplayHitSfx));
        p.setProperty("audio.ui.sfx.volume", Integer.toString(uiSfxVolume));
        p.setProperty("gameplay.note.offset.ms", Integer.toString(noteOffsetMs));
        p.setProperty("gameplay.reaction.time.ms", Integer.toString(reactionTimeMs));
        p.setProperty("popups.combo", Boolean.toString(showComboPopups));
        p.setProperty("gameplay.countdown.resume", Boolean.toString(countdownOnResume));
        p.setProperty("display.fullscreen.start", Boolean.toString(fullscreenOnStart));
        p.setProperty("audio.mute.unfocused", Boolean.toString(muteWhenUnfocused));
        p.setProperty("display.locale", localeTag);
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się utworzyć katalogu ustawień " + storageFile, ex);
            return;
        }
        try (OutputStream out = Files.newOutputStream(storageFile)) {
            p.store(out, "OpenGuitar — ustawienia użytkownika");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się zapisać ustawień do " + storageFile, ex);
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
                    for (KeyCode fallback : DEFAULT_KEYS) {
                        boolean used = false;
                        for (int k = 0; k <= i; k++) {
                            if (laneKeys[k] == fallback) {
                                used = true;
                                break;
                            }
                        }
                        if (!used) {
                            laneKeys[j] = fallback;
                            break;
                        }
                    }
                }
            }
        }
    }
}
