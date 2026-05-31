package com.openguitar.game.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Pomocnik rysowania napisów w stylu Persona 3 Reload na {@link GraphicsContext}
 * (Canvas). Składa razem trzy efekty robione „ręcznie”, bez kosztownych
 * {@code Effect}-ów JavaFX (które wymuszają render do off-screen bufora co klatkę):
 *
 * <ol>
 *   <li><b>Pochył (italic)</b> — ścinanie poziome przez {@code transform()} (shear),
 *       działa na każdy font niezależnie od tego, czy ma krój kursywny.</li>
 *   <li><b>Przesunięty cień</b> — drugi {@code fillText} z offsetem w kontrastowym kolorze.</li>
 *   <li><b>Gruby obrys</b> — {@code strokeText} pod właściwym {@code fillText}.</li>
 * </ol>
 *
 * Klasa jest bezstanowa i nie zna logiki gry.
 */
public final class PersonaText {

    /** Domyślny pochył napisów (ujemny = czubki liter w prawo, jak w P3R). */
    public static final double SLANT = -0.22;

    private PersonaText() {}

    /**
     * Pełny rysunek napisu: pochył + cień + obrys + wypełnienie.
     *
     * @param g        kontekst
     * @param s        tekst
     * @param x        kotwica X (zależna od {@code align})
     * @param baseline kotwica Y (linia bazowa tekstu)
     * @param font     font
     * @param fill     kolor wypełnienia
     * @param outline  kolor obrysu (lub {@code null} — bez obrysu)
     * @param outlineW grubość obrysu w px
     * @param shadow   kolor cienia (lub {@code null} — bez cienia)
     * @param slant    współczynnik ścięcia poziomego (0 = brak)
     * @param align    wyrównanie poziome
     */
    public static void draw(GraphicsContext g, String s, double x, double baseline,
                            Font font, Color fill, Color outline, double outlineW,
                            Color shadow, double slant, TextAlignment align) {
        g.save();
        g.setFont(font);
        g.setTextAlign(align);

        // Pochył realizujemy jako shear wokół punktu (x, baseline): przesuwamy tam
        // układ, ścinamy, rysujemy w (0,0). Dzięki temu wyrównanie i baseline są zachowane.
        if (slant != 0) {
            g.translate(x, baseline);
            g.transform(1, 0, slant, 1, 0, 0);
            x = 0;
            baseline = 0;
        }

        double shadowOff = Math.max(2, font.getSize() * 0.06);
        if (shadow != null) {
            g.setFill(shadow);
            g.fillText(s, x + shadowOff, baseline + shadowOff);
        }
        if (outline != null && outlineW > 0) {
            g.setLineWidth(outlineW);
            g.setStroke(outline);
            g.strokeText(s, x, baseline);
        }
        g.setFill(fill);
        g.fillText(s, x, baseline);

        g.restore();
    }

    /** Wariant skrócony: pochył P3R, biały wypełniacz, czarny obrys, błękitny cień. */
    public static void slanted(GraphicsContext g, String s, double x, double baseline,
                               Font font, Color fill, TextAlignment align) {
        draw(g, s, x, baseline, font, fill,
                PersonaPalette.BLACK, Math.max(2.0, font.getSize() * 0.08),
                PersonaPalette.alpha(PersonaPalette.AQUA, 0.45),
                SLANT, align);
    }

    /** Bez pochyłu — np. drobne podpisy. */
    public static void plain(GraphicsContext g, String s, double x, double baseline,
                             Font font, Color fill, TextAlignment align) {
        g.save();
        g.setFont(font);
        g.setTextAlign(align);
        g.setFill(fill);
        g.fillText(s, x, baseline);
        g.restore();
    }
}
