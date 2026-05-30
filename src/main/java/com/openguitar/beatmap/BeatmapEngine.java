package com.openguitar.beatmap;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.onsets.ComplexOnsetDetector;
import be.tarsos.dsp.onsets.OnsetHandler;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.HammingWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Generator beatmapy z pliku audio (WAV lub MP3).
 *
 * <h2>Pipeline</h2>
 * <ol>
 *     <li>Plik audio -&gt; {@link AudioInputStream} (WAV natywnie, MP3 przez SPI mp3spi/JLayer).</li>
 *     <li>Konwersja do PCM signed 44.1kHz / 16-bit / mono (TarsosDSP wymaga PCM).</li>
 *     <li>Detekcja onsetów (Complex Domain Onset Detection).</li>
 *     <li>Opcjonalnie: klasyfikacja pasma częstotliwości (FFT + 4 pasma) per onset.</li>
 *     <li>Estymacja BPM z mediany inter-onset intervals (IOI).</li>
 *     <li>Mapowanie onsetów na nuty {@link Note} z deterministyczną alokacją ścieżek.</li>
 *     <li>Serializacja do JSON.</li>
 * </ol>
 *
 * <h2>Jak działa wykrywanie uderzeń</h2>
 * Najprostsza metoda - <b>energy threshold</b> - liczy energię okna sygnału
 * (sumę kwadratów próbek) i porównuje ją z lokalną średnią; przekroczenie progu
 * traktowane jest jako "uderzenie". To podejście działa względnie dobrze dla
 * perkusji, ale dla muzyki melodycznej generuje duże ilości fałszywych alarmów.
 * <p>
 * TarsosDSP używa metody <b>Complex Domain</b> (Bello et al. 2004): liczy STFT
 * (Short-Time Fourier Transform), a następnie dla każdego pasma porównuje
 * przewidywaną fazę i amplitudę z rzeczywistą - duża "odległość" w przestrzeni
 * zespolonej oznacza nagłą zmianę spektrum (atak instrumentu). To znacznie
 * trafniej wykrywa początki dźwięków niż prosty próg energii.
 * <p>
 * Detektor używa progu (threshold) typowo w zakresie 0.1-0.6: niższy = więcej
 * onsetów (czulszy), wyższy = tylko najbardziej wyraziste uderzenia.
 */
public final class BeatmapEngine {

    private static final Logger LOG = Logger.getLogger(BeatmapEngine.class.getName());

    /** Docelowy format PCM dla TarsosDSP - mono ułatwia analizę spektrum. */
    private static final float TARGET_SAMPLE_RATE = 44100f;
    private static final int TARGET_CHANNELS = 1;
    private static final int TARGET_SAMPLE_SIZE_BITS = 16;

    /** Rozmiar bufora STFT - 1024 próbki @ 44.1kHz daje rozdzielczość ~23ms. */
    private static final int BUFFER_SIZE = 1024;
    private static final int BUFFER_OVERLAP = 0;

    /** Próg detekcji onsetów (0..1). Niższy = więcej nut, wyższy = tylko wyraźne. */
    private final double onsetThreshold;

    /** Minimalny odstęp między dwoma onsetami w sekundach (filtruje "flam"). */
    private final double minimumInterOnsetIntervalSec;

    /** Strategia przypisywania ścieżek (lane). */
    private final LaneStrategy laneStrategy;

    private final ObjectMapper jsonMapper;

    public BeatmapEngine() {
        this(0.3, 0.08, LaneStrategy.SEEDED_PSEUDO_RANDOM);
    }

