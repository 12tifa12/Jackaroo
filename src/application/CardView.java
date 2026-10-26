package application;

import java.util.function.Function;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import model.card.Card;
import model.card.standard.Standard;
import model.card.wild.Burner;

/** A playing card drawn in pixel style, with a Balatro-like idle sway. A null card draws as a card back. */
public class CardView extends Pane {
    public static final double BASE_W = 120;

    // pip layouts for 2..10 as (x, y) fractions of the pip area
    private static final double[][][] PIPS = new double[11][][];
    static {
        double[][] corners = { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } };
        double[][] sides = { { 0, 0.333 }, { 1, 0.333 }, { 0, 0.667 }, { 1, 0.667 } };
        PIPS[2] = new double[][] { { .5, 0 }, { .5, 1 } };
        PIPS[3] = new double[][] { { .5, 0 }, { .5, .5 }, { .5, 1 } };
        PIPS[4] = corners;
        PIPS[5] = concat(corners, new double[][] { { .5, .5 } });
        PIPS[6] = concat(corners, new double[][] { { 0, .5 }, { 1, .5 } });
        PIPS[7] = concat(PIPS[6], new double[][] { { .5, .25 } });
        PIPS[8] = concat(PIPS[6], new double[][] { { .5, .25 }, { .5, .75 } });
        PIPS[9] = concat(concat(corners, sides), new double[][] { { .5, .5 } });
        PIPS[10] = concat(concat(corners, sides), new double[][] { { .5, .167 }, { .5, .833 } });
    }

    private static double[][] concat(double[][] a, double[][] b) {
        double[][] r = new double[a.length + b.length][];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private final Card card;
    private final double w, h, s;
    private final Canvas canvas;
    private final double phase = Math.random() * Math.PI * 2;
    private boolean faceUp;
    private boolean sway = true;
    private boolean playable = true;
    private double baseRotate;

    /** Hand bookkeeping used by the game screen. */
    public boolean hovered;

    public CardView(Card card, double width, boolean faceUp) {
        this.card = card;
        this.w = width;
        this.h = Math.round(width * 1.4);
        this.s = width / BASE_W;
        this.faceUp = faceUp;
        canvas = new Canvas(w, h + 6 * s);
        getChildren().add(canvas);
        Theme.fixSize(this, w, h);
        redraw();
        Theme.tick(this, t -> {
            if (sway) setRotate(baseRotate + Math.sin(t * 1.7 + phase) * 1.3);
        });
    }

    public Card getCard() { return card; }
    public double getW() { return w; }
    public double getH() { return h; }
    public boolean isFaceUp() { return faceUp; }
    public double getBaseRotate() { return baseRotate; }

    public void setBaseRotate(double r) {
        baseRotate = r;
        if (!sway) setRotate(r);
    }

    public void setSway(boolean on) {
        sway = on;
        if (!on) setRotate(baseRotate);
    }

    public boolean isPlayable() { return playable; }

    /** Greys the card out when it has no legal play. */
    public void setPlayable(boolean p) {
        if (playable == p) return;
        playable = p;
        canvas.setEffect(p ? null : new ColorAdjust(0, -0.8, -0.35, -0.2));
    }

    public void setFaceUp(boolean up) {
        faceUp = up;
        redraw();
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double r = 12 * s;
        g.setFill(Color.rgb(0, 0, 0, 0.35));
        g.fillRoundRect(0, 5 * s, w, h, r, r);
        if (!faceUp || card == null) {
            drawBack(g, r);
            return;
        }
        g.setFill(Color.web("#fbfaf6"));
        g.fillRoundRect(0, 0, w, h, r, r);
        g.setStroke(Color.web("#cfcabf"));
        g.setLineWidth(2 * s);
        g.strokeRoundRect(s, s, w - 2 * s, h - 2 * s, r - s, r - s);
        if (card instanceof Standard) drawStandard(g, (Standard) card);
        else drawWild(g, r);
    }

    private static String rankText(int rank) {
        switch (rank) {
            case 1: return "A";
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            default: return String.valueOf(rank);
        }
    }

    private void drawStandard(GraphicsContext g, Standard c) {
        Color ink = Theme.suitColour(c.getSuit());
        String[] pip = PixelArt.suit(c.getSuit());
        Function<Character, Color> mono = PixelArt.mono(ink);
        int rank = c.getRank();

        for (int pass = 0; pass < 2; pass++) {
            g.save();
            if (pass == 1) {
                g.translate(w, h);
                g.rotate(180);
            }
            g.setFill(ink);
            g.setFont(Theme.font((rank == 10 ? 21 : 26) * s));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.TOP);
            g.fillText(rankText(rank), 14 * s, 4 * s);
            PixelArt.drawCentered(g, pip, 14 * s, 40 * s, 2 * s, mono, false);
            g.restore();
        }

        if (rank == 1) {
            PixelArt.drawCentered(g, pip, w / 2, h / 2, 6 * s, mono, false);
        } else if (rank >= 11) {
            drawCourt(g, rank, ink);
        } else {
            double ax = 40 * s, aw = w - 80 * s, ay = 30 * s, ah = h - 60 * s;
            for (double[] p : PIPS[rank])
                PixelArt.drawCentered(g, pip, ax + p[0] * aw, ay + p[1] * ah, 3 * s, mono, p[1] > 0.5);
        }
    }

    private void drawCourt(GraphicsContext g, int rank, Color ink) {
        double fx = 28 * s, fy = 12 * s, fw = w - 56 * s, fh = h - 24 * s;
        g.setFill(ink.interpolate(Color.WHITE, 0.82));
        g.fillRect(fx, fy, fw, fh);
        String[] art = rank == 11 ? PixelArt.JACK : rank == 12 ? PixelArt.QUEEN : PixelArt.KING;
        Function<Character, Color> pal = PixelArt.palette(
                'Y', Color.web("#f2b33d"), 'C', Color.web("#4fb6e3"), 'H', Color.web("#5a3b2e"),
                'S', Color.web("#f3c9a6"), 'K', Color.web("#262b3a"), 'R', ink, 'W', Color.WHITE, 'P', Color.web("#e05a6a"));
        double px = 4 * s;
        for (int pass = 0; pass < 2; pass++) {
            g.save();
            if (pass == 1) {
                g.translate(w, h);
                g.rotate(180);
            }
            PixelArt.draw(g, art, w / 2 - 7 * px, h / 2 - 13 * px, px, pal, false);
            g.restore();
        }
        g.setStroke(ink);
        g.setLineWidth(2 * s);
        g.strokeRect(fx, fy, fw, fh);
        g.strokeLine(fx, h / 2, fx + fw, h / 2);
    }

    private void drawWild(GraphicsContext g, double r) {
        boolean burner = card instanceof Burner;
        g.setFill(Color.web(burner ? "#3a1c24" : "#13304d"));
        g.fillRoundRect(7 * s, 7 * s, w - 14 * s, h - 14 * s, r * 0.7, r * 0.7);
        g.setFill(Color.rgb(255, 255, 255, 0.13));
        for (int i = 0; i < 16; i++)
            g.fillRect((12 + (i * 37) % 92) * s, (12 + (i * 53) % 140) * s, 2 * s, 2 * s);

        if (burner) PixelArt.drawCentered(g, PixelArt.FLAME, w / 2, h / 2, 5 * s, PixelArt.fire(), false);
        else PixelArt.drawCentered(g, PixelArt.SHIELD, w / 2, h / 2, 5 * s,
                PixelArt.palette('B', Color.web("#0b1c2e"), 'C', Theme.BLUE, 'W', Color.web("#eef6ff")), false);

        g.setFill(Color.WHITE);
        g.setFont(Theme.font(15 * s));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        String label = burner ? "BURNER" : "SAVER";
        for (int pass = 0; pass < 2; pass++) {
            g.save();
            if (pass == 1) {
                g.translate(w, h);
                g.rotate(180);
            }
            g.translate(28 * s, 13 * s);
            g.rotate(90);
            g.fillText(label, 0, 0);
            g.restore();
        }
    }

    private void drawBack(GraphicsContext g, double r) {
        Color cream = Color.web("#f4f1ea");
        g.setFill(cream);
        g.fillRoundRect(0, 0, w, h, r, r);
        g.setFill(Color.web("#d9463f"));
        g.fillRoundRect(5 * s, 5 * s, w - 10 * s, h - 10 * s, r * 0.6, r * 0.6);
        Function<Character, Color> dark = PixelArt.mono(Color.web("#b5332e"));
        double step = 12 * s;
        for (double y = 9 * s; y < h - 14 * s; y += step)
            for (double x = 9 * s; x < w - 14 * s; x += step)
                PixelArt.draw(g, PixelArt.DOT, x, y, 2 * s, dark, false);
        g.setFill(Color.web("#d9463f"));
        g.fillRoundRect(w * 0.25, h * 0.33, w * 0.5, h * 0.34, 8 * s, 8 * s);
        g.setStroke(cream);
        g.setLineWidth(3 * s);
        g.strokeRoundRect(w * 0.25, h * 0.33, w * 0.5, h * 0.34, 8 * s, 8 * s);
        PixelArt.drawCentered(g, PixelArt.SPADE, w / 2, h / 2, 3 * s, PixelArt.mono(cream), false);
    }
}
