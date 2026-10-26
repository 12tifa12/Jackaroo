package application;

import java.io.IOException;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import model.card.Card;
import model.card.standard.Ace;
import model.card.standard.Jack;
import model.card.standard.King;
import model.card.standard.Seven;
import model.card.standard.Suit;
import model.card.wild.Burner;
import model.card.wild.Saver;

/** Main menu: the wobbly JACK[A]ROO logo, a name box and Play / Rules / Quit. */
public class TitleScreen extends Pane {
    private StackPane overlay;

    public TitleScreen(Main app) {
        Theme.fixSize(this, Theme.W, Theme.H);

        decor(new King("King", "", Suit.HEART, null, null), 1250, 110, 14);
        decor(new Burner("MarbleBurner", "", null, null), 1390, 250, 24);
        decor(new Saver("MarbleSaver", "", null, null), 150, 140, -16);
        decor(new Seven("Seven", "", Suit.CLUB, null, null), 60, 330, -6);
        decor(new Jack("Jack", "", Suit.DIAMOND, null, null), 1300, 520, 8);

        HBox logo = new HBox(4);
        logo.setAlignment(Pos.CENTER);
        String word = "JACK*ROO";
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == '*') {
                CardView ace = new CardView(new Ace("Ace", "", Suit.SPADE, null, null), 128, true);
                ace.setBaseRotate(-8);
                StackPane holder = new StackPane(ace);
                holder.setPadding(new Insets(0, 10, 0, 10));
                logo.getChildren().add(holder);
            } else {
                logo.getChildren().add(letter(String.valueOf(word.charAt(i)), i));
            }
        }

        Label tagline = Theme.label("marbles, cards and a little mayhem", 28, Color.web("#f7e9d7"));

        TextField name = new TextField();
        name.setPromptText("Player");
        name.setFont(Theme.font(26));
        name.setPrefWidth(340);
        name.setStyle("-fx-background-color: #141c1f; -fx-text-fill: white; -fx-prompt-text-fill: #6d7c80;"
                + "-fx-background-radius: 10; -fx-padding: 8 14; -fx-highlight-fill: #009dff;");
        HBox nameRow = new HBox(14, Theme.label("Your name", 24, Color.WHITE), name);
        nameRow.setAlignment(Pos.CENTER);

        Label error = Theme.label("", 18, Theme.RED);
        Runnable play = () -> {
            String n = name.getText().trim();
            try {
                app.startGame(n.isEmpty() ? "Player" : n);
            } catch (IOException ex) {
                error.setText("Couldn't load Cards.csv. Run the game from the project folder.");
            }
        };
        name.setOnAction(e -> play.run());

        HBox buttons = new HBox(14,
                Theme.button("Play", Theme.BLUE, 240, 84, 34, play),
                Theme.button("Rules", Theme.ORANGE, 200, 84, 30, this::showRules),
                Theme.button("Quit", Theme.RED, 200, 84, 30, app::quit));
        buttons.setAlignment(Pos.CENTER);

        VBox panel = new VBox(16, nameRow, buttons, error);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20, 26, 12, 26));
        panel.setStyle("-fx-background-color: rgba(0,0,0,0.35), #1e2a2d; -fx-background-insets: 6 0 -6 0, 0; -fx-background-radius: 18;");
        panel.setMaxWidth(Region.USE_PREF_SIZE);

        VBox column = new VBox(26, logo, tagline, panel);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(150, 0, 0, 0));
        Theme.fixSize(column, Theme.W, Theme.H);
        column.setPickOnBounds(false);
        getChildren().add(column);

        Label credit = Theme.label("Jackaroo  /  university project", 16, Color.web("#f7e9d7", 0.7));
        credit.relocate(24, Theme.H - 40);
        getChildren().add(credit);
    }

    private void decor(Card c, double x, double y, double rot) {
        CardView v = new CardView(c, 130, true);
        v.setBaseRotate(rot);
        v.relocate(x, y);
        v.setMouseTransparent(true);
        getChildren().add(v);
    }

    private static Text letter(String s, int i) {
        Text t = new Text(s);
        t.setFont(Theme.font(168));
        t.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fffaf0")), new Stop(1, Color.web("#e6d9c2"))));
        t.setStroke(Color.web("#24323a"));
        t.setStrokeWidth(10);
        t.setStrokeType(StrokeType.OUTSIDE);
        t.setEffect(Theme.hardShadow(9, 0.45));
        Theme.tick(t, time -> {
            t.setTranslateY(Math.sin(time * 2.1 + i * 0.7) * 7);
            t.setRotate(Math.sin(time * 1.5 + i * 1.3) * 2.5);
        });
        return t;
    }

    private void showRules() {
        if (overlay != null) return;
        overlay = Overlays.wrap(Overlays.rules(this::closeRules), this::closeRules);
        getChildren().add(overlay);
    }

    private void closeRules() {
        getChildren().remove(overlay);
        overlay = null;
    }
}