    public BeatmapEngine(double onsetThreshold,
                         double minimumInterOnsetIntervalSec,
                         LaneStrategy laneStrategy) {
        this.onsetThreshold = onsetThreshold;
        this.minimumInterOnsetIntervalSec = minimumInterOnsetIntervalSec;
        this.laneStrategy = laneStrategy;
        this.jsonMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ----------------------------- API publiczne -----------------------------

    /**
     * Generuje obiekt {@link SongContext} z pliku audio (bez zapisu na dysk).
     *
     * @param audioFile ścieżka do .wav lub .mp3
     * @param songId    identyfikator (gdy {@code null}, generowany UUID)
     * @param title     tytuł (gdy {@code null}, używana nazwa pliku bez rozszerzenia)
     */
    public SongContext generateBeatmap(Path audioFile, String songId, String title)
            throws IOException, UnsupportedAudioFileException {

        if (!Files.isRegularFile(audioFile)) {
            throw new IOException("Plik audio nie istnieje: " + audioFile);
        }

        String resolvedSongId = (songId != null && !songId.isBlank())
                ? songId
                : UUID.randomUUID().toString();

        String resolvedTitle = (title != null && !title.isBlank())
                ? title
                : stripExtension(audioFile.getFileName().toString());

        LOG.info(() -> "Analiza audio: " + audioFile);
        List<OnsetEvent> events = detectOnsets(audioFile);
        LOG.info(() -> "Wykryto " + events.size() + " onsetów.");

        int bpm = estimateBpm(events);
        LOG.info(() -> "Szacowane BPM: " + bpm);

        List<Note> notes = assignLanes(events, resolvedSongId);

        return new SongContext(
                resolvedSongId,
                resolvedTitle,
                bpm,
                audioFile.getFileName().toString(),
                notes
        );
    }

    /**
     * Generuje beatmapę i zapisuje ją do pliku JSON.
     *
     * @return zwraca utworzony {@link SongContext}, aby pozwolić na łańcuchowe użycie
     */
    public SongContext generateAndSave(Path audioFile, Path outputJson,
                                       String songId, String title)
            throws IOException, UnsupportedAudioFileException {
        SongContext ctx = generateBeatmap(audioFile, songId, title);
        save(ctx, outputJson);
        return ctx;
    }

    /** Zapisuje istniejący {@link SongContext} do pliku JSON. */
    public void save(SongContext ctx, Path outputJson) throws IOException {
        if (outputJson.getParent() != null) {
            Files.createDirectories(outputJson.getParent());
        }
        jsonMapper.writeValue(outputJson.toFile(), ctx);
        LOG.info(() -> "Zapisano beatmapę: " + outputJson);
    }

    // ----------------------------- Onset detection ---------------------------

    /**
     * Wewnętrzna struktura: czas onsetu + dominujące pasmo (lub -1 gdy nieliczone).
     */
    private record OnsetEvent(int timeMs, int dominantBand) {}

    /**
     * Uruchamia TarsosDSP {@link ComplexOnsetDetector} na pliku audio i (opcjonalnie)
     * klasyfikator pasma częstotliwości, jeśli aktywna jest strategia FREQUENCY_BANDS.
     */
    private List<OnsetEvent> detectOnsets(Path audioFile)
            throws IOException, UnsupportedAudioFileException {

        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(audioFile.toFile());
             AudioInputStream pcmStream = toTargetPcm(rawStream)) {

            JVMAudioInputStream tarsosStream = new JVMAudioInputStream(pcmStream);
            AudioDispatcher dispatcher = new AudioDispatcher(tarsosStream, BUFFER_SIZE, BUFFER_OVERLAP);

            // Klasyfikator pasm dodajemy *przed* detektorem, żeby przy wywołaniu
            // OnsetHandler#handleOnset() miał już zaktualizowane energie pasm
            // dla ostatnio przetworzonej ramki audio.
            FrequencyBandClassifier classifier = (laneStrategy == LaneStrategy.FREQUENCY_BANDS)
                    ? new FrequencyBandClassifier(BUFFER_SIZE, TARGET_SAMPLE_RATE)
                    : null;
            if (classifier != null) {
                dispatcher.addAudioProcessor(classifier);
            }

            ComplexOnsetDetector detector = new ComplexOnsetDetector(
                    BUFFER_SIZE,
                    onsetThreshold,
                    minimumInterOnsetIntervalSec
            );

            List<OnsetEvent> events = new ArrayList<>();
            OnsetHandler handler = (time, salience) -> {
                int ms = (int) Math.round(time * 1000.0);
                int band = (classifier != null) ? classifier.getDominantBand() : -1;
                events.add(new OnsetEvent(ms, band));
            };
            detector.setHandler(handler);

            dispatcher.addAudioProcessor(detector);
            dispatcher.run(); // blokujące - przetwarza cały plik

            events.sort(Comparator.comparingInt(OnsetEvent::timeMs));
            return events;
        }
    }

