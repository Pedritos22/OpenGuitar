package com.openguitar.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Resolves writable application data paths for development and packaged builds. */
final class AppPaths {

    private static final String APP_NAME = "OpenGuitar";

    private AppPaths() {
    }

    static Path dataDirectory() {
        boolean packaged = System.getProperty("jpackage.app-version") != null;
        return resolveDataDirectory(
                packaged,
                System.getProperty("os.name", ""),
                Paths.get(System.getProperty("user.home", ".")),
                Paths.get("").toAbsolutePath()
        );
    }

    static Path songsDirectory() {
        return dataDirectory().resolve("songs");
    }

    static Path statsFile() {
        return dataDirectory().resolve("stats.db");
    }

    static Path settingsFile() {
        return dataDirectory().resolve("settings.properties");
    }

    static void ensureDataDirectories() throws IOException {
        Files.createDirectories(songsDirectory());
    }

    static Path resolveDataDirectory(boolean packaged, String osName, Path userHome, Path workingDir) {
        if (!packaged) {
            return workingDir.toAbsolutePath().normalize();
        }
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return userHome.resolve("Library").resolve("Application Support").resolve(APP_NAME);
        }
        if (os.contains("win")) {
            return userHome.resolve("AppData").resolve("Local").resolve(APP_NAME);
        }
        return userHome.resolve(".local").resolve("share").resolve(APP_NAME);
    }
}
