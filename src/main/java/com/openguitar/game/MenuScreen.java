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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ekran menu głównego: lista utworów z {@code songs/}, każdy ze swoim
 * statusem (GOTOWE / BRAK MAPY) i przyciskiem akcji (Graj lub najpierw Generuj, potem Graj).
 */
public final class MenuScreen {

    private static final Logger LOG = Logger.getLogger(MenuScreen.class.getName());

    public static final int WIDTH  = GameScreen.CANVAS_WIDTH;
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

        VBox root = new VBox(14);
        root.setPadding(new Insets(28, 28, 22, 28));
        root.setStyle(UiTheme.rootStyle());
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("OpenGuitar");
        title.setFont(UiTheme.fontBold(34));
        title.setTextFill(Color.web(UiTheme.TEXT));

        Label subtitle = new Label("Wybierz utwór i graj");
        subtitle.setFont(UiTheme.font(13));
        subtitle.setTextFill(Color.web(UiTheme.TEXT_MUTED));

        Region accentBar = new Region();
        accentBar.setPrefSize(88, 3);
        accentBar.setMaxWidth(88);
        accentBar.setStyle(
                "-fx-background-color: linear-gradient(to right, " + UiTheme.ACCENT + ", #a855f7);"
              + "-fx-background-radius: 2;");

        VBox header = new VBox(6, title, subtitle, accentBar);
        header.setAlignment(Pos.CENTER);

        statusLabel = new Label(" ");
        statusLabel.setTextFill(Color.web(UiTheme.TEXT_DIM));
        statusLabel.setFont(UiTheme.font(11));

