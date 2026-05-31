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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralny menedżer muzyki menu i krótkich efektów dźwiękowych (JavaFX {@link AudioClip}).
 *
 * <p>Muzyka lobby losowo rotuje między {@code song_lobby.mp3} a {@code song_ending.mp3}
 * z krótkim crossfade'm między utworami. Po zakończeniu gry {@code song_ending.mp3}
 * towarzyszy ekranowi wyników (w pętli).</p>
 *
 * <p>Efekty UI pochodzą z pakietu Kenney UI Audio (CC0) — patrz
 * {@code resources/sound/KENNEY_UI_AUDIO_LICENSE.txt}.</p>
 */
public final class SoundManager {

    private static final Logger LOG = Logger.getLogger(SoundManager.class.getName());

    private static SoundManager instance;

    private static final double SFX_VOL = 0.72;

    private static final String[] LOBBY_TRACKS = {
            "/sound/song_lobby.mp3",
            "/sound/song_ending.mp3"
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
        playClip(sfx, SFX_VOL, 1.0);
    }

    /**
     * Efekt rozgrywki (trafienia na ścieżkach). Respektuje {@link GameSettings#gameplayHitSfx()};
     * PERFECT/GREAT używają szklanego „tap” z lekką modulacją wysokości.
     */
    public void playGameplay(Sfx sfx) {
        if (!GameSettings.get().gameplayHitSfx()) {
            return;
        }
        if (sfx == Sfx.PERFECT) {
            playClip(Sfx.CLICK_GLASS, SFX_VOL, 1.22);
            return;
        }
        if (sfx == Sfx.GREAT) {
            playClip(Sfx.CLICK_GLASS, SFX_VOL, 1.0);
            return;
        }
        play(sfx);
    }

    /** Stosuje bieżącą głośność lobby do aktywnych odtwarzaczy menu/wyników. */
    public void refreshLobbyVolume() {
        runFx(this::applyLobbyVolume);
    }

    private void playClip(Sfx sfx, double volume, double rate) {
        Runnable task = () -> {
            AudioClip clip = clips.get(sfx);
            if (clip != null) {
                clip.setVolume(volume);
                clip.setRate(rate);
                clip.play();
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /** Uruchamia losową rotację muzyki lobby. */
    public void startLobbyMusic() {
        runFx(this::startLobbyMusicFx);
    }

    /** Zatrzymuje muzykę menu — przed wejściem w rozgrywkę. */
    public void enterGameplay() {
        runFx(() -> {
            lobbyActive = false;
            resultsActive = false;
            stopOverlay();
            stopLobbyInternal();
        });
    }

    /** Muzyka ending na ekranie wyników (delikatna pętla). */
    public void playResultsMusic() {
        runFx(this::playResultsMusicFx);
    }

    /** Zatrzymuje całą muzykę aplikacji (np. przy zamykaniu okna). */
    public void stopAll() {
        runFx(() -> {
            lobbyActive = false;
            resultsActive = false;
            stopOverlay();
            stopLobbyInternal();
        });
    }

    public void dispose() {
        stopAll();
    }

    // ── lobby ────────────────────────────────────────────────────────────────

    private void startLobbyMusicFx() {
        stopOverlay();
        stopLobbyInternal();
        lobbyActive = true;
        resultsActive = false;
        lastLobbyTrack = null;
        startLobbyTrack(pickLobbyTrack(null));
    }

    /** Startuje pojedynczy utwór lobby i uzbraja crossfade przed końcem. */
    private void startLobbyTrack(String track) {
        if (!lobbyActive) {
            return;
        }
        cancelLobbyCrossfade();
        stopLobbyInternal(false);

        lastLobbyTrack = track;
        lobbyPlayer = createPlayer(track, lobbyVolume());
        if (lobbyPlayer == null) {
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
            return;
        }

        String nextTrack = pickLobbyTrack(lastLobbyTrack);
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

    private static String pickLobbyTrack(String avoid) {
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
        lobbyActive = false;
        resultsActive = true;
        stopLobbyInternal();
        stopOverlay();

        overlayPlayer = createPlayer("/sound/song_ending.mp3", lobbyVolume());
        if (overlayPlayer == null) {
            return;
        }
        overlayPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        overlayPlayer.play();
    }

    // ── wewnętrzne ───────────────────────────────────────────────────────────

    private void loadClip(Sfx sfx) {
        URL url = SoundManager.class.getResource("/sound/" + sfx.file);
        if (url == null) {
            LOG.warning(() -> "Brak SFX: " + sfx.file);
            return;
        }
        try {
            clips.put(sfx, new AudioClip(url.toExternalForm()));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się załadować SFX " + sfx.file, ex);
        }
    }

    private MediaPlayer createPlayer(String resourcePath, double volume) {
        URL url = SoundManager.class.getResource(resourcePath);
        if (url == null) {
            LOG.warning(() -> "Brak zasobu audio: " + resourcePath);
            return null;
        }
        try {
            MediaPlayer player = new MediaPlayer(new Media(url.toExternalForm()));
            player.setVolume(volume);
            return player;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się załadować " + resourcePath, ex);
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
            lobbyPlayer.stop();
            lobbyPlayer.dispose();
            lobbyPlayer = null;
        }
    }

    private void stopOverlay() {
        if (overlayPlayer != null) {
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