    /**
     * Konwertuje dowolny strumień audio (np. MP3 dekodowany przez mp3spi) do
     * znormalizowanego formatu PCM, którego oczekuje TarsosDSP.
     */
    private static AudioInputStream toTargetPcm(AudioInputStream in) {
        AudioFormat sourceFormat = in.getFormat();

        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                TARGET_SAMPLE_RATE,
                TARGET_SAMPLE_SIZE_BITS,
                TARGET_CHANNELS,
                TARGET_CHANNELS * (TARGET_SAMPLE_SIZE_BITS / 8), // frame size
                TARGET_SAMPLE_RATE,
                false // little-endian (standard dla WAV/PCM)
        );

        if (sourceFormat.matches(targetFormat)) {
            return in;
        }
        return AudioSystem.getAudioInputStream(targetFormat, in);
    }

    // ----------------------------- Frequency-band classifier -----------------

    /**
     * Procesor audio który dla każdej ramki liczy FFT i sumuje magnitudy
     * w 4 pasmach częstotliwości:
     * <pre>
     *   pasmo 0 (bass)     :   20 -  150 Hz   - kick, basowe nuty
     *   pasmo 1 (low-mid)  :  150 -  500 Hz   - snare, niskie wokale
     *   pasmo 2 (mid)      :  500 - 2500 Hz   - melodia, gitary, wokal
     *   pasmo 3 (high)     : 2500 -12000 Hz   - hi-hat, talerze, wysokie tony
     * </pre>
     * Dominujące pasmo (z największą energią) jest mapowane na numer ścieżki 0..3.
     * <p>
     * Procesor jest <i>side-effect-free</i> względem bufora audio:
     * klonuje go przed FFT (FFT jest in-place), aby kolejne procesory
     * w pipeline (np. ComplexOnsetDetector) widziały oryginalne dane.
     */
    static final class FrequencyBandClassifier implements AudioProcessor {

        private static final double[][] BAND_HZ = {
                {20.0,    150.0},   // 0 - bass
                {150.0,   500.0},   // 1 - low-mid
                {500.0,  2500.0},   // 2 - mid
                {2500.0, 12000.0}   // 3 - high
        };

        private final FFT fft;
        private final float[] amplitudes;
        private final int[] startBin;
        private final int[] endBin;
        private final double[] bandEnergies = new double[4];

        FrequencyBandClassifier(int bufferSize, float sampleRate) {
            // HammingWindow zmniejsza spectral leakage - typowy wybór dla analizy muzyki.
            this.fft = new FFT(bufferSize, new HammingWindow());
            this.amplitudes = new float[bufferSize / 2];

            double binWidth = sampleRate / bufferSize; // ~43 Hz dla 1024 @ 44.1kHz
            int maxBin = bufferSize / 2 - 1;
            startBin = new int[4];
            endBin = new int[4];
            for (int b = 0; b < 4; b++) {
                startBin[b] = Math.max(0, (int) Math.floor(BAND_HZ[b][0] / binWidth));
                endBin[b]   = Math.min(maxBin, (int) Math.ceil(BAND_HZ[b][1] / binWidth));
            }
        }

        @Override
        public boolean process(AudioEvent event) {
            // FFT jest in-place - klonujemy bufor, żeby nie zaburzyć innych procesorów.
            float[] buffer = event.getFloatBuffer().clone();
            fft.forwardTransform(buffer);
            fft.modulus(buffer, amplitudes);

            for (int b = 0; b < 4; b++) {
                double sum = 0.0;
                for (int i = startBin[b]; i <= endBin[b]; i++) {
                    sum += amplitudes[i];
                }
                // Normalizacja przez liczbę binów (różne pasma mają różną szerokość) -
                // bez tego pasma wysokie miałyby zawsze przewagę dzięki większej liczbie binów.
                int width = endBin[b] - startBin[b] + 1;
                bandEnergies[b] = sum / Math.max(1, width);
            }
            return true;
        }

        @Override
        public void processingFinished() { /* nic do zrobienia */ }

        /** Zwraca indeks pasma (0..3) z największą energią dla ostatnio przetworzonej ramki. */
        int getDominantBand() {
            int best = 0;
            double max = bandEnergies[0];
            for (int i = 1; i < 4; i++) {
                if (bandEnergies[i] > max) {
                    max = bandEnergies[i];
                    best = i;
                }
            }
            return best;
        }
    }

    // ----------------------------- BPM estimation ----------------------------

    /**
     * Estymuje BPM utworu na podstawie mediany inter-onset intervals (IOI).
     * <p>
     * Mediana jest odporna na pojedyncze "rwane" odstępy - nawet jeśli detektor
     * raz na jakiś czas pominie nutę, połowa próbek wciąż jest poprawna.
     * Następnie wynik "snap'ujemy" do typowego zakresu muzycznego [60, 200] BPM
     * przez mnożenie/dzielenie przez 2 (oktawy tempa - 60 BPM jest matematycznie
     * tym samym co 120 BPM z ósemkami).
     */
    private int estimateBpm(List<OnsetEvent> events) {
        if (events.size() < 4) {
            return 120;
        }
        int[] iois = new int[events.size() - 1];
        for (int i = 1; i < events.size(); i++) {
            iois[i - 1] = events.get(i).timeMs() - events.get(i - 1).timeMs();
        }
        int[] sorted = iois.clone();
        Arrays.sort(sorted);
        int medianIoi = sorted[sorted.length / 2];
        if (medianIoi <= 0) {
            return 120;
        }

        double bpm = 60_000.0 / medianIoi;
        while (bpm < 60.0)  bpm *= 2.0;
        while (bpm > 200.0) bpm /= 2.0;
        return (int) Math.round(bpm);
    }

    // ----------------------------- Lane assignment ---------------------------

    /**
     * Strategia mapowania onsetów na ścieżki gry (0..3).
     */
    public enum LaneStrategy {
        /** Cykl 0,1,2,3,0,1,2,3,... - najprostsze, dobre do testów. */
        ROUND_ROBIN,
        /** Pseudo-losowo, ziarno z {@code songId} - reproduktywne między uruchomieniami. */
        SEEDED_PSEUDO_RANDOM,
        /**
         * Lane = dominujące pasmo częstotliwości w momencie uderzenia.
         * Bass (lane 0) / low-mid (1) / mid (2) / high (3).
         * Daje najbardziej naturalne wrażenie "zgodności z muzyką".
         */
        FREQUENCY_BANDS
    }

    private List<Note> assignLanes(List<OnsetEvent> events, String songId) {
        List<Note> notes = new ArrayList<>(events.size());

        switch (laneStrategy) {
            case ROUND_ROBIN -> {
                for (int i = 0; i < events.size(); i++) {
                    notes.add(new Note(events.get(i).timeMs(), i % 4));
                }
            }
            case SEEDED_PSEUDO_RANDOM -> {
                // Seed pochodzi z songId, dzięki czemu wielokrotne wywołanie dla tego samego
                // utworu zwraca tę samą beatmapę (deterministyczność = łatwiejsze testowanie i fair play).
                Random rng = new Random(songId.hashCode());
                int previousLane = -1;
                for (OnsetEvent e : events) {
                    int lane = rng.nextInt(4);
                    // Heurystyka playability: unikamy dwóch nut z rzędu na tej samej ścieżce.
                    if (lane == previousLane) {
                        lane = (lane + 1 + rng.nextInt(3)) % 4;
                    }
                    notes.add(new Note(e.timeMs(), lane));
                    previousLane = lane;
                }
            }
            case FREQUENCY_BANDS -> {
                for (OnsetEvent e : events) {
                    int lane = (e.dominantBand() >= 0 && e.dominantBand() <= 3)
                            ? e.dominantBand()
                            : 0;
                    notes.add(new Note(e.timeMs(), lane));
                }
            }
        }
        return notes;
    }

    // ----------------------------- helpers ----------------------------------

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }
}
