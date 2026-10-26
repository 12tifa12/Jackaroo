package application;

import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The slowly swirling paint background. Rendered on a tiny image every other frame and
 * scaled up with smoothing, which gives the soft, painterly look for very little CPU.
 */
public class SwirlBackground extends ImageView {
    private static final int W = 128, H = 72;

    private final WritableImage image = new WritableImage(W, H);
    private final int[] pixels = new int[W * H];
    private final double[] from = new double[9], to = new double[9], cur = new double[9];
    private double blendStart = -1, blendSeconds = 1;
    private int frame;

    public SwirlBackground() {
        setImage(image);
        setSmooth(true);
        setPreserveRatio(false);
        setPalette(Color.BLACK, Color.BLACK, Color.BLACK, 0);
        Theme.tick(this, this::render);
    }

    /** Main colour, deep swirl colour, thin streak colour; blends over {@code seconds}. */
    public void setPalette(Color main, Color deep, Color streak, double seconds) {
        System.arraycopy(cur, 0, from, 0, 9);
        Color[] cs = { main, deep, streak };
        for (int i = 0; i < 3; i++) {
            to[i * 3] = cs[i].getRed();
            to[i * 3 + 1] = cs[i].getGreen();
            to[i * 3 + 2] = cs[i].getBlue();
        }
        if (seconds <= 0) {
            System.arraycopy(to, 0, cur, 0, 9);
            blendStart = -1;
        } else {
            blendStart = Theme.time();
            blendSeconds = seconds;
        }
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private void render(double t) {
        if ((frame++ & 1) == 1) return;
        if (blendStart >= 0) {
            double k = smooth(0, 1, (t - blendStart) / blendSeconds);
            for (int i = 0; i < 9; i++) cur[i] = from[i] + (to[i] - from[i]) * k;
            if (k >= 1) blendStart = -1;
        }
        double tt = t * 0.35;
        for (int y = 0; y < H; y++) {
            double v = (y - H / 2.0) / H * 2;
            for (int x = 0; x < W; x++) {
                double u = (x - W / 2.0) / H * 2;
                double r = Math.sqrt(u * u + v * v);
                double a = Math.atan2(v, u) + tt * 0.25 - r * 1.6;
                double px = Math.cos(a) * r * 2.6, py = Math.sin(a) * r * 2.6;
                for (int i = 0; i < 3; i++) {
                    double nx = px + 0.7 * Math.sin(py * 1.4 + tt + i * 1.9);
                    double ny = py + 0.7 * Math.cos(px * 1.2 - tt * 0.8 + i * 2.7);
                    px = nx;
                    py = ny;
                }
                double f = 0.5 + 0.5 * Math.sin(px * 1.3 + py * 0.9 + tt * 0.6);
                double deep = smooth(0.52, 0.66, f);
                double streak = (1 - smooth(0.0, 0.07, Math.abs(f - 0.28))) * 0.85;
                int rgb = 0xFF000000;
                for (int c = 0; c < 3; c++) {
                    double val = cur[c] * (1 - deep) + cur[3 + c] * deep;
                    val = val * (1 - streak) + cur[6 + c] * streak;
                    rgb |= ((int) Math.round(Math.max(0, Math.min(1, val)) * 255)) << (16 - c * 8);
                }
                pixels[y * W + x] = rgb;
            }
        }
        image.getPixelWriter().setPixels(0, 0, W, H, PixelFormat.getIntArgbInstance(), pixels, 0, W);
    }
}
