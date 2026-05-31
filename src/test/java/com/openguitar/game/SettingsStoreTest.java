package com.openguitar.game;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreTest
{

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateDefaultConfigWhenMissing() throws Exception {
        Path file = tempDir.resolve("config.properties");
        SettingsStore store = new SettingsStore(file);

        assertTrue(Files.exists(file), "Plik config powinien zostać utworzony automatycznie");
        assertEquals(KeyCode.D, store.current().laneKey(0));
        assertEquals(KeyCode.F, store.current().laneKey(1));
        assertEquals(KeyCode.J, store.current().laneKey(2));
        assertEquals(KeyCode.K, store.current().laneKey(3));
    }

    @Test
    void shouldPersistAndReloadLaneKeys() throws Exception {
        Path file = tempDir.resolve("config.properties");
        SettingsStore store = new SettingsStore(file);

        GameSettings updated = store.current()
                .withLaneKey(0, KeyCode.A)
                .withLaneKey(1, KeyCode.S)
                .withLaneKey(2, KeyCode.L)
                .withLaneKey(3, KeyCode.SEMICOLON);
        store.save(updated);

        SettingsStore reloaded = new SettingsStore(file);
        assertEquals(KeyCode.A, reloaded.current().laneKey(0));
        assertEquals(KeyCode.S, reloaded.current().laneKey(1));
        assertEquals(KeyCode.L, reloaded.current().laneKey(2));
        assertEquals(KeyCode.SEMICOLON, reloaded.current().laneKey(3));
    }

    @Test
    void shouldFallbackToDefaultsForBrokenConfig() throws Exception {
        Path file = tempDir.resolve("config.properties");
        Files.writeString(file, """
                lane.0=Q
                lane.1=Q
                lane.2=Q
                lane.3=Q
                """);

        SettingsStore store = new SettingsStore(file);
        assertEquals(GameSettings.defaults().laneKeys()[0], store.current().laneKey(0));
        assertTrue(store.current().hasUniqueKeys());
    }

    @Test
    void shouldRejectDuplicateKeysInGameSettings() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new GameSettings(new KeyCode[]{KeyCode.D, KeyCode.D, KeyCode.J, KeyCode.K}));
        assertTrue(ex.getMessage().contains("unikal"));
    }

    @Test
    void shouldParsePropertiesIntoGameSettings() {
        Properties props = new Properties();
        props.setProperty("lane.0", "A");
        props.setProperty("lane.1", "S");
        props.setProperty("lane.2", "D");
        props.setProperty("lane.3", "F");

        GameSettings settings = GameSettings.fromProperties(props);
        assertEquals(KeyCode.A, settings.laneKey(0));
        assertEquals(KeyCode.S, settings.laneKey(1));
        assertEquals(KeyCode.D, settings.laneKey(2));
        assertEquals(KeyCode.F, settings.laneKey(3));
    }
}
