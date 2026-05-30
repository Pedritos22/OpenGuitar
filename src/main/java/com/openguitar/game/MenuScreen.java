package com.openguitar.game;

import com.openguitar.beatmap.BeatmapEngine;
import com.openguitar.beatmap.SongContext;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ekran menu głównego: lista utworów z {@code songs/}, każdy ze swoim
 * statusem (READY / NO BEATMAP) i przyciskiem akcji (Graj / Generuj + graj).
 *
 * <p>Generacja beatmapy odbywa się w {@link Task} na osobnym wątku, aby UI
 * pozostało responsywne. Gdy się zakończy, wywoływany jest callback
 * {@link Consumer onSongSelected} ze świeżo wygenerowanym {@link SongContext}.
 */
public final class MenuScreen {

    private static final Logger LOG = Logger.getLogger(MenuScreen.class.getName());

    public static final int WIDTH  = GameScreen.CANVAS_WIDTH;   // ten sam rozmiar co gra
    public static final int HEIGHT = GameScreen.CANVAS_HEIGHT;

    private final Path songsDir;
    private final Consumer<SongContext> onSongSelected;
    private final Runnable onExit;

    private final Scene scene;
    private final VBox songsList;
    private final Label statusLabel;

    public MenuScreen(Path songsDir, Consumer<SongContext> onSongSelected, Runnable onExit) {
        this.songsDir = songsDir;
        this.onSongSelected = onSongSelected;
        this.onExit = onExit;

        VBox root = new VBox(18);
        root.setPadding(new Insets(36, 36, 28, 36));
        root.setStyle("-fx-background-color: #0b0d12;");
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("OpenGuitar");
        title.setFont(Font.font("System", FontWeight.BOLD, 36));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Wybierz utwór");
        subtitle.setFont(Font.font(13));
        subtitle.setTextFill(Color.web("#9ca3af"));

        statusLabel = new Label(" ");
        statusLabel.setTextFill(Color.web("#9ca3af"));
        statusLabel.setFont(Font.font(11));

        songsList = new VBox(8);
        songsList.setAlignment(Pos.TOP_CENTER);
        songsList.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(songsList);
        scroll.setFitToWidth(true);
        scroll.setStyle(
                "-fx-background: #0b0d12;" +
                "-fx-background-color: #0b0d12;" +
                "-fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button refreshBtn = darkButton("Odśwież listę");
        refreshBtn.setOnAction(e -> reload());
        Button exitBtn = darkButton("Wyjście");
        exitBtn.setOnAction(e -> onExit.run());
        HBox footer = new HBox(10, refreshBtn, exitBtn);
        footer.setAlignment(Pos.CENTER);

        Label hint = new Label("Klawisze w grze:  D  F  J  K     ·     ESC: zakończ utwór / wyjście");
        hint.setTextFill(Color.web("#6b7280"));
        hint.setFont(Font.font(10));

        root.getChildren().addAll(title, subtitle, statusLabel, scroll, footer, hint);

        this.scene = new Scene(root, WIDTH, HEIGHT);
        this.scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) onExit.run();
        });

        reload();
    }

    public Scene getScene() {
        return scene;
    }

    /** Re-skanuje katalog z utworami i odbudowuje listę. */
    public void reload() {
        songsList.getChildren().clear();
        try {
            List<SongEntry> entries = new SongLibrary(songsDir).scan();
            if (entries.isEmpty()) {
                Label empty = new Label("(pusto - wrzuć pliki .mp3 / .wav do " + songsDir + "/)");
                empty.setTextFill(Color.web("#6b7280"));
                empty.setPadding(new Insets(20));
                songsList.getChildren().add(empty);
                setStatus("Brak utworów");
                return;
            }
            for (SongEntry e : entries) {
                songsList.getChildren().add(buildRow(e));
            }
            int ready = (int) entries.stream().filter(SongEntry::hasBeatmap).count();
            setStatus(entries.size() + " utwór(ów),  " + ready + " z gotową beatmapą");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Skanowanie songs/ nie powiodło się", ex);
            setStatus("Błąd skanowania: " + ex.getMessage());
        }
    }

    // --------------------------- row builder ---------------------------

    private HBox buildRow(SongEntry entry) {
        Label name = new Label(entry.title());
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("System", FontWeight.SEMI_BOLD, 15));

        Label fileLabel = new Label(entry.audioPath().getFileName().toString()
                + (entry.hasBeatmap()
                ? "   ·   " + entry.context().notes().size() + " nut, " + entry.context().bpm() + " BPM"
                : ""));
        fileLabel.setTextFill(Color.web("#6b7280"));
        fileLabel.setFont(Font.font(11));

        VBox info = new VBox(2, name, fileLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label badge = new Label(entry.hasBeatmap() ? "READY" : "NO BEATMAP");
        badge.setFont(Font.font("System", FontWeight.BOLD, 10));
        badge.setStyle(entry.hasBeatmap()
                ? "-fx-background-color: #14532d; -fx-text-fill: #86efac;"
                + " -fx-padding: 3 8 3 8; -fx-background-radius: 4;"
                : "-fx-background-color: #422006; -fx-text-fill: #fdba74;"
                + " -fx-padding: 3 8 3 8; -fx-background-radius: 4;");

        Button action = new Button(entry.hasBeatmap() ? "Graj" : "Generuj + graj");
        action.setStyle(entry.hasBeatmap()
                ? "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-padding: 7 18 7 18; -fx-cursor: hand; -fx-background-radius: 5;"
                : "-fx-background-color: #b45309; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-padding: 7 18 7 18; -fx-cursor: hand; -fx-background-radius: 5;");
        action.setOnAction(e -> handleSelect(entry, action));

        HBox row = new HBox(12, info, badge, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 16));
        row.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        return row;
    }

    private static Button darkButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #d1d5db;"
                + " -fx-padding: 8 18 8 18; -fx-cursor: hand; -fx-background-radius: 5;");
        return b;
    }

    // --------------------------- selection / generation ---------------------

    private void handleSelect(SongEntry entry, Button button) {
        button.setDisable(true);

        if (entry.hasBeatmap()) {
            // SongContext z BeatmapLoader-a ma audioPath relatywny - rozwiniemy do absolutnego,
            // żeby MediaPlayer w GameScreen nie polegał na bieżącym katalogu.
            onSongSelected.accept(withAbsoluteAudio(entry.context(), entry.audioPath()));
            return;
        }

        button.setText("Generuję...");
        setStatus("Analiza audio: " + entry.audioPath().getFileName() + " - może chwilę potrwać.");

        // Generacja na osobnym wątku - blokujący ComplexOnsetDetector przeszkadzałby UI.
        Task<SongContext> task = new Task<>() {
            @Override
            protected SongContext call() throws Exception {
                BeatmapEngine engine = new BeatmapEngine();
                SongContext generated = engine.generateAndSave(
                        entry.audioPath(),
                        entry.beatmapPath(),
                        entry.title(),       // songId = title (deterministyczny seed dla SEEDED_PSEUDO_RANDOM)
                        entry.title()
                );
                return withAbsoluteAudio(generated, entry.audioPath());
            }
        };
        task.setOnSucceeded(ev -> {
            setStatus("Gotowe.");
            onSongSelected.accept(task.getValue());
        });
        task.setOnFailed(ev -> {
            Throwable t = task.getException();
            LOG.log(Level.WARNING, "Generacja beatmapy nie powiodła się", t);
            button.setDisable(false);
            button.setText("Spróbuj ponownie");
            setStatus("Błąd: " + (t != null ? t.getMessage() : "?"));
        });
        Thread thread = new Thread(task, "BeatmapGen-" + entry.title());
        thread.setDaemon(true);
        thread.start();
    }

    /** Buduje kopię {@link SongContext} z absolutną ścieżką audio. */
    private static SongContext withAbsoluteAudio(SongContext ctx, Path audioFile) {
        return new SongContext(
                ctx.songId(),
                ctx.title(),
                ctx.bpm(),
                audioFile.toAbsolutePath().toString(),
                ctx.notes()
        );
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }
}
