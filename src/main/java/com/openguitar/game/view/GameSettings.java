package com.openguitar.game.view;

import javafx.scene.input.KeyCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Ustawienia gry zapisywane w pliku konfiguracyjnym.
 *
 * <p>Na ten moment obejmują tylko keybindy czterech ścieżek, ale format jest
 * przygotowany tak, żeby dało się go rozszerzyć bez rozbijania reszty kodu.</p>
 */
public final class GameSettings
{

    public static final int LANES = 4;
    public static final String KEY_LANE_PREFIX = "lane.";

    private final KeyCode[] laneKeys;

    public GameSettings(KeyCode[] laneKeys) {
        Objects.requireNonNull(laneKeys, "laneKeys");
        if (laneKeys.length != LANES) {
            throw new IllegalArgumentException("laneKeys musi mieć dokładnie " + LANES + " elementy");
        }
        this.laneKeys = Arrays.copyOf(laneKeys, laneKeys.length);
        validateUnique(this.laneKeys);
    }

    public static GameSettings defaults() {
        return new GameSettings(new KeyCode[]{
                KeyCode.D, KeyCode.F, KeyCode.J, KeyCode.K
        });
    }

    public static GameSettings fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        KeyCode[] keys = new KeyCode[LANES];
        GameSettings defaults = defaults();
        for (int lane = 0; lane < LANES; lane++) {
            String raw = properties.getProperty(KEY_LANE_PREFIX + lane);
            keys[lane] = parseKeyCode(raw, defaults.laneKey(lane));
        }
        return new GameSettings(keys);
    }

    public void toProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        for (int lane = 0; lane < laneKeys.length; lane++) {
            properties.setProperty(KEY_LANE_PREFIX + lane, laneKeys[lane].name());
        }
    }

    public KeyCode laneKey(int lane) {
        checkLaneIndex(lane);
        return laneKeys[lane];
    }

    public String laneKeyName(int lane) {
        return laneKey(lane).getName();
    }

    public KeyCode[] laneKeys() {
        return Arrays.copyOf(laneKeys, laneKeys.length);
    }

    public GameSettings withLaneKey(int lane, KeyCode key) {
        checkLaneIndex(lane);
        Objects.requireNonNull(key, "key");
        KeyCode[] next = laneKeys();
        next[lane] = key;
        return new GameSettings(next);
    }

    public boolean hasUniqueKeys() {
        try {
            validateUnique(laneKeys);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void checkLaneIndex(int lane) {
        if (lane < 0 || lane >= LANES) {
            throw new IllegalArgumentException("lane musi być w zakresie [0.." + (LANES - 1) + "], otrzymano " + lane);
        }
    }

    private static void validateUnique(KeyCode[] keys) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) {
                throw new IllegalArgumentException("laneKeys[" + i + "] nie może być nullem");
            }
            for (int j = i + 1; j < keys.length; j++) {
                if (keys[i] == keys[j]) {
                    throw new IllegalArgumentException("Keybindy muszą być unikalne; duplikat: " + keys[i].name());
                }
            }
        }
    }

    private static KeyCode parseKeyCode(String raw, KeyCode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return KeyCode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
