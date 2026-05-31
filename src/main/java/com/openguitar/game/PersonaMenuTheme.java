package com.openguitar.game;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Paleta i style CSS menu — minimalistyczny układ z akcentem Persona (granat + cyan).
 */
public final class PersonaMenuTheme {

    public static final String BG_DEEP      = "#03060d";
    public static final String BG_PANEL     = "#0c1830";
    public static final String BG_ROW       = "#0a1428";
    public static final String BG_ROW_HOVER = "#122040";
    public static final String BORDER       = "#1e4a7a";
    public static final String BORDER_BRIGHT = "#3d9eff";
    public static final String TEXT         = "#f0f6ff";
    public static final String TEXT_MUTED   = "#7a9bc4";
    public static final String TEXT_DIM     = "#4a6488";
    public static final String ACCENT       = "#00d4ff";
    public static final String ACCENT_DEEP  = "#1565c0";
    public static final String ACCENT_GLOW  = "#4fc3f7";
    public static final String READY        = "#00e5ff";
    public static final String PENDING      = "#ffb020";

    /** Wspólny rozmiar przycisków w wierszu listy i nagłówku. */
    public static final double BTN_HEIGHT = 32;
    public static final double BTN_WIDTH_ROW = 84;

    private PersonaMenuTheme() {}

    public static Font titleFont() {
        return Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 24);
    }

    public static Font labelFont(double size) {
        return Font.font(Font.getDefault().getFamily(), FontWeight.SEMI_BOLD, size);
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
             + "-fx-padding: 0;";
    }

    public static String divider() {
        return "-fx-background-color: " + BORDER + ";"
             + "-fx-min-height: 1;"
             + "-fx-max-height: 1;"
             + "-fx-pref-height: 1;";
    }

    public static String sectionLabel() {
        return "-fx-text-fill: " + TEXT_MUTED + ";"
             + "-fx-font-size: 11px;";
    }

    public static String statusBar() {
        return "-fx-background-color: rgba(8, 18, 36, 0.85);"
             + "-fx-border-color: " + BORDER + " transparent transparent transparent;"
             + "-fx-border-width: 1 0 0 0;"
             + "-fx-padding: 10 0 0 0;";
    }

    public static String indexLabel() {
        return "-fx-text-fill: " + TEXT_DIM + ";"
             + "-fx-font-size: 12px;";
    }

    public static String statusText() {
        return "-fx-text-fill: " + TEXT_DIM + ";"
             + "-fx-font-size: 10px;";
    }

    public static String cardRow(boolean ready) {
        String stripe = ready ? READY : PENDING;
        return "-fx-background-color: " + BG_ROW + ";"
             + "-fx-border-color: " + BORDER + " " + BORDER + " " + BORDER + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 2;";
    }

    public static String cardRowHover(boolean ready) {
        String stripe = ready ? ACCENT : PENDING;
        return "-fx-background-color: " + BG_ROW_HOVER + ";"
             + "-fx-border-color: " + BORDER_BRIGHT + " " + BORDER + " " + BORDER + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 2;";
    }

    public static String badgeReady() {
        return badgeBase(READY, "rgba(0, 229, 255, 0.1)");
    }

    public static String badgePending() {
        return badgeBase(PENDING, "rgba(255, 176, 32, 0.1)");
    }

    private static String badgeBase(String color, String bg) {
        return "-fx-background-color: " + bg + ";"
             + "-fx-text-fill: " + color + ";"
             + "-fx-padding: 0 8 0 8;"
             + "-fx-font-size: 10px;"
             + "-fx-font-weight: bold;"
             + "-fx-alignment: center;"
             + "-fx-min-height: " + (int) BTN_HEIGHT + "px;"
             + "-fx-pref-height: " + (int) BTN_HEIGHT + "px;"
             + "-fx-max-height: " + (int) BTN_HEIGHT + "px;";
    }

    public static String rowActionPrimary() {
        return rowButtonFilled(
                "linear-gradient(to bottom, " + ACCENT_GLOW + ", " + ACCENT_DEEP + ")",
                TEXT, BORDER_BRIGHT);
    }

    public static String rowActionWarn() {
        return rowButtonFilled(
                "linear-gradient(to bottom, #ffc04d, #3d2808)",
                "#ffe0a0", PENDING);
    }

    public static String toolbarButton() {
        return rowButtonOutline(ACCENT_GLOW, BORDER);
    }

    private static String rowButtonFilled(String background, String fg, String border) {
        return "-fx-background-color: " + background + ";"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-font-size: 11px;"
             + "-fx-font-weight: bold;"
             + "-fx-padding: 0 14 0 14;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + border + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }

    private static String rowButtonOutline(String fg, String border) {
        return "-fx-background-color: transparent;"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-font-size: 11px;"
             + "-fx-font-weight: bold;"
             + "-fx-padding: 0 14 0 14;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + border + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }

    public static String emptyCard() {
        return "-fx-background-color: " + BG_PANEL + ";"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-width: 1;"
             + "-fx-padding: 24 16 24 16;";
    }
}
