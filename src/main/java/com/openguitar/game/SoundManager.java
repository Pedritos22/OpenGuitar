package com.openguitar.game;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Centralny menedżer muzyki menu i krótkich efektów dźwiękowych (JavaFX {@link AudioClip}).
 *
 * <p>Muzyka menu jest przypisana do ekranu: panel startowy {@code song_lobby.mp3},
 * lista utworów {@code song_ending.mp3} (crossfade ~2,8 s przy przejściu). Po grze
 * {@code song_ending.mp3} w pętli na ekranie wyników. Efekty UI: Kenney UI Audio (CC0).</p>
 */
public final class SoundManager {

    private static final Logger LOG = Logger.getLogger(SoundManager.class.getName());

    private static SoundManager instance;


    /** Utwór 1 — panel startowy (ekran tytułowy). */
    private static final String TITLE_TRACK = "/sound/song_lobby.mp3";
    /** Utwór 2 — lista utworów (i ekran wyników). */
    private static final String MENU_TRACK = "/sound/song_ending.mp3";

    private static final String[] LOBBY_TRACKS = {
            TITLE_TRACK,
            MENU_TRACK
    };

    /** Czas wygaszania / pojawiania się kolejnego utworu lobby. */
    private static final double LOBBY_CROSSFADE_SEC = 2.8;

    /** Krótkie efekty dźwiękowe (ścieżki w {@code resources/sound/}). */
    public enum Sfx {
        CLICK_GLASS("sfx_click_glass.wav"),
        NAV("sfx_nav.wav"),
        CONFIRM("sfx_confirm.wav"),
        BACK("sfx_back.wav"),
        PAUSE("sfx_pause.wav"),
        RESUME("sfx_resume.wav"),
        COUNTDOWN_TICK("sfx_countdown_tick.wav"),
        COUNTDOWN_GO("sfx_countdown_go.wav"),
        PERFECT("sfx_perfect.wav"),
        GREAT("sfx_great.wav"),
        MISS("sfx_miss.wav"),
        COMBO("sfx_combo.wav");

        final String file;

        Sfx(String file) {
            this.file = file;
        }
    }

    private final Map<Sfx, AudioClip> clips = new EnumMap<>(Sfx.class);

    private MediaPlayer lobbyPlayer;
    private MediaPlayer lobbyOutgoingPlayer;
    private MediaPlayer overlayPlayer;
    private Timeline lobbyCrossfadeTimeline;
    private ChangeListener<Duration> lobbyCrossfadeArmListener;
    private boolean lobbyActive;
    private boolean resultsActive;
    private boolean lobbyCrossfadeArmed;
    /** Ostatnio odtworzony utwór lobby — by nie powtarzać tego samego dwa razy z rzędu. */
    private String lastLobbyTrack;
    /** Utwór menu wymuszony przez ekran; {@code null} = losowa rotacja. */
    private String forcedTrack;

    private SoundManager() {
        for (Sfx sfx : Sfx.values()) {
            loadClip(sfx);
        }
    }

    public static synchronized SoundManager get() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    /** Odtwarza krótki efekt (bezpieczne z dowolnego wątku). */
    public void play(Sfx sfx) {
        GameLog.fine(LOG, "sound", "play(SFX) " + sfx.name());
        playClip(sfx, GameSettings.get().uiSfxVolumeScale());
    }

    /**
     * Efekt rozgrywki (trafienia na ścieżkach). Respektuje {@link GameSettings#gameplayHitSfx()};
     * każdy judgment ma dedykowany plik WAV (np. {@link Sfx#PERFECT}).
     */
    public void playGameplay(Sfx sfx) {
        if (!GameSettings.get().gameplayHitSfx()) {
            return;
        }
        play(sfx);
    }

    /** Stosuje bieżącą głośność lobby do aktywnych odtwarzaczy menu/wyników. */
    public void refreshLobbyVolume() {
        runFx(this::applyLobbyVolume);
    }

