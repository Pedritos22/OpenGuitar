package com.openguitar.game.view;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rejestr i cache fontów w stylu Persona 3 Reload (czysta warstwa widoku).
 *
 * <p>Ładuje pliki .ttf z classpath <b>raz</b> przez {@link Font#loadFont(InputStream, double)}
 * (JavaFX rejestruje wtedy rodzinę w swoim runtime), a potem rozdaje gotowe,
 * cache'owane instancje {@link Font}. Dzięki temu w pętli renderowania nie ma
 * ani jednego wczytania fontu z dysku.</p>
 *
 * <p>Dobór krojów (darmowe, licencja SIL OFL — patrz {@code resources/fonts/OFL-*.txt}):</p>
 * <ul>
 *   <li><b>Bebas Neue</b> — wysoki, kondensowany krój wersalikowy. Ogromne liczniki
 *       HUD, tytuły. Pochył „italic” uzyskujemy ścinaniem (Shear/transform), nie samym fontem.</li>
 *   <li><b>Rajdhani</b> — kanciasty, techniczny krój (wagi Medium/SemiBold/Bold) na
 *       etykiety menu, body i mniejsze napisy.</li>
 * </ul>
 *
 * Klasa nie zna logiki gry — wyłącznie dostarcza fonty.
 */
public final class PersonaFonts {

    private static final Logger LOG = Logger.getLogger(PersonaFonts.class.getName());

    private static volatile boolean initialized;
    private static String displayFamily; // Bebas Neue
    private static String uiFamily;       // Rajdhani

    private static final Map<String, Font> CACHE = new HashMap<>();

    private PersonaFonts() {}

    /**
     * Ładuje fonty z classpath. Wołać RAZ na starcie aplikacji
     * (z wątku JavaFX, przed budową scen). Idempotentne.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        displayFamily = load("/fonts/BebasNeue-Regular.ttf");
        // Wagi Rajdhani rejestrują się pod wspólną rodziną "Rajdhani";
        // ładujemy wszystkie trzy, by FontWeight mógł trafić w konkretny krój.
        uiFamily = load("/fonts/Rajdhani-Medium.ttf");
        load("/fonts/Rajdhani-SemiBold.ttf");
        String bold = load("/fonts/Rajdhani-Bold.ttf");
        if (uiFamily == null) {
            uiFamily = bold;
        }
        initialized = true;
    }

    /** Wielki krój ekspozycyjny (Bebas Neue) — liczniki, tytuły, „pop-up”. */
    public static Font display(double size) {
        ensureInit();
        return cached(family(displayFamily), FontWeight.NORMAL, size);
    }

    /** Mocny krój nagłówkowy (Rajdhani Bold). */
    public static Font heading(double size) {
        ensureInit();
        return cached(family(uiFamily), FontWeight.BOLD, size);
    }

    /** Krój etykiet (Rajdhani SemiBold). */
    public static Font label(double size) {
        ensureInit();
        return cached(family(uiFamily), FontWeight.SEMI_BOLD, size);
    }

    /** Krój tekstu pomocniczego (Rajdhani Medium). */
    public static Font body(double size) {
        ensureInit();
        return cached(family(uiFamily), FontWeight.MEDIUM, size);
    }

    /** Nazwa rodziny krojów UI (Rajdhani) do użycia w stylach CSS, np. {@code -fx-font-family}. */
    public static String uiFamilyName() {
        ensureInit();
        return family(uiFamily);
    }

    /** Nazwa rodziny krojów ekspozycyjnych (Bebas Neue) do CSS. */
    public static String displayFamilyName() {
        ensureInit();
        return family(displayFamily);
    }

    // ── wnętrze ──────────────────────────────────────────────────────────────

    private static void ensureInit() {
        if (!initialized) {
            init();
        }
    }

    private static String family(String loaded) {
        return (loaded != null) ? loaded : Font.getDefault().getFamily();
    }

    private static Font cached(String family, FontWeight weight, double size) {
        String key = family + '|' + weight + '|' + size;
        Font hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        Font f = Font.font(family, weight, size);
        CACHE.put(key, f);
        return f;
    }

    private static String load(String resourcePath) {
        try (InputStream in = PersonaFonts.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOG.warning(() -> "Brak fontu na classpath: " + resourcePath + " — używam fallbacku systemowego.");
                return null;
            }
            Font f = Font.loadFont(in, 12);
            if (f == null) {
                LOG.warning(() -> "Font.loadFont zwrócił null dla: " + resourcePath);
                return null;
            }
            String fam = f.getFamily();
            LOG.info(() -> "Załadowano font: " + fam + " (" + resourcePath + ")");
            return fam;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Błąd ładowania fontu " + resourcePath, ex);
            return null;
        }
    }
}
