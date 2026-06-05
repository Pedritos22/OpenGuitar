package com.openguitar.game.view;

import javafx.scene.paint.Color;

/**
 * Paleta kolorów UI w stylu Persona 3 Reload.
 * Kolory jako {@code static final} — bez alokacji w pętli renderowania Canvas.
 */
public final class PersonaPalette {

    private PersonaPalette() {}

    // ── bazowe ────────────────────────────────────────────────────────────
    public static final Color BLACK       = Color.web("#03060d");
    public static final Color INK         = Color.web("#050b16");
    public static final Color DEEP_BLUE   = Color.web("#0a1d3a");
    public static final Color NAVY        = Color.web("#06101f");
    public static final Color STEEL       = Color.web("#12304f");

    // ── akcenty ─────────────────────────────────────────────────────────────
    public static final Color AQUA        = Color.web("#00d4ff");
    public static final Color AQUA_BRIGHT = Color.web("#7df9ff");
    public static final Color TEAL        = Color.web("#19e3d0");
    public static final Color ELECTRIC    = Color.web("#2f7dff");

    // ── tekst ────────────────────────────────────────────────────────────────
    public static final Color WHITE       = Color.web("#f2f8ff");
    public static final Color WHITE_DIM   = Color.web("#a9c8e8");
    public static final Color MUTED       = Color.web("#5d7da3");

    // ── stany ────────────────────────────────────────────────────────────────
    public static final Color PERFECT     = Color.web("#7df9ff");
    public static final Color GREAT       = Color.web("#5eead4");
    public static final Color MISS        = Color.web("#ff5d73");
    public static final Color WARN        = Color.web("#ffb020");
    public static final Color COMBO       = Color.web("#ffd166");

    /**
     * Kolory ścieżek — wszystkie w rodzinie błękit/cyjan/turkus, ale wyraźnie
     * rozróżnialne (różny odcień i jasność), żeby nuty czytały się na pierwszy rzut oka.
     */
    private static final Color[] LANES = {
            Color.web("#00e5ff"), // 0 — aqua
            Color.web("#3b82f6"), // 1 — błękit
            Color.web("#2dd4bf"), // 2 — turkus
            Color.web("#8b9bff"), // 3 — chłodny fiolet-błękit
    };

    public static Color lane(int i) {
        return LANES[Math.floorMod(i, LANES.length)];
    }

    /** Półprzezroczysty wariant koloru (np. poświata). */
    public static Color alpha(Color base, double a) {
        return Color.color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }
}
