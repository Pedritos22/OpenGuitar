package com.openguitar.game.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Rysuje nutę jako ostro ścięty kryształ / odłamek szkła w stylu Persona 3 Reload.
 *
 * <p><b>Wydajność:</b> kształt jednostkowy (wyśrodkowany w 0,0, „promień” ~1) jest
 * stałą; per-nutę tylko skalujemy/ścinamy współrzędne do statycznych tablic
 * roboczych — bez alokacji obiektów w pętli renderowania. Render odbywa się wyłącznie
 * na wątku JavaFX, więc statyczne bufory są bezpieczne.</p>
 *
 * Klasa czysto wizualna — dostaje gotowe współrzędne i stan (nie zna logiki gry).
 */
public final class CrystalNote {

    /** Sześciowierzchołkowy „odłamek”: ostry czubek u góry i dołu, szersze boki. */
    private static final double[] UX = { 0.00,  0.72,  0.46, 0.00, -0.46, -0.72 };
    private static final double[] UY = {-1.00, -0.28,  0.55, 1.00,  0.55, -0.28 };
    private static final int N = UX.length;

    /** Lekki pochył kryształu — dynamiczny „pęd” charakterystyczny dla P3R. */
    private static final double SHEAR = 0.18;

    private static final double[] SX = new double[N];
    private static final double[] SY = new double[N];
    private static final double[] GX = new double[N];
    private static final double[] GY = new double[N];
    private static final double[] TRI_X = new double[3];
    private static final double[] TRI_Y = new double[3];

    private CrystalNote() {}

    /**
     * @param cx,cy  środek nuty na ekranie
     * @param w,h    szerokość/wysokość (już przeskalowane perspektywą)
     * @param depth  0 = horyzont, 1 = hit-line (steruje jasnością/odbłyskiem)
     * @param lane   bazowy kolor ścieżki
     */
    public static void draw(GraphicsContext g, double cx, double cy,
                            double w, double h, double depth, Color lane) {
        double halfW = w * 0.5;
        double halfH = h * 0.5;

        // poświata (większy, rozmyty kształt pod spodem)
        shape(GX, GY, cx, cy, halfW * 1.4, halfH * 1.25);
        g.setFill(PersonaPalette.alpha(lane, 0.10 + 0.22 * (1 - depth)));
        g.fillPolygon(GX, GY, N);

        // korpus kryształu
        shape(SX, SY, cx, cy, halfW, halfH);
        g.setFill(lane);
        g.fillPolygon(SX, SY, N);

        // górna fasetka „szkła” (czubek + dwa górne wierzchołki) — jasny odblask
        TRI_X[0] = SX[0]; TRI_Y[0] = SY[0];
        TRI_X[1] = SX[1]; TRI_Y[1] = SY[1];
        TRI_X[2] = SX[5]; TRI_Y[2] = SY[5];
        g.setFill(PersonaPalette.alpha(PersonaPalette.WHITE, 0.30 + 0.30 * depth));
        g.fillPolygon(TRI_X, TRI_Y, 3);

        // krawędź neonowa
        g.setStroke(depth > 0.7 ? PersonaPalette.AQUA_BRIGHT : PersonaPalette.alpha(lane, 0.9));
        g.setLineWidth(depth > 0.82 ? 2.0 : 1.2);
        g.strokePolygon(SX, SY, N);
    }

    /** Receptor na hit-line — kontur kryształu jako „cel” trafienia. */
    public static void drawReceptor(GraphicsContext g, double cx, double cy,
                                    double w, double h, Color lane, boolean held) {
        double halfW = w * 0.5;
        double halfH = h * 0.5;

        shape(SX, SY, cx, cy, halfW, halfH);
        g.setFill(PersonaPalette.alpha(PersonaPalette.BLACK, 0.55));
        g.fillPolygon(SX, SY, N);

        if (held) {
            g.setFill(PersonaPalette.alpha(lane, 0.45));
            g.fillPolygon(SX, SY, N);
        }

        g.setStroke(held ? PersonaPalette.AQUA_BRIGHT : lane);
        g.setLineWidth(held ? 3.0 : 1.8);
        g.strokePolygon(SX, SY, N);
    }

    private static void shape(double[] outX, double[] outY,
                              double cx, double cy, double halfW, double halfH) {
        for (int i = 0; i < N; i++) {
            double ux = UX[i];
            double uy = UY[i];
            outX[i] = cx + (ux + SHEAR * uy) * halfW;
            outY[i] = cy + uy * halfH;
        }
    }
}
