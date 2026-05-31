# OpenGuitar — dokumentacja techniczna

Ten dokument opisuje architekturę, algorytmy i konwencje projektu. Do szybkiego uruchomienia patrz [README.md](README.md).

---

## Spis treści

1. [Przegląd architektury](#1-przegląd-architektury)
2. [Moduł beatmapy (`com.openguitar.beatmap`)](#2-moduł-beatmapy-comopenguitarbeatmap)
3. [Moduł gry (`com.openguitar.game`)](#3-moduł-gry-comopenguitargame)
4. [Warstwa widoku P3R (`com.openguitar.game.view`)](#4-warstwa-widoku-p3r-comopenguitargameview)
5. [Synchronizacja czasu i audio](#5-synchronizacja-czasu-i-audio)
6. [Hit detection i punktacja](#6-hit-detection-i-punktacja)
7. [Statystyki (SQLite)](#7-statystyki-sqlite)
8. [System dźwiękowy (`SoundManager`)](#8-system-dźwiękowy-soundmanager)
9. [Ustawienia użytkownika](#9-ustawienia-użytkownika)
10. [Przepływ aplikacji](#10-przepływ-aplikacji)
11. [Pliki danych](#11-pliki-danych)
12. [Testy](#12-testy)
13. [Struktura pakietów](#13-struktura-pakietów)

---

## 1. Przegląd architektury

```
┌──────────────────────────────────────────────────────────────────┐
│                          GameApp (JavaFX)                        │
│  Stage ──► MenuScreen ◄──► GameScreen                            │
│              │                    │                              │
│         SongLibrary          MediaPlayer (utwór) + AnimationTimer│
│         StatsStore           ScoreState + HitJudgment            │
│         GameSettings         Canvas (render tylko odczytuje stan)│
│         SoundManager ◄──────► lobby / SFX / muzyka wyników       │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ SongContext (JSON)
┌─────────────────────────────┴────────────────────────────────────┐
│                      com.openguitar.beatmap                      │
│  audio ──► BeatmapEngine (TarsosDSP) ──► beatmap.json            │
└──────────────────────────────────────────────────────────────────┘
```

**Zasada separacji:** logika gry (punkty, combo, trafienia, DSP) jest oddzielona od warstwy widoku. `GameScreen` **czyta** stan z `ScoreState` i `MediaPlayer`, ale nie modyfikuje reguł punktacji. Zmiany wizualne (P3R, countdown, HUD) nie wpływają na poprawność hit detection.

**Dwa kanały audio:**

| Kanał | Klasa | Źródło | Kiedy |
|-------|-------|--------|-------|
| Muzyka aplikacji | `SoundManager` | `src/main/resources/sound/` | Menu, wyniki, SFX |
| Muzyka utworu | `GameScreen` → `MediaPlayer` | pliki w `songs/` | Rozgrywka |

Przy wejściu w utwór `SoundManager.enterGameplay()` zatrzymuje muzykę lobby; po powrocie do menu `startLobbyMusic()` wznawia rotację.

---

## 2. Moduł beatmapy (`com.openguitar.beatmap`)

### Pipeline

```
audio (.wav / .mp3 / .aiff)
        │
        ▼
 BeatmapEngine ── TarsosDSP ComplexOnsetDetector ──► [onsetTimesMs]
        │                                              │
        │ (opcj.) FrequencyBandClassifier ──► [dominantBand per onset]
        │                                              │
        │            estymacja BPM (mediana IOI)       │
        │                                              ▼
        │                            lane assignment (3 strategie)
        ▼
 SongContext ── Jackson ──► beatmap.json
                              │
                              ▼
                       BeatmapLoader ──► SongContext
```

### Format JSON beatmapy

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

- `lane ∈ {0, 1, 2, 3}` — cztery ścieżki gry.
- `audioPath` — względem katalogu pliku JSON (loader rozwija do ścieżki absolutnej).
- `BeatmapLoader` sortuje nuty po `timeMs` (odporność na ręczną edycję).

### Strategie przypisywania ścieżek

| Strategia | Opis |
|-----------|------|
| `ROUND_ROBIN` | Cykl 0→1→2→3→0… — proste, deterministyczne |
| `SEEDED_PSEUDO_RANDOM` | **Domyślna.** Pseudo-los z ziarnem `songId.hashCode()`, bez dwóch tych samych lane z rzędu |
| `FREQUENCY_BANDS` | Lane z dominującego pasma FFT: bass→0, low-mid→1, mid→2, high→3 |

Granice pasm (`FREQUENCY_BANDS`):

| Lane | Zakres Hz | Typowe źródło |
|------|-----------|---------------|
| 0 | 20–150 | kick, bas |
| 1 | 150–500 | snare, niski wokal |
| 2 | 500–2500 | melodia, gitary |
| 3 | 2500–12000 | hi-hat, talerze |

### DSP — TarsosDSP

- **Onset detection:** Complex Domain (Bello et al. 2004) — okna 1024 próbek, próg domyślnie 0.3.
- **BPM:** mediana inter-onset intervals, snap 60–200 BPM (oktawy tempa przez ×2/÷2).
- **Formaty:** WAV/AIFF natywnie; MP3 przez mp3spi (JLayer + tritonus-share).

### CLI parsera (bez UI)

```bash
mvn -q exec:java -Dexec.args="utwor.mp3 beatmap.json 'Tytuł'"
mvn -q exec:java -Dexec.args="--demo /tmp/openguitar-demo"
```

---

## 3. Moduł gry (`com.openguitar.game`)

### Wybór JavaFX

- `MediaPlayer` — odtwarzanie MP3/WAV z raportowaniem pozycji w ms.
- `AnimationTimer` — pętla ~60 Hz z `nanoTime`.
- `Canvas` + `GraphicsContext` — render 2D bez alokacji obiektów Scene Graph w pętli gry.
- `AudioClip` — krótkie SFX (menu, trafienia, odliczanie).

### Klasy rdzeniowe (logika)

| Klasa | Rola |
|-------|------|
| `HitJudgment` | PERFECT / GREAT / MISS + okna czasowe (±50 / ±100 ms) |
| `ScoreState` | Score, combo, multiplier, liczniki perfect/great — czysta logika |
| `GameResult` | Niemożliwy do zmiany snapshot po utworze (accuracy, rank) |
| `Rank` | S–E z progów celności i kolorów P3R |

### Klasy aplikacji (orkiestracja + UI)

| Klasa | Rola |
|-------|------|
| `GameApp` | `Application`, przełączanie scen, fullscreen F11, CLI args, cykl życia `SoundManager` |
| `MenuScreen` | Lista utworów, generacja beatmapy w tle, statystyki, historia, ustawienia (scroll) |
| `GameScreen` | Canvas, input, audio sync, pauza, countdown, ekran wyników |
| `SoundManager` | Muzyka lobby/wyników, SFX UI i rozgrywki (singleton) |
| `StatsStore` | SQLite: agregaty + historia podejść |
| `GameSettings` | `settings.properties`: klawisze, audio, countdown, popupy |
| `SongLibrary` | Skan `songs/`, dopasowanie audio ↔ JSON |

---

## 4. Warstwa widoku P3R (`com.openguitar.game.view`)

Pakiet **wyłącznie wizualny** — nie importuje logiki punktacji poza kolorami/paletą.

| Klasa | Funkcja |
|-------|---------|
| `PersonaFonts` | Jednorazowe ładowanie Bebas Neue + Rajdhani, cache instancji |
| `PersonaPalette` | Stałe kolory (deep blue, aqua, lane tints) |
| `PersonaText` | Tekst na Canvas: pochył (shear), obrys, cień — bez kosztownych `Effect` |
| `CrystalNote` | Geometria kryształowych nut i receptorów |
| `PersonaMenuFx` | Shear + hover slide na węzłach JavaFX (klikalność zachowana) |
| `FullscreenScaler` | Skalowanie letterbox do rozmiaru okna |

Menu używa JavaFX Scene Graph (`MenuScreen`, `PersonaMenuTheme`). Rozgrywka używa Canvas (`GameScreen`) dla wydajności. Panel ustawień ma przewijaną treść (`ScrollPane`).

---

## 5. Synchronizacja czasu i audio

Pozycja nuty na autostradzie 3D to funkcja **`currentTimeMs`** — wygładzonego zegara gry zsynchronizowanego z audio.

### Model hybrydowy (`GameScreen.advanceClock`)

Audio pozostaje **źródłem prawdy**, ale `getCurrentTime()` nie jest kopiowany wprost co klatkę (dla WAV zegar mediów aktualizuje się skokowo → wizualny jutter).

```
co klatkę:
  raw = player.getCurrentTime()

  jeśli audio jeszcze nie gra (buforowanie MP3):
    currentTimeMs = raw          // nie wyprzedzamy dźwięku

  jeśli raw != poprzednia próbka:
    drift = raw - currentTimeMs
    jeśli |drift| > 150 ms → currentTimeMs = raw      (twardy sync)
    w przeciwnym razie → currentTimeMs += frameDt + drift × 0.15

  jeśli raw stoi między próbkami:
    currentTimeMs += frameDt     // interpolacja zegarem ściennym
```

Bez audio: `currentTimeMs = nanoTime - loopStartNanos`.

**Hit detection** używa tego samego `currentTimeMs` co renderer — gracz trafia w to, co widzi.

Głośność utworu: `MediaPlayer.setVolume()` ← `GameSettings.songMusicVolumeScale()` (0.0–1.0, ustawiane przy starcie odtwarzania i w `onReady`).

### Odliczanie startowe

Przed pierwszym startem i po wznowieniu z pauzy (jeśli włączone w ustawieniach):

- zegar i audio **stoją**;
- wejście klawiatury ignorowane;
- overlay P3R: 3→2→1→GO!;
- SFX odliczania (`COUNTDOWN_TICK`, `COUNTDOWN_GO`) — niezależne od przełącznika dźwięków trafień;
- po zakończeniu: `player.play()` + wyrównanie zegara.

---

## 6. Hit detection i punktacja

### Okna trafień

| \|dt\| | Judgment | Punkty bazowe |
|--------|----------|---------------|
| ≤ 50 ms | PERFECT | 300 |
| ≤ 100 ms | GREAT | 150 |
| > 100 ms lub brak naciśnięcia po minięciu hit-line | MISS | 0, reset combo |

### Algorytm (`tryHitOnLane`)

Dla naciśniętego klawisza na ścieżce `lane`:

1. Przeszukaj nieprzetworzone nuty na tej ścieżce (lista posortowana po czasie).
2. Wybierz **najbliższą czasowo** w oknie ±100 ms.
3. `HitJudgment.classify(absDt)` → `ScoreState.register()`.

Nuty, które minęły hit-line + okno bez naciśnięcia, stają się MISS w `markPassedNotesAsMisses()`.

### Mnożnik combo

`multiplier = 1 + min(combo / 10, 3)` — maksymalnie ×4 przy combo ≥ 30.

### Ranga (S–E)

Celność ważona: `accuracy = (perfect + great × 0.6) / totalNotes`.

| Ranga | Próg celności | Uwagi |
|-------|---------------|-------|
| S | ≥ 95% | lub full combo przy ≥ 90% |
| A | ≥ 88% | |
| B | ≥ 78% | |
| C | ≥ 65% | |
| D | ≥ 50% | |
| E | < 50% | |

---

## 7. Statystyki (SQLite)

Plik: **`stats.db`** (JDBC SQLite, sterownik `org.xerial:sqlite-jdbc`).

### Tabele

**`song_stats`** — agregat per `song_id`:

- `plays`, `best_score`, `max_combo`, `best_hits`
- `best_perfect`, `best_great`, `best_accuracy`, `best_rank`

**`play_history`** — jeden wiersz na ukończone podejście:

- pełny wynik, trafienia, celność, ranga, `played_at` (epoch ms)

Zapis następuje w `GameApp.onSongFinished()` po zamknięciu animowanego ekranu wyników. Odczyt w menu: panel statystyk, kafelek rangi, nakładka historii.

> **`stats.json`** — format z wcześniejszej wersji (Jackson). Zastąpiony przez SQLite; plik można usunąć, aplikacja go nie czyta.

---

## 8. System dźwiękowy (`SoundManager`)

Singleton zarządzający muzyką aplikacji i krótkimi efektami. Bezpieczny względem wątku JavaFX (`Platform.runLater`).

### Muzyka lobby

Rotacja między dwoma utworami z classpath:

- `/sound/song_lobby.mp3`
- `/sound/song_ending.mp3`

**Algorytm:**

1. Losowy wybór utworu (bez dwóch tych samych z rzędu).
2. ~**2.8 s** przed końcem bieżącego utworu startuje **crossfade** (`Timeline` + `KeyValue` na `volumeProperty`).
3. Stary utwór wygasa 0→target, nowy wchodzi 0→target — bez twardego cięcia.
4. Po wejściu w grę rotacja zatrzymana (`enterGameplay()`).

### Muzyka wyników

Po `finishIfNotYet()` → `playResultsMusic()`: `song_ending.mp3` w pętli, głośność ze suwaka lobby.

### Efekty dźwiękowe (`SoundManager.Sfx`)

Pliki WAV w `src/main/resources/sound/` (Kenney UI Audio, CC0):

| Sfx | Plik | Typowe użycie |
|-----|------|---------------|
| `CLICK_GLASS` | `sfx_click_glass.wav` | Klik w utwór w menu, rebinding klawisza |
| `NAV` | `sfx_nav.wav` | Nawigacja strzałkami, suwaki, odliczanie w ustawieniach |
| `CONFIRM` | `sfx_confirm.wav` | Graj / Enter / otwarcie panelu |
| `BACK` | `sfx_back.wav` | Wyjście, ESC, zamknięcie panelu |
| `PAUSE` / `RESUME` | `sfx_pause.wav` / `sfx_resume.wav` | Pauza w grze |
| `COUNTDOWN_TICK` / `COUNTDOWN_GO` | tick / go | Odliczanie przed startem |
| `PERFECT` / `GREAT` / `MISS` / `COMBO` | odpowiednie pliki | Trafienia (patrz poniżej) |

### Rozdzielenie SFX w grze

| Metoda | Respektuje `gameplayHitSfx` | Opis |
|--------|----------------------------|------|
| `play(Sfx)` | nie | Menu, pauza, odliczanie, UI |
| `playGameplay(Sfx)` | tak | PERFECT, GREAT, MISS, COMBO |

PERFECT i GREAT używają szklanego `CLICK_GLASS` z modulacją pitch (`setRate`: 1.22 / 1.0). MISS i COMBO — dedykowane próbki.

### Głośność

| Suwak w ustawieniach | Wpływ |
|---------------------|-------|
| Głośność muzyki lobby | `SoundManager` — lobby, crossfade, wyniki |
| Głośność piosenek | `GameScreen` → `MediaPlayer` utworu z `songs/` |

Skala: **0–100% → 0.0–1.0** bez ukrytych mnożników. Zmiana lobby działa na żywo (`refreshLobbyVolume()`).

---

## 9. Ustawienia użytkownika

Plik: **`settings.properties`** w katalogu roboczym.

```properties
lane.0=D
lane.1=F
lane.2=J
lane.3=K
countdown.seconds=3
popups.hits=true
audio.lobby.volume=100
audio.song.volume=100
audio.gameplay.sfx=true
```

| Klucz | Domyślnie | Opis |
|-------|-----------|------|
| `lane.0` … `lane.3` | D, F, J, K | `KeyCode` JavaFX |
| `countdown.seconds` | 3 | 0–5; 0 = brak odliczania |
| `popups.hits` | true | Komunikaty PERFECT/GREAT/MISS nad autostradą |
| `audio.lobby.volume` | 100 | Głośność muzyki menu i wyników (0–100) |
| `audio.song.volume` | 100 | Głośność utworu w rozgrywce (0–100) |
| `audio.gameplay.sfx` | true | Dźwięki trafień (PERFECT/GREAT/MISS/combo) |

Edycja w menu (⚙). Panel przewijany (`ScrollPane`). Przy przypisaniu zajętego klawisza następuje **swap** między ścieżkami. Głośność lobby — suwak z natychmiastowym efektem; głośność piosenek — od następnego startu utworu. Zapis przy zamknięciu panelu (ESC).

---

## 10. Przepływ aplikacji

```
┌──────────────┐  Graj / Generuj   ┌──────────────┐
│  MenuScreen  │ ────────────────► │  GameScreen  │
│  + lobby BGM │                   │  + utwór     │
│  ⚙ ustawienia│  ◄── wyniki ──── │  countdown   │
│  historia H  │      ENTER        │  gra + pauza │
└──────────────┘                   └──────────────┘
        ▲                                   │
        └──────── ESC (wyjście z pauzy) ────┘
              SoundManager.startLobbyMusic()
```

1. **Menu** — skan `songs/`, wybór utworu, opcjonalna generacja beatmapy (`Task` w tle); rotacja muzyki lobby.
2. **Countdown** — zamrożona scena, potem start audio utworu.
3. **Gra** — pętla `AnimationTimer`: clock → miss check → render; SFX trafień opcjonalne.
4. **Pauza (ESC)** — overlay P3R, audio w pause; wznowienie przez countdown.
5. **Koniec utworu** — animowany ekran wyników + `song_ending` → `GameResult` → `StatsStore.record()` → menu.

CLI (`./play.sh utwor.mp3`) pomija menu; okno zamyka się po utworze.

---

## 11. Pliki danych

| Ścieżka | Commitować? | Opis |
|---------|-------------|------|
| `songs/*.json` | opcjonalnie | Beatmapy |
| `songs/*.{mp3,wav,aiff,flac}` | nie | Audio użytkownika |
| `stats.db` | nie | Statystyki runtime |
| `settings.properties` | nie | Preferencje gracza |
| `src/main/resources/sound/` | tak | Muzyka lobby + SFX |
| `stats.json` | — | **Przestarzały** — usuń, jeśli istnieje lokalnie |

---

## 12. Testy

```bash
mvn test
```

| Pakiet | Zakres |
|--------|--------|
| `com.openguitar.beatmap` | JSON round-trip, pipeline click-track, frequency bands, loader |
| `com.openguitar.game` | `ScoreState`, `SongLibrary`, `StatsStore` |

Testy syntetyczne (click-track WAV w pamięci) — deterministyczne, bez plików audio w repo.

---

## 13. Struktura pakietów

```
src/main/java/com/openguitar/
├── beatmap/
│   ├── BeatmapEngine.java      # audio → SongContext → JSON
│   ├── BeatmapLoader.java      # JSON → SongContext
│   ├── SongContext.java        # root beatmapy
│   ├── Note.java               # timeMs + lane
│   └── Main.java               # CLI
│
└── game/
    ├── GameApp.java            # Application entry
    ├── GameScreen.java         # rozgrywka (Canvas)
    ├── MenuScreen.java         # menu (JavaFX nodes)
    ├── SoundManager.java       # muzyka lobby, SFX, wyniki
    ├── GameSettings.java       # settings.properties
    ├── StatsStore.java         # SQLite
    ├── ScoreState.java         # logika punktacji
    ├── HitJudgment.java
    ├── GameResult.java
    ├── Rank.java
    ├── SongLibrary.java
    ├── PersonaMenuTheme.java   # CSS menu
    ├── MenuBackground.java     # Canvas tła menu
    ├── FloatingPopup.java
    └── view/                   # komponenty P3R (Canvas + FX)
        ├── PersonaFonts.java
        ├── PersonaPalette.java
        ├── PersonaText.java
        ├── CrystalNote.java
        ├── PersonaMenuFx.java
        └── FullscreenScaler.java

src/main/resources/
├── fonts/                      # Bebas Neue, Rajdhani (OFL)
├── images/menu-logo.png
└── sound/
    ├── song_lobby.mp3          # muzyka menu
    ├── song_ending.mp3         # lobby (rotacja) + ekran wyników
    ├── sfx_*.wav               # Kenney UI Audio (CC0)
    └── KENNEY_UI_AUDIO_LICENSE.txt
```

---

## Wydajność renderowania (skrót)

Optymalizacje w `GameScreen` (warstwa widoku):

- precompute geometrii torów i cache kolorów (zero alokacji `Color`/`double[]` co klatkę);
- rysowanie tylko widocznego okna nut (binary search po czasie);
- placement nut w polach scratch zamiast rekordów;
- gładki zegar audio (patrz [§5](#5-synchronizacja-czasu-i-audio)).

Logika gry i DSP pozostają nietknięte.
