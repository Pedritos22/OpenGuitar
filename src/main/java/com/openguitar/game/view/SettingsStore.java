package com.openguitar.game.view;

import javafx.scene.input.KeyCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ładuje i zapisuje ustawienia gry do pliku konfiguracyjnego.
 */
public final class SettingsStore {

    private static final Logger LOG = Logger.getLogger(SettingsStore.class.getName());

    private final Path file;
    private GameSettings current;

    public SettingsStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
        this.current = loadOrCreate();
    }

    public synchronized GameSettings current() {
        return current;
    }

    public synchronized void update(GameSettings newSettings) {
        current = Objects.requireNonNull(newSettings, "newSettings");
    }

    public synchronized void updateLaneKey(int lane, KeyCode key) {
        current = current.withLaneKey(lane, key);
    }

    public synchronized void resetDefaults() {
        current = GameSettings.defaults();
    }

    public synchronized void save() throws IOException {
        save(current);
    }

    public synchronized void save(GameSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        current = settings;

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Properties props = new Properties();
        settings.toProperties(props);

        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "OpenGuitar configuration");
        }
    }

    public Path file() {
        return file;
    }

    private GameSettings loadOrCreate() {
        if (!Files.isRegularFile(file)) {
            try {
                GameSettings defaults = GameSettings.defaults();
                save(defaults);
                return defaults;
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Nie udało się utworzyć domyślnego pliku konfiguracyjnego", ex);
                return GameSettings.defaults();
            }
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            return GameSettings.fromProperties(props);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Nie udało się wczytać configu, używam wartości domyślnych", ex);
            return GameSettings.defaults();
        }
    }
}
