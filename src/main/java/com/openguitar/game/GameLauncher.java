package com.openguitar.game;

/**
 * Plain Java entry point used by native launchers such as jpackage.
 */
public final class GameLauncher {

    private GameLauncher() {
    }

    public static void main(String[] args) {
        GameApp.main(args);
    }
}