        songsList = new VBox(10);
        songsList.setAlignment(Pos.TOP_CENTER);
        songsList.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(songsList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(UiTheme.scrollPaneStyle());
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button refreshBtn = styledButton("Odśwież", false);
        refreshBtn.setOnAction(e -> reload());
        Button exitBtn = styledButton("Wyjście", false);
        exitBtn.setOnAction(e -> onExit.run());
        HBox footer = new HBox(10, refreshBtn, exitBtn);
        footer.setAlignment(Pos.CENTER);

        Label hint = new Label("D · F · J · K  —  gra   |   ESC — menu / wyjście");
        hint.setTextFill(Color.web(UiTheme.TEXT_DIM));
        hint.setFont(Font.font(10));
        hint.setStyle(
                "-fx-background-color: " + UiTheme.BG_CARD + ";"
              + "-fx-padding: 6 14 6 14;"
              + "-fx-background-radius: 20;"
              + "-fx-border-color: " + UiTheme.BORDER + ";"
              + "-fx-border-radius: 20;"
              + "-fx-border-width: 1;");

        root.getChildren().addAll(header, statusLabel, scroll, footer, hint);

        this.scene = new Scene(root, WIDTH, HEIGHT);
        this.scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) onExit.run();
        });

        reload();
    }

    public Scene getScene() {
        return scene;
    }

    public void reload() {
        songsList.getChildren().clear();
        try {
            List<SongEntry> entries = new SongLibrary(songsDir).scan();
            if (entries.isEmpty()) {
                Label empty = new Label("Wrzuć pliki .mp3 lub .wav do folderu songs/");
                empty.setTextFill(Color.web(UiTheme.TEXT_DIM));
                empty.setFont(UiTheme.font(13));
                empty.setPadding(new Insets(24));
                empty.setStyle(
                        "-fx-background-color: " + UiTheme.BG_CARD + ";"
                      + "-fx-background-radius: 10;"
                      + "-fx-border-color: " + UiTheme.BORDER + ";"
                      + "-fx-border-radius: 10;"
                      + "-fx-border-width: 1;");
                songsList.getChildren().add(empty);
                setStatus("Brak utworów w songs/");
                return;
            }
            for (SongEntry e : entries) {
                songsList.getChildren().add(buildRow(e));
            }
            int ready = (int) entries.stream().filter(SongEntry::hasBeatmap).count();
            setStatus(entries.size() + " utworów  ·  " + ready + " gotowych do gry");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Skanowanie songs/ nie powiodło się", ex);
            setStatus("Błąd: " + ex.getMessage());
        }
    }

    private HBox buildRow(SongEntry entry) {
        boolean ready = entry.hasBeatmap();

        Label name = ellipsisLabel(entry.title());
        name.setTextFill(Color.web(UiTheme.TEXT));
        name.setFont(UiTheme.fontSemiBold(15));

        Label fileLabel = ellipsisLabel(metaLine(entry, ready ? entry.context() : null));
        fileLabel.setTextFill(Color.web(UiTheme.TEXT_DIM));
        fileLabel.setFont(UiTheme.font(11));

        VBox info = new VBox(3, name, fileLabel);
        info.setMinWidth(0);
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label badge = new Label(ready ? "GOTOWE" : "BRAK MAPY");
        badge.setStyle(ready ? UiTheme.badgeReady() : UiTheme.badgePending());
        badge.setMinWidth(Region.USE_PREF_SIZE);

        Button action = new Button(ready ? "Graj" : "Generuj");
        action.setStyle(ready ? UiTheme.primaryButton() : UiTheme.warnButton());
        action.setMinWidth(Region.USE_PREF_SIZE);

        final SongContext[] contextHolder = new SongContext[1];
        if (ready) {
            contextHolder[0] = withAbsoluteAudio(entry.context(), entry.audioPath());
        }

        HBox row = new HBox(14, info, badge, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 16, 14, 18));
        row.setStyle(UiTheme.cardRow(ready));
        row.setOnMouseEntered(e -> row.setStyle(UiTheme.cardRowHover(ready)));
        row.setOnMouseExited(e -> row.setStyle(UiTheme.cardRow(ready)));

        action.setOnAction(e -> {
            if (contextHolder[0] != null) {
                startGame(contextHolder[0], action);
            } else {
                generateBeatmap(entry, action, row, badge, fileLabel, contextHolder);
            }
        });

        return row;
    }

    private static Label ellipsisLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setEllipsisString("...");
        return label;
    }

    private static String metaLine(SongEntry entry, SongContext ctx) {
        String meta = entry.audioPath().getFileName().toString();
        if (ctx != null) {
            meta += "  ·  " + ctx.notes().size() + " nut  ·  " + ctx.bpm() + " BPM";
        }
        return meta;
    }

    private void startGame(SongContext ctx, Button button) {
        button.setDisable(true);
        onSongSelected.accept(ctx);
    }

    private void applyReadyRow(
            HBox row,
            Label badge,
            Label fileLabel,
            Button action,
            SongContext[] contextHolder,
            SongEntry entry,
            SongContext ctx) {
        contextHolder[0] = ctx;
        action.setDisable(false);
        action.setText("Graj");
        action.setStyle(UiTheme.primaryButton());
        badge.setText("GOTOWE");
        badge.setStyle(UiTheme.badgeReady());
        fileLabel.setText(metaLine(entry, ctx));
        row.setStyle(UiTheme.cardRow(true));
        row.setOnMouseEntered(e -> row.setStyle(UiTheme.cardRowHover(true)));
        row.setOnMouseExited(e -> row.setStyle(UiTheme.cardRow(true)));
    }

    private static Button styledButton(String text, boolean primary) {
        Button b = new Button(text);
        b.setStyle(primary ? UiTheme.primaryButton() : UiTheme.secondaryButton());
        return b;
    }

    private void generateBeatmap(
            SongEntry entry,
            Button button,
            HBox row,
            Label badge,
            Label fileLabel,
            SongContext[] contextHolder) {
        button.setDisable(true);
        button.setText("Generuję...");
        setStatus("Analiza: " + entry.audioPath().getFileName() + " — to może chwilę potrwać");

        Task<SongContext> task = new Task<>() {
            @Override
            protected SongContext call() throws Exception {
                BeatmapEngine engine = new BeatmapEngine();
                SongContext generated = engine.generateAndSave(
                        entry.audioPath(),
                        entry.beatmapPath(),
                        entry.title(),
                        entry.title()
                );
                return withAbsoluteAudio(generated, entry.audioPath());
            }
        };
        task.setOnSucceeded(ev -> {
            setStatus("Beatmapa gotowa — kliknij Graj.");
            applyReadyRow(row, badge, fileLabel, button, contextHolder, entry, task.getValue());
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
