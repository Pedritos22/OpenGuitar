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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ekran menu głównego w stylu Persona 3 Reload: lista utworów z {@code songs/},
 * status (GOTOWE / BRAK MAPY) i akcja (Graj lub Generuj → Graj).
 */
public final class MenuScreen {

    private static final Logger LOG = Logger.getLogger(MenuScreen.class.getName());

    public static final int WIDTH  = GameScreen.CANVAS_WIDTH;
    public static final int HEIGHT = GameScreen.CANVAS_HEIGHT;

    private static final Insets CONTENT_PAD = new Insets(14, 16, 10, 16);
    private static final double ROW_INDEX_WIDTH = 30;
    private static final double ROW_ACTION_WIDTH = 76;

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

        MenuBackground background = new MenuBackground();
        background.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        songsList = new VBox(5);
        songsList.setAlignment(Pos.TOP_CENTER);
        songsList.setFillWidth(true);

        statusLabel = ellipsisLabel(" ");
        statusLabel.setTextFill(Color.web(PersonaMenuTheme.ACCENT_GLOW));
        statusLabel.setFont(PersonaMenuTheme.bodyFont(10));
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        BorderPane content = new BorderPane();
        content.setPadding(CONTENT_PAD);
        content.setStyle(PersonaMenuTheme.rootOverlay());
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        content.setTop(buildHeader());
        content.setCenter(buildListArea());
        content.setBottom(buildFooter());

        StackPane root = new StackPane(background, content);
        StackPane.setAlignment(content, Pos.TOP_LEFT);

