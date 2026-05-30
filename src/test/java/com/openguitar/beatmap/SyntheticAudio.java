package com.openguitar.beatmap;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generator syntetycznych plików WAV używanych w testach.
 * <p>
 * Pozwala zweryfikować silnik beatmapy bez konieczności trzymania
 * prawdziwych plików audio w repozytorium.
 */
final class SyntheticAudio {

    static final int SAMPLE_RATE = 44100;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;

    private SyntheticAudio() {
    }

    /**
     * Tworzy "click-track": krótkie, sinusoidalne kliknięcia o znanej częstotliwości,
     * powtarzane co {@code beatIntervalMs} milisekund. Każdy klik ma wykładniczą
     * obwiednię (atak + szybkie wygaszenie), co daje wyrazisty onset.
     */
    static Path writeClickTrack(Path output, int beatCount, int beatIntervalMs,
                                double clickFreqHz) throws IOException {
        int totalMs = beatIntervalMs * beatCount + 500;
        int totalSamples = (int) ((long) SAMPLE_RATE * totalMs / 1000);
        short[] pcm = new short[totalSamples];

        int clickDurationMs = 60;
        int clickSamples = SAMPLE_RATE * clickDurationMs / 1000;

        for (int b = 0; b < beatCount; b++) {
            int startSample = (int) ((long) SAMPLE_RATE * b * beatIntervalMs / 1000);
            mixClick(pcm, startSample, clickSamples, clickFreqHz, 0.9);
        }
        return writePcm(output, pcm);
    }

    /**
     * Tworzy ścieżkę z czterema rodzajami uderzeń, każdy w innym paśmie częstotliwości:
     * bass (80 Hz), low-mid (300 Hz), mid (1.2 kHz), high (5 kHz).
     * Kolejność uderzeń określa argument {@code bandSequence} (0..3).
     * Używane do testowania {@link BeatmapEngine.LaneStrategy#FREQUENCY_BANDS}.
     */
    static Path writeMultiBandTrack(Path output, List<Integer> bandSequence,
                                    int beatIntervalMs) throws IOException {
        double[] bandFreqs = {80.0, 300.0, 1200.0, 5000.0};

        int totalMs = beatIntervalMs * bandSequence.size() + 500;
        int totalSamples = (int) ((long) SAMPLE_RATE * totalMs / 1000);
        short[] pcm = new short[totalSamples];

        int clickSamples = SAMPLE_RATE * 80 / 1000; // 80ms - dłuższe niż klik, żeby pasmo było wyraźne

        for (int b = 0; b < bandSequence.size(); b++) {
            int band = bandSequence.get(b);
            double freq = bandFreqs[band];
            int startSample = (int) ((long) SAMPLE_RATE * b * beatIntervalMs / 1000);
            mixClick(pcm, startSample, clickSamples, freq, 0.85);
        }
        return writePcm(output, pcm);
    }

    /**
     * Domieszowuje sinusoidalny "klik" do bufora PCM z wykładniczą obwiednią.
     * Wykładnicze opadanie obwiedni daje wyraźny atak, który łatwo wykrywa
     * zarówno energy threshold jak i Complex Domain Onset Detector.
     */
    private static void mixClick(short[] pcm, int startSample, int clickSamples,
                                 double freqHz, double amplitude) {
        for (int i = 0; i < clickSamples; i++) {
            int idx = startSample + i;
            if (idx >= pcm.length) break;

            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.exp(-3.5 * i / clickSamples);
            double sample = amplitude * envelope * Math.sin(2.0 * Math.PI * freqHz * t);

            int mixed = pcm[idx] + (int) (sample * Short.MAX_VALUE);
            mixed = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
            pcm[idx] = (short) mixed;
        }
    }

    /** Zapisuje bufor 16-bit mono do pliku WAV. */
    private static Path writePcm(Path output, short[] pcm) throws IOException {
        byte[] bytes = new byte[pcm.length * BYTES_PER_SAMPLE];
        for (int i = 0; i < pcm.length; i++) {
            bytes[i * 2]     = (byte) (pcm[i] & 0xFF);          // LSB - little-endian
            bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);   // MSB
        }

        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, BITS_PER_SAMPLE, 1,
                BYTES_PER_SAMPLE, SAMPLE_RATE,
                false // little-endian
        );

        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(bytes), format, pcm.length)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, output.toFile());
        }
        return output;
    }
}
