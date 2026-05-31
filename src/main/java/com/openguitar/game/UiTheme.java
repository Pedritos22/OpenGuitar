package com.openguitar.game;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Wspólna paleta i style CSS dla menu oraz ekranu gry.
 */
public final class UiTheme {

    public static final String BG          = "#06080f";
    public static final String BG_ELEVATED = "#0f1522";
    public static final String BG_CARD     = "#141c2b";
    public static final String BG_CARD_HOVER = "#1a2436";
    public static final String BORDER      = "#243044";
    public static final String TEXT        = "#f1f5f9";
    public static final String TEXT_MUTED   = "#94a3b8";
    public static final String TEXT_DIM     = "#64748b";
    public static final String ACCENT      = "#6366f1";
    public static final String ACCENT_SOFT = "#818cf8";

    private static final String[] LANE_HEX = {
            "#34d399", "#f87171", "#fbbf24", "#60a5fa"
    };

    private static final Color[] LANE_COLORS = {
            Color.web(LANE_HEX[0]),
            Color.web(LANE_HEX[1]),
            Color.web(LANE_HEX[2]),
            Color.web(LANE_HEX[3])
    };

    private UiTheme() {}

    public static Color laneColor(int lane) {
        return LANE_COLORS[Math.floorMod(lane, LANE_COLORS.length)];
    }

    public static String laneHex(int lane) {
        return LANE_HEX[Math.floorMod(lane, LANE_HEX.length)];
    }

    public static Color canvasBgTop()    { return Color.web("#0c1220"); }
    public static Color canvasBgBottom() { return Color.web("#05070d"); }
    public static Color canvasVignette() { return Color.color(0, 0, 0, 0.45); }

    public static Font font(double size) {
        return Font.font(Font.getDefault().getFamily(), size);
    }

    public static Font fontBold(double size) {
        return Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, size);
    }

    public static Font fontSemiBold(double size) {
        return Font.font(Font.getDefault().getFamily(), FontWeight.SEMI_BOLD, size);
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
        return baseButton(ACCENT, "#ffffff", "bold");
    }

    public static String warnButton() {
        return baseButton("#d97706", "#ffffff", "bold");
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
             + "-fx-background-radius: 8;"
             + "-fx-border-color: " + BORDER + ";"
             + "-fx-border-radius: 8;"
             + "-fx-border-width: 1;";
    }

    public static String cardRow(boolean ready) {
        String accent = ready ? "#22c55e" : "#f59e0b";
        return "-fx-background-color: " + BG_CARD + ";"
             + "-fx-background-radius: 10;"
             + "-fx-border-color: " + BORDER + " " + BORDER + " " + BORDER + " " + accent + ";"
             + "-fx-border-width: 1 1 1 3;"
             + "-fx-border-radius: 10;";
    }

    public static String cardRowHover(boolean ready) {
        String accent = ready ? "#34d399" : "#fbbf24";
        return "-fx-background-color: " + BG_CARD_HOVER + ";"
             + "-fx-background-radius: 10;"
             + "-fx-border-color: " + ACCENT_SOFT + " " + BORDER + " " + BORDER + " " + accent + ";"
             + "-fx-border-width: 1 1 1 3;"
             + "-fx-border-radius: 10;";
    }

    public static String badgeReady() {
        return "-fx-background-color: #052e1a;"
             + "-fx-text-fill: #6ee7b7;"
             + "-fx-padding: 4 10 4 10;"
             + "-fx-background-radius: 6;"
             + "-fx-font-weight: bold;"
             + "-fx-font-size: 10px;";
    }

    public static String badgePending() {
        return "-fx-background-color: #3b2206;"
             + "-fx-text-fill: #fcd34d;"
             + "-fx-padding: 4 10 4 10;"
             + "-fx-background-radius: 6;"
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
