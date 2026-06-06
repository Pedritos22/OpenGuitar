# OpenGuitar — Technical Documentation

This document describes the architecture, algorithms, and project conventions. For a quick start, see [README.md](README.md).

---

## Table of contents

1. [Architecture overview](#1-architecture-overview)
2. [Beatmap module (`com.openguitar.beatmap`)](#2-beatmap-module-comopenguitarbeatmap)
3. [Game module (`com.openguitar.game`)](#3-game-module-comopenguitargame)
4. [View layer (`com.openguitar.game.view`)](#4-view-layer-comopenguitargameview)
5. [Time and audio synchronization](#5-time-and-audio-synchronization)
6. [Hit detection and scoring](#6-hit-detection-and-scoring)
7. [Statistics (SQLite)](#7-statistics-sqlite)
8. [Audio system (`SoundManager`)](#8-audio-system-soundmanager)
9. [User settings](#9-user-settings)
10. [Application flow](#10-application-flow)
11. [Data files](#11-data-files)
12. [Tests](#12-tests)
13. [Package structure](#13-package-structure)
14. [Diagnostic logging](#14-diagnostic-logging)

---

## 1. Architecture overview

```
┌──────────────────────────────────────────────────────────────────┐
│                          GameApp (JavaFX)                        │
│  Stage ──► TitleScreen ──► MenuScreen ◄──► GameScreen            │
│              │                 │                    │            │
│         SettingsOverlay    SongLibrary          MediaPlayer      │
│         (shared)           StatsStore           ScoreState       │
│                            GameSettings         Canvas render    │
│         SoundManager ◄──── lobby per screen / SFX / results      │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ SongContext (JSON)
┌─────────────────────────────┴────────────────────────────────────┐
│                      com.openguitar.beatmap                      │
│  audio ──► BeatmapEngine (TarsosDSP) ──► beatmap.json             │
└──────────────────────────────────────────────────────────────────┘
```

**Separation of concerns:** Game logic (scoring, combo, hits, DSP) is kept separate from the view layer. `GameScreen` **reads** state from `ScoreState` and `MediaPlayer` but does not change scoring rules. Visual changes (HUD, countdown, rendering) do not affect hit-detection correctness.

**Two audio channels:**

| Channel | Class | Source | When |
|---------|-------|--------|------|
| App music | `SoundManager` | `src/main/resources/sound/` | Menus, results, SFX |
| Song music | `GameScreen` → `MediaPlayer` | files in `songs/` | Gameplay |

When entering a song, `SoundManager.enterGameplay()` stops lobby music. On return to the song list, `playMenuMusic()` runs; on return to the title screen, `playTitleMusic()`.

---

## 2. Beatmap module (`com.openguitar.beatmap`)

### Pipeline

```
audio (.wav / .mp3 / .aiff)
        │
        ▼
 BeatmapEngine ── TarsosDSP ComplexOnsetDetector ──► [onsetTimesMs]
        │                                              │
        │ (optional) FrequencyBandClassifier ──► [dominantBand per onset]
        │                                              │
        │            BPM estimation (median IOI)         │
        │                                              ▼
        │                            lane assignment (3 strategies)
        ▼
 SongContext ── Jackson ──► beatmap.json
                              │
                              ▼
                       BeatmapLoader ──► SongContext
```

### Beatmap JSON format

```json
{
  "songId": "demo",
  "title": "Click Track Demo",
  "bpm": 118,
  "audioPath": "demo-click-track.wav",
  "notes": [
    { "timeMs": 534, "lane": 2 },
    { "timeMs": 1045, "lane": 1 }
  ]
}
```

- `lane ∈ {0, 1, 2, 3}` — four gameplay lanes.
- `audioPath` — relative to the JSON file directory (the loader resolves to an absolute path).
- `BeatmapLoader` sorts notes by `timeMs` (tolerant of manual edits).

### Lane assignment strategies

| Strategy | Description |
|----------|-------------|
| `ROUND_ROBIN` | Cycle 0→1→2→3→0… — simple, deterministic |
| `SEEDED_PSEUDO_RANDOM` | **Default.** Pseudo-random with seed `songId.hashCode()`, no two identical lanes in a row |
| `FREQUENCY_BANDS` | Lane from dominant FFT band: bass→0, low-mid→1, mid→2, high→3 |

Frequency band boundaries (`FREQUENCY_BANDS`):

| Lane | Range (Hz) | Typical source |
|------|------------|----------------|
| 0 | 20–150 | kick, bass |
| 1 | 150–500 | snare, low vocals |
| 2 | 500–2500 | melody, guitars |
| 3 | 2500–12000 | hi-hat, cymbals |

### DSP — TarsosDSP

- **Onset detection:** Complex Domain (Bello et al. 2004) — 1024-sample windows, default threshold 0.3.
- **BPM:** median inter-onset intervals, snapped to 60–200 BPM (octave correction via ×2/÷2).
- **Formats:** WAV/AIFF natively; MP3 via mp3spi (JLayer + tritonus-share).

### Parser CLI (no UI)

```bash
mvn -q exec:java -Dexec.args="track.mp3 beatmap.json 'Title'"
mvn -q exec:java -Dexec.args="--demo /tmp/openguitar-demo"
```

---

## 3. Game module (`com.openguitar.game`)

### Why JavaFX

- `MediaPlayer` — MP3/WAV playback with position reporting in ms.
- `AnimationTimer` — ~60 Hz loop driven by `nanoTime`.
- `Canvas` + `GraphicsContext` — 2D rendering without per-frame Scene Graph allocations.
- `AudioClip` — short SFX (menu, hits, countdown).

### Core classes (logic)

| Class | Role |
|-------|------|
| `HitJudgment` | PERFECT / GREAT / MISS + timing windows (±50 / ±100 ms) |
| `ScoreState` | Score, combo, multiplier, perfect/great counters — pure logic |
| `GameResult` | Immutable snapshot after a song (accuracy, rank) |
| `Rank` | S–E from accuracy thresholds and display colors |

### Application classes (orchestration + UI)

| Class | Role |
|-------|------|
| `GameApp` | `Application`, scene switching (title → menu → game), fullscreen F11, CLI args, `SoundManager` lifecycle |
| `TitleScreen` | Title screen: PLAY / SETTINGS / QUIT |
| `MenuScreen` | Song list, background beatmap generation, stats, history, back to title |
| `SettingsOverlay` | Shared settings overlay (title + song list), sections in `ScrollPane` |
| `GameScreen` | Canvas, input, audio sync, pause, countdown, results screen, combo popups |
| `ComboMilestones` | Combo popup thresholds (every 10, plus 25 / 50 / 100) |
| `SoundManager` | Per-screen lobby music, results music, UI and gameplay SFX (singleton) |
| `StatsStore` | SQLite: aggregates + attempt history |
| `GameSettings` | `settings.properties`: keys, audio, countdown, reaction time, popups, locale |
| `I18n` | UI strings from `i18n/messages_xx.properties` (UTF-8), English fallback |
| `GameLog` | Consistent `java.util.logging` with `[component][FX\|BG]` prefix |
| `SongLibrary` | Scans `songs/`, matches audio ↔ JSON |

---

## 4. View layer (`com.openguitar.game.view`)

Package is **visual only** — does not import scoring logic beyond colors and palette helpers.

| Class | Function |
|-------|----------|
| `PersonaFonts` | One-time load of Bebas Neue + Rajdhani, instance cache |
| `PersonaPalette` | Color constants (deep blue, aqua, lane tints), `comboColor()` / `multiplierColor()` |
| `PersonaText` | Canvas text: shear, outline, shadow — without expensive `Effect` nodes |
| `CrystalNote` | Crystal note and receptor geometry |
| `PersonaMenuFx` | Shear + hover slide on JavaFX nodes (click targets preserved) |
| `FullscreenScaler` | Letterbox scaling to window size |

The title screen and song list use the JavaFX Scene Graph (`TitleScreen`, `MenuScreen`, `PersonaMenuTheme`). Gameplay uses Canvas (`GameScreen`) for performance. Settings in `SettingsOverlay` use scrollable content (`ScrollPane`); panel height is constrained with `USE_PREF_SIZE` (avoids stretching to full window height).

---

## 5. Time and audio synchronization

Note position on the 3D highway is a function of **`currentTimeMs`** — a smoothed game clock synchronized with audio.

### Hybrid model (`GameScreen.advanceClock`)

Audio remains the **source of truth**, but `getCurrentTime()` is not copied verbatim every frame (WAV media clocks update in steps → visible jitter).

```
each frame:
  raw = player.getCurrentTime()

  if audio is not playing yet (MP3 buffering):
    currentTimeMs = raw          // do not run ahead of sound

  if raw != previous sample:
    drift = raw - currentTimeMs
    if |drift| > 150 ms → currentTimeMs = raw      (hard sync)
    else → currentTimeMs += frameDt + drift × 0.15

  if raw is unchanged between samples:
    currentTimeMs += frameDt     // wall-clock interpolation
```

Without audio: `currentTimeMs = nanoTime - loopStartNanos`.

**Hit detection** uses the same `currentTimeMs` as the renderer — the player hits what they see.

Song volume: `MediaPlayer.setVolume()` ← `GameSettings.songMusicVolumeScale()` (0.0–1.0, set at playback start and in `onReady`).

### Start countdown

Before the first start and when resuming from pause (if enabled in settings):

- clock and audio are **frozen**;
- keyboard input is ignored;
- overlay: 3→2→1→GO!;
- countdown SFX (`COUNTDOWN_TICK`, `COUNTDOWN_GO`) — independent of the gameplay hit-sound toggle;
- on completion: `player.play()` + clock alignment.

### Audio resilience (MP3 / JavaFX)

`MediaPlayer` for files in `songs/` can become unstable after long sessions (stuck clock, `HALTED` status). `GameScreen` applies:

| Mechanism | Description |
|-----------|-------------|
| `playbackToken` | Invalidates delayed callbacks after `dispose()` or player reload |
| `maintainAudioPlayback()` | Each frame: detects stall (no `getCurrentTime()` progress for ~2 s) or `HALTED` |
| `recreateAudioAtCurrentTime()` | New `MediaPlayer` from current `currentTimeMs` instead of repeated `seek`/`play` |
| Recovery cooldown | Min. 4 s between reloads — avoids loops |
| `disposePlayer()` | Before exit to menu / stop — clears handlers, stops previous track |

When leaving pause (**Quit to menu**), do not set `finished=true` before swapping scenes — otherwise the render loop stops drawing and a black screen appears until `launchMenu()`.

---

## 6. Hit detection and scoring

### Hit windows

| \|dt\| | Judgment | Base points |
|--------|----------|-------------|
| ≤ 50 ms | PERFECT | 300 |
| ≤ 100 ms | GREAT | 150 |
| > 100 ms or no press after passing hit-line | MISS | 0, combo reset |

### Algorithm (`tryHitOnLane`)

When a lane key is pressed:

1. Scan unprocessed notes on that lane (list sorted by time).
2. Pick the **closest in time** within ±100 ms.
3. `HitJudgment.classify(absDt)` → `ScoreState.register()`.

Notes that pass the hit-line + window without a press become MISS in `markPassedNotesAsMisses()`.

### Combo multiplier

`multiplier = 1 + min(combo / 10, 3)` — maximum ×4 at combo ≥ 30.

### Combo popups and HUD colors

When `popups.combo=true`, `GameScreen` shows messages at thresholds from `ComboMilestones.popupAt()`:

- every **10** hits (10, 20, 30, …);
- additionally at **25**, **50**, **100** (no repeat at the same combo value).

Combo counter, multiplier, and popup colors scale with level (`PersonaPalette.comboColor()` / `multiplierColor()`):

| Combo threshold | Color (summary) |
|-----------------|-----------------|
| 1–9 | yellow (`COMBO`) |
| 10–19 | teal (`TEAL`) |
| 20–29 | blue (`ELECTRIC`) |
| 30–49 | bright cyan (`AQUA_BRIGHT`) |
| 50–99 | magenta (`COMBO_HOT`) |
| 100+ | gold (`COMBO_LEGEND`) |

Multiplier ×2–×4: teal → bright cyan → magenta.

### Reaction time

`GameSettings.noteLookAheadMs()` — how early notes appear on the highway (does not change song tempo):

| Preset | Ms | UI label |
|--------|-----|----------|
| 0 | 2200 | 2.2 s |
| 1 (default) | 1650 | 1.7 s |
| 2 | 1200 | 1.2 s |

Property key: `gameplay.reaction.time` (legacy: `gameplay.note.speed`).

### Rank (S–E)

Weighted accuracy: `accuracy = (perfect + great × 0.6) / totalNotes`.

| Rank | Accuracy threshold | Notes |
|------|-------------------|-------|
| S | ≥ 95% | or full combo at ≥ 90% |
| A | ≥ 88% | |
| B | ≥ 78% | |
| C | ≥ 65% | |
| D | ≥ 50% | |
| E | < 50% | |

---

## 7. Statistics (SQLite)

File: **`stats.db`** (JDBC SQLite, driver `org.xerial:sqlite-jdbc`).

### Tables

**`song_stats`** — aggregate per `song_id`:

- `plays`, `best_score`, `max_combo`, `best_hits`
- `best_perfect`, `best_great`, `best_accuracy`, `best_rank`

**`play_history`** — one row per completed attempt:

- full score, hits, accuracy, rank, `played_at` (epoch ms)

Saved in `GameApp.onSongFinished()` after the animated results screen closes. Read in the menu: stats panel, rank badge, history overlay.

> **`stats.json`** — format from an earlier version (Jackson). Replaced by SQLite; safe to delete, the app no longer reads it.

---

## 8. Audio system (`SoundManager`)

Singleton managing app music and short effects. JavaFX-thread safe (`Platform.runLater`).

### Lobby music

Two tracks from the classpath:

| Track | File | Screen |
|-------|------|--------|
| 1 | `/sound/song_lobby.mp3` | Title screen (`playTitleMusic()`) |
| 2 | `/sound/song_ending.mp3` | Song list (`playMenuMusic()`), results screen |

**Screen switch** (`switchScreenMusic()`):

1. Sets `forcedTrack` to the target screen’s track.
2. If a different track is already playing — **crossfade** ~**2.8 s** (`Timeline` + `KeyValue` on `volumeProperty`).
3. If the same track — skip restart (e.g. results → song list).
4. On entering gameplay, lobby music stops (`enterGameplay()`).

Random rotation was removed — menu music is tied to screens and started via `playTitleMusic()` / `playMenuMusic()`.

### Results music

After `finishIfNotYet()` → `playResultsMusic()`: `song_ending.mp3` looped, volume from the lobby slider.

### Sound effects (`SoundManager.Sfx`)

WAV files in `src/main/resources/sound/` (Kenney UI Audio, CC0):

| Sfx | File | Typical use |
|-----|------|-------------|
| `CLICK_GLASS` | `sfx_click_glass.wav` | Song click in menu, key rebinding |
| `NAV` | `sfx_nav.wav` | Arrow navigation, sliders, settings countdown |
| `CONFIRM` | `sfx_confirm.wav` | Play / Enter / panel open |
| `BACK` | `sfx_back.wav` | Exit, ESC, close panel |
| `PAUSE` / `RESUME` | `sfx_pause.wav` / `sfx_resume.wav` | In-game pause |
| `COUNTDOWN_TICK` / `COUNTDOWN_GO` | tick / go | Pre-start countdown |
| `PERFECT` / `GREAT` / `MISS` / `COMBO` | respective files | Hits (see below) |

### In-game SFX separation

| Method | Respects toggle | Description |
|--------|-----------------|-------------|
| `play(Sfx)` | no (volume: `uiSfxVolumeScale()`) | Menu, pause, countdown, UI |
| `playGameplay(Sfx)` | `audio.gameplay.sfx` | PERFECT, GREAT, MISS, COMBO |

PERFECT, GREAT, MISS, and COMBO use dedicated files `sfx_perfect.wav`, `sfx_great.wav`, etc. (no shared `AudioClip` with `setRate`, to avoid playback collisions). `CLICK_GLASS` is UI-only (menu, settings).

### Volume

| Settings slider | Key | Effect |
|-----------------|-----|--------|
| Lobby music volume | `audio.lobby.volume` | `SoundManager` — lobby, crossfade, results |
| Song volume | `audio.song.volume` | `GameScreen` → `MediaPlayer` for `songs/` tracks |
| UI SFX volume | `audio.ui.sfx.volume` | `SoundManager.play()` — clicks, navigation, menu countdown |

Scale: **0–100% → 0.0–1.0** with no hidden multipliers. Lobby volume changes apply live (`refreshLobbyVolume()`).

---

## 9. User settings

File: **`settings.properties`** in the working directory.

```properties
lane.0=D
lane.1=F
lane.2=J
lane.3=K
countdown.seconds=3
popups.hits=true
popups.combo=true
audio.lobby.volume=100
audio.song.volume=100
audio.ui.sfx.volume=72
audio.gameplay.sfx=true
gameplay.reaction.time=1
gameplay.countdown.resume=true
display.fullscreen.start=false
display.locale=pl
```

| Key | Default | Description |
|-----|---------|-------------|
| `lane.0` … `lane.3` | D, F, J, K | JavaFX `KeyCode` |
| `countdown.seconds` | 3 | 0–5; 0 = no pre-start countdown |
| `popups.hits` | true | PERFECT/GREAT/MISS messages above the highway |
| `popups.combo` | true | Combo / multiplier / combo-break popups |
| `audio.lobby.volume` | 100 | Lobby and results music volume (0–100) |
| `audio.song.volume` | 100 | In-game song volume (0–100) |
| `audio.ui.sfx.volume` | 72 | Menu UI SFX volume (click, navigation) |
| `audio.gameplay.sfx` | true | Hit sounds (PERFECT/GREAT/MISS/combo) |
| `gameplay.reaction.time` | 1 | Reaction-time preset (0–2 → 2200/1650/1200 ms) |
| `gameplay.note.speed` | — | Legacy — mapped to `gameplay.reaction.time` |
| `gameplay.countdown.resume` | true | Countdown when resuming from pause (when countdown > 0) |
| `display.fullscreen.start` | false | Fullscreen immediately after `stage.show()` |
| `display.locale` | `pl` | UI language: `pl`, `en`, `de`, `es`, `fr`, `it` |

Edit in the ⚙ overlay (title screen or song list). Panel is scrollable (`ScrollPane`). Assigning an already-used key **swaps** between lanes. Duplicate keys from manual file edits are fixed on load (`dedupeKeys`). Lobby and UI SFX volume apply immediately; song volume applies on the next track start. Saved when the panel closes (ESC).

Changing language calls `GameSettings.setLocaleTag()` → `I18n.setLocaleTag()` and rebuilds the overlay; open screens refresh their labels via callbacks.

`GameSettings.resetForTests(Path)` — JUnit only (overrides file path, resets singleton).

### Internationalization

- Bundles: `src/main/resources/i18n/messages_{pl,en,de,es,fr,it}.properties` (UTF-8).
- Missing keys: active locale → English → key name.
- In-game UI strings (`game.hud.*`, `game.judgment.*`, `game.results.*`, `game.popup.*`) are translated in each bundle.
- `locale.name.*` keys provide native language labels in the settings picker.

---

## 10. Application flow

```
┌──────────────┐  PLAY             ┌──────────────┐  Play / Generate  ┌──────────────┐
│ TitleScreen  │ ───────────────► │  MenuScreen  │ ────────────────► │  GameScreen  │
│ song_lobby   │                  │ song_ending  │                   │  + song      │
│ SETTINGS     │  ◄── Back ────── │ ⚙ settings   │  ◄── results ─── │  countdown   │
│ QUIT         │      ESC         │ history H    │      ENTER        │  play + pause│
└──────────────┘                  └──────────────┘                   └──────────────┘
       │                                  ▲                                   │
       │ quit                             └──────── ESC (pause exit) ──────────┘
       ▼                                        SoundManager.playMenuMusic()
  shutdownApplication()
```

1. **Title screen** — `TitleScreen`: PLAY → song list, SETTINGS → `SettingsOverlay`, QUIT → shutdown; `playTitleMusic()`.
2. **Song list** — scan `songs/`, song selection, beatmap generation (`Task` in background); `playMenuMusic()`; Back/ESC → title.
3. **Countdown** — frozen scene, then song audio starts (`countdownOnResume` after pause).
4. **Gameplay** — `AnimationTimer` loop: clock → miss check → render; `noteLookAheadMs()` from settings; hit SFX optional.
5. **Pause (ESC)** — pause overlay, audio paused; resume with optional countdown.
6. **Song end** — animated results + `song_ending` → `GameResult` → `StatsStore.record()` → song list.

CLI (`./play.sh track.mp3`) skips title and menu; the window closes after the song. `fullscreenOnStart` applies after `stage.show()` in normal mode.

---

## 11. Data files

| Path | Commit? | Description |
|------|---------|-------------|
| `songs/*.json` | optional | Beatmaps |
| `songs/*.{mp3,wav,aiff,flac}` | no | User audio |
| `stats.db` | no | Runtime statistics |
| `settings.properties` | no | Player preferences |
| `src/main/resources/sound/` | yes | Lobby music + SFX |
| `src/main/resources/i18n/` | yes | UI translation bundles |
| `stats.json` | — | **Deprecated** — delete if present locally |

---

## 12. Tests

```bash
mvn test                    # full suite (currently 100 tests)
mvn test -Dtest=GameSettingsTest,RankTest   # selected classes
```

### Coverage by package

| Test class | Scope |
|------------|-------|
| **beatmap** | |
| `BeatmapLoaderTest` | JSON, unknown fields, note validation, empty `notes`, sorting |
| `BeatmapJsonRoundTripTest` | Serialize ↔ deserialize |
| `BeatmapEngineIntegrationTest` | Click-track → JSON → loader, stereo 48 kHz, `songId` determinism |
| `BeatmapEngineAudioFormatTest` | Cadence WAV/MP3, stereo, round-robin, frequency bands |
| `BeatmapEngineEdgeCaseTest` | Silence, default title/UUID, short track, `minimumInterOnsetIntervalSec` |
| `FrequencyBandsStrategyTest` | Lane ↔ FFT band mapping |
| `BeatmapTestSupport` | Helper: median IOI, greedy onset pairing (not a JUnit test) |
| **game** | |
| `ScoreStateTest` | Points, combo, multiplier, `HitJudgment`, `toResult` |
| `RankTest` | S–E thresholds, full combo, `GameResult.accuracy()` |
| `SongLibraryTest` | `songs/` scan, beatmaps, corrupt JSON |
| `StatsStoreTest` | SQLite aggregates, history, rank fallback |
| `GameSettingsTest` | Clamp, key swap, reaction time, legacy `note.speed`, locale round-trip, `settings.properties` |
| `I18nTest` | Strings per locale, `format()`, fallback, game-term consistency |
| `I18nBundleTest` | Bundle completeness, key parity, English game terms in all locales |
| `ComboMilestonesTest` | Combo popup thresholds (10, 25, 50, 100) |
| `PersonaPaletteTest` | `comboColor()`, `multiplierColor()`, `alpha()`, lane colors |
| `GameLogTest` | Log format, levels, exceptions |

### Synthetic audio in tests

`SyntheticAudio` + `TestAudioSupport` generate WAV in `@TempDir` and optionally encode MP3 (`ffmpeg` / `lame` on PATH). **No audio files in the repository.**

### Comparing WAV vs MP3 (important)

Do not compare notes **by index** (`notes.get(0)` WAV vs `notes.get(0)` MP3) — MP3 introduces global offset and sometimes an extra onset at the start (decoder padding).

Instead, `BeatmapTestSupport` verifies:

1. **Median IOI** (inter-onset interval) ≈ expected click-track rhythm (e.g. 500 ms ± tolerance).
2. **Similar onset count** and **BPM** across formats.
3. **Greedy pairing** — each onset from one file has a match in the other within a time tolerance (e.g. ±180 ms), regardless of index.

This better reflects the requirement that “WAV and MP3 produce similar beatmaps” without flaky first-note comparisons.

---

## 13. Package structure

```
src/main/java/com/openguitar/
├── beatmap/
│   ├── BeatmapEngine.java      # audio → SongContext → JSON
│   ├── BeatmapLoader.java      # JSON → SongContext
│   ├── SongContext.java        # beatmap root
│   ├── Note.java               # timeMs + lane
│   └── Main.java               # CLI
│
└── game/
    ├── GameApp.java            # Application entry
    ├── TitleScreen.java        # title screen
    ├── MenuScreen.java         # song list (JavaFX nodes)
    ├── SettingsOverlay.java    # settings overlay
    ├── GameScreen.java         # gameplay (Canvas)
    ├── ComboMilestones.java    # combo popup thresholds
    ├── SoundManager.java       # per-screen lobby music, SFX, results
    ├── GameLog.java            # diagnostic log format
    ├── GameSettings.java       # settings.properties
    ├── I18n.java               # UI translations
    ├── StatsStore.java         # SQLite
    ├── ScoreState.java         # scoring logic
    ├── HitJudgment.java
    ├── GameResult.java
    ├── Rank.java
    ├── SongLibrary.java
    ├── PersonaMenuTheme.java   # menu CSS
    ├── MenuBackground.java     # menu background Canvas
    ├── FloatingPopup.java
    └── view/                   # visual components (Canvas + FX)
        ├── PersonaFonts.java
        ├── PersonaPalette.java
        ├── PersonaText.java
        ├── CrystalNote.java
        ├── PersonaMenuFx.java
        └── FullscreenScaler.java

src/test/java/com/openguitar/
├── beatmap/
│   ├── SyntheticAudio.java     # WAV generator for tests
│   ├── TestAudioSupport.java   # MP3 encoding (ffmpeg/lame)
│   ├── BeatmapTestSupport.java # rhythm / onset-pairing assertions
│   └── …*Test.java
└── game/
    └── …*Test.java

src/main/resources/
├── fonts/                      # Bebas Neue, Rajdhani (OFL)
├── i18n/                       # messages_pl, en, de, es, fr, it
├── images/menu-logo.png
├── logging.properties          # com.openguitar.* log levels
└── sound/
    ├── song_lobby.mp3          # title screen
    ├── song_ending.mp3         # song list + results screen
    ├── sfx_*.wav               # Kenney UI Audio (CC0)
    └── KENNEY_UI_AUDIO_LICENSE.txt
```

---

## Rendering performance (summary)

Optimizations in `GameScreen` (view layer):

- precomputed highway geometry and color cache (no per-frame `Color`/`double[]` allocations);
- draw only notes in the visible time window (binary search by time);
- note placement in scratch fields instead of records;
- smoothed audio clock (see [§5](#5-time-and-audio-synchronization)).

Game logic and DSP are unchanged.

---

## 14. Diagnostic logging

### Configuration

File: `src/main/resources/logging.properties` (loaded by `./play.sh`; can pass to Maven: `-Djava.util.logging.config.file=…`).

| Setting | Default | Effect |
|---------|---------|--------|
| `com.openguitar.game.level` | `INFO` | Scene transitions, lobby audio, song start/stop, pause, MP3 recovery |
| `com.openguitar.game.level=FINE` | (commented) | Audio watchdog ~every 15 s, SFX, clock sync |
| `com.openguitar.beatmap.level` | `INFO` | Audio analysis during beatmap generation |

### Format (`GameLog`)

```
[HH:mm:ss.SSS] LEVEL [component][FX|BG] message
```

| Segment | Meaning |
|---------|---------|
| `app` | `GameApp` — menu, game, shutdown |
| `game` | `GameScreen` — song player, countdown, pause |
| `sound` | `SoundManager` — lobby, crossfade, SFX |
| `FX` / `BG` | JavaFX thread vs other (e.g. JUnit) |

Example INFO events: `launchGame()`, `startPlayback()`, `enterGameplay()`, `finishIfNotYet()`.  
Example WARNING: `tryRecoverAudio(stall)`, `MediaPlayer` error, unhandled exception in `main`.

### JVM warnings (not from the game)

| Message | Source | Dangerous? |
|---------|--------|------------|
| `restricted method … System::load` + `NativeLibLoader` | JavaFX (rendering) | No — normal on Java 21+ |
| `SQLiteJDBCLoader` / `enable-native-access` | `stats.db` driver | No — when saving statistics |
| `PersonaFonts` — missing font | Missing file in `resources/fonts/` | Falls back to system font |

These messages **do not** indicate an audio or menu crash. They are Java requirements around native libraries; a future JVM flag `--enable-native-access=javafx.graphics` may be needed.

### Noise in `mvn test`

Tests intentionally trigger WARNING (e.g. `GameLogTest`, corrupt JSON in `SongLibraryTest`) — expected behavior, not a production regression.
