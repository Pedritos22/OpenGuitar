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

        g.setFill(new LinearGradient(
                0, 0, w * 0.4, h,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(PersonaMenuTheme.BG_DEEP)),
                new Stop(0.55, Color.web("#06101f")),
                new Stop(1, Color.web("#0a1830"))));
        g.fillRect(0, 0, w, h);

        g.setFill(new RadialGradient(
                0.5, 0.5, w * 0.85, h * 0.12, 140,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.12, 0.45, 0.85, 0.35)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillOval(w * 0.55, -40, w * 0.7, h * 0.45);

        g.setFill(new RadialGradient(
                0.5, 0.5, w * 0.1, h * 0.75, 120,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0, 0.55, 0.75, 0.18)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillOval(-60, h * 0.45, w * 0.65, h * 0.55);

        g.setStroke(Color.color(0.15, 0.45, 0.75, 0.12));
        g.setLineWidth(1);
        for (int i = -4; i < 12; i++) {
            double x0 = i * 48 - 20;
            g.strokeLine(x0, 0, x0 + h * 0.55, h);
        }

        g.setStroke(Color.web(PersonaMenuTheme.ACCENT, 0.55));
        g.setLineWidth(2);
        g.strokeLine(w - 72, 18, w - 18, 18);
        g.strokeLine(w - 18, 18, w - 18, 58);
        g.setLineWidth(1);
        g.setStroke(Color.web(PersonaMenuTheme.BORDER_BRIGHT, 0.35));
        g.strokeLine(0, h - 52, w, h - 52);

        g.setFill(Color.web(PersonaMenuTheme.ACCENT, 0.08));
        double[] shardX = {w * 0.02, w * 0.18, w * 0.35};
        for (double sx : shardX) {
            g.fillPolygon(
                    new double[]{sx, sx + 28, sx + 12},
                    new double[]{h * 0.08, h * 0.22, h * 0.35},
                    3);
        }
    }
}
