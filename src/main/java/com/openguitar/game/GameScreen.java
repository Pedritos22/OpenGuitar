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
import javafx.scene.text.Font;
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
 * <h2>Synchronizacja czas → pozycja Y</h2>
 * Pozycja każdej nuty jest funkcją czystą czasu odtwarzania audio:
 * <pre>
 *   y(note) = HIT_LINE_Y - (note.timeMs - currentAudioMs) * SPEED_PX_PER_MS
 * </pre>
 * Gdy {@code currentAudioMs == note.timeMs} -> nuta dokładnie na hit-line.
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

    /** 0.6 px/ms = 600 px/s -> nuta zjeżdża z górnej krawędzi do hit-line w ~1 sekundę. */
    public static final double SPEED_PX_PER_MS = 0.6;

    /** Klawisze poszczególnych ścieżek (klasyczny układ "DFJK"). */
    private static final KeyCode[] LANE_KEYS = {
            KeyCode.D, KeyCode.F, KeyCode.J, KeyCode.K
    };

    /** Kolory ścieżek - klasyczny GuitarHero (zielony/czerwony/żółty/niebieski). */
    private static final Color[] LANE_COLORS = {
            Color.web("#22c55e"),
            Color.web("#ef4444"),
            Color.web("#facc15"),
            Color.web("#3b82f6")
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
    /** Ostatnio zaobserwowane judgment per ścieżka - do napisu nad hit-line. */
    private final HitJudgment[] laneLastJudgment = new HitJudgment[LANES];

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
        root.setStyle("-fx-background-color: #0b0d12;");
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
                score.registerMiss();
                laneLastJudgment[rn.note.lane()] = HitJudgment.MISS;
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
        score.register(j);
        laneLastJudgment[lane] = j;
        laneFlashUntilNanos[lane] = System.nanoTime() + 180_000_000L; // 180ms feedback
    }

    // ---------- rendering ----------

    private void render(long nowNanos) {
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.web("#0b0d12"));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        drawLanes(g, nowNanos);
        drawHitLine(g);
        drawNotes(g);
        drawHud(g);
    }

    private void drawLanes(GraphicsContext g, long nowNanos) {
        for (int lane = 0; lane < LANES; lane++) {
            double x = SIDE_MARGIN + lane * LANE_WIDTH;

            // tło ścieżki - jaśniejsze gdy klawisz wciśnięty
            if (laneHeld[lane]) {
                g.setFill(Color.color(1, 1, 1, 0.07));
            } else {
                g.setFill(Color.color(1, 1, 1, 0.025));
            }
            g.fillRect(x, 0, LANE_WIDTH, CANVAS_HEIGHT);

            // separator
            g.setStroke(Color.color(1, 1, 1, 0.12));
            g.setLineWidth(1);
            g.strokeLine(x, 0, x, CANVAS_HEIGHT);

            // flash trafienia (krótkotrwałe podświetlenie ścieżki)
            if (nowNanos < laneFlashUntilNanos[lane]) {
                double alpha = (laneFlashUntilNanos[lane] - nowNanos) / 180_000_000.0;
                g.setFill(LANE_COLORS[lane].deriveColor(0, 1, 1, 0.35 * alpha));
                g.fillRect(x, 0, LANE_WIDTH, CANVAS_HEIGHT);
            }
        }
        // prawa krawędź ostatniej ścieżki
        g.setStroke(Color.color(1, 1, 1, 0.12));
        g.strokeLine(SIDE_MARGIN + LANES * LANE_WIDTH, 0,
                     SIDE_MARGIN + LANES * LANE_WIDTH, CANVAS_HEIGHT);
    }

    private void drawHitLine(GraphicsContext g) {
        // pasek hit-line + receptory na końcu każdej ścieżki
        g.setFill(Color.color(1, 1, 1, 0.08));
        g.fillRect(SIDE_MARGIN, HIT_LINE_Y - 4, LANE_WIDTH * LANES, 8);

        for (int lane = 0; lane < LANES; lane++) {
            double cx = SIDE_MARGIN + lane * LANE_WIDTH + LANE_WIDTH / 2.0;
            // okrąg-receptor
            g.setStroke(LANE_COLORS[lane]);
            g.setLineWidth(laneHeld[lane] ? 4 : 2);
            g.strokeOval(cx - 22, HIT_LINE_Y - 22, 44, 44);

            // klawisz
            g.setFill(Color.color(1, 1, 1, 0.85));
            g.setFont(Font.font(14));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(LANE_KEYS[lane].getName(), cx, HIT_LINE_Y + 60);
        }
    }

    private void drawNotes(GraphicsContext g) {
        for (RuntimeNote rn : runtimeNotes) {
            if (rn.processed) continue;
            int dt = rn.note.timeMs() - (int) currentTimeMs;
            double y = HIT_LINE_Y - dt * SPEED_PX_PER_MS;

            // off-screen culling
            if (y + NOTE_HEIGHT < 0 || y > CANVAS_HEIGHT) continue;

            int lane = rn.note.lane();
            double x = SIDE_MARGIN + lane * LANE_WIDTH + 10;
            double w = LANE_WIDTH - 20;

            g.setFill(LANE_COLORS[lane]);
            g.fillRoundRect(x, y - NOTE_HEIGHT / 2.0, w, NOTE_HEIGHT, 10, 10);

            // delikatny highlight u góry
            g.setFill(Color.color(1, 1, 1, 0.25));
            g.fillRoundRect(x, y - NOTE_HEIGHT / 2.0, w, NOTE_HEIGHT / 3.0, 10, 10);
        }
    }

    private void drawHud(GraphicsContext g) {
        g.setFill(Color.WHITE);
        g.setFont(Font.font(16));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText("Score: " + score.totalScore(), 16, 28);
        g.fillText("Hits: " + score.hits() + "  Misses: " + score.misses(), 16, 50);

        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText("x" + score.multiplier(), CANVAS_WIDTH - 16, 28);
        g.fillText("Combo: " + score.combo(), CANVAS_WIDTH - 16, 50);

        // tytuł utworu w stopce
        g.setFill(Color.color(1, 1, 1, 0.5));
        g.setFont(Font.font(12));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(context.title() + "  ·  " + context.bpm() + " BPM",
                CANVAS_WIDTH / 2.0, CANVAS_HEIGHT - 12);

        // ostatnie judgmenty nad hit-line
        for (int lane = 0; lane < LANES; lane++) {
            HitJudgment j = laneLastJudgment[lane];
            if (j == null) continue;
            double cx = SIDE_MARGIN + lane * LANE_WIDTH + LANE_WIDTH / 2.0;
            g.setFill(switch (j) {
                case PERFECT -> Color.web("#22d3ee");
                case GREAT   -> Color.web("#a3e635");
                case MISS    -> Color.web("#f87171");
            });
            g.setFont(Font.font(13));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(j.name(), cx, HIT_LINE_Y - 40);
        }
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
