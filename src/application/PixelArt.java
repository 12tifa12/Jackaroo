package application;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import model.card.standard.Suit;

/** Tiny bitmap sprites drawn pixel-by-pixel onto canvases, for the chunky Balatro look. '.' is transparent. */
public final class PixelArt {
    private PixelArt() {}

    public static final String[] SPADE = {
        "....#....",
        "...###...",
        "..#####..",
        ".#######.",
        "#########",
        "#########",
        ".##.#.##.",
        "....#....",
        "...###...",
    };

    public static final String[] HEART = {
        ".##...##.",
        "####.####",
        "#########",
        "#########",
        ".#######.",
        "..#####..",
        "...###...",
        "....#....",
    };

    public static final String[] DIAMOND = {
        "....#....",
        "...###...",
        "..#####..",
        ".#######.",
        "#########",
        ".#######.",
        "..#####..",
        "...###...",
        "....#....",
    };

    public static final String[] CLUB = {
        "...###...",
        "..#####..",
        "..#####..",
        "##.###.##",
        "#########",
        "#########",
        "##..#..##",
        "....#....",
        "...###...",
    };

    // Court card busts (top half; the bottom half is the same art rotated 180 degrees).
    // Y gold, C gem, H hair, S skin, K eyes/outline, R robe (suit colour), W white, P lips
    public static final String[] KING = {
        "...Y..YY..Y...",
        "...YYYYYYYY...",
        "...YCYYYYCY...",
        "...HHHHHHHH...",
        "..HSSSSSSSSH..",
        "..HSKSSSSKSH..",
        "..HSSSSSSSSH..",
        "..HHSSKKSSHH..",
        "...HHHHHHHH...",
        "..RRRRWWRRRR..",
        ".RRRRRWWRRRRR.",
        "RRRYRRWWRRYRRR",
        "RRRRRRWWRRRRRR",
    };

    public static final String[] QUEEN = {
        "......YY......",
        "....YYYYYY....",
        "...YCYYYYCY...",
        "..HHHHHHHHHH..",
        "..HSSSSSSSSH..",
        "..HSKSSSSKSH..",
        "..HSSSSSSSSH..",
        "..HSSSPPSSSH..",
        "..HHSSSSSSHH..",
        ".HHRRRWWRRRHH.",
        ".HRRRRWWRRRRH.",
        "RRRYRRWWRRYRRR",
        "RRRRRRWWRRRRRR",
    };

    public static final String[] JACK = {
        "...........W..",
        "...RRRRRRRWW..",
        "..RRRRRRRRRR..",
        "...HHHHHHHH...",
        "..HSSSSSSSSH..",
        "..HSKSSSSKSH..",
        "..HSSSSSSSSH..",
        "..HSSSKKSSSH..",
        "...SSSSSSSS...",
        "..RRRRWWRRRR..",
        ".RRRRRWWRRRRR.",
        "RRRYRRWWRRYRRR",
        "RRRRRRWWRRRRRR",
    };

    // R red, O orange, Y yellow, W white
    public static final String[] FLAME = {
        "....R.....",
        "....RR....",
        "...RRR..R.",
        "..RROR..RR",
        "..ROORR.RR",
        ".RROOORRRR",
        ".ROOYOORRR",
        "RROYYYOORR",
        "ROOYYYYOOR",
        "ROYYWWYYOR",
        "ROYYWWYYOR",
        ".ROYYYYOR.",
        "..RROORR..",
    };

    // B border, C blue, W white
    public static final String[] SHIELD = {
        "BBBBBBBBBB",
        "BCCCWWCCCB",
        "BCCCWWCCCB",
        "BWWWWWWWWB",
        "BWWWWWWWWB",
        "BCCCWWCCCB",
        "BCCCWWCCCB",
        ".BCCWWCCB.",
        ".BCCWWCCB.",
        "..BCWWCB..",
        "...BWWB...",
        "....BB....",
    };

    // K outline, W plate, C eyes/antenna
    public static final String[] ROBOT = {
        ".....KK.....",
        ".....CC.....",
        "..KKKKKKKK..",
        ".KWWWWWWWWK.",
        ".KWKKWWKKWK.",
        "KKWKCWWKCWKK",
        "KKWWWWWWWWKK",
        ".KWWKKKKWWK.",
        ".KWWWWWWWWK.",
        "..KKKKKKKK..",
    };

    public static final String[] DOT = {
        ".#.",
        "###",
        ".#.",
    };

    public static String[] suit(Suit s) {
        switch (s) {
            case HEART: return HEART;
            case DIAMOND: return DIAMOND;
            case CLUB: return CLUB;
            default: return SPADE;
        }
    }

    public static Function<Character, Color> fire() {
        return palette('R', Color.web("#e8433b"), 'O', Color.web("#fb8c2b"), 'Y', Color.web("#ffd447"), 'W', Color.web("#fffbe8"));
    }

    public static int width(String[] art) {
        int w = 0;
        for (String row : art) w = Math.max(w, row.length());
        return w;
    }

    public static void draw(GraphicsContext g, String[] art, double x, double y, double px, Function<Character, Color> pal, boolean flip) {
        for (int r = 0; r < art.length; r++) {
            String row = art[flip ? art.length - 1 - r : r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '.') continue;
                Color col = pal.apply(ch);
                if (col == null) continue;
                g.setFill(col);
                // a hair of overlap avoids seams between neighbouring pixels
                g.fillRect(x + c * px, y + r * px, px + 0.4, px + 0.4);
            }
        }
    }

    public static void drawCentered(GraphicsContext g, String[] art, double cx, double cy, double px, Function<Character, Color> pal, boolean flip) {
        draw(g, art, cx - width(art) * px / 2, cy - art.length * px / 2, px, pal, flip);
    }

    public static Function<Character, Color> mono(Color c) {
        return ch -> c;
    }

    public static Function<Character, Color> palette(Object... pairs) {
        Map<Character, Color> m = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put((Character) pairs[i], (Color) pairs[i + 1]);
        return m::get;
    }
}
