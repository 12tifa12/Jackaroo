package application;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import model.card.Card;
import model.card.standard.Ace;
import model.card.standard.Five;
import model.card.standard.Four;
import model.card.standard.Jack;
import model.card.standard.King;
import model.card.standard.Queen;
import model.card.standard.Seven;
import model.card.standard.Standard;
import model.card.standard.Suit;
import model.card.standard.Ten;
import model.card.wild.Burner;
import model.card.wild.Saver;

/** Modal panels (rules, menus, game over) and the card tooltip. */
public final class Overlays {
    private Overlays() {}

    private static final String RULES =
            "Get all four of your marbles into your Safe Zone before anyone else.\n\n"
            + "Each round everyone is dealt 4 cards. On your turn play one card: pick it, pick the marbles it acts on, then press Play.\n\n"
            + "Field marbles from Home onto your Base with an Ace or a King.\n\n"
            + "Marbles travel counter-clockwise. After a full lap, a marble passing its Entry (dashed ring) turns into its Safe Zone.\n\n"
            + "Landing on a marble sends it home. You can't pass your own marbles or jump over two. A marble on its own Base is protected.\n\n"
            + "8 hidden traps lurk on the track.\n\n"
            + "Can't move? Discard a card to end your turn.";

    /** Dimmed full-screen layer holding a centred panel. Clicking the dim area calls onDismiss (if any). */
    public static StackPane wrap(Region panel, Runnable onDismiss) {
        StackPane wrap = new StackPane(panel);
        Theme.fixSize(wrap, Theme.W, Theme.H);
        wrap.setStyle("-fx-background-color: rgba(8,12,14,0.62);");
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wrap.setOnMouseClicked(e -> {
            if (onDismiss != null && e.getTarget() == wrap) onDismiss.run();
        });
        wrap.setOpacity(0);
        FadeTransition fade = new FadeTransition(Theme.ms(180), wrap);
        fade.setToValue(1);
        fade.play();
        panel.setScaleX(0.85);
        panel.setScaleY(0.85);
        ScaleTransition pop = new ScaleTransition(Theme.ms(220), panel);
        pop.setToX(1);
        pop.setToY(1);
        pop.play();
        return wrap;
    }

    public static VBox panel(String title, double width) {
        VBox v = new VBox(16);
        v.setAlignment(Pos.TOP_CENTER);
        v.setPadding(new Insets(22, 28, 28, 28));
        v.setPrefWidth(width);
        v.setStyle("-fx-background-color: rgba(0,0,0,0.4), #4a5d63, #1e2a2d;"
                + "-fx-background-insets: 8 0 -8 0, 0, 4; -fx-background-radius: 20, 20, 17;");
        v.getChildren().add(Theme.label(title, 46, Color.WHITE));
        return v;
    }

    public static VBox rules(Runnable close) {
        VBox p = panel("HOW TO PLAY", 1260);
        Label text = new Label(RULES);
        text.setFont(Theme.font(18));
        text.setTextFill(Color.web("#e2eaec"));
        text.setWrapText(true);
        text.setMaxWidth(300);
        StackPane rulesBox = new StackPane(text);
        rulesBox.setPadding(new Insets(16));
        rulesBox.setStyle(Theme.insetCss());
        rulesBox.setAlignment(Pos.TOP_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        List<Card> cards = sampleCards();
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            CardView cv = new CardView(c, 56, true);
            Label name = Theme.label(Theme.prettyName(c), 19, Color.WHITE);
            Label desc = new Label(Theme.describe(c));
            desc.setFont(Theme.font(13.5));
            desc.setTextFill(Theme.MUTED);
            desc.setWrapText(true);
            desc.setMaxWidth(214);
            desc.setMinHeight(Region.USE_PREF_SIZE);
            VBox info = new VBox(2, name, desc);
            HBox cell = new HBox(12, cv, info);
            cell.setAlignment(Pos.CENTER_LEFT);
            cell.setPadding(new Insets(6, 8, 6, 8));
            cell.setPrefWidth(292);
            cell.setMinHeight(104);
            cell.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 10;");
            grid.add(cell, i % 3, i / 3);
        }
        HBox body = new HBox(20, rulesBox, grid);
        p.getChildren().addAll(body, Theme.button("Back", Theme.ORANGE, 220, 60, 26, close));
        return p;
    }

    /** One of each kind of card, read from Cards.csv (with null managers: they are only drawn). */
    static List<Card> sampleCards() {
        Map<String, List<String[]>> rows = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("Cards.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = parseCsv(line);
                if (f.length < 4) continue;
                rows.computeIfAbsent(f[2], k -> new ArrayList<>()).add(f);
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        List<Card> cards = new ArrayList<>();
        int k = 0;
        for (List<String[]> group : rows.values()) {
            String[] pick = group.get(0);
            Suit want = Suit.values()[k++ % 4];
            for (String[] f : group) if (f.length > 5 && f[5].equals(want.name())) pick = f;
            Card c = build(pick);
            if (c != null) cards.add(c);
        }
        return cards;
    }

    private static Card build(String[] f) {
        try {
            int code = Integer.parseInt(f[0]);
            String name = f[2], desc = f[3];
            if (code == 14) return new Burner(name, desc, null, null);
            if (code == 15) return new Saver(name, desc, null, null);
            int rank = Integer.parseInt(f[4]);
            Suit suit = Suit.valueOf(f[5]);
            switch (code) {
                case 1: return new Ace(name, desc, suit, null, null);
                case 4: return new Four(name, desc, suit, null, null);
                case 5: return new Five(name, desc, suit, null, null);
                case 7: return new Seven(name, desc, suit, null, null);
                case 10: return new Ten(name, desc, suit, null, null);
                case 11: return new Jack(name, desc, suit, null, null);
                case 12: return new Queen(name, desc, suit, null, null);
                case 13: return new King(name, desc, suit, null, null);
                default: return new Standard(name, desc, rank, suit, null, null);
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String[] parseCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    /** Balatro-style hover info for a card. */
    public static VBox cardTip(Card c) {
        Label name = Theme.label(Theme.prettyName(c), 25, Color.WHITE);
        Label desc = new Label(Theme.describe(c));
        desc.setFont(Theme.font(17));
        desc.setTextFill(Theme.INK);
        desc.setWrapText(true);
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setMaxWidth(220);
        StackPane box = new StackPane(desc);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setStyle("-fx-background-color: #f4f2ec; -fx-background-radius: 8;");

        boolean std = c instanceof Standard;
        Label tag = Theme.label(std ? Theme.suitName(((Standard) c).getSuit()) : "Wild", 15, Color.WHITE);
        StackPane tagBox = new StackPane(tag);
        tagBox.setPadding(new Insets(2, 14, 6, 14));
        tagBox.setStyle(Theme.panelCss(std ? Theme.suitColour(((Standard) c).getSuit()) : Theme.ORANGE, 8));
        tagBox.setMaxWidth(Region.USE_PREF_SIZE);

        VBox tip = new VBox(8, name, box, tagBox);
        tip.setAlignment(Pos.CENTER);
        tip.setPadding(new Insets(10, 12, 14, 12));
        tip.setPrefWidth(260);
        tip.setMouseTransparent(true);
        tip.setStyle("-fx-background-color: rgba(0,0,0,0.35), #c8d0d2, #2a3438;"
                + "-fx-background-insets: 5 0 -5 0, 0, 3; -fx-background-radius: 14, 14, 12;");
        return tip;
    }
}