    private void playClip(Sfx sfx, double volume) {
        Runnable task = () -> {
            AudioClip clip = clips.get(sfx);
            if (clip != null) {
                // Nie zmieniamy rate na współdzielonym AudioClip — to psuje inne odtworzenia.
                clip.setVolume(volume);
                clip.play();
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /** Muzyka panelu startowego (utwór 1, zapętlony). */
    public void playTitleMusic() {
        GameLog.event(LOG, "sound", "playTitleMusic()");
        runFx(() -> switchScreenMusic(TITLE_TRACK));
    }

    /** Muzyka listy utworów (utwór 2, zapętlony). */
    public void playMenuMusic() {
        GameLog.event(LOG, "sound", "playMenuMusic()");
        runFx(() -> switchScreenMusic(MENU_TRACK));
    }

    /** Zatrzymuje muzykę menu — przed wejściem w rozgrywkę. */
    public void enterGameplay() {
        GameLog.event(LOG, "sound", "enterGameplay() — zatrzymuję lobby/wyniki");
        runFx(() -> {
            lobbyActive = false;
            resultsActive = false;
            cancelLobbyCrossfade();
            stopOverlay();
            stopLobbyInternal(false);
            GameLog.event(LOG, "sound", "enterGameplay() — gotowe");
        });
    }

    /** Muzyka ending na ekranie wyników (delikatna pętla). */
    public void playResultsMusic() {
        GameLog.event(LOG, "sound", "playResultsMusic()");
        runFx(this::playResultsMusicFx);
    }

    /** Zatrzymuje całą muzykę aplikacji (np. przy zamykaniu okna). */
    public void stopAll() {
        GameLog.event(LOG, "sound", "stopAll()");
        runFx(() -> {
            lobbyActive = false;
            resultsActive = false;
            cancelLobbyCrossfade();
            stopOverlay();
            stopLobbyInternal(false);
        });
    }

    public void dispose() {
        GameLog.event(LOG, "sound", "dispose()");
        stopAll();
    }

    // ── lobby ────────────────────────────────────────────────────────────────

    /**
     * Przełącza muzykę menu na wskazany utwór (zapętlony). Gdy ten sam utwór już gra
     * — nic nie robi. W przeciwnym razie robi crossfade z aktualnego utworu albo
     * startuje od zera, jeśli nic nie gra.
     */
    private void switchScreenMusic(String track) {
        GameLog.event(LOG, "sound", "switchScreenMusic() — " + track);
        forcedTrack = track;
        resultsActive = false;
        stopOverlay();

        if (lobbyActive && track.equals(lastLobbyTrack) && lobbyPlayer != null) {
            GameLog.fine(LOG, "sound", "switchScreenMusic() — utwór już gra, pomijam");
            applyLobbyVolume();
            return;
        }

        lobbyActive = true;
        if (lobbyPlayer != null) {
            cancelLobbyCrossfade();
            crossfadeLobbyToNext(lobbyPlayer, false);
        } else {
            cancelLobbyCrossfade();
            startLobbyTrack(track);
        }
    }

    /** Startuje pojedynczy utwór lobby i uzbraja crossfade przed końcem. */
    private void startLobbyTrack(String track) {
        if (!lobbyActive) {
            return;
        }
        cancelLobbyCrossfade();
        stopLobbyInternal(false);

        lastLobbyTrack = track;
        GameLog.event(LOG, "sound", "startLobbyTrack() — " + track + " vol="
                + String.format("%.2f", lobbyVolume()));
        lobbyPlayer = createPlayer(track, lobbyVolume());
        if (lobbyPlayer == null) {
            GameLog.warn(LOG, "sound", "startLobbyTrack() — nie udało się utworzyć playera: " + track);
            return;
        }

        lobbyPlayer.setOnReady(() -> {
            if (!lobbyActive || lobbyPlayer == null) {
                return;
            }
            armLobbyCrossfade(lobbyPlayer);
        });
        lobbyPlayer.setOnEndOfMedia(() -> Platform.runLater(() -> {
            if (lobbyActive && lobbyPlayer != null && !lobbyCrossfadeArmed) {
                crossfadeLobbyToNext(lobbyPlayer, true);
            }
        }));
        lobbyPlayer.play();
        GameLog.fine(LOG, "sound", "startLobbyTrack() — play() status=" + lobbyPlayer.getStatus());
    }

    /** ~LOBBY_CROSSFADE_SEC przed końcem uruchamia wygaszanie i kolejny utwór. */
    private void armLobbyCrossfade(MediaPlayer player) {
        disarmLobbyCrossfade();
        lobbyCrossfadeArmed = false;

        lobbyCrossfadeArmListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Duration> obs, Duration old, Duration cur) {
                if (!lobbyActive || lobbyCrossfadeArmed || player != lobbyPlayer) {
                    return;
                }
                Duration total = player.getTotalDuration();
                if (total == null || total.isUnknown() || cur == null) {
                    return;
                }
                double remaining = total.subtract(cur).toSeconds();
                if (remaining <= LOBBY_CROSSFADE_SEC && remaining > 0.05) {
                    lobbyCrossfadeArmed = true;
                    GameLog.event(LOG, "sound", "crossfade — start za " + String.format("%.1f", remaining)
                            + "s (track=" + lastLobbyTrack + ")");
                    disarmLobbyCrossfade();
                    crossfadeLobbyToNext(player, false);
                }
            }
        };
        player.currentTimeProperty().addListener(lobbyCrossfadeArmListener);
    }

    private void disarmLobbyCrossfade() {
        if (lobbyPlayer != null && lobbyCrossfadeArmListener != null) {
            lobbyPlayer.currentTimeProperty().removeListener(lobbyCrossfadeArmListener);
        }
        lobbyCrossfadeArmListener = null;
    }

    private void crossfadeLobbyToNext(MediaPlayer outgoing, boolean outgoingSilent) {
        if (!lobbyActive) {
            GameLog.fine(LOG, "sound", "crossfadeLobbyToNext() — pominięte (lobby nieaktywne)");
            return;
        }

        String nextTrack = pickLobbyTrack(lastLobbyTrack);
        GameLog.event(LOG, "sound", "crossfadeLobbyToNext() — next=" + nextTrack
                + " silentOut=" + outgoingSilent);
        lastLobbyTrack = nextTrack;
        double targetVol = lobbyVolume();

        MediaPlayer incoming = createPlayer(nextTrack, 0);
        if (incoming == null) {
            lobbyCrossfadeArmed = false;
            if (outgoingSilent && outgoing != null) {
                outgoing.stop();
                outgoing.dispose();
                lobbyPlayer = null;
                startLobbyTrack(pickLobbyTrack(lastLobbyTrack));
            }
            return;
        }

        incoming.setOnReady(() -> {
            if (lobbyActive && incoming == lobbyPlayer) {
                armLobbyCrossfade(incoming);
            }
        });
        incoming.setOnEndOfMedia(() -> Platform.runLater(() -> {
            if (lobbyActive && incoming == lobbyPlayer && !lobbyCrossfadeArmed) {
                crossfadeLobbyToNext(incoming, true);
            }
        }));
        incoming.play();
        lobbyPlayer = incoming;

        if (!outgoingSilent && outgoing != null && outgoing != incoming) {
            outgoing.setOnEndOfMedia(null);
        }

        Duration fade = Duration.seconds(LOBBY_CROSSFADE_SEC);
        if (!outgoingSilent && outgoing != null && outgoing != incoming) {
            lobbyOutgoingPlayer = outgoing;
            lobbyCrossfadeTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(outgoing.volumeProperty(), outgoing.getVolume()),
                            new KeyValue(incoming.volumeProperty(), 0)),
                    new KeyFrame(fade,
                            e -> finishLobbyCrossfade(outgoing),
                            new KeyValue(outgoing.volumeProperty(), 0),
                            new KeyValue(incoming.volumeProperty(), targetVol)));
        } else {
            incoming.setVolume(0);
            lobbyCrossfadeTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(incoming.volumeProperty(), 0)),
                    new KeyFrame(fade,
                            e -> finishLobbyCrossfade(null),
                            new KeyValue(incoming.volumeProperty(), targetVol)));
        }
        lobbyCrossfadeTimeline.play();
    }

    private void finishLobbyCrossfade(MediaPlayer outgoing) {
        GameLog.fine(LOG, "sound", "finishLobbyCrossfade() — outgoing="
                + (outgoing != null ? outgoing.getStatus() : "null"));
        lobbyCrossfadeTimeline = null;
        lobbyCrossfadeArmed = false;
        if (outgoing != null && outgoing != lobbyPlayer) {
            outgoing.stop();
            outgoing.dispose();
        }
        if (lobbyOutgoingPlayer == outgoing) {
            lobbyOutgoingPlayer = null;
        }
    }

    private void cancelLobbyCrossfade() {
        GameLog.fine(LOG, "sound", "cancelLobbyCrossfade()");
        disarmLobbyCrossfade();
        lobbyCrossfadeArmed = false;
        if (lobbyCrossfadeTimeline != null) {
            lobbyCrossfadeTimeline.stop();
            lobbyCrossfadeTimeline = null;
        }
        if (lobbyOutgoingPlayer != null) {
            lobbyOutgoingPlayer.stop();
            lobbyOutgoingPlayer.dispose();
            lobbyOutgoingPlayer = null;
        }
    }

    private String pickLobbyTrack(String avoid) {
        if (forcedTrack != null) {
            return forcedTrack;
        }
        if (LOBBY_TRACKS.length == 1) {
            return LOBBY_TRACKS[0];
        }
        String picked;
        do {
            picked = LOBBY_TRACKS[ThreadLocalRandom.current().nextInt(LOBBY_TRACKS.length)];
        } while (picked.equals(avoid));
        return picked;
    }

    // ── wyniki ───────────────────────────────────────────────────────────────

    private void playResultsMusicFx() {
        GameLog.event(LOG, "sound", "playResultsMusicFx()");
        lobbyActive = false;
        resultsActive = true;
        stopLobbyInternal();
        stopOverlay();

        overlayPlayer = createPlayer("/sound/song_ending.mp3", lobbyVolume());
        if (overlayPlayer == null) {
            GameLog.warn(LOG, "sound", "playResultsMusicFx() — brak overlay playera");
            return;
        }
        overlayPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        overlayPlayer.play();
        GameLog.fine(LOG, "sound", "playResultsMusicFx() — play status=" + overlayPlayer.getStatus());
    }

    // ── wewnętrzne ───────────────────────────────────────────────────────────

    private void loadClip(Sfx sfx) {
        URL url = SoundManager.class.getResource("/sound/" + sfx.file);
        if (url == null) {
            GameLog.warn(LOG, "sound", "Brak SFX: " + sfx.file);
            return;
        }
        try {
            clips.put(sfx, new AudioClip(url.toExternalForm()));
        } catch (Exception ex) {
            GameLog.warn(LOG, "sound", "Nie udało się załadować SFX " + sfx.file, ex);
        }
    }

    private MediaPlayer createPlayer(String resourcePath, double volume) {
        URL url = SoundManager.class.getResource(resourcePath);
        if (url == null) {
            GameLog.warn(LOG, "sound", "createPlayer() — brak zasobu: " + resourcePath);
            return null;
        }
        try {
            MediaPlayer player = new MediaPlayer(new Media(url.toExternalForm()));
            player.setVolume(volume);
            GameLog.fine(LOG, "sound", "createPlayer() — OK " + resourcePath + " vol="
                    + String.format("%.2f", volume));
            return player;
        } catch (Exception ex) {
            GameLog.warn(LOG, "sound", "createPlayer() — błąd " + resourcePath, ex);
            return null;
        }
    }

    private void stopLobbyInternal() {
        stopLobbyInternal(true);
    }

    private void stopLobbyInternal(boolean cancelCrossfade) {
        if (cancelCrossfade) {
            cancelLobbyCrossfade();
        }
        if (lobbyPlayer != null) {
            GameLog.fine(LOG, "sound", "stopLobbyInternal() — dispose lobby");
            lobbyPlayer.stop();
            lobbyPlayer.dispose();
            lobbyPlayer = null;
        }
    }

    private void stopOverlay() {
        if (overlayPlayer != null) {
            GameLog.fine(LOG, "sound", "stopOverlay() — dispose overlay");
            overlayPlayer.stop();
            overlayPlayer.dispose();
            overlayPlayer = null;
        }
    }

    private void applyLobbyVolume() {
        double vol = lobbyVolume();
        if (lobbyPlayer != null) {
            lobbyPlayer.setVolume(vol);
        }
        if (lobbyOutgoingPlayer != null) {
            lobbyOutgoingPlayer.setVolume(Math.min(lobbyOutgoingPlayer.getVolume(), vol));
        }
        if (overlayPlayer != null && resultsActive) {
            overlayPlayer.setVolume(vol);
        }
    }

    /** Głośność muzyki menu (0.0–1.0) — bezpośrednio ze suwaka użytkownika. */
    private static double lobbyVolume() {
        return GameSettings.get().lobbyMusicVolumeScale();
    }

    /** Głośność utworu w rozgrywce (0.0–1.0) — bezpośrednio ze suwaka użytkownika. */
    public static double songVolume() {
        return GameSettings.get().songMusicVolumeScale();
    }

    private static void runFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
