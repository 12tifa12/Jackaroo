package application;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import model.Colour;
import model.card.Card;
import model.card.standard.Standard;
import model.card.standard.Suit;
import model.card.wild.Burner;
import model.card.wild.Saver;

/** Shared look and feel: Balatro-ish palette, pixel font, chunky panels/buttons and a global frame ticker. */
public final class Theme {
    private Theme() {}

    /** Every screen is laid out on a fixed 1600x900 canvas and scaled to the window. */
    public static final double W = 1600, H = 900;

    public static final Color BLUE = Color.web("#009dff");
    public static final Color RED = Color.web("#fe5f55");
    public static final Color ORANGE = Color.web("#fda200");
    public static final Color GOLD = Color.web("#f5b244");
    public static final Color GREEN = Color.web("#35bd86");
    public static final Color CYAN = Color.web("#4fd6e8");
    public static final Color PANEL = Color.web("#1e2a2d");
    public static final Color BOX = Color.web("#34444a");
    public static final Color INSET = Color.web("#141c1f");
    public static final Color MUTED = Color.web("#a7b4b8");
    public static final Color INK = Color.web("#2d3550");

    /** Animation duration multiplier: 1 = normal, lower = faster. */
    public static double speed = 1.0;

    private static String family;

    public static void loadFonts() {
        String[] resources = { "/fonts/PixelifySans.ttf", "/main/resources/fonts/PixelifySans.ttf" };
        for (String path : resources) {
            try (InputStream in = Theme.class.getResourceAsStream(path)) {
                if (in != null && register(in)) return;
            } catch (IOException ignored) {
            }
        }
        File file = new File("src/main/resources/fonts/PixelifySans.ttf");
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                register(in);
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean register(InputStream in) {
        Font f = Font.loadFont(in, 20);
        if (f == null) return false;
        family = f.getFamily();
        return true;
    }

    public static Font font(double size) {
        return family != null ? Font.font(family, size) : Font.font("Impact", size);
    }

    public static Duration ms(double millis) {
        return Duration.millis(millis * speed);
    }

    // ---------------------------------------------------------------- colours

    public static Color of(Colour c) {
        switch (c) {
            case GREEN: return GREEN;
            case RED: return RED;
            case YELLOW: return Color.web("#f4c430");
            default: return BLUE;
        }
    }

    public static Color suitColour(Suit s) {
        switch (s) {
            case HEART: return Color.web("#ef3a4a");
            case DIAMOND: return Color.web("#ee6a2c");
            case CLUB: return Color.web("#1e5a52");
            default: return Color.web("#383f62");
        }
    }

    public static String suitName(Suit s) {
        switch (s) {
            case HEART: return "Hearts";
            case DIAMOND: return "Diamonds";
            case CLUB: return "Clubs";
            default: return "Spades";
        }
    }

    public static String css(Color c) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255), c.getOpacity());
    }

    // ---------------------------------------------------------------- card text

    public static String prettyName(Card c) {
        if (c instanceof Burner) return "Burner";
        if (c instanceof Saver) return "Saver";
        return c.getName();
    }

    /** Full name, e.g. "Seven of Clubs". */
    public static String fullName(Card c) {
        if (c instanceof Standard) return c.getName() + " of " + suitName(((Standard) c).getSuit());
        return prettyName(c);
    }

    public static String describe(Card c) {
        String d = c.getDescription();
        return d == null ? "" : d;
    }

    // ---------------------------------------------------------------- widgets

    public static DropShadow hardShadow(double dy, double opacity) {
        return new DropShadow(BlurType.ONE_PASS_BOX, Color.rgb(0, 0, 0, opacity), 0, 0, 0, dy);
    }

    public static Label label(String text, double size, Color fill) {
        Label l = new Label(text);
        l.setFont(font(size));
        l.setTextFill(fill);
        l.setEffect(hardShadow(Math.max(1.5, size / 13), 0.45));
        return l;
    }

    public static Text outlined(String text, double size, Color fill) {
        Text t = new Text(text);
        t.setFont(font(size));
        t.setFill(fill);
        t.setStroke(Color.web("#1a1f24"));
        t.setStrokeWidth(Math.max(2, size / 9));
        t.setStrokeType(StrokeType.OUTSIDE);
        t.setEffect(hardShadow(Math.max(2, size / 10), 0.45));
        return t;
    }

    public static void fixSize(Region r, double w, double h) {
        r.setMinSize(w, h);
        r.setPrefSize(w, h);
        r.setMaxSize(w, h);
    }

    /** A rounded block with a darker "3D" lip along its bottom edge. */
    public static String panelCss(Color fill, double radius) {
        return "-fx-background-color: " + css(fill.deriveColor(0, 1, 0.62, 1)) + ", " + css(fill) + ";"
                + "-fx-background-insets: 0, 0 0 4 0; -fx-background-radius: " + radius + ";";
    }

    public static String insetCss() {
        return "-fx-background-color: " + css(INSET) + "; -fx-background-radius: 9;";
    }

    public static StackPane button(String text, Color colour, double w, double h, double size, Runnable action) {
        Label l = label(text, size, Color.WHITE);
        l.setTextAlignment(TextAlignment.CENTER);
        l.setWrapText(true);
        StackPane b = new StackPane(l);
        b.setPadding(new Insets(0, 6, 4, 6));
        fixSize(b, w, h);
        b.setStyle(panelCss(colour, 12));
        b.setCursor(Cursor.HAND);
        b.setOnMouseEntered(e -> b.setStyle(panelCss(colour.interpolate(Color.WHITE, 0.14), 12)));
        b.setOnMouseExited(e -> {
            b.setStyle(panelCss(colour, 12));
            b.setTranslateY(0);
        });
        b.setOnMousePressed(e -> b.setTranslateY(2));
        b.setOnMouseReleased(e -> b.setTranslateY(0));
        b.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || action == null) return;
            Sound.play(Sound.Fx.SELECT, 0.6);
            action.run();
        });
        b.disabledProperty().addListener((o, was, dis) -> b.setOpacity(dis ? 0.4 : 1));
        return b;
    }

    public static void setButtonText(StackPane button, String text) {
        ((Label) button.getChildren().get(0)).setText(text);
    }

    /** Balatro-style floating "+10" text: pops in, drifts up, fades out. */
    public static void popText(Pane layer, String text, double x, double y, Color fill, double size) {
        Text t = outlined(text, size, fill);
        t.setMouseTransparent(true);
        t.setTextOrigin(VPos.CENTER);
        layer.getChildren().add(t);
        t.setLayoutX(x - t.getLayoutBounds().getWidth() / 2);
        t.setLayoutY(y);
        t.setScaleX(0.2);
        t.setScaleY(0.2);
        t.setRotate((Math.random() - 0.5) * 10);

        ScaleTransition pop = new ScaleTransition(ms(160), t);
        pop.setToX(1.2);
        pop.setToY(1.2);
        ScaleTransition settle = new ScaleTransition(ms(110), t);
        settle.setToX(1);
        settle.setToY(1);
        TranslateTransition rise = new TranslateTransition(ms(1000), t);
        rise.setByY(-40);
        FadeTransition fade = new FadeTransition(ms(320), t);
        fade.setDelay(ms(780));
        fade.setToValue(0);
        ParallelTransition all = new ParallelTransition(new SequentialTransition(pop, settle), rise, fade);
        all.setOnFinished(e -> layer.getChildren().remove(t));
        all.play();
    }

    // ---------------------------------------------------------------- frame ticker

    private static final List<DoubleConsumer> tickers = new ArrayList<>();
    private static double now;

    public static double time() {
        return now;
    }

    public static void startTicker() {
        new AnimationTimer() {
            private long start = -1;

            @Override
            public void handle(long nanos) {
                if (start < 0) start = nanos;
                now = (nanos - start) / 1e9;
                for (DoubleConsumer c : new ArrayList<>(tickers)) c.accept(now);
            }
        }.start();
    }

    /** Runs {@code c} every frame while {@code n} is attached to a scene. */
    public static void tick(Node n, DoubleConsumer c) {
        n.sceneProperty().addListener((o, was, is) -> {
            if (is == null) tickers.remove(c);
            else if (!tickers.contains(c)) tickers.add(c);
        });
        if (n.getScene() != null) tickers.add(c);
    }
}
