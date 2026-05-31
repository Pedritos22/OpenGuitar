package com.openguitar.game;

import com.openguitar.beatmap.Note;
import com.openguitar.beatmap.SongContext;
import com.openguitar.game.view.CrystalNote;
import com.openguitar.game.view.FullscreenScaler;
import com.openguitar.game.view.PersonaFonts;
import com.openguitar.game.view.PersonaPalette;
import com.openguitar.game.view.PersonaText;
import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.TextAlignment;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ekran rozgrywki: 4 ścieżki, spadające nutki, hit-line, audio sync, score.
 *
 * <h2>Synchronizacja czas → pozycja (perspektywa)</h2>
 * Nuty jadą po „autostradzie” zbiegającej do punktu znikającego (jak Guitar Hero):
 * im dalej w czasie przed hit-line, tym mniejsza skala i węższy tor; przy
 * {@code currentAudioMs == note.timeMs} nuta ma pełny rozmiar na hit-line.
 * Czas pobieramy z {@link MediaPlayer#getCurrentTime()} (nie z licznika klatek),
 * dzięki czemu nawet po pauzie/buforowaniu/GC notki nigdy nie odpływają od muzyki.
 *
 * <h2>Hit detection</h2>
 * Dla naciśniętego klawisza szukamy najbliższej nieprzetworzonej nuty na danej
 * ścieżce w oknie ±{@link HitJudgment#hitWindowMs()} ms; klasyfikujemy
 * (PERFECT/GREAT) i zwiększamy combo. Nuty które przeleciały hit-line + okno
 * trafiające bez naciśnięcia są oznaczane jako MISS w pętli renderującej.
 */
public final class GameScreen {

    private static final Logger LOG = Logger.getLogger(GameScreen.class.getName());

    // ---------- konfiguracja ekranu ----------
    public static final int LANES = 4;
    public static final int LANE_WIDTH = 100;
    public static final int SIDE_MARGIN = 50;
    public static final int CANVAS_WIDTH  = LANE_WIDTH * LANES + SIDE_MARGIN * 2; // 500
    public static final int CANVAS_HEIGHT = 720;
    public static final int HIT_LINE_Y = 620;
    public static final int NOTE_HEIGHT = 28;

    /** Punkt znikający autostrady (góra ekranu). */
    private static final int VANISH_Y = 88;
    private static final double VANISH_CENTER_X = CANVAS_WIDTH / 2.0;
    /** Szerokość jednej ścieżki przy vanishing point (px). */
    private static final double TOP_LANE_WIDTH = 16;
    /** Skala nuty na horyzoncie (ułamek rozmiaru przy hit-line). */
    private static final double MIN_NOTE_SCALE = 0.14;
    /**
     * Wykładnik krzywej zbliżania: &gt;1 = dłużej małe u góry, potem szybki „sprint”
     * do hit-line (charakterystyczny feel GH).
     */
    private static final double PERSPECTIVE_CURVE = 2.05;
    /**
     * Ile ms przed trafieniem nuta pojawia się na horyzoncie.
     * Większa wartość = wcześniejszy spawn na autostradzie.
     */
    private static final int LOOK_AHEAD_MS = 1_650;

    /** Prędkość zjazdu nut już po minięciu hit-line (px/ms). */
    private static final double PAST_HIT_SPEED_PX_PER_MS = 0.45;

    /** Klawisze poszczególnych ścieżek (klasyczny układ "DFJK"). */
    private static final KeyCode[] LANE_KEYS = {
            KeyCode.D, KeyCode.F, KeyCode.J, KeyCode.K
    };

    /** Czas po ostatniej nucie, po którym uznajemy utwór za skończony (ms). */
    private static final int END_GRACE_PERIOD_MS = 2_000;

    // ---------- cache zasobów rysujących (zero alokacji per-klatkę) ----------

    private static final LinearGradient BG_GRADIENT = new LinearGradient(
            0, 0, 0, CANVAS_HEIGHT, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#081326")),
            new Stop(0.5, Color.web("#050b16")),
            new Stop(1, Color.web("#03060d")));

    private static final RadialGradient HORIZON_GLOW = new RadialGradient(
            0, 0, VANISH_CENTER_X, VANISH_Y - 10, 220, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.color(0.0, 0.55, 0.95, 0.20)),
            new Stop(1, Color.color(0, 0, 0, 0)));

    /** Czas trwania „pop” licznika HUD (skalowanie po zmianie wartości). */
    private static final long HUD_POP_NANOS = 220_000_000L;

    /** Opcje ekranu pauzy. */
    private static final String[] PAUSE_OPTIONS = {"WZNÓW", "WYJDŹ DO MENU"};

    // ---------- stan ----------
    private final SongContext context;
    private final Consumer<GameResult> onFinished;
    /** Powrót do menu bez ekranu wyniku (wyjście z pauzy). */
    private final Runnable onQuit;

    private final Canvas canvas;
    private final Scene scene;
    private final ScoreState score = new ScoreState();
    private final List<RuntimeNote> runtimeNotes;

    /** Aktywne wciśnięcie klawisza per ścieżka - do podświetlenia. */
    private final boolean[] laneHeld = new boolean[LANES];
    /** Timestamp (nanos) do którego trwa "flash" feedbacku trafienia. */
    private final long[] laneFlashUntilNanos = new long[LANES];
    /** Unoszące się komunikaty (combo, mnożnik, judgment). */
    private final List<FloatingPopup> popups = new ArrayList<>();

    private MediaPlayer player;
    private AnimationTimer loop;
    private boolean finished = false;
    private boolean paused = false;
    private int pauseSelection = 0;
    /** Moment wejścia w pauzę (nanos) — do korekty czasu w trybie bez audio. */
    private long pauseStartNanos = 0;
    private double currentTimeMs = 0.0;
    private final int songEndTimeMs;
    /** Punkt startu w nanosekundach - fallback gdy audio nie jest załadowane. */
    private long loopStartNanos = -1;

    // Stan widoku HUD: ostatnio pokazane wartości + moment ich zmiany (efekt „pop”).
    // To wyłącznie pamięć podręczna renderera — nie wpływa na logikę punktacji.
    private int shownScore = -1;
    private int shownCombo = -1;
    private int shownMult = -1;
    private long scorePopNanos = -HUD_POP_NANOS;
    private long comboPopNanos = -HUD_POP_NANOS;
    private long multPopNanos = -HUD_POP_NANOS;

    // Ekran wyników (animowany, w stylu P3R). Pętla renderująca działa dalej,
    // by animować wjazd paneli, „count-up” liczb i stempel rangi.
    private boolean showingResults = false;
    private long resultsStartNanos = 0;
    private GameResult result;

    // ---------- konstrukcja ----------

    public GameScreen(SongContext context, Consumer<GameResult> onFinished, Runnable onQuit) {
        this.context = context;
        this.onFinished = onFinished;
        this.onQuit = onQuit;

        this.runtimeNotes = new ArrayList<>(context.notes().size());
        for (Note n : context.notes()) {
            runtimeNotes.add(new RuntimeNote(n));
        }
        runtimeNotes.sort(Comparator.comparingInt(rn -> rn.note.timeMs()));

        int lastNoteMs = runtimeNotes.isEmpty()
                ? 0
                : runtimeNotes.get(runtimeNotes.size() - 1).note.timeMs();
        this.songEndTimeMs = lastNoteMs + END_GRACE_PERIOD_MS;

        this.canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        StackPane root = new StackPane(canvas);
        root.setStyle(UiTheme.rootStyle());
        Parent scaled = FullscreenScaler.wrap(root, CANVAS_WIDTH, CANVAS_HEIGHT);
        this.scene = new Scene(scaled, CANVAS_WIDTH, CANVAS_HEIGHT);

        this.scene.setOnKeyPressed(e -> handleKeyPressed(e.getCode()));
        this.scene.setOnKeyReleased(e -> handleKeyReleased(e.getCode()));
    }

    public Scene getScene() {
        return scene;
    }

    /** Inicjalizuje audio i uruchamia pętlę renderującą. Wywołać raz po pokazaniu sceny. */
    public void start() {
        String audioPathRaw = context.audioPath();
        if (audioPathRaw != null && !audioPathRaw.isBlank()) {
            Path audioPath = Paths.get(audioPathRaw);
            if (!audioPath.isAbsolute()) {
                audioPath = Paths.get(".").resolve(audioPath).normalize().toAbsolutePath();
            }
            URI uri = audioPath.toUri();
            try {
                Media media = new Media(uri.toString());
                player = new MediaPlayer(media);
                player.setOnEndOfMedia(this::finishIfNotYet);
                player.setOnError(() -> LOG.log(Level.WARNING,
                        "MediaPlayer error: " + player.getError()));
                player.play();
            } catch (Exception ex) {
                LOG.log(Level.WARNING,
                        "Nie udało się załadować audio " + uri + " - kontynuuję bez muzyki.", ex);
                player = null;
            }
        } else {
            LOG.info("Brak audioPath w SongContext - tryb tylko-wizualny.");
        }

        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (loopStartNanos < 0) loopStartNanos = now;
                tick(now);
            }
        };
        loop.start();
        canvas.requestFocus();
        scene.getRoot().requestFocus();
    }

    /** Zatrzymuje rozgrywkę i zwalnia zasoby (audio, pętla). Idempotentne. */
    public void stop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }
    }

    // ---------- pętla gry ----------

    private void tick(long nowNanos) {
        if (finished) {
            // Po zakończeniu utworu pętla żyje tylko po to, by animować ekran wyników.
            if (showingResults) {
                render(nowNanos);
            }
            return;
        }

        // W pauzie zamrażamy stan gry: nie przesuwamy czasu, nie naliczamy MISS-ów,
        // nie wygaszamy popupów. Rysujemy wciąż scenę + nakładkę pauzy.
        if (!paused) {
            // Audio jest źródłem prawdy o czasie. Jeśli go brakuje (tryb demo / błąd
            // ładowania), używamy upływu czasu od startu pętli, żeby gra dalej działała
            // dla celów demonstracyjnych.
            currentTimeMs = (player != null)
                    ? player.getCurrentTime().toMillis()
                    : (nowNanos - loopStartNanos) / 1_000_000.0;

            markPassedNotesAsMisses();
            popups.removeIf(p -> p.isExpired(nowNanos));
        }

        render(nowNanos);

        if (!paused && currentTimeMs >= songEndTimeMs) {
            finishIfNotYet();
        }
    }

    /**
     * Każda nuta która przeszła hit-line i wyleciała poza okno trafienia
     * bez naciśnięcia klawisza staje się MISS-em.
     */
    private void markPassedNotesAsMisses() {
        int missDeadline = (int) currentTimeMs - HitJudgment.hitWindowMs();
        for (RuntimeNote rn : runtimeNotes) {
            if (rn.processed) continue;
            if (rn.note.timeMs() < missDeadline) {
                rn.processed = true;
                rn.judgment = HitJudgment.MISS;
                applyMiss(rn.note.lane());
            } else {
                // notes posortowane rosnąco po timeMs - kolejne na pewno też nie minęły
                break;
            }
        }
    }

    // ---------- input ----------

    private void handleKeyPressed(KeyCode key) {
        if (showingResults) {
            if (key == KeyCode.ENTER || key == KeyCode.SPACE || key == KeyCode.ESCAPE) {
                finishResults();
            }
            return;
        }
        if (paused) {
            handlePauseKey(key);
            return;
        }
        if (key == KeyCode.ESCAPE) {
            pauseGame();
            return;
        }
        int lane = laneFor(key);
        if (lane < 0) return;

        laneHeld[lane] = true;
        tryHitOnLane(lane, (int) currentTimeMs);
    }

    private void handleKeyReleased(KeyCode key) {
        int lane = laneFor(key);
        if (lane >= 0) laneHeld[lane] = false;
    }

    // ---------- pauza ----------

    private void handlePauseKey(KeyCode key) {
        switch (key) {
            case ESCAPE -> resumeGame();
            case UP, LEFT -> pauseSelection = Math.floorMod(pauseSelection - 1, PAUSE_OPTIONS.length);
            case DOWN, RIGHT -> pauseSelection = Math.floorMod(pauseSelection + 1, PAUSE_OPTIONS.length);
            case ENTER, SPACE -> activatePauseOption();
            default -> { /* ignorujemy */ }
        }
    }

    private void activatePauseOption() {
        if (pauseSelection == 0) {
            resumeGame();
        } else {
            quitToMenu();
        }
    }

    private void pauseGame() {
        if (paused || finished) return;
        paused = true;
        pauseSelection = 0;
        pauseStartNanos = System.nanoTime();
        if (player != null) {
            player.pause();
        }
    }

    private void resumeGame() {
        if (!paused) return;
        paused = false;
        if (player != null) {
            player.play();
        } else {
            // tryb bez audio: przesuwamy punkt startu o długość pauzy, by czas nie skoczył
            loopStartNanos += System.nanoTime() - pauseStartNanos;
        }
        for (int i = 0; i < LANES; i++) {
            laneHeld[i] = false;
        }
    }

    /** Wyjście z pauzy prosto do menu (bez ekranu wyniku). */
    private void quitToMenu() {
        if (finished) return;
        finished = true;
        paused = false;
        stop();
        if (onQuit != null) {
            onQuit.run();
        }
    }

    private static int laneFor(KeyCode key) {
        for (int i = 0; i < LANES; i++) {
            if (LANE_KEYS[i] == key) return i;
        }
        return -1;
    }

    /**
     * Próbuje trafić nutę na danej ścieżce w pobliżu currentMs.
     * Wybiera najbliższą czasowo nieprzetworzoną nutę w oknie ±hitWindow,
     * klasyfikuje, aktualizuje wynik.
     */
    private void tryHitOnLane(int lane, int currentMs) {
        int bestIdx = -1;
        int bestAbsDt = Integer.MAX_VALUE;

        // Liniowe przeszukiwanie - przy 4 ścieżkach i kilkuset nutach to nano-koszt.
        // Można by trzymać kursor "earliestUnprocessedIdx" per ścieżka, jeśli okaże się
        // wąskim gardłem przy bardzo długich utworach.
        for (int i = 0; i < runtimeNotes.size(); i++) {
            RuntimeNote rn = runtimeNotes.get(i);
            if (rn.processed) continue;
            if (rn.note.lane() != lane) continue;
            int dt = rn.note.timeMs() - currentMs;
            if (dt > HitJudgment.hitWindowMs()) break; // za daleko w przyszłości - reszta tym bardziej
            int absDt = Math.abs(dt);
            if (absDt < bestAbsDt && absDt <= HitJudgment.hitWindowMs()) {
                bestAbsDt = absDt;
                bestIdx = i;
            }
        }

        if (bestIdx < 0) {
            // klawisz "wciśnięty w pustkę" - opcjonalnie można karać combo;
            // tu po prostu ignorujemy. Brak hard-misses dla bardziej forgiving feel.
            return;
        }

        RuntimeNote target = runtimeNotes.get(bestIdx);
        HitJudgment j = HitJudgment.classify(bestAbsDt);
        target.processed = true;
        target.judgment = j;
        applyHit(lane, j);
        laneFlashUntilNanos[lane] = System.nanoTime() + 180_000_000L; // 180ms feedback
    }

    private void applyHit(int lane, HitJudgment judgment) {
        int prevCombo = score.combo();
        int prevMult = score.multiplier();
        score.register(judgment);

        spawnJudgmentPopup(lane, judgment);
        if (judgment == HitJudgment.MISS) {
            if (prevCombo >= 5) {
                popups.add(FloatingPopup.comboBreak(popupCenterX(), popupCenterY()));
            }
            return;
        }

        int combo = score.combo();
        int mult = score.multiplier();
        if (mult > prevMult) {
            popups.add(FloatingPopup.multiplier(mult, popupCenterX(), popupCenterY()));
        }
        if (isComboMilestone(combo, prevCombo)) {
            popups.add(FloatingPopup.combo(combo, popupCenterX(), popupCenterY() - 18));
        }
    }

    private void applyMiss(int lane) {
        int prevCombo = score.combo();
        score.registerMiss();
        spawnJudgmentPopup(lane, HitJudgment.MISS);
        if (prevCombo >= 5) {
            popups.add(FloatingPopup.comboBreak(popupCenterX(), popupCenterY()));
        }
    }

    private static boolean isComboMilestone(int combo, int prevCombo) {
        if (combo < 10 || combo <= prevCombo) return false;
        if (combo % 10 == 0) return true;
        return combo == 25 || combo == 50 || combo == 100;
    }

    private void spawnJudgmentPopup(int lane, HitJudgment judgment) {
        NotePlacement target = notePlacement(lane, 0);
        double cx = target != null ? target.centerX
                : SIDE_MARGIN + lane * LANE_WIDTH + LANE_WIDTH / 2.0;
        double cy = target != null ? target.centerY - target.height
                : HIT_LINE_Y - NOTE_HEIGHT;

        String label = switch (judgment) {
            case PERFECT -> "PERFECT!";
            case GREAT   -> "GREAT!";
            case MISS    -> "MISS";
        };
        Color color = switch (judgment) {
            case PERFECT -> Color.web("#67e8f9");
            case GREAT   -> Color.web("#a3e635");
            case MISS    -> Color.web("#fca5a5");
        };
        popups.add(FloatingPopup.judgment(label, cx, cy, color));
    }

    private static double popupCenterX() {
        return CANVAS_WIDTH / 2.0;
    }

    private static double popupCenterY() {
        return HIT_LINE_Y - 130;
    }

    // ---------- rendering ----------

    private void render(long nowNanos) {
        GraphicsContext g = canvas.getGraphicsContext2D();

        if (showingResults) {
            drawResults(g, nowNanos);
            return;
        }

        drawBackground(g);

        drawLanes(g, nowNanos);
        drawHitLine(g);
        drawNotes(g);
        drawHud(g, nowNanos);
        drawPopups(g, nowNanos);

        if (paused) {
            drawPauseMenu(g, nowNanos);
        }
    }

    /** Ekran pauzy w stylu Persona 3 Reload — przyciemnienie + ukośne opcje. */
    private void drawPauseMenu(GraphicsContext g, long nowNanos) {
        g.setFill(PersonaPalette.alpha(PersonaPalette.BLACK, 0.74));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Ukośny pas akcentowy za tytułem
        double bandY = CANVAS_HEIGHT * 0.26;
        g.setFill(PersonaPalette.alpha(PersonaPalette.DEEP_BLUE, 0.92));
        g.fillPolygon(
                new double[]{0, CANVAS_WIDTH, CANVAS_WIDTH, 0},
                new double[]{bandY - 36, bandY - 64, bandY + 64, bandY + 92},
                4);
        g.setStroke(PersonaPalette.AQUA_BRIGHT);
        g.setLineWidth(2);
        g.strokeLine(0, bandY - 64, CANVAS_WIDTH, bandY - 64 + (92 - 36));
        g.strokeLine(0, bandY + 92, CANVAS_WIDTH, bandY + 92 - (92 - 36));

        PersonaText.draw(g, "PAUZA", CANVAS_WIDTH / 2.0, bandY + 22,
                PersonaFonts.display(64), PersonaPalette.WHITE,
                PersonaPalette.BLACK, 4, PersonaPalette.alpha(PersonaPalette.AQUA, 0.55),
                PersonaText.SLANT, TextAlignment.CENTER);

        // Opcje
        double startY = CANVAS_HEIGHT * 0.52;
        double pulse = 0.5 + 0.5 * Math.sin(nowNanos / 180_000_000.0);
        for (int i = 0; i < PAUSE_OPTIONS.length; i++) {
            boolean sel = i == pauseSelection;
            double cx = CANVAS_WIDTH / 2.0;
            double y = startY + i * 70;
            double slideX = sel ? 16 : 0;

            if (sel) {
                double w = 320;
                double h = 50;
                double s = 0.32 * h;
                double left = cx - w / 2 + slideX;
                g.setFill(PersonaPalette.alpha(PersonaPalette.AQUA, 0.16 + 0.10 * pulse));
                g.fillPolygon(
                        new double[]{left + s, left + w + s, left + w, left},
                        new double[]{y - h / 2, y - h / 2, y + h / 2, y + h / 2},
                        4);
                g.setStroke(PersonaPalette.AQUA_BRIGHT);
                g.setLineWidth(2.5);
                g.strokePolygon(
                        new double[]{left + s, left + w + s, left + w, left},
                        new double[]{y - h / 2, y - h / 2, y + h / 2, y + h / 2},
                        4);
            }

            Color fill = sel ? PersonaPalette.WHITE : PersonaPalette.WHITE_DIM;
            PersonaText.draw(g, PAUSE_OPTIONS[i], cx + slideX, y + 11,
                    PersonaFonts.heading(sel ? 32 : 27), fill,
                    PersonaPalette.BLACK, 3,
                    sel ? PersonaPalette.alpha(PersonaPalette.AQUA, 0.5) : null,
                    PersonaText.SLANT, TextAlignment.CENTER);
        }

        PersonaText.plain(g, "↑/↓  wybór      ENTER  zatwierdź      ESC  wznów",
                CANVAS_WIDTH / 2.0, CANVAS_HEIGHT * 0.52 + PAUSE_OPTIONS.length * 70 + 24,
                PersonaFonts.body(13), PersonaPalette.MUTED, TextAlignment.CENTER);
    }

    // ---------- ekran wyników (animowany, P3R) ----------

    /**
     * Rysuje animowany ekran wyników: wjazd ukośnych paneli, „count-up” liczb
     * (PERFECT/GREAT/MISS, max combo, celność, wynik) i stempel rangi. Czyta tylko
     * gotowy {@link GameResult} — nie dotyka logiki gry.
     */
    private void drawResults(GraphicsContext g, long nowNanos) {
        if (result == null) {
            finishResults();
            return;
        }
        double el = (nowNanos - resultsStartNanos) / 1_000_000_000.0;

        g.setFill(BG_GRADIENT);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        g.setFill(PersonaPalette.alpha(PersonaPalette.BLACK, 0.5));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // ── nagłówek (ukośny pas + tytuł, wjazd z góry) ──
        double head = easeOut(clamp01(el / 0.4));
        double bandY = 86 - (1 - head) * 50;
        g.setFill(PersonaPalette.alpha(PersonaPalette.DEEP_BLUE, 0.92 * head));
        g.fillPolygon(
                new double[]{0, CANVAS_WIDTH, CANVAS_WIDTH, 0},
                new double[]{bandY - 30, bandY - 50, bandY + 40, bandY + 60}, 4);
        g.setStroke(PersonaPalette.alpha(PersonaPalette.AQUA_BRIGHT, head));
        g.setLineWidth(2);
        g.strokeLine(0, bandY - 50, CANVAS_WIDTH, bandY - 50 + 90);
        g.strokeLine(0, bandY + 60, CANVAS_WIDTH, bandY + 60 - 90);
        PersonaText.draw(g, "WYNIKI", CANVAS_WIDTH / 2.0, bandY + 18,
                PersonaFonts.display(52), PersonaPalette.alpha(PersonaPalette.WHITE, head),
                PersonaPalette.alpha(PersonaPalette.BLACK, head), 4,
                PersonaPalette.alpha(PersonaPalette.AQUA, 0.5 * head),
                PersonaText.SLANT, TextAlignment.CENTER);
        PersonaText.plain(g, context.title(), CANVAS_WIDTH / 2.0, bandY + 46,
                PersonaFonts.label(12), PersonaPalette.alpha(PersonaPalette.WHITE_DIM, head),
                TextAlignment.CENTER);

        // ── wiersze statystyk (staggered count-up) ──
        drawResultRow(g, 346, "PERFECT", String.valueOf(countUp(result.perfect(), el, 0.20)),
                PersonaPalette.PERFECT, el, 0.20);
        drawResultRow(g, 386, "GREAT", String.valueOf(countUp(result.great(), el, 0.34)),
                PersonaPalette.GREAT, el, 0.34);
        drawResultRow(g, 426, "MISS", String.valueOf(countUp(result.misses(), el, 0.48)),
                PersonaPalette.MISS, el, 0.48);
        drawResultRow(g, 466, "MAX COMBO", String.valueOf(countUp(result.maxCombo(), el, 0.62)),
                PersonaPalette.COMBO, el, 0.62);
        double accShown = result.accuracy() * 100.0 * clamp01((el - 0.76) / 0.6);
        drawResultRow(g, 506, "CELNOŚĆ", String.format("%.1f%%", accShown),
                PersonaPalette.AQUA, el, 0.76);

        // ── wynik (duży) ──
        double sp = clamp01((el - 0.9) / 0.5);
        if (sp > 0) {
            PersonaText.plain(g, "WYNIK", CANVAS_WIDTH / 2.0, 562, PersonaFonts.label(13),
                    PersonaPalette.alpha(PersonaPalette.AQUA, easeOut(sp)), TextAlignment.CENTER);
            int shownScoreVal = (int) Math.round(result.totalScore() * sp);
            PersonaText.draw(g, formatScore(shownScoreVal), CANVAS_WIDTH / 2.0, 612,
                    PersonaFonts.display(54), PersonaPalette.WHITE, PersonaPalette.BLACK, 3,
                    PersonaPalette.alpha(PersonaPalette.AQUA, 0.5), PersonaText.SLANT,
                    TextAlignment.CENTER);
        }

        // ── stempel rangi ──
        drawRankStamp(g, el);

        // ── podpowiedź ──
        if (el > 1.8) {
            double blink = 0.5 + 0.5 * Math.sin(nowNanos / 300_000_000.0);
            PersonaText.plain(g, "ENTER — powrót do menu", CANVAS_WIDTH / 2.0, 694,
                    PersonaFonts.body(13),
                    PersonaPalette.alpha(PersonaPalette.WHITE_DIM, 0.4 + 0.6 * blink),
                    TextAlignment.CENTER);
        }
    }

    /** Pojedynczy ukośny wiersz statystyki z wjazdem z lewej i policzonym wynikiem. */
    private static void drawResultRow(GraphicsContext g, double y, String label, String value,
                                      Color color, double el, double delay) {
        double ap = easeOut(clamp01((el - delay) / 0.35));
        if (ap <= 0) {
            return;
        }
        double l = 72;
        double r = 428;
        double h = 32;
        double s = 11;
        double dx = (1 - ap) * -26;
        double top = y - h / 2;
        double bot = y + h / 2;
        double[] xs = {l + s + dx, r + s + dx, r + dx, l + dx};
        double[] ys = {top, top, bot, bot};

        g.setFill(PersonaPalette.alpha(PersonaPalette.NAVY, 0.82 * ap));
        g.fillPolygon(xs, ys, 4);
        g.setStroke(PersonaPalette.alpha(color, 0.55 * ap));
        g.setLineWidth(1.5);
        g.strokePolygon(xs, ys, 4);
        g.setStroke(PersonaPalette.alpha(color, ap));
        g.setLineWidth(3);
        g.strokeLine(l + s + dx, top, l + dx, bot);

        PersonaText.draw(g, label, l + 22 + dx, y + 6, PersonaFonts.label(15),
                PersonaPalette.alpha(PersonaPalette.WHITE_DIM, ap), null, 0, null,
                PersonaText.SLANT, TextAlignment.LEFT);
        PersonaText.draw(g, value, r - 12 + dx, y + 9, PersonaFonts.display(24),
                PersonaPalette.alpha(color, ap), PersonaPalette.alpha(PersonaPalette.BLACK, ap), 2,
                null, PersonaText.SLANT, TextAlignment.RIGHT);
    }

    /** Stempel rangi: pierścień + wielka litera ze skalą „overshoot”. */
    private void drawRankStamp(GraphicsContext g, double el) {
        double rp = clamp01((el - 1.30) / 0.5);
        if (rp <= 0) {
            return;
        }
        double scale = 0.3 + 0.7 * easeOutBack(rp);
        double cx = CANVAS_WIDTH / 2.0;
        double cy = 232;
        double ringR = 78;
        Rank rank = result.rank();
        double a = Math.min(1, rp);

        g.setFill(rank.color(0.10 * a));
        g.fillOval(cx - ringR, cy - ringR, ringR * 2, ringR * 2);
        g.setStroke(rank.color(0.85 * a));
        g.setLineWidth(4);
        g.strokeOval(cx - ringR, cy - ringR, ringR * 2, ringR * 2);

        g.save();
        g.translate(cx, cy);
        g.scale(scale, scale);
        PersonaText.draw(g, rank.label(), 0, 48, PersonaFonts.display(140),
                rank.color(), PersonaPalette.BLACK, 6, rank.color(0.5),
                PersonaText.SLANT, TextAlignment.CENTER);
        g.restore();

        PersonaText.plain(g, "RANGA", cx, cy + 100, PersonaFonts.label(12),
                PersonaPalette.alpha(PersonaPalette.WHITE_DIM, a), TextAlignment.CENTER);
    }

    private static int countUp(int target, double el, double delay) {
        return (int) Math.round(target * clamp01((el - delay) / 0.6));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (Math.min(v, 1));
    }

    private static double easeOut(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    private void drawPopups(GraphicsContext g, long nowNanos) {
        for (FloatingPopup popup : popups) {
            popup.render(g, nowNanos);
        }
    }

    private void drawBackground(GraphicsContext g) {
        g.setFill(BG_GRADIENT);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        g.setFill(HORIZON_GLOW);
        g.fillRect(0, 0, CANVAS_WIDTH, HIT_LINE_Y + 40);

        // Diagonalna faktura tła (poza autostradą) — klimat P3R
        g.setStroke(PersonaPalette.alpha(PersonaPalette.AQUA, 0.05));
        g.setLineWidth(1);
        for (int i = -2; i < 10; i++) {
            double x0 = i * 60;
            g.strokeLine(x0, 0, x0 + CANVAS_HEIGHT * 0.4, CANVAS_HEIGHT);
        }

        g.setFill(PersonaPalette.alpha(PersonaPalette.BLACK, 0.45));
        g.fillRect(0, 0, SIDE_MARGIN - 6, CANVAS_HEIGHT);
        g.fillRect(SIDE_MARGIN + LANES * LANE_WIDTH + 6, 0, SIDE_MARGIN - 6, CANVAS_HEIGHT);
    }

    private void drawLanes(GraphicsContext g, long nowNanos) {
        // Podświetlenie horyzontu (głębia autostrady)
        g.setFill(PersonaPalette.alpha(PersonaPalette.AQUA, 0.12));
        g.fillPolygon(
                new double[]{VANISH_CENTER_X - 6, VANISH_CENTER_X + 6, VANISH_CENTER_X},
                new double[]{VANISH_Y, VANISH_Y, VANISH_Y - 28},
                3);

        for (int lane = 0; lane < LANES; lane++) {
            double[] top = laneBoundsAtDepth(lane, 0);
            double[] hit = laneBoundsAtDepth(lane, 1);
            double bottomL = SIDE_MARGIN + lane * LANE_WIDTH;
            double bottomR = SIDE_MARGIN + (lane + 1) * LANE_WIDTH;
            Color laneTint = PersonaPalette.lane(lane);

            g.setFill(PersonaPalette.alpha(laneTint, laneHeld[lane] ? 0.18 : 0.05));
            g.fillPolygon(
                    new double[]{top[0], top[1], hit[1], bottomR, bottomL, hit[0]},
                    new double[]{VANISH_Y, VANISH_Y, HIT_LINE_Y, CANVAS_HEIGHT, CANVAS_HEIGHT, HIT_LINE_Y},
                    6);

            g.setStroke(PersonaPalette.alpha(PersonaPalette.WHITE, 0.12));
            g.setLineWidth(1);
            g.strokeLine(top[1], VANISH_Y, bottomR, CANVAS_HEIGHT);

            if (nowNanos < laneFlashUntilNanos[lane]) {
                double alpha = (laneFlashUntilNanos[lane] - nowNanos) / 180_000_000.0;
                g.setFill(PersonaPalette.alpha(laneTint, 0.42 * alpha));
                g.fillPolygon(
                        new double[]{top[0], top[1], hit[1], bottomR, bottomL, hit[0]},
                        new double[]{VANISH_Y, VANISH_Y, HIT_LINE_Y, CANVAS_HEIGHT, CANVAS_HEIGHT, HIT_LINE_Y},
                        6);
            }
        }

        // Perspektywne poprzeczki — coraz jaśniejsze bliżej hit-line
        for (int i = 1; i <= 6; i++) {
            double depth = i / 7.0;
            double y = VANISH_Y + (HIT_LINE_Y - VANISH_Y) * depth;
            double left = laneBoundsAtDepth(0, depth)[0];
            double right = laneBoundsAtDepth(LANES - 1, depth)[1];
            g.setStroke(PersonaPalette.alpha(PersonaPalette.AQUA, 0.05 + 0.12 * depth));
            g.setLineWidth(1);
            g.strokeLine(left, y, right, y);
        }

        // Neonowa rama gryfu (poświata + ostra linia) — sygnatura P3R
        double[] leftTop = laneBoundsAtDepth(0, 0);
        double[] rightTop = laneBoundsAtDepth(LANES - 1, 0);
        double bx0 = SIDE_MARGIN;
        double bx1 = SIDE_MARGIN + LANES * LANE_WIDTH;

        g.setStroke(PersonaPalette.alpha(PersonaPalette.AQUA, 0.25));
        g.setLineWidth(6);
        g.strokeLine(leftTop[0], VANISH_Y, bx0, CANVAS_HEIGHT);
        g.strokeLine(rightTop[1], VANISH_Y, bx1, CANVAS_HEIGHT);
        g.strokeLine(leftTop[0], VANISH_Y, rightTop[1], VANISH_Y);

        g.setStroke(PersonaPalette.AQUA_BRIGHT);
        g.setLineWidth(2.5);
        g.strokeLine(leftTop[0], VANISH_Y, bx0, CANVAS_HEIGHT);
        g.strokeLine(rightTop[1], VANISH_Y, bx1, CANVAS_HEIGHT);
        g.strokeLine(leftTop[0], VANISH_Y, rightTop[1], VANISH_Y);
    }

    private void drawHitLine(GraphicsContext g) {
        double highwayL = SIDE_MARGIN;
        double highwayW = LANE_WIDTH * LANES;

        // Świecący pasek hit-line
        g.setFill(PersonaPalette.alpha(PersonaPalette.AQUA, 0.06));
        g.fillRect(highwayL, HIT_LINE_Y - 16, highwayW, 32);
        g.setStroke(PersonaPalette.AQUA_BRIGHT);
        g.setLineWidth(2);
        g.strokeLine(highwayL, HIT_LINE_Y, highwayL + highwayW, HIT_LINE_Y);

        for (int lane = 0; lane < LANES; lane++) {
            NotePlacement target = notePlacement(lane, 0);
            if (target == null) continue;

            Color laneCol = PersonaPalette.lane(lane);
            CrystalNote.drawReceptor(g, target.centerX, target.centerY,
                    target.width, target.height, laneCol, laneHeld[lane]);
            drawKeyPill(g, lane, target.centerX, laneCol);
        }
    }

    private void drawKeyPill(GraphicsContext g, int lane, double centerX, Color laneCol) {
        String key = LANE_KEYS[lane].getName();
        double pillW = 30;
        double pillH = 24;
        double px = centerX - pillW / 2.0;
        double py = HIT_LINE_Y + 40;

        g.setFill(PersonaPalette.alpha(PersonaPalette.BLACK, 0.6));
        g.fillRect(px, py, pillW, pillH);
        g.setStroke(laneHeld[lane] ? PersonaPalette.AQUA_BRIGHT : PersonaPalette.alpha(laneCol, 0.9));
        g.setLineWidth(laneHeld[lane] ? 2.0 : 1.5);
        g.strokeRect(px + 0.5, py + 0.5, pillW - 1, pillH - 1);

        PersonaText.draw(g, key, centerX, py + 17,
                PersonaFonts.heading(14), PersonaPalette.WHITE,
                null, 0, null, PersonaText.SLANT, TextAlignment.CENTER);
    }

    private void drawNotes(GraphicsContext g) {
        // Dalekie nuty rysujemy pierwsze (nakładanie jak na autostradzie 3D).
        List<DrawNote> visible = new ArrayList<>();
        for (RuntimeNote rn : runtimeNotes) {
            if (rn.processed) continue;
            int dt = rn.note.timeMs() - (int) currentTimeMs;
            NotePlacement place = notePlacement(rn.note.lane(), dt);
            if (place == null) continue;
            visible.add(new DrawNote(rn.note.lane(), place));
        }
        visible.sort(Comparator.comparingDouble(d -> d.place.depth));

        for (DrawNote dn : visible) {
            NotePlacement place = dn.place;
            // Kryształ skalujemy lekko w pionie, by „odłamek” był wyrazisty
            // (geometria/kolizja w logice gry pozostaje bez zmian — to tylko render).
            CrystalNote.draw(g, place.centerX, place.centerY,
                    place.width * 1.25, place.height * 2.0,
                    place.depth, PersonaPalette.lane(dn.lane));
        }
    }

    /**
     * Głębokość 0 = horyzont, 1 = hit-line. Mapowanie czasu używa krzywej
     * potęgowej, żeby nuty dłużej „siedziały” w oddali i przyspieszały wizualnie.
     */
    private static double perspectiveDepthFromDt(int dtMs) {
        if (dtMs >= LOOK_AHEAD_MS) return 0;
        if (dtMs <= 0) return 1;
        double linear = 1.0 - (double) dtMs / LOOK_AHEAD_MS;
        return Math.pow(linear, PERSPECTIVE_CURVE);
    }

    /** Lewa i prawa krawędź ścieżki przy danej głębokości (0..1). */
    private static double[] laneBoundsAtDepth(int lane, double depth) {
        double topL = vanishLaneLeft(lane);
        double topR = topL + TOP_LANE_WIDTH;
        double hitL = SIDE_MARGIN + lane * LANE_WIDTH;
        double hitR = SIDE_MARGIN + (lane + 1) * LANE_WIDTH;
        return new double[]{
                topL + (hitL - topL) * depth,
                topR + (hitR - topR) * depth
        };
    }

    private static double vanishLaneLeft(int lane) {
        double totalTop = LANES * TOP_LANE_WIDTH;
        return VANISH_CENTER_X - totalTop / 2.0 + lane * TOP_LANE_WIDTH;
    }

    private NotePlacement notePlacement(int lane, int dtMs) {
        double depth;
        double centerY;

        if (dtMs > LOOK_AHEAD_MS) {
            return null;
        }
        if (dtMs < -120) {
            return null;
        }
        if (dtMs <= 0) {
            depth = 1.0;
            centerY = HIT_LINE_Y - dtMs * PAST_HIT_SPEED_PX_PER_MS;
        } else {
            depth = perspectiveDepthFromDt(dtMs);
            centerY = VANISH_Y + (HIT_LINE_Y - VANISH_Y) * depth;
        }

        if (centerY + NOTE_HEIGHT < VANISH_Y - 40 || centerY > CANVAS_HEIGHT + 40) {
            return null;
        }

        double[] bounds = laneBoundsAtDepth(lane, depth);
        double inset = 4 + 6 * (1 - depth);
        double left = bounds[0] + inset;
        double right = bounds[1] - inset;
        double width = Math.max(4, right - left);
        double height = NOTE_HEIGHT * (MIN_NOTE_SCALE + (1 - MIN_NOTE_SCALE) * depth);
        double centerX = (left + right) / 2.0;

        return new NotePlacement(centerX, centerY, width, height, depth);
    }

    private record NotePlacement(
            double centerX, double centerY, double width, double height, double depth) {}

    private record DrawNote(int lane, NotePlacement place) {}

    private void drawHud(GraphicsContext g, long nowNanos) {
        int scoreVal = score.totalScore();
        int comboVal = score.combo();
        int multVal = score.multiplier();

        // Wykrycie zmiany wartości wyzwala efekt „pop” (czysto wizualne, nie dotyka punktacji).
        if (scoreVal != shownScore) { shownScore = scoreVal; scorePopNanos = nowNanos; }
        if (comboVal != shownCombo) { shownCombo = comboVal; comboPopNanos = nowNanos; }
        if (multVal  != shownMult)  { shownMult  = multVal;  multPopNanos  = nowNanos; }

        // ── panel WYNIK (lewy) ──
        drawGlassPanel(g, 12, 10, 190, 80);
        PersonaText.plain(g, "SCORE", 26, 32, PersonaFonts.label(13),
                PersonaPalette.AQUA, TextAlignment.LEFT);
        drawPopNumber(g, formatScore(scoreVal), 26, 70, 34,
                popScale(nowNanos, scorePopNanos), PersonaPalette.WHITE, TextAlignment.LEFT);
        PersonaText.plain(g, "HIT " + score.hits() + "   MISS " + score.misses(), 26, 86,
                PersonaFonts.body(11), PersonaPalette.WHITE_DIM, TextAlignment.LEFT);

        // ── panel COMBO / MNOŻNIK (prawy) ──
        double rx = CANVAS_WIDTH - 202;
        drawGlassPanel(g, rx, 10, 190, 80);
        PersonaText.plain(g, "COMBO", CANVAS_WIDTH - 26, 32, PersonaFonts.label(13),
                PersonaPalette.AQUA, TextAlignment.RIGHT);
        Color comboCol = comboVal > 0 ? PersonaPalette.COMBO : PersonaPalette.MUTED;
        drawPopNumber(g, String.valueOf(comboVal), CANVAS_WIDTH - 26, 72, 36,
                popScale(nowNanos, comboPopNanos), comboCol, TextAlignment.RIGHT);
        Color multCol = multVal > 1 ? PersonaPalette.AQUA_BRIGHT : PersonaPalette.MUTED;
        drawPopNumber(g, "x" + multVal, rx + 14, 72, 22,
                popScale(nowNanos, multPopNanos), multCol, TextAlignment.LEFT);

        // ── pasek postępu utworu (góra) ──
        double prog = songEndTimeMs > 0
                ? Math.max(0, Math.min(1.0, currentTimeMs / songEndTimeMs))
                : 0;
        g.setFill(PersonaPalette.alpha(PersonaPalette.NAVY, 0.8));
        g.fillRect(0, 0, CANVAS_WIDTH, 5);
        g.setFill(PersonaPalette.AQUA);
        g.fillRect(0, 0, CANVAS_WIDTH * prog, 5);
        g.setFill(PersonaPalette.AQUA_BRIGHT);
        g.fillRect(Math.max(0, CANVAS_WIDTH * prog - 2), 0, 3, 6);

        // ── pasek tytułu + postęp utworu (dół, ukośny) ──
        double barW = Math.min(360, CANVAS_WIDTH - 40);
        double barX = (CANVAS_WIDTH - barW) / 2.0;
        double barY = CANVAS_HEIGHT - 36;
        double barH = 28;
        double bs = 10;
        double[] bxs = {barX + bs, barX + barW + bs, barX + barW, barX};
        double[] bys = {barY, barY, barY + barH, barY + barH};

        g.setFill(PersonaPalette.alpha(PersonaPalette.NAVY, 0.9));
        g.fillPolygon(bxs, bys, 4);

        // Wypełnienie postępu — przycięte do kształtu paska (rośnie wraz z utworem).
        g.save();
        g.beginPath();
        g.moveTo(bxs[0], bys[0]);
        g.lineTo(bxs[1], bys[1]);
        g.lineTo(bxs[2], bys[2]);
        g.lineTo(bxs[3], bys[3]);
        g.closePath();
        g.clip();
        g.setFill(PersonaPalette.alpha(PersonaPalette.AQUA, 0.22));
        g.fillRect(barX, barY, barW * prog + bs, barH);
        g.restore();

        g.setStroke(PersonaPalette.alpha(PersonaPalette.AQUA, 0.55));
        g.setLineWidth(1);
        g.strokeLine(barX + bs, barY, barX + barW + bs, barY);

        // Linia postępu wzdłuż dolnej krawędzi + jasny znacznik czoła.
        g.setFill(PersonaPalette.AQUA);
        g.fillRect(barX, barY + barH - 2, barW * prog, 2);
        g.setFill(PersonaPalette.AQUA_BRIGHT);
        g.fillRect(barX + barW * prog - 1, barY + barH - 4, 2, 5);

        PersonaText.plain(g, truncate(context.title(), 22), barX + bs + 12, barY + 19,
                PersonaFonts.label(12), PersonaPalette.WHITE_DIM, TextAlignment.LEFT);
        PersonaText.plain(g, formatTime(currentTimeMs) + " / " + formatTime(songEndTimeMs),
                barX + barW - 12, barY + 19, PersonaFonts.label(12),
                PersonaPalette.AQUA_BRIGHT, TextAlignment.RIGHT);
    }

    /** Formatuje czas (ms) jako {@code m:ss}. */
    private static String formatTime(double ms) {
        int total = (int) Math.max(0, ms / 1000.0);
        return total / 60 + ":" + (total % 60 < 10 ? "0" : "") + total % 60;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Skala „pop” licznika: krótki impuls po zmianie wartości, potem 1.0. */
    private static double popScale(long now, long popStart) {
        double t = (now - popStart) / (double) HUD_POP_NANOS;
        if (t < 0 || t >= 1.0) return 1.0;
        return 1.0 + 0.35 * Math.sin(t * Math.PI);
    }

    /**
     * Rysuje wielki licznik HUD ze skalą „pop” realizowaną transformacją kontekstu
     * (a nie zmianą rozmiaru fontu — dzięki czemu cache fontów nie puchnie).
     */
    private static void drawPopNumber(GraphicsContext g, String s, double x, double baseline,
                                      double baseSize, double scale, Color fill, TextAlignment align) {
        g.save();
        g.translate(x, baseline);
        g.scale(scale, scale);
        PersonaText.draw(g, s, 0, 0, PersonaFonts.display(baseSize), fill,
                PersonaPalette.BLACK, 2.5,
                PersonaPalette.alpha(PersonaPalette.AQUA, 0.5),
                PersonaText.SLANT, align);
        g.restore();
    }

    private static void drawGlassPanel(GraphicsContext g, double x, double y, double w, double h) {
        double s = 12; // ścięcie górnej krawędzi w prawo (ukos P3R)
        double[] xs = {x + s, x + w + s, x + w, x};
        double[] ys = {y, y, y + h, y + h};

        g.setFill(PersonaPalette.alpha(PersonaPalette.NAVY, 0.82));
        g.fillPolygon(xs, ys, 4);
        g.setStroke(PersonaPalette.alpha(PersonaPalette.AQUA, 0.5));
        g.setLineWidth(1);
        g.strokePolygon(xs, ys, 4);

        // narożny akcent neonowy (corner bracket) wzdłuż ukośnej krawędzi
        g.setStroke(PersonaPalette.AQUA_BRIGHT);
        g.setLineWidth(2);
        g.strokeLine(x + s, y, x + s + 18, y);
        g.strokeLine(x + s, y, x + s - s * 16 / h, y + 16);
    }

    private static String formatScore(int score) {
        if (score < 1000) return String.valueOf(score);
        return String.format("%,d", score).replace(',', ' ');
    }

    // ---------- finalizacja ----------

    private void finishIfNotYet() {
        if (finished) return;
        finished = true;
        paused = false;
        // Zatrzymujemy audio, ale ZOSTAWIAMY pętlę renderującą — animuje ekran wyników.
        if (player != null) {
            player.stop();
        }
        result = score.toResult(context.songId());
        LOG.info(() -> "GameResult: " + result);
        showingResults = true;
        resultsStartNanos = System.nanoTime();
    }

    /** Domknięcie ekranu wyników: zwolnienie zasobów i powrót do menu (przez callback). */
    private void finishResults() {
        if (!showingResults) {
            return;
        }
        showingResults = false;
        stop();
        if (onFinished != null) {
            onFinished.accept(result);
        }
    }

    // ---------- typy pomocnicze ----------

    private static final class RuntimeNote {
        final Note note;
        boolean processed = false;
        HitJudgment judgment;

        RuntimeNote(Note note) { this.note = note; }
    }
}
