package com.openguitar.beatmap;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Prosty CLI do generowania beatmapy.
 *
 * <h3>Tryb 1 - prawdziwy plik audio</h3>
 * <pre>
 *   mvn -q exec:java -Dexec.args="ścieżka/do/song.mp3 beatmap.json 'Tytuł'"
 * </pre>
 *
 * <h3>Tryb 2 - demo bez plików zewnętrznych</h3>
 * Generuje syntetyczny click-track WAV (120 BPM, 16 uderzeń)
 * i przepuszcza go przez pełny pipeline:
 * <pre>
 *   mvn -q exec:java -Dexec.args="--demo"
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws IOException, UnsupportedAudioFileException {
        if (args.length == 0) {
            System.err.println("""
                    Użycie:
                      <audioPath> [outputJson] [title]      - generuj beatmapę dla pliku audio
                      --demo [outputDir]                    - syntetyczny click-track + round-trip JSON
                    """);
            System.exit(1);
        }

        if ("--demo".equals(args[0])) {
            runDemo(args.length > 1 ? Paths.get(args[1]) : Paths.get("."));
            return;
        }

        Path audio = Paths.get(args[0]);
        Path output = (args.length >= 2) ? Paths.get(args[1]) : Paths.get("beatmap.json");
        String title = (args.length >= 3) ? args[2] : null;

        BeatmapEngine engine = new BeatmapEngine();
        SongContext generated = engine.generateAndSave(audio, output, null, title);
        printResult("Wygenerowano", generated, output);

        SongContext reloaded = new BeatmapLoader().load(output);
        printResult("Wczytano",   reloaded, output);
    }

    /**
     * Demo: generuje WAV w pamięci, zapisuje na dysk, generuje beatmapę,
     * wczytuje z powrotem - wszystko bez potrzeby trzymania utworu w repo.
     */
    private static void runDemo(Path outputDir) throws IOException, UnsupportedAudioFileException {
        Files.createDirectories(outputDir);
        Path wav = outputDir.resolve("demo-click-track.wav");
        Path json = outputDir.resolve("demo-beatmap.json");

        writeClickTrackWav(wav, 16, 500, 1000.0);
        System.out.println("Wygenerowano click-track: " + wav);

        BeatmapEngine engine = new BeatmapEngine();
        SongContext ctx = engine.generateAndSave(wav, json, "demo", "Click Track Demo");
        printResult("Wygenerowano beatmapę", ctx, json);

        SongContext reloaded = new BeatmapLoader().load(json);
        printResult("Wczytano ponownie", reloaded, json);
    }

    /**
     * Wbudowany prosty generator WAV z syntetycznymi kliknięciami (sinus z wykładniczą obwiednią).
     * Identyczna logika jak {@code SyntheticAudio} z testów - kopiowana, żeby nie wciągać
     * zależności testowych do main code.
     */
    private static void writeClickTrackWav(Path output, int beatCount, int beatIntervalMs,
                                           double clickFreqHz) throws IOException {
        int sampleRate = 44100;
        int totalMs = beatIntervalMs * beatCount + 500;
        int totalSamples = (int) ((long) sampleRate * totalMs / 1000);
        short[] pcm = new short[totalSamples];

        int clickSamples = sampleRate * 60 / 1000;

        for (int b = 0; b < beatCount; b++) {
            int startSample = (int) ((long) sampleRate * b * beatIntervalMs / 1000);
            for (int i = 0; i < clickSamples; i++) {
                int idx = startSample + i;
                if (idx >= pcm.length) break;
                double t = (double) i / sampleRate;
                double envelope = Math.exp(-3.5 * i / clickSamples);
                double sample = 0.9 * envelope * Math.sin(2.0 * Math.PI * clickFreqHz * t);
                pcm[idx] = (short) (sample * Short.MAX_VALUE);
            }
        }

        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[i * 2]     = (byte) (pcm[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        AudioFormat fmt = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, 1, 2, sampleRate, false);
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(bytes), fmt, pcm.length)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, output.toFile());
        }
    }

    private static void printResult(String label, SongContext ctx, Path file) {
        System.out.printf(
                "%-25s songId=%s, title=\"%s\", bpm=%d, notes=%d -> %s%n",
                label, ctx.songId(), ctx.title(), ctx.bpm(), ctx.notes().size(), file
        );
        if (!ctx.notes().isEmpty()) {
            System.out.printf(
                    "  pierwsza @ %d ms (lane %d), ostatnia @ %d ms (lane %d)%n",
                    ctx.notes().getFirst().timeMs(), ctx.notes().getFirst().lane(),
                    ctx.notes().getLast().timeMs(),  ctx.notes().getLast().lane()
            );
        }
    }
}
