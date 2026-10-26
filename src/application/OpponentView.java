package application;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import model.player.Player;

/** A CPU opponent shown like a Balatro joker card: portrait, name, face-down hand and marble counts. */
public class OpponentView extends StackPane {
    public static final double W = 155, H = 205;

    private final Player player;
    private final HBox backs = new HBox(-5);
    private final Label stats;
    private final DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Theme.GOLD, 26, 0.45, 0, 0);
    private boolean active;

    public OpponentView(Player player) {
        this.player = player;
        Color colour = Theme.of(player.getColour());
        Theme.fixSize(this, W, H);
        setStyle("-fx-background-color: rgba(0,0,0,0.35), #f1ede4, " + Theme.css(colour.deriveColor(0, 0.85, 0.5, 1)) + ";"
                + "-fx-background-insets: 6 0 -6 0, 0, 7; -fx-background-radius: 14, 14, 9;");

        Canvas art = new Canvas(84, 72);
        GraphicsContext g = art.getGraphicsContext2D();
        g.setFill(colour.deriveColor(0, 0.8, 0.8, 1));
        g.fillRoundRect(0, 0, 84, 72, 18, 18);
        PixelArt.drawCentered(g, PixelArt.ROBOT, 42, 37, 6,
                PixelArt.palette('K', Color.web("#1d2327"), 'W', Color.web("#e9eef0"), 'C', colour.interpolate(Color.WHITE, 0.35)), false);

        Label name = Theme.label(player.getName(), 23, Color.WHITE);
        stats = Theme.label("", 15, Color.web("#f0f3f2"));
        backs.setAlignment(Pos.CENTER);
        backs.setMinHeight(26);
        VBox v = new VBox(6, art, name, backs, stats);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(10, 8, 14, 8));
        getChildren().add(v);

        Theme.tick(this, t -> {
            if (!active) return;
            setTranslateY(-14 + Math.sin(t * 3.2) * 2.5);
            glow.setRadius(22 + Math.sin(t * 4) * 6);
        });
    }

    public Player getPlayer() {
        return player;
    }

    public void refresh(int handSize, int safe) {
        backs.getChildren().clear();
        for (int i = 0; i < handSize; i++) backs.getChildren().add(miniBack());
        if (handSize == 0) backs.getChildren().add(Theme.label("no cards", 14, Color.web("#d6dcdc")));
        stats.setText("Home " + player.getMarbles().size() + "   Safe " + safe);
    }

    public void setActive(boolean a) {
        if (active == a) return;
        active = a;
        setEffect(a ? glow : null);
        if (!a) new Timeline(new KeyFrame(Theme.ms(200), new KeyValue(translateYProperty(), 0))).play();
    }

    private static Canvas miniBack() {
        Canvas c = new Canvas(18, 26);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.rgb(0, 0, 0, 0.3));
        g.fillRoundRect(0, 2, 18, 24, 5, 5);
        g.setFill(Color.web("#f4f1ea"));
        g.fillRoundRect(0, 0, 18, 24, 5, 5);
        g.setFill(Color.web("#d9463f"));
        g.fillRoundRect(2, 2, 14, 20, 3, 3);
        g.setFill(Color.web("#f4f1ea"));
        g.fillRect(7, 10, 4, 4);
        return c;
    }
}