        this.scene = new Scene(root, WIDTH, HEIGHT);
        this.scene.setFill(Color.web(PersonaMenuTheme.BG_DEEP));
        this.scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) onExit.run();
        });

        reload();
    }

    private VBox buildHeader() {
        Label sessionTag = new Label("MUSIC SELECT");
        sessionTag.setStyle(PersonaMenuTheme.sessionTag());
        sessionTag.setMaxWidth(Region.USE_PREF_SIZE);

        Label titleOpen = new Label("OPEN");
        titleOpen.setFont(PersonaMenuTheme.displayFont(32));
        titleOpen.setTextFill(Color.web(PersonaMenuTheme.TEXT));
        titleOpen.setStyle("-fx-effect: dropshadow(gaussian, rgba(0, 212, 255, 0.4), 14, 0, 2, 0);");

        Label titleGuitar = new Label("GUITAR");
        titleGuitar.setFont(PersonaMenuTheme.displayFont(32));
        titleGuitar.setTextFill(Color.web(PersonaMenuTheme.ACCENT));

        Label subtitle = new Label("WYBIERZ UTWÓR");
        subtitle.setFont(PersonaMenuTheme.labelFont(10));
        subtitle.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));

        Region headerLine = new Region();
        headerLine.setStyle(PersonaMenuTheme.listHeaderLine());
        headerLine.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(headerLine, new Insets(6, 0, 0, 0));

        VBox titles = new VBox(-4, titleOpen, titleGuitar);
        HBox titleRow = new HBox(12, sessionTag, titles);
        titleRow.setAlignment(Pos.BOTTOM_LEFT);

        VBox header = new VBox(4, titleRow, subtitle, headerLine);
        BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
        return header;
    }

    private VBox buildListArea() {
        Label listCaption = new Label("TRACK LIST");
        listCaption.setFont(PersonaMenuTheme.labelFont(9));
        listCaption.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));

        ScrollPane scroll = new ScrollPane(songsList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(PersonaMenuTheme.scrollPane());
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox listBlock = new VBox(5, listCaption, scroll);
        VBox.setVgrow(listBlock, Priority.ALWAYS);
        return listBlock;
    }

    private VBox buildFooter() {
        Label hint = new Label("D·F·J·K — GRA  |  ESC — WYJŚCIE");
        hint.setStyle(PersonaMenuTheme.hintChip());
        hint.setWrapText(false);

        Button refreshBtn = styledButton("ODŚWIEŻ", false);
        refreshBtn.setOnAction(e -> reload());
        Button exitBtn = styledButton("WYJŚCIE", false);
        exitBtn.setOnAction(e -> onExit.run());

        HBox actions = new HBox(6, refreshBtn, exitBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox footerRow = new HBox(8, hint, new Region(), actions);
        footerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(footerRow.getChildren().get(1), Priority.ALWAYS);

        VBox footer = new VBox(6, statusLabel, footerRow);
        footer.setStyle(PersonaMenuTheme.statusBar());
        footer.setMaxWidth(Double.MAX_VALUE);
        BorderPane.setMargin(footer, new Insets(8, 0, 0, 0));
        return footer;
    }

    public Scene getScene() {
        return scene;
    }

    public void reload() {
        songsList.getChildren().clear();
        try {
            List<SongEntry> entries = new SongLibrary(songsDir).scan();
            if (entries.isEmpty()) {
                Label empty = new Label("WRZUĆ PLIKI .MP3 LUB .WAV DO FOLDERU songs/");
                empty.setTextFill(Color.web(PersonaMenuTheme.TEXT_MUTED));
                empty.setFont(PersonaMenuTheme.bodyFont(12));
                empty.setWrapText(true);
                empty.setMaxWidth(Double.MAX_VALUE);
                empty.setStyle(PersonaMenuTheme.emptyCard());
                songsList.getChildren().add(empty);
                setStatus("BRAK UTWORÓW W songs/");
                return;
            }
            int index = 1;
            for (SongEntry e : entries) {
                songsList.getChildren().add(buildRow(e, index++));
            }
            int ready = (int) entries.stream().filter(SongEntry::hasBeatmap).count();
            setStatus(entries.size() + " UTWORÓW · " + ready + " GOTOWYCH");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Skanowanie songs/ nie powiodło się", ex);
            setStatus("BŁĄD: " + ex.getMessage());
        }
    }

    private HBox buildRow(SongEntry entry, int index) {
        boolean ready = entry.hasBeatmap();

        Label indexLabel = new Label(String.format("%02d", index));
        indexLabel.setStyle(PersonaMenuTheme.indexLabel());
        indexLabel.setMinWidth(ROW_INDEX_WIDTH);
        indexLabel.setPrefWidth(ROW_INDEX_WIDTH);
        indexLabel.setMaxWidth(ROW_INDEX_WIDTH);
        indexLabel.setAlignment(Pos.TOP_CENTER);

        Label name = ellipsisLabel(entry.title().toUpperCase());
        name.setTextFill(Color.web(PersonaMenuTheme.TEXT));
        name.setFont(PersonaMenuTheme.labelFont(13));

        Label fileLabel = ellipsisLabel(metaLine(entry, ready ? entry.context() : null));
        fileLabel.setTextFill(Color.web(PersonaMenuTheme.TEXT_DIM));
        fileLabel.setFont(PersonaMenuTheme.bodyFont(9));

        Label badge = new Label(ready ? "READY" : "NO MAP");
        badge.setStyle(ready ? PersonaMenuTheme.badgeReady() : PersonaMenuTheme.badgePending());
        badge.setMinWidth(Region.USE_PREF_SIZE);

        HBox metaRow = new HBox(6, fileLabel, badge);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fileLabel, Priority.ALWAYS);

        VBox info = new VBox(2, name, metaRow);
        info.setMinWidth(0);
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button action = new Button(ready ? "GRAJ" : "GEN");
        action.setStyle(ready ? PersonaMenuTheme.rowActionButton() : PersonaMenuTheme.rowWarnButton());
        action.setMinWidth(ROW_ACTION_WIDTH);
        action.setPrefWidth(ROW_ACTION_WIDTH);
        action.setMaxWidth(ROW_ACTION_WIDTH);

        final SongContext[] contextHolder = new SongContext[1];
        if (ready) {
            contextHolder[0] = withAbsoluteAudio(entry.context(), entry.audioPath());
        }

        HBox row = new HBox(8, indexLabel, info, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 10, 9, 8));
        row.setMaxWidth(Double.MAX_VALUE);
        applyRowStyle(row, ready, false);

        row.setOnMouseEntered(e -> applyRowStyle(row, ready, true));
        row.setOnMouseExited(e -> applyRowStyle(row, ready, false));

        action.setOnAction(e -> {
            if (contextHolder[0] != null) {
                startGame(contextHolder[0], action);
            } else {
                generateBeatmap(entry, action, row, badge, fileLabel, contextHolder);
            }
        });

        return row;
    }

    private static void applyRowStyle(HBox row, boolean ready, boolean hover) {
        row.setStyle(hover ? PersonaMenuTheme.cardRowHover(ready) : PersonaMenuTheme.cardRow(ready));
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
            meta += " · " + ctx.notes().size() + " NUT · " + ctx.bpm() + " BPM";
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
        action.setText("GRAJ");
        action.setStyle(PersonaMenuTheme.rowActionButton());
        badge.setText("READY");
        badge.setStyle(PersonaMenuTheme.badgeReady());
        fileLabel.setText(metaLine(entry, ctx));
        applyRowStyle(row, true, false);
        row.setOnMouseEntered(e -> applyRowStyle(row, true, true));
        row.setOnMouseExited(e -> applyRowStyle(row, true, false));
    }

    private static Button styledButton(String text, boolean primary) {
        Button b = new Button(text);
        b.setStyle(primary ? PersonaMenuTheme.primaryButton() : PersonaMenuTheme.secondaryButton());
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
        button.setText("…");
        setStatus("ANALIZA: " + entry.audioPath().getFileName());

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
            setStatus("GOTOWE — KLIKNIJ GRAJ");
            applyReadyRow(row, badge, fileLabel, button, contextHolder, entry, task.getValue());
        });
        task.setOnFailed(ev -> {
            Throwable t = task.getException();
            LOG.log(Level.WARNING, "Generacja beatmapy nie powiodła się", t);
            button.setDisable(false);
            button.setText("PONÓW");
            setStatus("BŁĄD: " + (t != null ? t.getMessage() : "?"));
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
