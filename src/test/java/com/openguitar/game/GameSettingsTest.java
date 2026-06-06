package com.openguitar.game;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameSettingsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetStorage() {
        GameSettings.resetForTests(null);
    }

    @Test
    void shouldClampCountdownAndVolumes() {
        GameSettings s = GameSettings.get();
        s.setCountdownSeconds(-5);
        assertEquals(0, s.countdownSeconds());
        s.setCountdownSeconds(99);
        assertEquals(5, s.countdownSeconds());

        s.setLobbyMusicVolume(-10);
        s.setSongMusicVolume(200);
        assertEquals(0, s.lobbyMusicVolume());
        assertEquals(100, s.songMusicVolume());
        assertEquals(0.0, s.lobbyMusicVolumeScale(), 1e-9);
        assertEquals(1.0, s.songMusicVolumeScale(), 1e-9);
    }

    @Test
    void adjustVolumeShouldClampAtBounds() {
        GameSettings s = GameSettings.get();
        s.setLobbyMusicVolume(5);
        s.adjustLobbyMusicVolume(-20);
        assertEquals(0, s.lobbyMusicVolume());

        s.setSongMusicVolume(95);
        s.adjustSongMusicVolume(50);
        assertEquals(100, s.songMusicVolume());
    }

    @Test
    void uiSfxVolumeScaleShouldMatchPercent() {
        GameSettings s = GameSettings.get();
        s.setUiSfxVolume(72);
        assertEquals(0.72, s.uiSfxVolumeScale(), 1e-9);
    }

    @Test
    void songVolumeScaleMatchesSoundManager() {
        GameSettings s = GameSettings.get();
        s.setSongMusicVolume(42);
        assertEquals(0.42, s.songMusicVolumeScale(), 1e-9);
        assertEquals(s.songMusicVolumeScale(), SoundManager.songVolume(), 1e-9);
    }

    @Test
    void setLaneKeyShouldSwapWhenKeyAlreadyUsed() {
        GameSettings s = GameSettings.get();
        KeyCode[] before = s.laneKeys();
        assertEquals(KeyCode.D, before[0]);
        assertEquals(KeyCode.F, before[1]);

        s.setLaneKey(0, KeyCode.F);
        assertEquals(KeyCode.F, s.laneKey(0));
        assertEquals(KeyCode.D, s.laneKey(1), "ścieżka 1 powinna dostać poprzedni klawisz 0");
    }

    @Test
    void shouldRoundTripSettingsFile() throws Exception {
        Path props = tempDir.resolve("settings.properties");
        GameSettings.resetForTests(props);

        GameSettings s = GameSettings.get();
        s.setCountdownSeconds(2);
        s.setShowHitPopups(false);
        s.setLobbyMusicVolume(30);
        s.setSongMusicVolume(70);
        s.setGameplayHitSfx(false);
        s.setUiSfxVolume(55);
        s.setReactionTimePreset(0);
        s.setShowComboPopups(false);
        s.setCountdownOnResume(false);
        s.setFullscreenOnStart(true);
        s.setLocaleTag("en");
        s.setLaneKey(2, KeyCode.SPACE);
        s.save();

        GameSettings.resetForTests(props);
        GameSettings loaded = GameSettings.get();

        assertEquals(2, loaded.countdownSeconds());
        assertFalse(loaded.showHitPopups());
        assertEquals(30, loaded.lobbyMusicVolume());
        assertEquals(70, loaded.songMusicVolume());
        assertFalse(loaded.gameplayHitSfx());
        assertEquals(55, loaded.uiSfxVolume());
        assertEquals(0, loaded.reactionTimePreset());
        assertEquals(GameSettings.REACTION_TIME_MS[0], loaded.noteLookAheadMs());
        assertFalse(loaded.showComboPopups());
        assertFalse(loaded.countdownOnResume());
        assertTrue(loaded.fullscreenOnStart());
        assertEquals("en", loaded.localeTag());
        assertEquals(KeyCode.SPACE, loaded.laneKey(2));
    }

    @Test
    void normalizeLocaleTagShouldTrimAndRejectUnknown() {
        assertEquals("pl", GameSettings.normalizeLocaleTag(null));
        assertEquals("pl", GameSettings.normalizeLocaleTag("  "));
        assertEquals("pl", GameSettings.normalizeLocaleTag("invalid"));
        assertEquals("en", GameSettings.normalizeLocaleTag(" EN "));
        assertEquals("it", GameSettings.normalizeLocaleTag("it"));
    }

    @Test
    void setLocaleTagShouldSwitchActiveI18nBundle() {
        GameSettings s = GameSettings.get();
        s.setLocaleTag("de");
        assertEquals("SPIELEN", I18n.get("title.play"));
        s.setLocaleTag("it");
        assertEquals("GIOCA", I18n.get("title.play"));
        assertEquals("Italiano", s.localeLabel());
    }

    @Test
    void cycleLocaleBackwardShouldWrap() {
        GameSettings s = GameSettings.get();
        s.setLocaleTag("en");
        s.cycleLocale(-1);
        assertEquals("pl", s.localeTag());
    }

    @Test
    void shouldLoadLocaleFromSettingsFile() throws Exception {
        Path props = tempDir.resolve("settings.properties");
        Files.writeString(props, "display.locale=fr\n");
        GameSettings.resetForTests(props);

        GameSettings s = GameSettings.get();
        assertEquals("fr", s.localeTag());
        assertEquals("JOUER", I18n.get("title.play"));
    }

    @Test
    void invalidLocaleInFileShouldDefaultToPolish() throws Exception {
        Path props = tempDir.resolve("settings.properties");
        Files.writeString(props, "display.locale=zz\n");
        GameSettings.resetForTests(props);

        assertEquals("pl", GameSettings.get().localeTag());
    }

    @Test
    void localeShouldCycleBetweenSupportedTags() {
        GameSettings s = GameSettings.get();
        assertEquals(6, GameSettings.LOCALE_TAGS.length);
        s.setLocaleTag("pl");
        s.cycleLocale(1);
        assertEquals("en", s.localeTag());
        s.cycleLocale(1);
        assertEquals("de", s.localeTag());
        s.cycleLocale(1);
        assertEquals("es", s.localeTag());
        s.cycleLocale(1);
        assertEquals("fr", s.localeTag());
        s.cycleLocale(1);
        assertEquals("it", s.localeTag());
        s.cycleLocale(1);
        assertEquals("pl", s.localeTag());
        assertEquals("Polski", s.localeLabel());
    }

    @Test
    void reactionTimePresetShouldClampAndCycle() {
        GameSettings s = GameSettings.get();
        s.setReactionTimePreset(99);
        assertEquals(GameSettings.REACTION_TIME_MS.length - 1, s.reactionTimePreset());
        assertEquals("1.2 s", s.reactionTimeLabel());

        s.adjustReactionTimePreset(-1);
        assertEquals("1.7 s", s.reactionTimeLabel());
        s.adjustReactionTimePreset(-1);
        assertEquals("2.2 s", s.reactionTimeLabel());
        s.adjustReactionTimePreset(-1);
        assertEquals("2.2 s", s.reactionTimeLabel());
    }

    @Test
    void shouldLoadLegacyNoteSpeedProperty() throws Exception {
        Path props = tempDir.resolve("settings.properties");
        Files.writeString(props, "gameplay.note.speed=0\n");
        GameSettings.resetForTests(props);
        GameSettings s = GameSettings.get();
        assertEquals(0, s.reactionTimePreset());
        assertEquals("2.2 s", s.reactionTimeLabel());
    }

    @Test
    void shouldIgnoreInvalidValuesInFile() throws Exception {
        Path props = tempDir.resolve("settings.properties");
        Files.writeString(props, """
                countdown.seconds=not-a-number
                audio.lobby.volume=999
                lane.0=NOT_A_REAL_KEY
                audio.gameplay.sfx=false
                """);

        GameSettings.resetForTests(props);
        GameSettings s = GameSettings.get();

        assertEquals(3, s.countdownSeconds(), "nieparsowalny countdown -> domyślne 3");
        assertEquals(100, s.lobbyMusicVolume(), "volume poza zakresem -> clamp 100");
        assertEquals(KeyCode.D, s.laneKey(0), "nieznany KeyCode -> domyślny D");
        assertFalse(s.gameplayHitSfx());
    }

    @Test
    void setLaneKeyShouldIgnoreInvalidLaneOrNullKey() {
        GameSettings s = GameSettings.get();
        KeyCode before = s.laneKey(0);
        s.setLaneKey(-1, KeyCode.A);
        s.setLaneKey(0, null);
        assertEquals(before, s.laneKey(0));
        s.setLaneKey(99, KeyCode.B);
        assertEquals(before, s.laneKey(0));
    }

    @Test
    void freshSettingsShouldDefaultToPolishLocale() {
        Path missing = tempDir.resolve("no-settings-yet.properties");
        GameSettings.resetForTests(missing);

        GameSettings s = GameSettings.get();
        assertEquals("pl", s.localeTag());
        assertEquals("GRAJ", I18n.get("title.play"));
    }

    @Test
    void noteLookAheadMsShouldMapPresetsToMilliseconds() {
        GameSettings s = GameSettings.get();
        s.setReactionTimePreset(0);
        assertEquals(2_200, s.noteLookAheadMs());
        s.setReactionTimePreset(1);
        assertEquals(1_650, s.noteLookAheadMs());
        s.setReactionTimePreset(2);
        assertEquals(1_200, s.noteLookAheadMs());
    }

    @Test
    void localeLabelShouldResolveNativeNameForActiveLocale() {
        GameSettings s = GameSettings.get();
        s.setLocaleTag("pl");
        assertEquals("Polski", s.localeLabel());
        s.setLocaleTag("de");
        assertEquals("Deutsch", s.localeLabel());
        s.setLocaleTag("it");
        assertEquals("Italiano", s.localeLabel());
    }

    @Test
    void dedupeShouldFixDuplicateKeysFromFile() throws Exception {
        Path props = tempDir.resolve("settings.properties");
        Files.writeString(props, """
                lane.0=D
                lane.1=D
                lane.2=J
                lane.3=K
                """);

        GameSettings.resetForTests(props);
        GameSettings s = GameSettings.get();

        long distinct = java.util.Arrays.stream(s.laneKeys()).distinct().count();
        assertEquals(GameSettings.LANES, distinct, "każda ścieżka powinna mieć inny klawisz");
    }
}
