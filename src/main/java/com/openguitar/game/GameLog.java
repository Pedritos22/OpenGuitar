package com.openguitar.game;

import javafx.application.Platform;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spójne logi diagnostyczne dla przepływu gry, audio i menu.
 * Włącz poziom FINE dla pakietu {@code com.openguitar.game} w {@code logging.properties},
 * aby zobaczyć szczegóły watchdog'a audio.
 */
public final class GameLog {

    private GameLog() {}

    public static void event(Logger log, String component, String message) {
        event(log, Level.INFO, component, message);
    }

    public static void fine(Logger log, String component, String message) {
        event(log, Level.FINE, component, message);
    }

    public static void warn(Logger log, String component, String message) {
        event(log, Level.WARNING, component, message);
    }

    public static void warn(Logger log, String component, String message, Throwable error) {
        log.log(Level.WARNING, format(component, message), error);
    }

    public static void error(Logger log, String component, String message, Throwable error) {
        log.log(Level.SEVERE, format(component, message), error);
    }

    private static void event(Logger log, Level level, String component, String message) {
        if (log.isLoggable(level)) {
            log.log(level, format(component, message));
        }
    }

    private static String format(String component, String message) {
        return "[" + component + "][" + threadTag() + "] " + message;
    }

    private static String threadTag() {
        return Platform.isFxApplicationThread() ? "FX" : "BG";
    }
}
