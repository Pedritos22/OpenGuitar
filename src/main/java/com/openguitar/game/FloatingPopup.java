package com.openguitar.game;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Krótkotrwały napis unoszący się nad grą (combo, mnożnik, judgment).
 */
final class FloatingPopup {

    enum Style {
        /** Nad ścieżką — PERFECT / GREAT / MISS. */
        JUDGMENT,
        /** Środek ekranu — x2 / x3 / x4. */
        MULTIPLIER,
        /** Środek — 10 COMBO!, 50 COMBO! itd. */
        COMBO,
        /** Środek — utrata combo. */
        COMBO_BREAK
    }

    private final String text;
    private final String subtext;
    private final double x;
    private final double y;
    private final Color color;
    private final double baseFontSize;
    private final long startNanos;
    private final long durationNanos;
    private final Style style;

    private FloatingPopup(
            String text,
            String subtext,
            double x,
            double y,
            Color color,
            double baseFontSize,
            long startNanos,
            long durationNanos,
            Style style) {
        this.text = text;
        this.subtext = subtext;
        this.x = x;
        this.y = y;
        this.color = color;
        this.baseFontSize = baseFontSize;
        this.startNanos = startNanos;
        this.durationNanos = durationNanos;
        this.style = style;
    }

    static FloatingPopup judgment(String text, double x, double y, Color color) {
        long now = System.nanoTime();
        return new FloatingPopup(text, null, x, y, color, 20, now, 720_000_000L, Style.JUDGMENT);
    }

    static FloatingPopup multiplier(int mult, double centerX, double centerY) {
        long now = System.nanoTime();
        return new FloatingPopup(
                "x" + mult + "!",
                "MULTIPLIER",
                centerX,
                centerY,
                Color.web(UiTheme.ACCENT_SOFT),
                36,
                now,
                1_450_000_000L,
                Style.MULTIPLIER);
    }

    static FloatingPopup combo(int combo, double centerX, double centerY) {
        long now = System.nanoTime();
        double size = combo >= 50 ? 34 : 28;
        return new FloatingPopup(
                combo + " COMBO!",
                null,
                centerX,
                centerY - 28,
                Color.web("#fb923c"),
                size,
                now,
                1_150_000_000L,
                Style.COMBO);
    }

    static FloatingPopup comboBreak(double centerX, double centerY) {
        long now = System.nanoTime();
        return new FloatingPopup(
                "COMBO BREAK",
                null,
                centerX,
                centerY,
                Color.web("#f87171"),
                30,
                now,
                1_050_000_000L,
                Style.COMBO_BREAK);
    }

    boolean isExpired(long nowNanos) {
        return nowNanos - startNanos >= durationNanos;
    }

    void render(GraphicsContext g, long nowNanos) {
        double t = (nowNanos - startNanos) / (double) durationNanos;
        if (t >= 1.0) return;

        double rise = switch (style) {
            case MULTIPLIER, COMBO, COMBO_BREAK -> 70;
            case JUDGMENT -> 42;
        };
        double yOff = -rise * easeOutCubic(t);

        double alpha = t < 0.6 ? 1.0 : 1.0 - (t - 0.6) / 0.4;

        double scale = switch (style) {
            case MULTIPLIER -> 1.0 + 0.5 * pulse(t);
            case COMBO -> 1.0 + 0.35 * pulse(t);
            case COMBO_BREAK -> 1.0 + 0.2 * (1.0 - t);
            case JUDGMENT -> 1.0 + 0.2 * (1.0 - t);
        };

        double fontSize = baseFontSize * scale;
        double drawY = y + yOff;

        g.save();
        g.setGlobalAlpha(alpha);
        g.setTextAlign(TextAlignment.CENTER);

        String family = Font.getDefault().getFamily();
        if (subtext != null) {
            g.setFill(Color.color(1, 1, 1, 0.75));
            g.setFont(Font.font(family, 14));
            g.fillText(subtext, x, drawY - fontSize * 0.55);
        }

        g.setFill(color);
        g.setFont(Font.font(family, FontWeight.BOLD, fontSize));
        g.fillText(text, x, drawY);

        // Delikatna poświata pod napisem (większe popupy)
        if (style == Style.MULTIPLIER || style == Style.COMBO) {
            g.setFill(color.deriveColor(0, 1, 1, 0.18));
            g.setFont(Font.font(family, FontWeight.BOLD, fontSize * 1.08));
            g.fillText(text, x, drawY + 2);
        }

        g.restore();
    }

    private static double easeOutCubic(double t) {
        return 1.0 - Math.pow(1.0 - t, 3);
    }

    /** Krótki „punch” na początku animacji. */
    private static double pulse(double t) {
        if (t > 0.35) return 0;
        return Math.sin(t / 0.35 * Math.PI);
    }
}
