package com.openguitar.game;

import com.openguitar.game.view.PersonaFonts;
import com.openguitar.game.view.PersonaPalette;
import com.openguitar.game.view.PersonaText;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
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
        return new FloatingPopup(text, null, x, y, color, 30, now, 720_000_000L, Style.JUDGMENT);
    }

    static FloatingPopup multiplier(int mult, double centerX, double centerY) {
        long now = System.nanoTime();
        return new FloatingPopup(
                "x" + mult,
                "MULTIPLIER",
                centerX,
                centerY,
                PersonaPalette.AQUA_BRIGHT,
                52,
                now,
                1_450_000_000L,
                Style.MULTIPLIER);
    }

    static FloatingPopup combo(int combo, double centerX, double centerY) {
        long now = System.nanoTime();
        double size = combo >= 50 ? 50 : 40;
        return new FloatingPopup(
                combo + " COMBO",
                null,
                centerX,
                centerY - 28,
                PersonaPalette.COMBO,
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
                PersonaPalette.MISS,
                40,
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

        if (subtext != null) {
            PersonaText.draw(g, subtext, x, drawY - fontSize * 0.62,
                    PersonaFonts.label(15), PersonaPalette.WHITE_DIM,
                    null, 0, null, PersonaText.SLANT, TextAlignment.CENTER);
        }

        // Poświata pod napisem (większe popupy) — drugi przebieg bez alokacji
        Font font = PersonaFonts.display(fontSize);
        if (style == Style.MULTIPLIER || style == Style.COMBO) {
            PersonaText.draw(g, text, x, drawY + 2,
                    PersonaFonts.display(fontSize * 1.1),
                    PersonaPalette.alpha(color, 0.22),
                    null, 0, null, PersonaText.SLANT, TextAlignment.CENTER);
        }

        // Właściwy napis: pochył + gruby obrys + kontrastowy cień
        PersonaText.draw(g, text, x, drawY, font, color,
                PersonaPalette.BLACK, Math.max(2.5, fontSize * 0.07),
                PersonaPalette.alpha(PersonaPalette.AQUA, 0.5),
                PersonaText.SLANT, TextAlignment.CENTER);

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
