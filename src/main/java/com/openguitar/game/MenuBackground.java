package com.openguitar.game;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Tło menu: gradient, poświaty i ukośne pasy w stylu Persona 3 Reload.
 */
final class MenuBackground extends Region {

    private final Canvas canvas = new Canvas();
    private double lastW;
    private double lastH;

    MenuBackground() {
        getChildren().add(canvas);
        widthProperty().addListener((o, a, b) -> resizeAndRedraw());
        heightProperty().addListener((o, a, b) -> resizeAndRedraw());
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        resizeAndRedraw();
    }

    private void resizeAndRedraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w == lastW && h == lastH) {
            return;
        }
        lastW = w;
        lastH = h;
        canvas.setWidth(w);
        canvas.setHeight(h);
        redraw(w, h);
    }

    private void redraw(double w, double h) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // Bazowy gradient granat → czerń (diagonalny, jak w P3R)
        g.setFill(new LinearGradient(
                0, 0, w, h,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#03060d")),
                new Stop(0.5, Color.web("#06101f")),
                new Stop(1, Color.web("#0a1d3a"))));
        g.fillRect(0, 0, w, h);

        // Poświata cyjanowa w prawym górnym rogu
        g.setFill(new RadialGradient(
                0, 0, w * 0.9, h * 0.04, Math.max(w, h) * 0.7,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.0, 0.55, 0.95, 0.40)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, h);

        // Poświata turkusowa w lewym dole
        g.setFill(new RadialGradient(
                0, 0, w * 0.05, h * 0.92, Math.max(w, h) * 0.55,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.0, 0.75, 0.7, 0.22)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, h);

        // Szerokie ukośne pasy (asymetryczne, nachodzące — sygnatura P3R)
        drawSlashBand(g, w, h, w * 0.02, 120, Color.color(0.0, 0.45, 0.85, 0.10));
        drawSlashBand(g, w, h, w * 0.30, 56,  Color.color(0.0, 0.83, 1.0, 0.06));
        drawSlashBand(g, w, h, w * 0.66, 90,  Color.color(0.1, 0.55, 0.95, 0.08));

        // Gęste cienkie linie diagonalne (faktura)
        g.setStroke(Color.color(0.2, 0.55, 0.85, 0.08));
        g.setLineWidth(1);
        double slope = h * 0.6;
        for (int i = -6; i < 26; i++) {
            double x0 = i * 42 - 30;
            g.strokeLine(x0, 0, x0 + slope, h);
        }

        // Odłamki szkła (kryształowe akcenty)
        double[][] shards = {
                {w * 0.05, h * 0.20, 34, 1},
                {w * 0.12, h * 0.66, 22, -1},
                {w * 0.86, h * 0.30, 40, 1},
                {w * 0.78, h * 0.80, 26, -1},
        };
        for (double[] s : shards) {
            drawShard(g, s[0], s[1], s[2], s[3] > 0);
        }

        // Neonowa ramka-narożniki (corner brackets) w stylu UI Persony
        g.setStroke(Color.web(PersonaMenuTheme.ACCENT, 0.75));
        g.setLineWidth(2.5);
        double m = 14, len = 46;
        // prawy-górny
        g.strokeLine(w - m - len, m, w - m, m);
        g.strokeLine(w - m, m, w - m, m + len);
        // lewy-dolny
        g.strokeLine(m, h - m, m + len, h - m);
        g.strokeLine(m, h - m - len, m, h - m);

        // Cienka linia bazowa nad stopką
        g.setStroke(Color.web(PersonaMenuTheme.BORDER_BRIGHT, 0.30));
        g.setLineWidth(1);
        g.strokeLine(0, h - 52, w, h - 52);
    }

    private static void drawSlashBand(GraphicsContext g, double w, double h,
                                      double x, double bandW, Color fill) {
        double skew = h * 0.32;
        g.setFill(fill);
        g.fillPolygon(
                new double[]{x, x + bandW, x + bandW + skew, x + skew},
                new double[]{0, 0, h, h},
                4);
    }

    private static void drawShard(GraphicsContext g, double cx, double cy,
                                  double size, boolean up) {
        double dir = up ? 1 : -1;
        g.setFill(Color.web(PersonaMenuTheme.ACCENT, 0.10));
        g.fillPolygon(
                new double[]{cx, cx + size * 0.55, cx + size * 0.2, cx - size * 0.25},
                new double[]{cy - dir * size, cy, cy + dir * size, cy + dir * size * 0.4},
                4);
        g.setStroke(Color.web(PersonaMenuTheme.ACCENT_GLOW, 0.35));
        g.setLineWidth(1);
        g.strokePolygon(
                new double[]{cx, cx + size * 0.55, cx + size * 0.2, cx - size * 0.25},
                new double[]{cy - dir * size, cy, cy + dir * size, cy + dir * size * 0.4},
                4);
    }
}
