package com.openguitar.game.view;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.transform.Shear;
import javafx.util.Duration;

/**
 * Lekkie pomocniki widoku do nadawania węzłom menu charakteru Persona 3 Reload:
 * ukośne ścięcie (Shear) oraz płynne „wysuwanie” przy zaznaczeniu/hoverze.
 *
 * <p>Świadomie operujemy na <b>węzłach sceny</b> (a nie na Canvasie), bo bloki menu
 * muszą pozostać klikalne — JavaFX przelicza zdarzenia myszy przez transformacje,
 * więc ścięty przycisk dalej reaguje poprawnie. Animacje idą przez kompozytor
 * sceny (TranslateTransition), nie obciążając pętli renderowania gry.</p>
 */
public final class PersonaMenuFx {

    /** Domyślne ścięcie poziome bloków menu. */
    public static final double SLANT = -0.28;
    /** Dystans wysunięcia zaznaczonego wiersza (px). */
    public static final double SLIDE = 22;

    private PersonaMenuFx() {}

    /** Nakłada stałe ukośne ścięcie na węzeł. */
    public static void slant(Node node, double shear) {
        node.getTransforms().add(new Shear(shear, 0));
    }

    public static void slant(Node node) {
        slant(node, SLANT);
    }

    /**
     * Dowiązuje płynne wysuwanie w prawo (oś X) sterowane stanem zaznaczenia.
     * Zwraca obiekt sterujący, którego {@code set(boolean)} woła menu przy hoverze
     * i nawigacji klawiaturą.
     */
    public static SlideControl hoverSlide(Region node, double dx) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(150), node);
        tt.setInterpolator(Interpolator.EASE_OUT);
        return value -> {
            tt.stop();
            tt.setToX(value ? dx : 0);
            tt.play();
        };
    }

    public static SlideControl hoverSlide(Region node) {
        return hoverSlide(node, SLIDE);
    }

    /** Prosty przełącznik animacji zaznaczenia. */
    @FunctionalInterface
    public interface SlideControl {
        void set(boolean selected);
    }
}
