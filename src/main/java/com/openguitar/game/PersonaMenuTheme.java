package com.openguitar.game;

import com.openguitar.game.view.PersonaFonts;
import javafx.scene.text.Font;

/**
 * Paleta i style CSS menu — układ inspirowany Persona 3 Reload
 * (głęboki granat + elektryczny cyjan, ostre kąty, brak zaokrągleń).
 */
public final class PersonaMenuTheme {

    public static final String BG_DEEP      = "#03060d";
    public static final String BG_PANEL     = "#0a1d3a";
    public static final String BG_ROW       = "#081326";
    public static final String BG_ROW_HOVER = "#122040";
    public static final String BORDER       = "#1e4a7a";
    public static final String BORDER_BRIGHT = "#3d9eff";
    public static final String TEXT         = "#f2f8ff";
    public static final String TEXT_MUTED   = "#a9c8e8";
    public static final String TEXT_DIM     = "#5d7da3";
    public static final String ACCENT       = "#00d4ff";
    public static final String ACCENT_DEEP  = "#0b3a6b";
    public static final String ACCENT_GLOW  = "#7df9ff";
    public static final String READY        = "#00e5ff";
    public static final String PENDING      = "#ffb020";

    /** Rodzina krojów UI (Rajdhani) do CSS. */
    private static final String UI = "'Rajdhani'";

    /** Wspólny rozmiar przycisków w wierszu listy i nagłówku. */
    public static final double BTN_HEIGHT = 34;
    public static final double BTN_WIDTH_ROW = 90;

    private PersonaMenuTheme() {}

    public static Font titleFont() {
        return PersonaFonts.display(34);
    }

    public static Font labelFont(double size) {
        return PersonaFonts.label(size);
    }

    public static Font bodyFont(double size) {
        return PersonaFonts.body(size);
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
        return "-fx-background-color: linear-gradient(to right, " + ACCENT + ", transparent);"
             + "-fx-min-height: 2;"
             + "-fx-max-height: 2;"
             + "-fx-pref-height: 2;";
    }

    public static String sectionLabel() {
        return "-fx-text-fill: " + ACCENT + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 12px;";
    }

    public static String statusBar() {
        return "-fx-background-color: transparent;"
             + "-fx-border-color: transparent;"
             + "-fx-padding: 0;";
    }

    public static String indexLabel() {
        return "-fx-text-fill: " + ACCENT + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 13px;";
    }

    public static String statusText() {
        return "-fx-text-fill: " + TEXT_DIM + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-size: 11px;";
    }

    public static String cardRow(boolean ready) {
        String stripe = ready ? READY : PENDING;
        return "-fx-background-color: " + BG_ROW + ";"
             + "-fx-border-color: " + BORDER + " " + BORDER + " " + BORDER + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 3;";
    }

    public static String cardRowHover(boolean ready) {
        String stripe = ready ? ACCENT : PENDING;
        return "-fx-background-color: linear-gradient(to right, #122040, #0a1830);"
             + "-fx-border-color: " + BORDER_BRIGHT + " " + BORDER + " " + BORDER + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 4;";
    }

    /** Wyraźne, trwałe zaznaczenie (mocniejsze niż hover): jasne tło + gruby akcent. */
    public static String cardRowSelected(boolean ready) {
        String stripe = ready ? ACCENT_GLOW : PENDING;
        return "-fx-background-color: linear-gradient(to right,"
             + " rgba(0, 229, 255, 0.30), rgba(11, 58, 107, 0.45));"
             + "-fx-border-color: " + ACCENT_GLOW + " " + BORDER_BRIGHT + " "
             + BORDER_BRIGHT + " " + stripe + ";"
             + "-fx-border-width: 1 1 1 6;";
    }

    public static String badgeReady() {
        return badgeBase(READY, "rgba(0, 229, 255, 0.12)");
    }

    public static String badgePending() {
        return badgeBase(PENDING, "rgba(255, 176, 32, 0.12)");
    }

