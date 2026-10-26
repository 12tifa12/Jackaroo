package application;

import java.io.IOException;

import engine.Game;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Entry point. Screens are laid out on a fixed 1600x900 canvas and uniformly scaled to the
 * window, over a swirling background with a vignette and faint CRT scanlines.
 */
public class Main extends Application {
    private final StackPane root = new StackPane();
    private final Pane holder = new Pane();
    private final SwirlBackground background = new SwirlBackground();
    private Scene scene;
    private Pane screen;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Theme.loadFonts();
        Theme.startTicker();
        Sound.load();

        background.setManaged(false);
        background.fitWidthProperty().bind(root.widthProperty());
        background.fitHeightProperty().bind(root.heightProperty());

        Rectangle vignette = overlayRect(new RadialGradient(0, 0, 0.5, 0.5, 0.75, true, CycleMethod.NO_CYCLE,
                new Stop(0.55, Color.TRANSPARENT), new Stop(1, Color.rgb(0, 0, 0, 0.55))));
        WritableImage lines = new WritableImage(1, 4);
        lines.getPixelWriter().setColor(0, 3, Color.rgb(0, 0, 0, 0.10));
        Rectangle scanlines = overlayRect(new ImagePattern(lines, 0, 0, 4, 4, false));

        root.getChildren().addAll(background, vignette, holder, scanlines);

        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double w = Math.min(Theme.W, vb.getWidth() * 0.92);
        double h = w * Theme.H / Theme.W;
        if (h > vb.getHeight() * 0.92) {
            h = vb.getHeight() * 0.92;
            w = h * Theme.W / Theme.H;
        }
        scene = new Scene(root, w, h, Color.BLACK);
        scene.widthProperty().addListener((o, a, b) -> rescale());
        scene.heightProperty().addListener((o, a, b) -> rescale());
        scene.setOnKeyPressed(e -> {
            if (screen instanceof GameScreen) ((GameScreen) screen).handleKey(e);
        });

        stage.setTitle("Jackaroo");
        stage.setMinWidth(800);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
        showTitle();
    }

    private Rectangle overlayRect(javafx.scene.paint.Paint fill) {
        Rectangle r = new Rectangle();
        r.setManaged(false);
        r.setMouseTransparent(true);
        r.widthProperty().bind(root.widthProperty());
        r.heightProperty().bind(root.heightProperty());
        r.setFill(fill);
        return r;
    }

    public void showTitle() {
        background.setPalette(Color.web("#b83a34"), Color.web("#1f4d8a"), Color.web("#2a1a22"), 1.2);
        setScreen(new TitleScreen(this));
    }

    public void startGame(String name) throws IOException {
        Game game = new Game(name);
        background.setPalette(Color.web("#3f7d62"), Color.web("#2b5d48"), Color.web("#5e9c80"), 1.2);
        setScreen(new GameScreen(this, game));
    }

    public void newGame(String name) {
        try {
            startGame(name);
        } catch (IOException e) {
            showTitle();
        }
    }

    public void quit() {
        Platform.exit();
    }

    private void setScreen(Pane next) {
        Pane old = screen;
        screen = next;
        next.setOpacity(0);
        holder.getChildren().add(next);
        rescale();
        FadeTransition in = new FadeTransition(Duration.millis(450), next);
        in.setToValue(1);
        in.play();
        if (old != null) {
            old.setMouseTransparent(true);
            FadeTransition out = new FadeTransition(Duration.millis(300), old);
            out.setToValue(0);
            out.setOnFinished(e -> holder.getChildren().remove(old));
            out.play();
        }
    }

    /** Scales each screen about its centre and centres it in the window (overflowing children don't matter). */
    private void rescale() {
        if (scene == null) return;
        double s = Math.min(scene.getWidth() / Theme.W, scene.getHeight() / Theme.H);
        for (Node n : holder.getChildren()) {
            n.setScaleX(s);
            n.setScaleY(s);
            n.setLayoutX((scene.getWidth() - Theme.W) / 2);
            n.setLayoutY((scene.getHeight() - Theme.H) / 2);
        }
    }
}
