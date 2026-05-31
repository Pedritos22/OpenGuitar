package com.openguitar.game;

import com.openguitar.beatmap.Note;
import com.openguitar.beatmap.SongContext;
import javafx.animation.AnimationTimer;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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

    // ---------- stan ----------
    private final SongContext context;
    private final Consumer<GameResult> onFinished;

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
    private double currentTimeMs = 0.0;
    private final int songEndTimeMs;
    /** Punkt startu w nanosekundach - fallback gdy audio nie jest załadowane. */
    private long loopStartNanos = -1;

    // ---------- konstrukcja ----------

    public GameScreen(SongContext context, Consumer<GameResult> onFinished) {
        this.context = context;
        this.onFinished = onFinished;

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
        this.scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

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
        if (loop != null) loop.stop();
        if (player != null) {
            player.stop();
            player.dispose();
        }
    }

    // ---------- pętla gry ----------

    private void tick(long nowNanos) {
        if (finished) return;

        // Audio jest źródłem prawdy o czasie. Jeśli go brakuje (tryb demo / błąd
        // ładowania), używamy upływu czasu od startu pętli, żeby gra dalej działała
        // dla celów demonstracyjnych.
        currentTimeMs = (player != null)
                ? player.getCurrentTime().toMillis()
                : (nowNanos - loopStartNanos) / 1_000_000.0;

        markPassedNotesAsMisses();
        popups.removeIf(p -> p.isExpired(nowNanos));

        render(nowNanos);

        if (currentTimeMs >= songEndTimeMs) {
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
        if (key == KeyCode.ESCAPE) {
            finishIfNotYet();
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

        drawBackground(g);

        drawLanes(g, nowNanos);
        drawHitLine(g);
        drawNotes(g);
        drawHud(g);
        drawPopups(g, nowNanos);
    }

    private void drawPopups(GraphicsContext g, long nowNanos) {
        for (FloatingPopup popup : popups) {
            popup.render(g, nowNanos);
        }
    }

    private void drawBackground(GraphicsContext g) {
        LinearGradient bg = new LinearGradient(
                0, 0, 0, CANVAS_HEIGHT, false, CycleMethod.NO_CYCLE,
                new Stop(0, UiTheme.canvasBgTop()),
                new Stop(0.5, Color.web("#080d18")),
                new Stop(1, UiTheme.canvasBgBottom()));
        g.setFill(bg);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        RadialGradient horizon = new RadialGradient(
                0, 0, VANISH_CENTER_X, VANISH_Y - 10, 200, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.4, 0.45, 0.95, 0.16)),
                new Stop(1, Color.color(0, 0, 0, 0)));
        g.setFill(horizon);
        g.fillRect(0, 0, CANVAS_WIDTH, HIT_LINE_Y + 40);

        g.setFill(Color.color(0, 0, 0, 0.4));
        g.fillRect(0, 0, SIDE_MARGIN - 6, CANVAS_HEIGHT);
        g.fillRect(SIDE_MARGIN + LANES * LANE_WIDTH + 6, 0, SIDE_MARGIN - 6, CANVAS_HEIGHT);
    }

    private void drawLanes(GraphicsContext g, long nowNanos) {
        // Podświetlenie horyzontu (głębia autostrady)
        g.setFill(Color.color(0.45, 0.55, 0.95, 0.08));
        g.fillPolygon(
                new double[]{VANISH_CENTER_X - 6, VANISH_CENTER_X + 6, VANISH_CENTER_X},
                new double[]{VANISH_Y, VANISH_Y, VANISH_Y - 28},
                3);

        for (int lane = 0; lane < LANES; lane++) {
            double[] top = laneBoundsAtDepth(lane, 0);
            double[] hit = laneBoundsAtDepth(lane, 1);
            double bottomL = SIDE_MARGIN + lane * LANE_WIDTH;
            double bottomR = SIDE_MARGIN + (lane + 1) * LANE_WIDTH;
            Color laneTint = UiTheme.laneColor(lane);

            if (laneHeld[lane]) {
                g.setFill(laneTint.deriveColor(0, 1, 1, 0.14));
            } else {
                g.setFill(laneTint.deriveColor(0, 1, 1, 0.04));
            }
            g.fillPolygon(
                    new double[]{top[0], top[1], hit[1], bottomR, bottomL, hit[0]},
                    new double[]{VANISH_Y, VANISH_Y, HIT_LINE_Y, CANVAS_HEIGHT, CANVAS_HEIGHT, HIT_LINE_Y},
                    6);

            g.setStroke(Color.color(1, 1, 1, 0.14));
            g.setLineWidth(1);
            g.strokeLine(top[1], VANISH_Y, bottomR, CANVAS_HEIGHT);

            if (nowNanos < laneFlashUntilNanos[lane]) {
                double alpha = (laneFlashUntilNanos[lane] - nowNanos) / 180_000_000.0;
                g.setFill(UiTheme.laneColor(lane).deriveColor(0, 1, 1, 0.38 * alpha));
                g.fillPolygon(
                        new double[]{top[0], top[1], hit[1], bottomR, bottomL, hit[0]},
                        new double[]{VANISH_Y, VANISH_Y, HIT_LINE_Y, CANVAS_HEIGHT, CANVAS_HEIGHT, HIT_LINE_Y},
                        6);
            }
        }

        // Krawędzie zewnętrzne autostrady
        double[] leftTop = laneBoundsAtDepth(0, 0);
        double[] rightTop = laneBoundsAtDepth(LANES - 1, 0);
        g.setStroke(Color.color(1, 1, 1, 0.22));
        g.setLineWidth(2);
        g.strokeLine(leftTop[0], VANISH_Y, SIDE_MARGIN, CANVAS_HEIGHT);
        g.strokeLine(rightTop[1], VANISH_Y, SIDE_MARGIN + LANES * LANE_WIDTH, CANVAS_HEIGHT);

        // Linie „desk” przed hit-line (perspektywne poprzeczki)
        g.setStroke(Color.color(1, 1, 1, 0.06));
        g.setLineWidth(1);
        for (int i = 1; i <= 5; i++) {
            double depth = i / 6.0;
            double y = VANISH_Y + (HIT_LINE_Y - VANISH_Y) * depth;
            double left = laneBoundsAtDepth(0, depth)[0];
            double right = laneBoundsAtDepth(LANES - 1, depth)[1];
            g.strokeLine(left, y, right, y);
        }
    }

    private void drawHitLine(GraphicsContext g) {
        double highwayL = SIDE_MARGIN;
        double highwayW = LANE_WIDTH * LANES;

        // Świecący pasek hit-line
        g.setFill(Color.color(1, 1, 1, 0.04));
        g.fillRect(highwayL, HIT_LINE_Y - 14, highwayW, 28);
        g.setFill(Color.color(0.55, 0.65, 1.0, 0.12));
        g.fillRect(highwayL, HIT_LINE_Y - 2, highwayW, 4);

        for (int lane = 0; lane < LANES; lane++) {
            NotePlacement target = notePlacement(lane, 0);
            if (target == null) continue;

            Color laneCol = UiTheme.laneColor(lane);
            double x = target.centerX - target.width / 2.0;
            double y = target.centerY - target.height / 2.0;
            double corner = noteCornerRadius(1.0);

            g.setFill(Color.color(0, 0, 0, 0.5));
            g.fillRoundRect(x, y, target.width, target.height, corner, corner);

            if (laneHeld[lane]) {
                g.setFill(laneCol.deriveColor(0, 1, 1, 0.4));
                g.fillRoundRect(x, y, target.width, target.height, corner, corner);
            }

            g.setStroke(laneCol);
            g.setLineWidth(laneHeld[lane] ? 3.5 : 2);
            g.strokeRoundRect(x, y, target.width, target.height, corner, corner);

            drawKeyPill(g, lane, target.centerX, laneCol);
        }
    }

    private void drawKeyPill(GraphicsContext g, int lane, double centerX, Color laneCol) {
        String key = LANE_KEYS[lane].getName();
        double pillW = 28;
        double pillH = 22;
        double px = centerX - pillW / 2.0;
        double py = HIT_LINE_Y + 38;

        g.setFill(Color.color(0, 0, 0, 0.55));
        g.fillRoundRect(px, py, pillW, pillH, 8, 8);
        g.setStroke(laneCol.deriveColor(0, 1, 1, 0.85));
        g.setLineWidth(1.5);
        g.strokeRoundRect(px + 0.5, py + 0.5, pillW - 1, pillH - 1, 8, 8);

        g.setFill(Color.web(UiTheme.TEXT));
        g.setFont(UiTheme.fontBold(12));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(key, centerX, py + 15);
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
            int lane = dn.lane;

            double corner = noteCornerRadius(place.depth);

            // Poświata „z daleka” — im mniejsza nuta, tym jaśniejszy ślad
            Color laneCol = UiTheme.laneColor(lane);
            g.setFill(laneCol.deriveColor(0, 1, 1, 0.12 + 0.18 * (1 - place.depth)));
            g.fillRoundRect(
                    place.centerX - place.width * 0.55,
                    place.centerY - place.height * 0.45,
                    place.width * 1.1,
                    place.height * 1.1,
                    corner, corner);

            g.setFill(laneCol);
            g.fillRoundRect(
                    place.centerX - place.width / 2.0,
                    place.centerY - place.height / 2.0,
                    place.width,
                    place.height,
                    corner, corner);

            g.setFill(Color.color(1, 1, 1, 0.2 + 0.25 * place.depth));
            g.fillRoundRect(
                    place.centerX - place.width / 2.0,
                    place.centerY - place.height / 2.0,
                    place.width,
                    place.height / 3.0,
                    corner, corner);

            // Odbłysk na „szybie” przy pełnym zbliżeniu
            if (place.depth > 0.82) {
                g.setStroke(Color.color(1, 1, 1, 0.35 * (place.depth - 0.82) / 0.18));
                g.setLineWidth(1.5);
                g.strokeRoundRect(
                        place.centerX - place.width / 2.0 + 1,
                        place.centerY - place.height / 2.0 + 1,
                        place.width - 2,
                        place.height - 2,
                        corner, corner);
            }
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

    /** Zaokrąglenie rogów nuty / receptora — spójne na całej autostradzie. */
    private static double noteCornerRadius(double depth) {
        return 6 + 6 * Math.min(1.0, depth * 1.15);
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

    private void drawHud(GraphicsContext g) {
        drawGlassPanel(g, 12, 12, 172, 62);
        drawGlassPanel(g, CANVAS_WIDTH - 184, 12, 172, 62);

        g.setTextAlign(TextAlignment.LEFT);
        g.setFill(Color.web(UiTheme.TEXT_DIM));
        g.setFont(UiTheme.font(10));
        g.fillText("WYNIK", 24, 30);
        g.setFill(Color.web(UiTheme.TEXT));
        g.setFont(UiTheme.fontBold(22));
        g.fillText(formatScore(score.totalScore()), 24, 54);

        g.setFill(Color.web(UiTheme.TEXT_DIM));
        g.setFont(UiTheme.font(10));
        g.fillText("H " + score.hits() + "   M " + score.misses(), 24, 68);

        g.setTextAlign(TextAlignment.RIGHT);
        int mult = score.multiplier();
        int combo = score.combo();
        g.setFill(Color.web(UiTheme.TEXT_DIM));
        g.setFont(UiTheme.font(10));
        g.fillText("COMBO", CANVAS_WIDTH - 24, 30);

        Color multColor = mult > 1 ? Color.web(UiTheme.ACCENT_SOFT) : Color.web(UiTheme.TEXT_MUTED);
        g.setFill(multColor);
        g.setFont(UiTheme.fontBold(22));
        g.fillText("x" + mult, CANVAS_WIDTH - 24, 54);

        if (combo > 0) {
            g.setFill(Color.web("#fb923c"));
            g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 13));
            g.fillText(combo + "×", CANVAS_WIDTH - 24, 68);
        }

        // Pasek tytułu na dole
        double barW = Math.min(340, CANVAS_WIDTH - 40);
        double barX = (CANVAS_WIDTH - barW) / 2.0;
        double barY = CANVAS_HEIGHT - 34;
        g.setFill(Color.color(0.06, 0.08, 0.14, 0.88));
        g.fillRoundRect(barX, barY, barW, 26, 13, 13);
        g.setStroke(Color.web(UiTheme.BORDER));
        g.setLineWidth(1);
        g.strokeRoundRect(barX + 0.5, barY + 0.5, barW - 1, 25, 13, 13);

        g.setFill(Color.web(UiTheme.TEXT_MUTED));
        g.setFont(UiTheme.font(11));
        g.setTextAlign(TextAlignment.CENTER);
        String footer = context.title() + "  ·  " + context.bpm() + " BPM";
        g.fillText(footer, CANVAS_WIDTH / 2.0, barY + 17);
    }

    private static void drawGlassPanel(GraphicsContext g, double x, double y, double w, double h) {
        g.setFill(Color.color(0.07, 0.09, 0.15, 0.82));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setStroke(Color.web(UiTheme.BORDER));
        g.setLineWidth(1);
        g.strokeRoundRect(x + 0.5, y + 0.5, w - 1, h - 1, 12, 12);
    }

    private static String formatScore(int score) {
        if (score < 1000) return String.valueOf(score);
        return String.format("%,d", score).replace(',', ' ');
    }

    // ---------- finalizacja ----------

    private void finishIfNotYet() {
        if (finished) return;
        finished = true;
        if (loop != null) loop.stop();
        if (player != null) {
            player.stop();
            player.dispose();
        }
        GameResult result = score.toResult(context.songId());
        LOG.info(() -> "GameResult: " + result);
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
