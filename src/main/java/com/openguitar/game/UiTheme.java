package com.openguitar.game;

import com.openguitar.game.view.PersonaFonts;
import com.openguitar.game.view.PersonaPalette;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Wspólna paleta i style w duchu Persona 3 Reload (Deep Blue / Aqua / White / Black).
 * Fonty delegowane do {@link PersonaFonts}, kolory do {@link PersonaPalette}.
 *
 * <p>Sygnatury metod pozostają zgodne z poprzednią wersją — to wyłącznie rework
 * warstwy widoku, logika gry korzystająca z tych stałych działa bez zmian.</p>
 */
public final class UiTheme {

    public static final String BG            = "#03060d";
    public static final String BG_ELEVATED   = "#0a1d3a";
    public static final String BG_CARD       = "#0a1428";
    public static final String BG_CARD_HOVER = "#122040";
    public static final String BORDER        = "#1e4a7a";
    public static final String TEXT          = "#f2f8ff";
    public static final String TEXT_MUTED    = "#a9c8e8";
    public static final String TEXT_DIM      = "#5d7da3";
    public static final String ACCENT        = "#00d4ff";
    public static final String ACCENT_SOFT   = "#7df9ff";

    private UiTheme() {}

    public static Color laneColor(int lane) {
        return PersonaPalette.lane(lane);
    }

    public static String laneHex(int lane) {
        Color c = PersonaPalette.lane(lane);
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    public static Color canvasBgTop()    { return Color.web("#081326"); }
    public static Color canvasBgBottom() { return Color.web("#03060d"); }
    public static Color canvasVignette() { return Color.color(0, 0, 0, 0.5); }

    public static Font font(double size) {
        return PersonaFonts.body(size);
    }

    public static Font fontBold(double size) {
        return PersonaFonts.heading(size);
    }

    public static Font fontSemiBold(double size) {
        return PersonaFonts.label(size);
    }

    public static String rootStyle() {
        return "-fx-background-color: " + BG + ";";
    }

    public static String scrollPaneStyle() {
        return "-fx-background: transparent;"
             + "-fx-background-color: transparent;"
             + "-fx-border-color: transparent;"
             + "-fx-padding: 0;";
    }

    public static String primaryButton() {
        return baseButton(ACCENT, "#03060d", "bold");
    }

    public static String warnButton() {
        return baseButton("#ffb020", "#03060d", "bold");
    }

    public static String secondaryButton() {
        return baseButton(BG_CARD, TEXT_MUTED, "normal");
    }

    private static String baseButton(String bg, String fg, String weight) {
        return "-fx-background-color: " + bg + ";"
             + "-fx-text-fill: " + fg + ";"
             + "-fx-font-weight: " + weight + ";"
             + "-fx-padding: 9 20 9 20;"
             + "-fx-cursor: hand;"
             + "-fx-background-radius: 2;"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-radius: 2;"
             + "-fx-border-width: 1;";
    }

    public static String cardRow(boolean ready) {
        String accent = ready ? ACCENT : "#ffb020";
        return "-fx-background-color: " + BG_CARD + ";"
             + "-fx-background-radius: 2;"
             + "-fx-border-color: " + BORDER + " " + BORDER + " " + BORDER + " " + accent + ";"
             + "-fx-border-width: 1 1 1 3;"
             + "-fx-border-radius: 2;";
    }

    public static String cardRowHover(boolean ready) {
        String accent = ready ? ACCENT_SOFT : "#ffd166";
        return "-fx-background-color: " + BG_CARD_HOVER + ";"
             + "-fx-background-radius: 2;"
             + "-fx-border-color: " + ACCENT_SOFT + " " + BORDER + " " + BORDER + " " + accent + ";"
             + "-fx-border-width: 1 1 1 3;"
             + "-fx-border-radius: 2;";
    }

    public static String badgeReady() {
        return "-fx-background-color: rgba(0, 212, 255, 0.12);"
             + "-fx-text-fill: #7df9ff;"
             + "-fx-padding: 4 10 4 10;"
             + "-fx-background-radius: 2;"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 10px;";
    }

    public static String badgePending() {
        return "-fx-background-color: rgba(255, 176, 32, 0.12);"
             + "-fx-text-fill: #ffd166;"
             + "-fx-padding: 4 10 4 10;"
             + "-fx-background-radius: 2;"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 10px;";
    }

    public static String dialogPaneStyle() {
        return "-fx-background-color: " + BG_ELEVATED + ";"
             + "-fx-text-fill: " + TEXT + ";";
    }

    public static String dialogHeaderStyle() {
        return "-fx-text-fill: " + TEXT + "; -fx-font-size: 16px; -fx-font-weight: bold;";
    }
}
