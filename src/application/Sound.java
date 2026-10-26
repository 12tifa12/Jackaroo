package application;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

import javafx.scene.media.AudioClip;

/**
 * Retro sound effects synthesised in code (square-wave blips, noise bursts) so the game ships no audio files.
 * Clips are written once to temporary WAV files and played with AudioClip. Fails silently if audio is unavailable.
 */
public final class Sound {
    private Sound() {}

    public enum Fx { SELECT, CARD, DEAL, CLACK, KNOCK, FIELD, SAFE, SWAP, ERROR, WIN, LOSE }

    private static final int RATE = 22050;
    private static final Map<Fx, AudioClip> clips = new EnumMap<>(Fx.class);
    public static boolean enabled = true;

    public static void load() {
        try {
            Random rnd = new Random(7);
            put(Fx.SELECT, 0.06, t -> square(880, t) * 0.22 * Math.exp(-t * 45));
            put(Fx.CARD, 0.09, t -> (rnd.nextDouble() * 2 - 1) * 0.35 * Math.exp(-t * 38));
            put(Fx.DEAL, 0.12, t -> (rnd.nextDouble() * 2 - 1) * 0.22 * Math.sin(Math.PI * t / 0.12));
            put(Fx.CLACK, 0.05, t -> (Math.sin(2 * Math.PI * 2400 * t) * 0.6 + Math.sin(2 * Math.PI * 3700 * t) * 0.3) * 0.35 * Math.exp(-t * 90));
            put(Fx.KNOCK, 0.28, t -> (Math.sin(2 * Math.PI * (150 - 220 * t) * t) * 0.7 + (rnd.nextDouble() * 2 - 1) * 0.3 * Math.exp(-t * 50)) * 0.6 * Math.exp(-t * 10));
            put(Fx.FIELD, 0.16, notes(0.08, 523, 784));
            put(Fx.SAFE, 0.36, notes(0.09, 659, 784, 1047, 1319));
            put(Fx.SWAP, 0.16, notes(0.08, 784, 523));
            put(Fx.ERROR, 0.26, notes(0.13, 165, 123));
            put(Fx.WIN, 0.75, notes(0.11, 523, 659, 784, 1047, 784, 1047, 1319));
            put(Fx.LOSE, 0.6, notes(0.15, 392, 330, 294, 262));
        } catch (Throwable e) {
            clips.clear(); // no audio device or media module: play nothing
        }
    }

    public static void play(Fx fx) {
        play(fx, 1);
    }

    public static void play(Fx fx, double volume) {
        AudioClip c = clips.get(fx);
        if (enabled && c != null) c.play(volume);
    }

    private static double square(double freq, double t) {
        return Math.sin(2 * Math.PI * freq * t) >= 0 ? 1 : -1;
    }

    /** A little tune: each note is a square wave with a short decay. */
    private static DoubleUnaryOperator notes(double each, double... freqs) {
        return t -> {
            int i = (int) (t / each);
            if (i >= freqs.length) return 0;
            double local = t - i * each;
            return square(freqs[i], t) * 0.16 * Math.exp(-local * 14);
        };
    }

    private static void put(Fx fx, double seconds, DoubleUnaryOperator wave) throws IOException {
        int n = (int) (seconds * RATE);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream(n * 2);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double fade = Math.min(1, (n - i) / (RATE * 0.005)); // avoid a click at the end
            int s = (int) Math.round(Math.max(-1, Math.min(1, wave.applyAsDouble(t) * fade)) * 32767);
            pcm.write(s & 0xff);
            pcm.write((s >> 8) & 0xff);
        }
        File f = File.createTempFile("jackaroo-" + fx.name().toLowerCase() + "-", ".wav");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            byte[] data = pcm.toByteArray();
            out.write(wavHeader(data.length));
            out.write(data);
        }
        clips.put(fx, new AudioClip(f.toURI().toString()));
    }

    private static byte[] wavHeader(int dataLength) {
        ByteArrayOutputStream h = new ByteArrayOutputStream(44);
        writeAscii(h, "RIFF");
        writeInt(h, 36 + dataLength);
        writeAscii(h, "WAVEfmt ");
        writeInt(h, 16);
        writeShort(h, 1);           // PCM
        writeShort(h, 1);           // mono
        writeInt(h, RATE);
        writeInt(h, RATE * 2);      // byte rate
        writeShort(h, 2);           // block align
        writeShort(h, 16);          // bits per sample
        writeAscii(h, "data");
        writeInt(h, dataLength);
        return h.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream o, String s) {
        for (char c : s.toCharArray()) o.write(c);
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        for (int i = 0; i < 4; i++) o.write((v >> (8 * i)) & 0xff);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >> 8) & 0xff);
    }
}
