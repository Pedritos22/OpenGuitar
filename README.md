# OpenGuitar

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-FF6A00?logo=java&logoColor=white)](https://openjfx.io/)
[![Tests](https://img.shields.io/badge/Tests-100-25A162?logo=junit5&logoColor=white)](DOCUMENTATION.md#12-tests)
[![GitHub](https://img.shields.io/badge/GitHub-OpenGuitar-181717?logo=github)](https://github.com/Pedritos22/OpenGuitar)

A desktop rhythm game in the style of Guitar Hero. Drop your music into a folder, hit the notes in time, and chase high scores and S-ranks.

Built with **Java 21**, **Maven**, and **JavaFX**. Beatmaps are generated automatically from your audio files and saved as JSON for instant replay.

For architecture, audio sync, file formats, and test details, see **[DOCUMENTATION.md](DOCUMENTATION.md)**.

---

## Requirements

- **JDK 21** — JavaFX is pulled in by Maven; no separate JavaFX install needed
- **Maven 3.9+**

---

## Quick start

1. Add audio files to `songs/`:

   ```
   songs/my-track.mp3
   songs/another-track.wav
   ```

2. Launch the game:

   ```bash
   ./play.sh
   ```

3. On the title screen, choose **PLAY**, pick a song from the list, then click **Play** (or **Generate** if no `.json` beatmap exists yet).

### Screen flow

```
Title screen (PLAY / SETTINGS / QUIT)
    │ PLAY
    ▼
Song list (stats, history, settings)
    │ Play
    ▼
Gameplay → results screen → song list
    │ Back / ESC (from song list)
    ▼
Title screen
```

Quit the application (**QUIT**) from the title screen only.

### In-game controls

| Action | Default |
|--------|---------|
| Lanes 1–4 | **D** **F** **J** **K** (configurable in settings) |
| Pause | **ESC** → pause menu (Resume / Quit to menu) |
| Fullscreen | **F11** (preserved when switching between menu and gameplay) |

Before a song starts, a **3 → 2 → 1 → GO!** countdown runs. Set the length in settings (0 disables it). You can also enable a countdown when resuming from pause.

When a song ends, an **animated results screen** shows PERFECT / GREAT / MISS counts, combo, accuracy, and an S–E rank. Press **ENTER** to return to the song list.

### Title screen and song list

**Title screen**

- **PLAY** — open the song list
- **SETTINGS** — same panel as on the song list (controls, audio, gameplay, display)
- **QUIT** — exit the application
- Navigation: arrow keys, Enter, mouse (hover + click)

**Song list**

- Click a row to select; arrow keys + Enter also work
- **Stats** — panel at the top (attempts, best score, best combo for the selected song)
- **Rank** — S–E letter next to the title (best result)
- **History** — recent attempts (button or **H**)
- **⚙ Settings** — see section below
- **Back** / **ESC** — return to the title screen
- **Refresh** — rescan the `songs/` folder

### Settings (⚙)

The settings panel is shared on the title screen and song list.

| Section | Options |
|---------|---------|
| **CONTROLS** | Key bindings for 4 lanes |
| **AUDIO** | Lobby music volume, in-game song volume, menu UI SFX volume, hit sounds during gameplay |
| **GAMEPLAY** | Reaction time (2.2 s / 1.7 s / 1.2 s — how early notes appear), pre-start countdown, countdown on resume, hit popups (PERFECT/GREAT/MISS), combo and multiplier popups |
| **DISPLAY** | Fullscreen on launch, UI language |

Supported languages: **Polish**, **English**, **German**, **Spanish**, **French**, and **Italian**. Rhythm-game terms (PERFECT, SCORE, COMBO, GO!) stay in English across all locales.

Settings are saved to `settings.properties` (e.g. `gameplay.reaction.time`, `audio.ui.sfx.volume`, `popups.combo`, `gameplay.countdown.resume`, `display.fullscreen.start`, `display.locale`).

### Audio

| Context | What plays |
|---------|------------|
| **Title screen** | `song_lobby.mp3` |
| **Song list** | `song_ending.mp3` |
| **Gameplay** | Selected track from `songs/` (volume in settings) |
| **Results** | `song_ending.mp3` (looped) |
| **UI** | Menu clicks, navigation, countdown, pause (UI SFX volume in settings) |
| **Hits** | PERFECT / GREAT / MISS / combo sounds (can be disabled in settings) |

Transitions between the title screen and song list use a short crossfade between lobby tracks.

### Combo and colors

The combo counter and multiplier in the HUD, plus on-screen popups, shift color as you climb thresholds (e.g. teal from 10, blue from 20, bright cyan from 30, magenta from 50, gold from 100). Combo popups appear every 10 hits and at 25, 50, and 100.

---

## Launch options

```bash
./play.sh                              # title screen (default)
./play.sh songs/track.mp3              # jump straight into gameplay (auto-generate beatmap)
./play.sh songs/track.json             # play with an existing beatmap
./play.sh songs/track.mp3 --regen      # force beatmap regeneration
./play.sh --list                       # list audio files in songs/
```

**Diagnostic logs** — `./play.sh` loads `src/main/resources/logging.properties`. The console shows app, lobby audio, and gameplay events (prefix `[component][FX|BG]`, e.g. `[game][FX] start()`). See [DOCUMENTATION.md §14](DOCUMENTATION.md#14-diagnostic-logging).

Equivalent Maven commands:

```bash
mvn javafx:run
mvn javafx:run -Djavafx.args="songs/track.json"
mvn test                               # unit tests (beatmap + game)
```

### JVM warnings on startup (macOS / Java 21+)

When running `./play.sh` or `mvn javafx:run`, you may see yellow lines like `WARNING: A restricted method in java.lang.System has been called`. This is **not a game error** — it is Java reporting native library loading (JavaFX, and optionally SQLite for stats). The game should run normally. Details in [DOCUMENTATION.md §14](DOCUMENTATION.md#14-diagnostic-logging).

---

## Runtime files

These files are created next to the repository while you play and are **not committed** (see `.gitignore`):

| File | Description |
|------|-------------|
| `stats.db` | Stats and attempt history (SQLite) |
| `settings.properties` | Keys, volume, sounds, countdown, reaction time, UI options |
| `songs/*.json` | Beatmaps — **can** be committed if you want to share them |
| `songs/*.{mp3,wav,...}` | Your audio — **do not commit** (size, copyright) |

> **Note:** The older `stats.json` format is no longer used — stats moved to `stats.db`. If you still have a local `stats.json`, you can delete it safely.

---

## Repository layout

```
OpenGuitar/
├── play.sh                 # launch wrapper
├── songs/                  # audio + JSON beatmaps
├── src/main/java/
│   ├── com/openguitar/beatmap/   # audio analysis & beatmap generation
│   └── com/openguitar/game/      # JavaFX application
├── src/main/resources/
│   ├── fonts/                    # Bebas Neue, Rajdhani (OFL)
│   ├── i18n/                     # UI translations (pl, en, de, es, fr, it)
│   ├── images/                   # menu logo
│   ├── logging.properties        # console log config (INFO / FINE)
│   └── sound/                    # lobby music + SFX (Kenney CC0)
├── src/test/java/                # JUnit 5 tests (beatmap + game)
├── README.md                     # this file
└── DOCUMENTATION.md              # technical documentation
```

---

## Asset licenses

| Asset | License |
|-------|---------|
| Fonts (`src/main/resources/fonts/`) | **SIL Open Font License** — see `OFL-*.txt` |
| UI SFX (`src/main/resources/sound/sfx_*.wav`) | **CC0** — [Kenney UI Audio](https://kenney.nl/assets/ui-audio); see `KENNEY_UI_AUDIO_LICENSE.txt` |
| Lobby music (`song_lobby.mp3`, `song_ending.mp3`) | Project files in the repository |
