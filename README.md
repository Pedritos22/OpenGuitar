# OpenGuitar — moduł beatmapy

Klon „Guitar Hero” na desktop (Java 21 + Maven). Repozytorium zawiera obecnie
**tylko warstwę parsera/generatora beatmapy** z plików audio (WAV/MP3/AIFF).

## Architektura modułu

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
 SongContext ──Jackson──► beatmap.json
                              │
                              ▼
                       BeatmapLoader ──► SongContext (gotowe dla silnika gry)
```

## Format pliku JSON beatmapy

```json
{
  "songId": "demo",
  "title": "Click Track Demo",
  "bpm": 118,
  "audioPath": "demo-click-track.wav",
  "notes": [
    { "timeMs": 534, "lane": 2 },
    { "timeMs": 1045, "lane": 1 },
    { "timeMs": 1533, "lane": 2 }
  ]
}
```

JSON jest mapowany 1:1 na `SongContext`. `lane ∈ {0,1,2,3}` to czterech ścieżek gry.
`BeatmapLoader` automatycznie sortuje listę nut po `timeMs` (na wypadek ręcznej edycji).

## Jak to sprawdzić

### 1. Testy automatyczne (8 testów, ~2s)

```bash
mvn test
```

Pokrywają:
- round-trip JSON (3 testy: serializacja, walidacja `lane`, sortowanie)
- pełen pipeline na syntetycznym click-tracku (3 testy: detekcja onsetów, BPM, determinizm)
- strategię `FREQUENCY_BANDS` na sygnale wielopasmowym (2 testy: trafność klasyfikacji, zakres lane)

Zalety syntetycznych testów: nie wymagają trzymania utworów w repo, są deterministyczne,
weryfikują algorytmy z dokładnością do milisekund.

### 2. Tryb demo (bez plików zewnętrznych)

`Main` potrafi sam wygenerować click-track WAV w pamięci, zapisać go na dysk
i przepuścić przez pełen pipeline:

```bash
mvn -q exec:java -Dexec.args="--demo /tmp/openguitar-demo"
```

Wynikowy `demo-beatmap.json` zawiera ~15 nut, BPM ≈ 120, czasy w okolicach
co 500 ms — łatwo gołym okiem zweryfikować poprawność.

### 3. Prawdziwy plik audio

```bash
mvn -q exec:java -Dexec.args="moja-piosenka.mp3 beatmap.json 'Tytuł'"
```

Działają WAV, MP3 (przez mp3spi/JLayer) oraz AIFF (przez wbudowane
javax.sound). Na macOS można szybko przetestować na dźwięku systemowym:

```bash
mvn -q exec:java -Dexec.args="/System/Library/Sounds/Hero.aiff /tmp/hero.json"
```

## Strategie przypisywania ścieżek (lane)

Konfigurowalne przez konstruktor `BeatmapEngine`:

| Strategia               | Opis                                                                      |
|-------------------------|---------------------------------------------------------------------------|
| `ROUND_ROBIN`           | Cykl 0,1,2,3,0,1,2,3,... — najprostsze, dobre do testów                   |
| `SEEDED_PSEUDO_RANDOM`  | **Domyślne**. Pseudo-losowo z ziarnem `songId.hashCode()`, pomija powtórzenia tej samej ścieżki z rzędu. Deterministyczne — ten sam plik daje tę samą beatmapę. |
| `FREQUENCY_BANDS`       | Lane = dominujące pasmo częstotliwości (FFT, 4 pasma): bass→0, low-mid→1, mid→2, high→3. Najbardziej naturalny feel. |

Granice pasm w `FREQUENCY_BANDS`:
- **lane 0 (bass):** 20–150 Hz — kick, basowe nuty
- **lane 1 (low-mid):** 150–500 Hz — snare, niskie wokale
- **lane 2 (mid):** 500–2500 Hz — melodia, gitary, główny wokal
- **lane 3 (high):** 2500–12000 Hz — hi-hat, talerze, wysokie tony

```java
BeatmapEngine engine = new BeatmapEngine(
    0.3,                                    // próg detekcji onsetów
    0.08,                                   // min IOI (s)
    BeatmapEngine.LaneStrategy.FREQUENCY_BANDS
);
```

## Wybór DSP — dlaczego TarsosDSP

Czysta Java (`AudioInputStream` + ręczne liczenie energii okna) wystarcza tylko
do najbardziej trywialnej detekcji „głośnych uderzeń” w nagraniach perkusyjnych
WAV. Dla muzyki melodycznej oraz dla MP3 (wymaga dekodera) potrzebny jest
profesjonalny pipeline DSP.

Wybrałem **TarsosDSP** (single-jar, czysty Java), ponieważ:

- udostępnia gotowy `ComplexOnsetDetector` (metoda *Complex Domain* —
  Bello et al. 2004), znacznie skuteczniejszy niż prosty próg energii;
- czyta WAV i AIFF natywnie, a MP3 przez SPI **mp3spi** (JLayer + tritonus-share),
  co daje jedną ścieżkę kodu dla wszystkich formatów;
- udostępnia FFT z oknem Hamminga, którego używamy do klasyfikacji pasm;
- zero zależności natywnych — łatwo deployować na każdy desktop z JRE.

### Jak działa detekcja onsetów (skrót)

1. Sygnał audio dzielony jest na okna po 1024 próbek (~23 ms @ 44.1 kHz).
2. Dla każdego okna liczony jest STFT (Short-Time Fourier Transform).
3. *Complex Domain*: dla każdego pasma porównuje się **przewidywaną** fazę i
   amplitudę (z poprzedniego okna) z **rzeczywistą**. Duża odległość w
   przestrzeni zespolonej = nagła zmiana spektrum = atak instrumentu.
4. Funkcja detekcji jest progowana (parametr `threshold`, domyślnie 0.3)
   i lokalnie maksymalizowana — peaki to onsety.

### BPM

Estymowane z **mediany inter-onset intervals**, snap do zakresu 60–200 BPM
przez mnożenie/dzielenie przez 2 (oktawy tempa). Heurystyka prosta, ale
odporna na pojedyncze błędne onsety.

### Klasyfikacja pasm (`FrequencyBandClassifier`)

Procesor audio działający równolegle z onset detectorem:

1. Klonuje bufor (FFT jest in-place, nie chcemy zaburzyć innych procesorów).
2. FFT 1024-punktowe z oknem Hamminga (redukcja spectral leakage).
3. Sumuje magnitudy w 4 pasmach Hz, **normalizuje przez liczbę binów**
   (inaczej szerokie pasmo wysokie zawsze by wygrywało).
4. Onset handler odczytuje aktualny stan klasyfikatora w momencie wykrycia
   uderzenia → mapuje na `lane`.

## Struktura pakietu

```
src/main/java/com/openguitar/beatmap/
    Note.java            — rekord Note(timeMs, lane)
    SongContext.java     — rekord SongContext (root JSON-a)
    BeatmapEngine.java   — generator: audio → SongContext → JSON
    BeatmapLoader.java   — loader: JSON → SongContext
    Main.java            — CLI demo (round-trip + tryb --demo)

src/test/java/com/openguitar/beatmap/
    SyntheticAudio.java                  — helper: generuje WAV programowo
    BeatmapJsonRoundTripTest.java        — testy serializacji
    BeatmapEngineIntegrationTest.java    — testy pełnego pipeline'u
    FrequencyBandsStrategyTest.java      — testy klasyfikacji pasm
```
