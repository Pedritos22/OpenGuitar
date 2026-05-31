package com.openguitar.game.view;

import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;

/**
 * Owija treść o stałym rozmiarze (np. {@code 500×720}) w kontener, który
 * skaluje ją jednorodnie do rozmiaru okna z zachowaniem proporcji
 * („letterbox” na czarno). Dzięki temu cały interfejs gry działa w trybie
 * pełnoekranowym i przy zmianie rozmiaru okna, a logika pozostaje rysowana
 * w stałym układzie współrzędnych.
 *
 * <p>Czysta warstwa widoku — transformacja skali jest nakładana na węzeł sceny,
 * więc zdarzenia myszy są automatycznie przeliczane (klikalność zachowana).</p>
 */
public final class FullscreenScaler {

    private FullscreenScaler() {}

    public static Parent wrap(Region content, double baseW, double baseH) {
        content.setMinSize(baseW, baseH);
        content.setPrefSize(baseW, baseH);
        content.setMaxSize(baseW, baseH);

        Scale scale = new Scale(1, 1, 0, 0);
        content.getTransforms().add(scale);

        Group group = new Group(content);
        StackPane holder = new StackPane(group);
        holder.setStyle("-fx-background-color: black;");

        Runnable rescale = () -> {
            double s = Math.min(holder.getWidth() / baseW, holder.getHeight() / baseH);
            if (s <= 0 || Double.isNaN(s) || Double.isInfinite(s)) {
                s = 1;
            }
            scale.setX(s);
            scale.setY(s);
        };
        holder.widthProperty().addListener((o, a, b) -> rescale.run());
        holder.heightProperty().addListener((o, a, b) -> rescale.run());
        return holder;
    }
}
