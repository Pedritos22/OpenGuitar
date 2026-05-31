package com.openguitar.game;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Paleta i style CSS menu w duchu Persona 3 Reload — granat, elektryczny błękit,
 * ostre kąty, uppercase i wysoki kontrast.
 */
public final class PersonaMenuTheme {

    public static final String BG_DEEP     = "#03060d";
    public static final String BG_PANEL    = "#0c1830";
    public static final String BG_ROW      = "#0a1428";
    public static final String BG_ROW_HOVER = "#122040";
    public static final String BORDER      = "#1e4a7a";
    public static final String BORDER_BRIGHT = "#3d9eff";
    public static final String TEXT        = "#f0f6ff";
    public static final String TEXT_MUTED  = "#7a9bc4";
    public static final String TEXT_DIM    = "#4a6488";
    public static final String ACCENT      = "#00d4ff";
    public static final String ACCENT_DEEP = "#1565c0";
    public static final String ACCENT_GLOW = "#4fc3f7";
    public static final String READY       = "#00e5ff";
    public static final String PENDING     = "#ffb020";

    private PersonaMenuTheme() {}

    public static Font displayFont(double size) {
        return Font.font(Font.getDefault().getFamily(), FontWeight.BLACK, size);
    }

    public static Font labelFont(double size) {
        return Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, size);
    }

    public static Font bodyFont(double size) {
        return Font.font(Font.getDefault().getFamily(), FontWeight.NORMAL, size);
    }

    public static String rootOverlay() {
        return "-fx-background-color: transparent;";
    }

    public static String scrollPane() {
        return "-fx-background: transparent;"
             + "-fx-background-color: transparent;"
             + "-fx-border-color: transparent;"
             + "-fx-padding: 0 4 0 0;";
    }

    public static String sessionTag() {
        return "-fx-background-color: " + ACCENT_DEEP + ";"
             + "-fx-text-fill: " + TEXT + ";"
             + "-fx-padding: 3 10 3 10;"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 9px;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + BORDER_BRIGHT + ";"
             + "-fx-border-width: 1 0 1 0;";
    }

    public static String statusBar() {
        return "-fx-background-color: rgba(8, 18, 36, 0.92);"
             + "-fx-border-color: " + BORDER + " transparent " + BORDER_BRIGHT + " transparent;"
             + "-fx-border-width: 1 0 2 0;"
             + "-fx-padding: 8 10 8 10;";
    }

    public static String hintChip() {
        return "-fx-background-color: rgba(12, 24, 48, 0.75);"
             + "-fx-text-fill: " + TEXT_DIM + ";"
             + "-fx-padding: 4 8 4 8;"
             + "-fx-font-size: 8px;"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-width: 1;"
             + "-fx-background-radius: 0;";
    }

    public static String indexLabel() {
        return "-fx-text-fill: " + ACCENT_GLOW + ";"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 14px;";
    }

    public static String cardRow(boolean ready) {
        String stripe = ready ? READY : PENDING;
        return "-fx-background-color: " + BG_ROW + ";"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + BORDER + " " + BORDER + " " + BORDER + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 3;"
             + "-fx-border-radius: 0;";
    }

    public static String cardRowHover(boolean ready) {
        String stripe = ready ? ACCENT : PENDING;
        return "-fx-background-color: " + BG_ROW_HOVER + ";"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + BORDER_BRIGHT + " " + BORDER + " " + BORDER + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 4;"
             + "-fx-border-radius: 0;"
             + "-fx-effect: dropshadow(gaussian, rgba(0, 212, 255, 0.25), 12, 0, 0, 0);";
    }

    public static String badgeReady() {
        return "-fx-background-color: rgba(0, 229, 255, 0.12);"
             + "-fx-text-fill: " + READY + ";"
             + "-fx-padding: 3 8 3 8;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + READY + ";"
             + "-fx-border-width: 1;"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 9px;";
    }

    public static String badgePending() {
        return "-fx-background-color: rgba(255, 176, 32, 0.1);"
             + "-fx-text-fill: " + PENDING + ";"
             + "-fx-padding: 3 8 3 8;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + PENDING + ";"
             + "-fx-border-width: 1;"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 9px;";
    }

    public static String primaryButton() {
        return menuButton(ACCENT_DEEP, TEXT, BORDER_BRIGHT, true);
    }

    public static String warnButton() {
        return menuButton("#3d2808", "#ffe0a0", PENDING, true);
    }

    public static String secondaryButton() {
        return menuButton("transparent", TEXT_MUTED, BORDER, false, 8, 14);
    }

    public static String rowActionButton() {
        return menuButton(ACCENT_DEEP, TEXT, BORDER_BRIGHT, true, 6, 10);
    }

    public static String rowWarnButton() {
        return menuButton("#3d2808", "#ffe0a0", PENDING, true, 6, 10);
    }

    private static String menuButton(String bg, String fg, String border, boolean filled) {
        return menuButton(bg, fg, border, filled, 8, 16);
    }

    private static String menuButton(String bg, String fg, String border, boolean filled, int padY, int padX) {
        String bgRule = filled
                ? "-fx-background-color: linear-gradient(to bottom, " + ACCENT_DEEP + ", " + bg + ");"
                : "-fx-background-color: " + bg + ";";
        return bgRule
             + "-fx-text-fill: " + fg + ";"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 11px;"
             + "-fx-padding: " + padY + " " + padX + " " + padY + " " + padX + ";"
             + "-fx-cursor: hand;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + border + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;";
    }

    public static String emptyCard() {
        return "-fx-background-color: " + BG_PANEL + ";"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-width: 1;"
             + "-fx-padding: 28 20 28 20;";
    }

    public static String listHeaderLine() {
        return "-fx-background-color: linear-gradient(to right, " + ACCENT + ", transparent);"
             + "-fx-min-height: 2;"
             + "-fx-max-height: 2;"
             + "-fx-pref-height: 2;";
    }
}