    private static String badgeBase(String color, String bg) {
        return "-fx-background-color: " + bg + ";"
             + "-fx-text-fill: " + color + ";"
             + "-fx-font-family: " + UI + ";"
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
                "#03060d", BORDER_BRIGHT);
    }

    public static String rowActionWarn() {
        return rowButtonFilled(
                "linear-gradient(to bottom, #ffc04d, #3d2808)",
                "#1a0f00", PENDING);
    }

    public static String toolbarButton() {
        return rowButtonOutline(ACCENT_GLOW, BORDER);
    }

    private static String rowButtonFilled(String background, String fg, String border) {
        return "-fx-background-color: " + background + ";"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-size: 12px;"
             + "-fx-font-weight: bold;"
             + "-fx-padding: 0 14 0 14;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + border + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }

    private static String rowButtonOutline(String fg, String border) {
        return "-fx-background-color: rgba(0, 212, 255, 0.06);"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-size: 12px;"
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

    /** Panel nakładki z historią podejść. */
    public static String historyPanel() {
        return "-fx-background-color: linear-gradient(to bottom, #0a1d3a, #050b16);"
             + "-fx-border-color: " + BORDER_BRIGHT + " " + BORDER + " " + BORDER + " " + ACCENT + ";"
             + "-fx-border-width: 1 1 1 4;";
    }

    /** Pojedynczy wiersz historii; lewy akcent w kolorze rangi. */
    public static String historyRow(String accentHex) {
        return "-fx-background-color: " + BG_ROW + ";"
             + "-fx-border-color: transparent transparent transparent " + accentHex + ";"
             + "-fx-border-width: 0 0 0 3;";
    }

    /** Panel nakładki ustawień (jak historia, ale z innym akcentem). */
    public static String settingsPanel() {
        return settingsPanelCompact();
    }

    /** Lżejszy panel ustawień — cieńsza ramka, mniej „ciężki” gradient. */
    public static String settingsPanelCompact() {
        return "-fx-background-color: linear-gradient(to bottom, rgba(10, 29, 58, 0.94), rgba(5, 11, 22, 0.97));"
             + "-fx-border-color: " + BORDER + " " + BORDER + " " + BORDER + " " + ACCENT + ";"
             + "-fx-border-width: 1 1 1 2;";
    }

    /** Wiersz ustawienia (etykieta + kontrolka). */
    public static String settingRow() {
        return settingRowCompact();
    }

    /** Cienki wiersz ustawienia — bez pełnego tła karty, tylko delikatna linia. */
    public static String settingRowCompact() {
        return "-fx-background-color: transparent;"
             + "-fx-border-color: transparent transparent rgba(30, 74, 122, 0.45) transparent;"
             + "-fx-border-width: 0 0 1 0;"
             + "-fx-padding: 4 2 4 2;";
    }

    /** Grupa powiązanych wierszy (np. cztery ścieżki klawiszy). */
    public static String settingGroup() {
        return "-fx-background-color: rgba(8, 19, 38, 0.5);"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-width: 1;"
             + "-fx-padding: 4 8 6 8;";
    }

    /** Nagłówek sekcji w panelu ustawień (mniejszy niż sectionLabel). */
    public static String settingsSectionLabel() {
        return "-fx-text-fill: " + TEXT_DIM + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 10px;";
    }

    /** Wysokość kompaktowych kontrolek w ustawieniach. */
    public static final double SETTINGS_BTN_HEIGHT = 28;

    /** Klawiszowy „cap” pokazujący przypisany przycisk ścieżki. */
    public static String keyCap(boolean listening) {
        return keyCapCompact(listening);
    }

    /** Mniejszy key-cap do panelu ustawień. */
    public static String keyCapCompact(boolean listening) {
        String border = listening ? ACCENT_GLOW : BORDER;
        String bg = listening ? "rgba(125, 249, 255, 0.14)" : "rgba(0, 212, 255, 0.04)";
        return "-fx-background-color: " + bg + ";"
             + "-fx-text-fill: " + (listening ? ACCENT_GLOW : TEXT_MUTED) + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-size: 12px;"
             + "-fx-font-weight: bold;"
             + "-fx-padding: 0 8 0 8;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + border + ";"
             + "-fx-border-width: " + (listening ? 2 : 1) + ";"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }

    /** Mały przycisk-stepper (np. +/- przy odliczaniu). */
    public static String stepperButton() {
        return stepperButtonCompact();
    }

    /** Kompaktowy stepper w ustawieniach. */
    public static String stepperButtonCompact() {
        return "-fx-background-color: rgba(0, 212, 255, 0.04);"
             + "-fx-text-fill: " + ACCENT_GLOW + ";"
             + "-fx-font-family: " + UI + ";"
             + "-fx-font-size: 11px;"
             + "-fx-font-weight: bold;"
             + "-fx-padding: 0 8 0 8;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }

    /** Suwak głośności w stylu P3R (akcent cyjanowy). */
    public static String volumeSlider() {
        return "-fx-accent: " + ACCENT + ";"
             + "-fx-background-color: transparent;"
             + "-fx-padding: 0;"
             + "-fx-control-inner-background: " + BG_PANEL + ";";
    }

    /** Przycisk z ikoną zębatki (font systemowy, by glif na pewno się renderował). */
    public static String gearButton() {
        return "-fx-background-color: rgba(0, 212, 255, 0.06);"
             + "-fx-text-fill: " + ACCENT_GLOW + ";"
             + "-fx-font-family: 'System';"
             + "-fx-font-size: 16px;"
             + "-fx-padding: 0 10 0 10;"
             + "-fx-background-radius: 0;"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-width: 1;"
             + "-fx-border-radius: 0;"
             + "-fx-cursor: hand;";
    }
}
