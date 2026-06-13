package com.openguitar.game;

import com.openguitar.game.view.PersonaPalette;
import javafx.scene.paint.Color;

/**
 * Ranga utworu w stylu Persona 3 Reload — pojedyncza litera nadawana na podstawie
 * celności gracza (z dodatkowym wyróżnieniem za full combo).
 *
 * <p>To czysta funkcja prezentacji wyniku: nie zmienia punktacji ani detekcji
 * trafień, jedynie podsumowuje gotowy {@link GameResult}.</p>
 *
 * <pre>
 *   S  — celność ≥ 0.95  (lub full combo ≥ 0.90)   złoto
 *   A  — celność ≥ 0.88                            aqua
 *   B  — celność ≥ 0.78                            turkus
 *   C  — celność ≥ 0.65                            błękit
 *   D  — celność ≥ 0.50                            stalowy
 *   F  — poniżej                                   czerwień
 * </pre>
 */
public enum Rank {
    S("#ffd166"),
    A("#7df9ff"),
    B("#19e3d0"),
    C("#2f7dff"),
    D("#5d7da3"),
    F("#ff5d73");

    /** Waga trafienia GREAT przy liczeniu celności (PERFECT = 1.0). */
    public static final double GREAT_WEIGHT = 0.6;

    private final Color color;

    Rank(String hex) {
        this.color = Color.web(hex);
    }

    /** Kolor akcentu rangi (paleta P3R). */
    public Color color() {
        return color;
    }

    /** Półprzezroczysty wariant koloru rangi. */
    public Color color(double alpha) {
        return PersonaPalette.alpha(color, alpha);
    }

    /** Litera rangi do wyświetlenia. */
    public String label() {
        return name();
    }

    /** Wylicza rangę dla gotowego wyniku. */
    public static Rank of(GameResult r) {
        if (r == null || r.totalNotes() == 0) {
            return F;
        }
        double acc = r.accuracy();
        if (acc >= 0.95 || (r.fullCombo() && acc >= 0.90)) {
            return S;
        }
        if (acc >= 0.88) {
            return A;
        }
        if (acc >= 0.78) {
            return B;
        }
        if (acc >= 0.65) {
            return C;
        }
        if (acc >= 0.50) {
            return D;
        }
        return F;
    }

    /** Ranga z zapisanej celności (np. odtworzona z bazy) — bez pełnego {@link GameResult}. */
    public static Rank fromAccuracy(double acc, boolean fullCombo) {
        if (acc >= 0.95 || (fullCombo && acc >= 0.90)) {
            return S;
        }
        if (acc >= 0.88) {
            return A;
        }
        if (acc >= 0.78) {
            return B;
        }
        if (acc >= 0.65) {
            return C;
        }
        if (acc >= 0.50) {
            return D;
        }
        return F;
    }
}
