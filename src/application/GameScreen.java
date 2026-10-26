package application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import engine.Game;
import engine.board.Board;
import engine.board.Cell;
import engine.board.Moves;
import engine.board.SafeZone;
import exception.GameException;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import model.Colour;
import model.card.Card;
import model.card.Deck;
import model.card.standard.Ace;
import model.card.standard.Five;
import model.card.standard.Four;
import model.card.standard.Jack;
import model.card.standard.King;
import model.card.standard.Queen;
import model.card.standard.Seven;
import model.card.standard.Standard;
import model.card.standard.Ten;
import model.card.wild.Burner;
import model.card.wild.Saver;
import model.player.Marble;
import model.player.Player;

/**
 * The table. Left: Balatro-style info column. Middle: opponents (as "jokers"), the log, your hand and
 * the Play / Discard buttons. Right: the board. Drives the turn loop: you, then the three CPUs.
 */
public class GameScreen extends Pane {
    private static final double MID_X = 362, MID_W = 510, HAND_Y = 546, BOARD_X = 868, BOARD_Y = 92;

    private final Main app;
    private final Game game;
    private final BoardView board;
    private final Pane fx = new Pane();
    private final Pane handPane = new Pane();
    private final List<CardView> handViews = new ArrayList<>();
    private final OpponentView[] opponents = new OpponentView[4];
    private final int[] shownHands = new int[4];
    private final VBox logBox = new VBox(6);
    private final Label hint, handCount;
    private final StackPane playButton, discardButton;

    // sidebar
    private VBox headerFrame;
    private StackPane headerBanner;
    private Label headerTitle, roundLabel, cardStatus, splitLabel;
    private final StackPane[] orderChips = new StackPane[4];
    private final RaceRow[] raceRows = new RaceRow[4];
    private final VBox cardHead = new VBox(), optionsBox = new VBox(6);
    private HBox splitRow;
    private Card headCard;
    /** True when nothing in your hand can be played this turn (the Discard button pulses). */
    private boolean noMoves;

    // state
    private Card selectedCard;
    private CardView selectedView;
    private final List<Marble> selectedMarbles = new ArrayList<>();
    private int split = 3;
    private boolean busy = true, over;
    private int turnInRound, round = 1;
    /** Player whose finished turn is being animated (the model has already moved on), or -1. */
    private int presenting = -1;
    private Node tooltip;
    private StackPane overlay;
    private final List<Runnable> paused = new ArrayList<>();

    public GameScreen(Main app, Game game) {
        this.app = app;
        this.game = game;
        Theme.fixSize(this, Theme.W, Theme.H);

        board = new BoardView(game);
        board.relocate(BOARD_X, BOARD_Y);
        board.setOnMarbleClick(this::toggleMarble);
        board.setSelectable(this::canSelectMarble);

        hint = Theme.label("", 24, Color.WHITE);
        hint.setAlignment(Pos.CENTER);
        hint.setTextAlignment(TextAlignment.CENTER);
        hint.setPrefWidth(BoardView.SIZE);
        hint.relocate(BOARD_X, 36);

        handPane.relocate(MID_X, HAND_Y);
        Theme.fixSize(handPane, MID_W, 210);
        handCount = Theme.label("0/4", 18, Theme.MUTED);
        handCount.setAlignment(Pos.CENTER);
        handCount.setPrefWidth(MID_W);
        handCount.relocate(MID_X, 756);

        playButton = Theme.button("Play", Theme.BLUE, 210, 72, 30, this::playSelected);
        playButton.relocate(MID_X + 35, 796);
        discardButton = Theme.button("Discard", Theme.RED, 210, 72, 30, this::discardSelected);
        discardButton.relocate(MID_X + 265, 796);
        Theme.tick(discardButton, t -> {
            double s = noMoves && myTurn() ? 1 + 0.05 * Math.sin(t * 6) : 1;
            discardButton.setScaleX(s);
            discardButton.setScaleY(s);
        });

        fx.setMouseTransparent(true);
        Theme.fixSize(fx, Theme.W, Theme.H);

        getChildren().addAll(buildSidebar(), buildLog(), board, hint, buildLegend(), handPane, handCount, playButton, discardButton);
        for (int i = 1; i < 4; i++) {
            opponents[i] = new OpponentView(player(i));
            opponents[i].relocate(MID_X + (i - 1) * (OpponentView.W + 22.5), 22);
            getChildren().add(opponents[i]);
        }
        getChildren().add(fx);

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) clearSelection();
        });

        refreshAll();
        log(player(0).getColour(), "Welcome, " + player(0).getName() + "! Race all four marbles into your safe zone.");
        setHint("Shuffling...");
        later(500, () -> deal(this::beginTurn));
    }

    // ================================================================ layout

    private Region buildSidebar() {
        VBox side = new VBox(10);
        side.setPadding(new Insets(12));
        side.relocate(16, 16);
        Theme.fixSize(side, 330, 868);
        side.setStyle("-fx-background-color: rgba(0,0,0,0.35), #1c2629; -fx-background-insets: 6 0 -6 0, 0; -fx-background-radius: 16;");

        // whose turn it is, and the seating order around the table
        headerTitle = Theme.label("", 32, Color.WHITE);
        headerBanner = new StackPane(headerTitle);
        headerBanner.setPrefHeight(56);
        roundLabel = Theme.label("", 17, Color.web("#e6eeee"));
        HBox order = new HBox(9);
        order.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            if (i > 0) order.getChildren().add(Theme.label(">", 18, Color.web("#ffffff", 0.5)));
            orderChips[i] = orderChip(i);
            order.getChildren().add(orderChips[i]);
        }
        headerFrame = new VBox(8, headerBanner, roundLabel, order);
        headerFrame.setAlignment(Pos.TOP_CENTER);
        headerFrame.setPadding(new Insets(8, 8, 12, 8));

        // the race: where everyone's marbles are and who's ahead
        VBox race = new VBox(8, Theme.label("THE RACE", 17, Theme.MUTED));
        for (int i = 0; i < 4; i++) {
            raceRows[i] = new RaceRow(i);
            race.getChildren().add(raceRows[i]);
        }
        HBox legend = new HBox(14, legendPip(2, "safe"), legendPip(1, "on track"), legendPip(0, "home"));
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(2, 0, 0, 0));
        race.getChildren().add(legend);
        race.setPadding(new Insets(10, 12, 14, 12));
        race.setStyle(Theme.panelCss(Theme.BOX, 12));

        // the selected card and the ways it can be played
        splitLabel = Theme.label("", 20, Color.WHITE);
        splitRow = new HBox(12, Theme.button("<", Theme.BOX, 44, 38, 22, () -> changeSplit(-1)), splitLabel,
                Theme.button(">", Theme.BOX, 44, 38, 22, () -> changeSplit(1)));
        splitRow.setAlignment(Pos.CENTER);
        splitRow.managedProperty().bind(splitRow.visibleProperty());
        cardStatus = new Label();
        cardStatus.setFont(Theme.font(16));
        cardStatus.setTextFill(Theme.MUTED);
        cardStatus.setWrapText(true);
        cardStatus.setTextAlignment(TextAlignment.CENTER);
        cardStatus.setMaxWidth(280);
        VBox cardPanel = new VBox(10, Theme.label("YOUR CARD", 17, Theme.MUTED), cardHead, optionsBox, splitRow, cardStatus);
        cardPanel.setAlignment(Pos.TOP_CENTER);
        cardPanel.setPadding(new Insets(10, 12, 12, 12));
        cardPanel.setStyle(Theme.insetCss());
        VBox.setVgrow(cardPanel, Priority.ALWAYS);

        HBox buttons = new HBox(10, Theme.button("Rules", Theme.RED, 148, 62, 26, this::showRules),
                Theme.button("Menu", Theme.ORANGE, 148, 62, 26, this::showMenu));

        side.getChildren().addAll(headerFrame, race, cardPanel, buttons);
        return side;
    }

    private StackPane orderChip(int i) {
        Color c = Theme.of(player(i).getColour());
        Circle ring = new Circle(25, null);
        ring.setStroke(Color.WHITE);
        ring.setStrokeWidth(3);
        ring.setVisible(false);
        Circle body = new Circle(21, c);
        body.setStroke(c.deriveColor(0, 1, 0.55, 1));
        body.setStrokeWidth(3);
        Label l = Theme.label(i == 0 ? "YOU" : String.valueOf(i), i == 0 ? 14 : 20, Color.WHITE);
        StackPane chip = new StackPane(ring, body, l);
        Theme.fixSize(chip, 54, 54);
        return chip;
    }

    /** kind: 0 at home, 1 on the track, 2 in the safe zone. */
    private static Circle pip(Color c, int kind) {
        Circle p = new Circle(8);
        if (kind == 0) {
            p.setFill(Color.rgb(0, 0, 0, 0.3));
            p.setStroke(c.deriveColor(0, 1, 1, 0.5));
            p.setStrokeWidth(1.5);
            p.getStrokeDashArray().addAll(3.0, 3.0);
        } else {
            p.setFill(c);
            p.setStroke(kind == 2 ? Theme.GOLD : c.deriveColor(0, 1, 0.55, 1));
            p.setStrokeWidth(kind == 2 ? 3 : 2);
        }
        return p;
    }

    private static HBox legendPip(int kind, String text) {
        HBox h = new HBox(5, pip(Theme.MUTED, kind), Theme.label(text, 14, Theme.MUTED));
        h.setAlignment(Pos.CENTER);
        return h;
    }

    /** Rough share of the journey done: steps travelled from base, with each safe marble counting as a full lap. */
    private double progress(int pi) {
        Colour col = player(pi).getColour();
        double total = safeCount(pi) * 103.0;
        List<Cell> track = game.getBoard().getTrack();
        for (int i = 0; i < track.size(); i++) {
            Marble m = track.get(i).getMarble();
            if (m != null && m.getColour() == col) total += 1 + (i - pi * 25 + 100) % 100;
        }
        return Math.min(1, total / (4 * 103.0));
    }

    /** One player's line in the race: four marble pips (safe / on track / home) and a progress bar. */
    private final class RaceRow extends VBox {
        private final int pi;
        private final HBox pips = new HBox(5);
        private final Rectangle fill = new Rectangle(0, 6);

        RaceRow(int pi) {
            super(5);
            this.pi = pi;
            Color c = Theme.of(player(pi).getColour());
            Label name = Theme.label(pi == 0 ? "You" : player(pi).getName(), 19, c);
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            pips.setAlignment(Pos.CENTER);
            HBox top = new HBox(10, name, gap, pips);
            top.setAlignment(Pos.CENTER_LEFT);
            Rectangle bg = new Rectangle(282, 6, Color.rgb(0, 0, 0, 0.35));
            bg.setArcWidth(6);
            bg.setArcHeight(6);
            fill.setFill(c);
            fill.setArcWidth(6);
            fill.setArcHeight(6);
            StackPane bar = new StackPane(bg, fill);
            bar.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(top, bar);
        }

        void refresh() {
            Color c = Theme.of(player(pi).getColour());
            int safe = safeCount(pi), home = player(pi).getMarbles().size();
            pips.getChildren().clear();
            for (int k = 0; k < 4; k++) pips.getChildren().add(pip(c, k < safe ? 2 : k < 4 - home ? 1 : 0));
            new Timeline(new KeyFrame(Theme.ms(400), new KeyValue(fill.widthProperty(), 282 * progress(pi), Interpolator.EASE_BOTH))).play();
        }
    }

    private Region buildLog() {
        VBox panel = new VBox(8, Theme.label("TABLE LOG", 17, Theme.MUTED), logBox);
        panel.setPadding(new Insets(12, 16, 12, 16));
        panel.relocate(MID_X, 252);
        Theme.fixSize(panel, MID_W, 270);
        panel.setStyle("-fx-background-color: rgba(0,0,0,0.32); -fx-background-radius: 14;");
        Rectangle clip = new Rectangle(MID_W, 270);
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        panel.setClip(clip);
        return panel;
    }

    private Region buildLegend() {
        HBox legend = new HBox(26);
        legend.setAlignment(Pos.CENTER);
        legend.setPrefWidth(BoardView.SIZE);
        legend.relocate(BOARD_X, 830);

        Circle base = new Circle(9, Theme.of(player(0).getColour()).deriveColor(0, 1, 0.3, 0.6));
        base.setStroke(Theme.of(player(0).getColour()));
        base.setStrokeWidth(3.5);
        Circle entry = new Circle(8, Color.rgb(0, 0, 0, 0.35));
        entry.setStroke(Theme.of(player(0).getColour()));
        entry.setStrokeWidth(2);
        entry.getStrokeDashArray().addAll(3.0, 3.0);
        Rectangle safe = new Rectangle(18, 18, Theme.of(player(0).getColour()).deriveColor(0, 1, 1, 0.22));
        safe.setArcWidth(8);
        safe.setArcHeight(8);
        safe.setStroke(Theme.of(player(0).getColour()));
        safe.setStrokeWidth(2);
        Label traps = Theme.label("8 hidden traps", 17, Theme.MUTED);
        legend.getChildren().addAll(legendItem(base, "Base"), legendItem(entry, "Entry"), legendItem(safe, "Safe zone"), traps);
        return legend;
    }

    private static HBox legendItem(Node icon, String text) {
        HBox h = new HBox(8, icon, Theme.label(text, 17, Theme.MUTED));
        h.setAlignment(Pos.CENTER);
        return h;
    }

    // ================================================================ helpers

    private Player player(int i) {
        return game.getPlayers().get(i);
    }

    private int indexOf(Colour c) {
        for (int i = 0; i < 4; i++) if (player(i).getColour() == c) return i;
        return 0;
    }

    private int activeIndex() {
        return indexOf(game.getActivePlayerColour());
    }

    private int safeCount(int pi) {
        for (SafeZone z : game.getBoard().getSafeZones()) {
            if (z.getColour() != player(pi).getColour()) continue;
            int n = 0;
            for (Cell c : z.getCells()) if (c.getMarble() != null) n++;
            return n;
        }
        return 0;
    }

    private Point2D centerOf(Node n) {
        Bounds b = n.getLayoutBounds();
        return sceneToLocal(n.localToScene(b.getCenterX(), b.getCenterY()));
    }

    private Point2D toLocal(Node n, Point2D p) {
        return sceneToLocal(n.localToScene(p));
    }

    /** Runs {@code r} after a (speed-scaled) delay; held back while a menu is open. */
    private void later(double ms, Runnable r) {
        PauseTransition p = new PauseTransition(Theme.ms(ms));
        p.setOnFinished(e -> {
            if (overlay != null && !over) paused.add(r);
            else r.run();
        });
        p.play();
    }

    private void setHint(String text) {
        hint.setText(text);
    }

    private void log(Colour c, String text) {
        Circle dot = new Circle(6, c == null ? Theme.MUTED : Theme.of(c));
        Label l = new Label(text);
        l.setFont(Theme.font(17));
        l.setTextFill(Color.WHITE);
        l.setWrapText(true);
        l.setMaxWidth(450);
        HBox row = new HBox(10, dot, l);
        row.setAlignment(Pos.CENTER_LEFT);
        logBox.getChildren().add(row);
        while (logBox.getChildren().size() > 7) logBox.getChildren().remove(0);
        row.setOpacity(0);
        row.setTranslateX(-14);
        FadeTransition f = new FadeTransition(Theme.ms(220), row);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Theme.ms(220), row);
        t.setToX(0);
        f.play();
        t.play();
    }

    private void popOn(int pi, String text, Color colour) {
        Point2D p = pi == 0 ? new Point2D(MID_X + MID_W / 2, HAND_Y + 90) : centerOf(opponents[pi]);
        Theme.popText(fx, text, p.getX(), p.getY(), colour, 30);
    }

    // ================================================================ refresh

    private void refreshAll() {
        int idx = presenting >= 0 ? presenting : activeIndex();
        Player p = player(idx);
        Color c = Theme.of(p.getColour());
        headerTitle.setText(idx == 0 ? "YOUR TURN" : p.getName().toUpperCase() + "'S TURN");
        headerBanner.setStyle(Theme.panelCss(c, 10));
        headerFrame.setStyle("-fx-background-color: " + Theme.css(c.deriveColor(0, 0.8, 0.42, 1)) + "; -fx-background-radius: 12;");
        roundLabel.setText("Round " + round + "     Turn " + (turnInRound + 1) + " of 4");
        for (int i = 0; i < 4; i++) {
            StackPane chip = orderChips[i];
            chip.getChildren().get(0).setVisible(i == idx && !over);
            double s = i == idx ? 1.12 : 1;
            chip.setScaleX(s);
            chip.setScaleY(s);
            chip.setOpacity(i < idx ? 0.45 : 1);
            raceRows[i].refresh();
        }
        for (int i = 1; i < 4; i++) {
            opponents[i].refresh(shownHands[i], safeCount(i));
            opponents[i].setActive(i == idx && !over);
        }
        board.setCounts(game.getFirePit().size(), Deck.getPoolSize());
        handCount.setText(handViews.size() + "/4");
        updatePlayable();
        refreshInfo();
        refreshButtons();
    }

    // ================================================================ hints and move preview

    /** Every legal way to play a card right now (only meaningful on your turn). */
    private List<Moves.Play> legalPlays(Card c) {
        boolean othersHaveCards = false;
        for (int i = 1; i < 4; i++) othersHaveCards |= !player(i).getHand().isEmpty();
        return Moves.find(game.getBoard(), c, player(0).getColour(), player(0).getMarbles().size(),
                !player(1).getHand().isEmpty(), othersHaveCards);
    }

    /** Dims hand cards that can't be played legally this turn. */
    private void updatePlayable() {
        boolean mine = myTurn();
        for (CardView v : handViews) v.setPlayable(!mine || !legalPlays(v.getCard()).isEmpty());
    }

    /** Draws where the current selection would go and what it would knock out. Returns why it can't be played, or null. */
    private String updatePreview() {
        board.clearPreview();
        Card c = selectedCard;
        if (!myTurn() || c == null) return null;
        Board b = game.getBoard();
        Colour me = player(0).getColour();
        Color mine = Theme.of(me);
        int n = selectedMarbles.size();

        if (n == 0) {
            if (c instanceof Ace || c instanceof King) {
                if (player(0).getMarbles().isEmpty()) return "You have no marbles left at home to field.";
                if (!b.canField(me)) return "Your Base already holds one of your marbles.";
                Cell base = b.getTrack().get(b.getBaseIndex(me));
                board.previewGhost(base, mine);
                if (base.getMarble() != null) board.previewVictim(base.getMarble());
            } else if (c instanceof Ten && player(1).getHand().isEmpty()) {
                return player(1).getName() + " has no cards to discard.";
            }
            return null;
        }

        if (n == 1) {
            Marble m = selectedMarbles.get(0);
            if (c instanceof Burner) {
                if (m.getColour() == me) return "The Burner needs an opponent's marble.";
                if (!b.canBurn(m)) return "That marble is protected on its Base.";
                board.previewVictim(m);
                return null;
            }
            if (c instanceof Saver) {
                if (m.getColour() != me) return "The Saver needs one of your own marbles.";
                if (b.getPositionInPath(b.getTrack(), m) == -1) return "That marble is already in your safe zone.";
                for (Cell cell : b.getSafeZoneCells(me))
                    if (cell.getMarble() == null) board.previewGhost(cell, mine);
                return null;
            }
            if (!(c instanceof Standard)) return null;
            if (m.getColour() != me && !(c instanceof Five)) return "This card can only move your own marbles.";
            int steps = c instanceof Four ? -4 : ((Standard) c).getRank();
            boolean destroy = c instanceof King;
            List<Cell> path = b.previewPath(m, steps, destroy);
            if (path == null) {
                board.previewBlocked(m);
                return "That marble can't move " + Math.abs(steps) + (steps < 0 ? " back." : " from there.");
            }
            board.previewMove(path, Theme.of(m.getColour()));
            previewVictims(path, destroy, m);
            return null;
        }

        Marble a = selectedMarbles.get(0), z = selectedMarbles.get(1);
        if (c instanceof Jack) {
            if ((a.getColour() == me) == (z.getColour() == me)) return "Swap one of your marbles with someone else's.";
            if (!b.canSwap(a, z)) return "Those marbles can't be swapped (both must be on the track, not on a rival's Base).";
            board.previewGhost(b.getTrack().get(b.getPositionInPath(b.getTrack(), z)), Theme.of(a.getColour()));
            board.previewGhost(b.getTrack().get(b.getPositionInPath(b.getTrack(), a)), Theme.of(z.getColour()));
            return null;
        }
        if (c instanceof Seven) {
            if (a.getColour() != me || z.getColour() != me) return "Split the Seven between two of your own marbles.";
            if (!b.canSplit(a, split, z, 7 - split)) {
                board.previewBlocked(a);
                board.previewBlocked(z);
                return "They can't move " + split + " and " + (7 - split) + ". Try another split.";
            }
            List<Cell> first = b.previewPath(a, split, false), second = b.previewPath(z, 7 - split, false);
            board.previewMove(first, mine);
            previewVictims(first, false, a);
            if (second != null) {
                board.previewMove(second, mine);
                previewVictims(second, false, z);
            }
        }
        return null;
    }

    private void previewVictims(List<Cell> path, boolean destroy, Marble mover) {
        if (destroy) {
            for (int i = 1; i < path.size(); i++)
                if (path.get(i).getMarble() != null) board.previewVictim(path.get(i).getMarble());
        } else {
            Marble hit = path.get(path.size() - 1).getMarble();
            if (hit != null && hit != mover) board.previewVictim(hit);
        }
    }

    private boolean myTurn() {
        return !busy && !over && activeIndex() == 0;
    }

    private void refreshButtons() {
        boolean can = myTurn() && selectedCard != null;
        playButton.setDisable(!can);
        discardButton.setDisable(!can);
    }

    private void refreshInfo() {
        refreshCardPanel();
        String problem = updatePreview();
        if (problem != null) cardStatus.setText(problem);
    }

    private void refreshCardPanel() {
        Card c = selectedCard;
        if (c != headCard || cardHead.getChildren().isEmpty()) buildCardHead(c);
        optionsBox.getChildren().clear();
        splitRow.setVisible(false);
        if (over) {
            cardStatus.setText("Game over.");
            return;
        }
        if (c == null) {
            cardStatus.setText(myTurn() ? "Click a card in your hand to see the ways you can play it." : waitingText());
            return;
        }
        int n = selectedMarbles.size(), max = 0;
        boolean matched = false;
        List<Integer> needs = new ArrayList<>();
        for (Option o : options(c)) {
            boolean on = o.marbles == n;
            matched |= on;
            max = Math.max(max, o.marbles);
            if (o.marbles > 0) needs.add(o.marbles);
            optionsBox.getChildren().add(optionRow(o, on));
        }
        splitRow.setVisible(c instanceof Seven && n == 2);
        splitLabel.setText("Split   " + split + " + " + (7 - split));
        if (!myTurn()) cardStatus.setText(waitingText());
        else if (n == 0 && ownMarbleOnly(c) && mostAdvancedPlayable(c) == null)
            cardStatus.setText("None of your marbles can make this move. Discard it instead.");
        else if (matched) cardStatus.setText(n == 0 ? "Ready! Press Play, or pick a marble." : "Ready! Press Play.");
        else if (n > max) cardStatus.setText("Too many marbles selected. Right-click to clear.");
        else {
            StringBuilder sb = new StringBuilder("Select ");
            for (int i = 0; i < needs.size(); i++) sb.append(i > 0 ? " or " : "").append(needs.get(i));
            cardStatus.setText(sb + (max == 1 ? " marble" : " marbles") + " on the board.");
        }
    }

    private String waitingText() {
        int idx = presenting >= 0 ? presenting : activeIndex();
        return idx == 0 ? "Hold on..." : "Waiting for " + player(idx).getName() + "...";
    }

    private void buildCardHead(Card c) {
        headCard = c;
        cardHead.getChildren().clear();
        if (c == null) {
            CardView back = new CardView(null, 58, false);
            VBox text = new VBox(2, Theme.label("No card", 30, Color.WHITE), Theme.label("picked yet", 16, Theme.MUTED));
            text.setAlignment(Pos.CENTER_LEFT);
            HBox row = new HBox(14, back, text);
            row.setAlignment(Pos.CENTER_LEFT);
            cardHead.setAlignment(Pos.CENTER_LEFT);
            cardHead.getChildren().add(row);
            return;
        }
        CardView cv = new CardView(c, 58, true);
        boolean std = c instanceof Standard;
        Label name = Theme.label(Theme.prettyName(c), 30, Color.WHITE);
        Label tag = Theme.label(std ? "of " + Theme.suitName(((Standard) c).getSuit()) : "Wild card", 16,
                std ? Theme.suitColour(((Standard) c).getSuit()).interpolate(Color.WHITE, 0.45) : Theme.ORANGE);
        VBox text = new VBox(2, name, tag);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(14, cv, text);
        row.setAlignment(Pos.CENTER_LEFT);
        cardHead.setAlignment(Pos.CENTER_LEFT);
        cardHead.getChildren().add(row);
    }

    /** One way of playing a card: how many marbles to select, and what happens. */
    private static final class Option {
        final int marbles;
        final String text;

        Option(int marbles, String text) {
            this.marbles = marbles;
            this.text = text;
        }
    }

    private static List<Option> options(Card c) {
        List<Option> o = new ArrayList<>();
        if (c instanceof Burner) o.add(new Option(1, "Send an opponent's marble home"));
        else if (c instanceof Saver) o.add(new Option(1, "Jump your marble into your safe zone"));
        else if (c instanceof Ace) {
            o.add(new Option(0, "Field a marble from home"));
            o.add(new Option(1, "Move your marble 1"));
        } else if (c instanceof King) {
            o.add(new Option(0, "Field a marble from home"));
            o.add(new Option(1, "Move 13, knocking out every marble passed"));
        } else if (c instanceof Queen) {
            o.add(new Option(0, "A random player discards and skips"));
            o.add(new Option(1, "Move your marble 12"));
        } else if (c instanceof Ten) {
            o.add(new Option(0, "Next player discards and skips"));
            o.add(new Option(1, "Move your marble 10"));
        } else if (c instanceof Jack) {
            o.add(new Option(1, "Move your marble 11"));
            o.add(new Option(2, "Swap your marble with any other"));
        } else if (c instanceof Seven) {
            o.add(new Option(1, "Move your marble 7"));
            o.add(new Option(2, "Split the 7 between two marbles"));
        } else if (c instanceof Five) o.add(new Option(1, "Move ANY marble 5"));
        else if (c instanceof Four) o.add(new Option(1, "Move your marble 4 backwards"));
        else o.add(new Option(1, "Move your marble " + ((Standard) c).getRank()));
        return o;
    }

    private static HBox optionRow(Option o, boolean on) {
        HBox dots = new HBox(4);
        dots.setAlignment(Pos.CENTER);
        if (o.marbles == 0) dots.getChildren().add(Theme.label("none", 13, on ? Color.WHITE : Theme.MUTED));
        for (int k = 0; k < o.marbles; k++) dots.getChildren().add(new Circle(6, on ? Color.WHITE : Theme.MUTED));
        StackPane badge = new StackPane(dots);
        Theme.fixSize(badge, 52, 30);
        badge.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 7;");
        Label l = new Label(o.text);
        l.setFont(Theme.font(17));
        l.setTextFill(on ? Color.WHITE : Theme.MUTED);
        l.setWrapText(true);
        l.setMaxWidth(200);
        HBox row = new HBox(10, badge, l);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 8, 8));
        row.setStyle(on ? Theme.panelCss(Theme.BLUE, 9) : "-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 9;");
        return row;
    }

    // ================================================================ hand

    private double[] slot(int i, int n) {
        double spacing = n > 1 ? Math.min(126, (MID_W - CardView.BASE_W) / (n - 1)) : 0;
        double off = i - (n - 1) / 2.0;
        return new double[] { MID_W / 2 + off * spacing - CardView.BASE_W / 2, 22 + off * off * 5, off * 4.5 };
    }

    private void layoutHand() {
        int n = handViews.size();
        for (int i = 0; i < n; i++) {
            CardView v = handViews.get(i);
            double[] s = slot(i, n);
            v.setBaseRotate(s[2]);
            new Timeline(new KeyFrame(Theme.ms(220),
                    new KeyValue(v.layoutXProperty(), s[0], Interpolator.EASE_BOTH),
                    new KeyValue(v.layoutYProperty(), s[1], Interpolator.EASE_BOTH))).play();
        }
        handCount.setText(n + "/4");
    }

    private CardView makeHandCard(Card c) {
        CardView v = new CardView(c, CardView.BASE_W, true);
        v.setCursor(Cursor.HAND);
        v.setOnMouseEntered(e -> {
            v.hovered = true;
            updateLift(v);
            showTooltip(v);
        });
        v.setOnMouseExited(e -> {
            v.hovered = false;
            updateLift(v);
            hideTooltip();
        });
        v.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) toggleCard(v);
        });
        return v;
    }

    private void updateLift(CardView v) {
        double y = (v == selectedView ? -30 : 0) + (v.hovered ? -8 : 0);
        double s = v.hovered ? 1.05 : 1;
        new Timeline(new KeyFrame(Theme.ms(120),
                new KeyValue(v.translateYProperty(), y, Interpolator.EASE_OUT),
                new KeyValue(v.scaleXProperty(), s, Interpolator.EASE_OUT),
                new KeyValue(v.scaleYProperty(), s, Interpolator.EASE_OUT))).play();
    }

    private void showTooltip(CardView v) {
        hideTooltip();
        if (v.getCard() == null) return;
        VBox tip = Overlays.cardTip(v.getCard());
        if (!v.isPlayable()) tip.getChildren().add(Theme.label("No legal move right now", 16, Theme.RED));
        fx.getChildren().add(tip);
        tip.applyCss();
        tip.autosize();
        Point2D top = toLocal(v, new Point2D(v.getW() / 2, 0));
        double x = Math.max(8, Math.min(Theme.W - tip.getWidth() - 8, top.getX() - tip.getWidth() / 2));
        tip.relocate(x, top.getY() - tip.getHeight() - 22);
        tip.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(120), tip);
        f.setToValue(1);
        f.play();
        tooltip = tip;
    }

    private void hideTooltip() {
        if (tooltip != null) fx.getChildren().remove(tooltip);
        tooltip = null;
    }

    private void toggleCard(CardView v) {
        if (!myTurn()) return;
        CardView old = selectedView;
        if (selectedView == v) {
            selectedView = null;
            selectedCard = null;
        } else {
            selectedView = v;
            selectedCard = v.getCard();
            split = 3;
            Sound.play(Sound.Fx.SELECT);
            if (ownMarbleOnly(selectedCard)) autoPickMarble();
        }
        if (old != null) updateLift(old);
        updateLift(v);
        board.setSelection(selectedMarbles, selectedCard instanceof Seven);
        refreshInfo();
        refreshButtons();
    }

    /** Cards whose only use is acting on one of your own marbles (no fielding, discarding, swapping or splitting). */
    private static boolean ownMarbleOnly(Card c) {
        if (c instanceof Saver) return true;
        return c instanceof Standard && !(c instanceof Ace || c instanceof King || c instanceof Queen
                || c instanceof Ten || c instanceof Jack || c instanceof Seven || c instanceof Five);
    }

    private boolean canPlayOn(Card c, Marble m) {
        BoardView.Loc l = board.locationOf(m);
        if (l == null || l.kind == BoardView.HOME) return false;
        if (c instanceof Saver) return l.kind == BoardView.TRACK;
        int steps = c instanceof Four ? -4 : ((Standard) c).getRank();
        return game.getBoard().canMoveBy(m, steps, false);
    }

    /** Your marble furthest along its journey that can legally take this card, or null. */
    private Marble mostAdvancedPlayable(Card c) {
        Marble best = null;
        int bestScore = -1;
        for (Map.Entry<Marble, BoardView.Loc> e : board.snapshot().entrySet()) {
            Marble m = e.getKey();
            BoardView.Loc l = e.getValue();
            if (m.getColour() != player(0).getColour() || l.kind == BoardView.HOME) continue;
            int score = l.kind == BoardView.SAFE ? 100 + l.b : l.a; // our base is track cell 0
            if (score > bestScore && canPlayOn(c, m)) {
                best = m;
                bestScore = score;
            }
        }
        return best;
    }

    /** Keeps a single marble you already picked if it works with the card, otherwise picks the most advanced one. */
    private void autoPickMarble() {
        if (selectedMarbles.size() == 1 && selectedMarbles.get(0).getColour() == player(0).getColour()
                && canPlayOn(selectedCard, selectedMarbles.get(0))) return;
        selectedMarbles.clear();
        Marble best = mostAdvancedPlayable(selectedCard);
        if (best != null) selectedMarbles.add(best);
    }

    private boolean canSelectMarble(Marble m) {
        if (!myTurn()) return false;
        BoardView.Loc l = board.locationOf(m);
        return l != null && (l.kind == BoardView.TRACK || (l.kind == BoardView.SAFE && l.a == 0));
    }

    private void toggleMarble(Marble m) {
        if (!myTurn()) return;
        Sound.play(Sound.Fx.SELECT, 0.7);
        if (!selectedMarbles.remove(m)) {
            if (selectedMarbles.size() == 2) selectedMarbles.remove(0);
            selectedMarbles.add(m);
        }
        board.setSelection(selectedMarbles, selectedCard instanceof Seven);
        refreshInfo();
    }

    private void changeSplit(int d) {
        split = Math.max(1, Math.min(6, split + d));
        refreshInfo();
    }

    private void clearSelection() {
        CardView old = selectedView;
        selectedView = null;
        selectedCard = null;
        selectedMarbles.clear();
        if (old != null) updateLift(old);
        board.setSelection(selectedMarbles, false);
        refreshInfo();
        refreshButtons();
    }

    private void showError(String msg) {
        Sound.play(Sound.Fx.ERROR);
        showToast(msg, Theme.RED);
        if (selectedView != null) {
            TranslateTransition shake = new TranslateTransition(Duration.millis(50), selectedView);
            shake.setByX(7);
            shake.setAutoReverse(true);
            shake.setCycleCount(6);
            shake.play();
        }
    }

    private void showToast(String msg, Color colour) {
        Label l = Theme.label(msg, 19, Color.WHITE);
        l.setWrapText(true);
        l.setMaxWidth(320);
        l.setTextAlignment(TextAlignment.CENTER);
        StackPane p = new StackPane(l);
        p.setPadding(new Insets(10, 16, 14, 16));
        p.setStyle(Theme.panelCss(colour, 12));
        fx.getChildren().add(p);
        p.applyCss();
        p.autosize();
        p.relocate(MID_X + MID_W / 2 - p.getWidth() / 2, HAND_Y - p.getHeight() - 20);
        p.setScaleX(0.6);
        p.setScaleY(0.6);
        ScaleTransition pop = new ScaleTransition(Duration.millis(160), p);
        pop.setToX(1);
        pop.setToY(1);
        pop.play();
        FadeTransition f = new FadeTransition(Duration.millis(400), p);
        f.setDelay(Duration.millis(2200));
        f.setToValue(0);
        f.setOnFinished(e -> fx.getChildren().remove(p));
        f.play();
    }

    // ================================================================ turn loop

    private static final class Snapshot {
        Map<Marble, BoardView.Loc> locs;
        final List<List<Card>> hands = new ArrayList<>();
    }

    private Snapshot snap() {
        Snapshot s = new Snapshot();
        s.locs = board.snapshot();
        for (int i = 0; i < 4; i++) s.hands.add(new ArrayList<>(player(i).getHand()));
        return s;
    }

    private void beginTurn() {
        if (over) return;
        Colour win = game.checkWin();
        if (win != null) {
            gameOver(win);
            return;
        }
        int idx = activeIndex();
        Player p = player(idx);
        if (!game.canPlayTurn()) {
            busy = true;
            refreshAll();
            log(p.getColour(), (idx == 0 ? "You are" : p.getName() + " is") + " skipped this turn.");
            popOn(idx, "SKIPPED!", Theme.RED);
            setHint(idx == 0 ? "You lost a card, so you skip this turn." : p.getName() + " skips a turn.");
            later(1000, () -> {
                game.deselectAll();
                boolean newRound = endTurn();
                afterTurn(newRound);
            });
            return;
        }
        if (idx == 0) {
            busy = false;
            refreshAll();
            noMoves = !handViews.isEmpty() && handViews.stream().noneMatch(v -> v.isPlayable());
            if (noMoves) {
                setHint("No legal moves. Pick a card to discard.");
                showToast("No moves this turn! Pick a card to discard.", Theme.ORANGE);
            } else {
                setHint("Your turn! Pick a card, then marbles.");
            }
        } else {
            busy = true;
            setHint(p.getName() + " is thinking...");
            refreshAll();
            later(650, () -> cpuTurn(idx));
        }
    }

    /** Ends the model's turn. Returns true when a new round (fresh hands) has started. */
    private boolean endTurn() {
        game.endPlayerTurn();
        if (activeIndex() == 0) {
            turnInRound = (turnInRound + 1) % 4;
            if (turnInRound == 0) {
                round++;
                return true;
            }
        }
        return false;
    }

    private void afterTurn(boolean newRound) {
        presenting = -1;
        refreshAll();
        if (newRound) {
            log(null, "Round " + round + ": fresh cards for everyone.");
            later(300, () -> deal(this::beginTurn));
        } else {
            later(300, this::beginTurn);
        }
    }

    private void cpuTurn(int idx) {
        Player p = player(idx);
        Snapshot before = snap();
        try {
            game.playPlayerTurn();
        } catch (GameException | RuntimeException ignored) {
            // the CPU couldn't act; it still burns its selected card
        }
        Card card = p.getSelectedCard();
        if (card == null && !p.getHand().isEmpty()) {
            try {
                game.selectCard(p.getHand().get(0));
            } catch (GameException ignored) {
            }
            card = p.getSelectedCard();
        }
        Snapshot after = snap();
        boolean newRound = endTurn();
        present(idx, card, before, after, null, false, newRound);
    }

    private void playSelected() {
        if (!myTurn() || selectedCard == null) return;
        Snapshot before = snap();
        try {
            game.deselectAll();
            game.selectCard(selectedCard);
            for (Marble m : selectedMarbles) game.selectMarble(m);
            if (selectedCard instanceof Seven && selectedMarbles.size() == 2) game.editSplitDistance(split);
            game.playPlayerTurn();
        } catch (GameException | RuntimeException ex) {
            // only a rejected move if nothing changed (a split Seven can fail halfway through)
            if (board.snapshot().equals(before.locs)) {
                String msg = ex.getMessage();
                showError(msg == null || msg.isEmpty() ? "That move isn't allowed." : msg);
                return;
            }
        }
        commitHuman(before, false);
    }

    private void discardSelected() {
        if (!myTurn() || selectedCard == null) return;
        commitHuman(snap(), true);
    }

    private void commitHuman(Snapshot before, boolean discarded) {
        noMoves = false;
        busy = true;
        Card card = selectedCard;
        CardView view = selectedView;
        hideTooltip();
        game.deselectAll();
        try {
            game.selectCard(card); // endPlayerTurn burns the selected card
        } catch (GameException ignored) {
        }
        Snapshot after = snap();
        selectedView = null;
        selectedCard = null;
        selectedMarbles.clear();
        board.setSelection(selectedMarbles, false);
        handViews.remove(view);
        boolean newRound = endTurn();
        present(0, card, before, after, view, discarded, newRound);
    }

    /** Plays back a finished turn: card to the fire pit, discards, marble moves, log lines. */
    private void present(int idx, Card card, Snapshot before, Snapshot after, CardView source, boolean discarded, boolean newRound) {
        Player p = player(idx);
        Colour mover = p.getColour();
        presenting = idx;
        for (int j = 1; j < 4; j++) shownHands[j] = after.hands.get(j).size();
        refreshAll();
        setHint(idx == 0 ? "" : p.getName() + " plays...");

        Runnable rest = () -> {
            String who = idx == 0 ? "You" : p.getName();
            boolean othersLost = false;
            for (int j = 0; j < 4; j++) if (j != idx && before.hands.get(j).size() != after.hands.get(j).size()) othersLost = true;
            boolean nothing = before.locs.equals(after.locs) && !othersLost;
            if (card == null) log(mover, who + " had nothing to play.");
            else if (discarded || nothing) log(mover, who + " discarded the " + Theme.fullName(card) + ".");
            else log(mover, who + " played the " + Theme.fullName(card) + ".");

            for (Map.Entry<Marble, BoardView.Loc> e : after.locs.entrySet()) {
                BoardView.Loc a = before.locs.get(e.getKey()), b = e.getValue();
                if (a.kind != BoardView.HOME && b.kind == BoardView.HOME) {
                    int owner = indexOf(e.getKey().getColour());
                    log(e.getKey().getColour(), (owner == 0 ? "Your" : player(owner).getName() + "'s") + " marble was sent home!");
                }
                if (a.kind != BoardView.SAFE && b.kind == BoardView.SAFE) {
                    int owner = indexOf(e.getKey().getColour());
                    log(e.getKey().getColour(), (owner == 0 ? "Your" : player(owner).getName() + "'s") + " marble reached the safe zone.");
                }
            }
            handleDiscards(idx, before, after);
            board.animate(before.locs, after.locs, card, mover, () -> afterTurn(newRound));
        };
        if (card == null) rest.run();
        else flyToPit(card, idx, source, rest);
    }

    private void handleDiscards(int idx, Snapshot before, Snapshot after) {
        for (int j = 0; j < 4; j++) {
            if (j == idx) continue;
            List<Card> lost = new ArrayList<>(before.hands.get(j));
            lost.removeAll(after.hands.get(j));
            for (Card c : lost) {
                popOn(j, "-1 CARD", Theme.RED);
                log(player(j).getColour(), (j == 0 ? "You" : player(j).getName()) + " lost the " + Theme.fullName(c) + ".");
                if (j != 0) {
                    board.pushPit(c, (Math.random() - 0.5) * 30);
                    continue;
                }
                for (CardView v : new ArrayList<>(handViews)) {
                    if (v.getCard() != c) continue;
                    handViews.remove(v);
                    if (v == selectedView) clearSelection();
                    flyToPit(c, 0, v, () -> {});
                }
            }
        }
    }

    private void flyToPit(Card card, int idx, CardView source, Runnable done) {
        Point2D to = toLocal(board, board.pitCenter());
        double rot = (Math.random() - 0.5) * 18;
        double s1 = BoardView.PIT_W / CardView.BASE_W;
        if (source != null) {
            Point2D from = centerOf(source);
            double r0 = source.getRotate(), s0 = source.getScaleX();
            handPane.getChildren().remove(source);
            layoutHand();
            fly(new CardView(card, CardView.BASE_W, true), from, to, s0, s1, r0, rot, 380, false, () -> {
                board.pushPit(card, rot);
                done.run();
            });
        } else {
            fly(new CardView(card, CardView.BASE_W, false), centerOf(opponents[idx]), to, 0.35, s1, 0, rot, 480, true, () -> {
                board.pushPit(card, rot);
                done.run();
            });
        }
    }

    private void fly(CardView f, Point2D from, Point2D to, double s0, double s1, double r0, double r1, double ms, boolean flip, Runnable done) {
        Sound.play(f.isFaceUp() ? Sound.Fx.CARD : Sound.Fx.DEAL, 0.8);
        f.setSway(false);
        f.setMouseTransparent(true);
        fx.getChildren().add(f);
        f.relocate(from.getX() - f.getW() / 2, from.getY() - f.getH() / 2);
        f.setScaleX(s0);
        f.setScaleY(s0);
        f.setRotate(r0);
        Duration d = Theme.ms(ms);
        Timeline tl = new Timeline(new KeyFrame(d,
                new KeyValue(f.translateXProperty(), to.getX() - from.getX(), Interpolator.EASE_BOTH),
                new KeyValue(f.translateYProperty(), to.getY() - from.getY(), Interpolator.EASE_BOTH),
                new KeyValue(f.scaleYProperty(), s1, Interpolator.EASE_BOTH),
                new KeyValue(f.rotateProperty(), r1, Interpolator.EASE_BOTH)));
        if (flip) {
            tl.getKeyFrames().add(new KeyFrame(d.multiply(0.5), e -> f.setFaceUp(!f.isFaceUp()),
                    new KeyValue(f.scaleXProperty(), 0.0, Interpolator.EASE_IN)));
            tl.getKeyFrames().add(new KeyFrame(d, new KeyValue(f.scaleXProperty(), s1, Interpolator.EASE_OUT)));
        } else {
            tl.getKeyFrames().add(new KeyFrame(d, new KeyValue(f.scaleXProperty(), s1, Interpolator.EASE_BOTH)));
        }
        tl.setOnFinished(e -> {
            fx.getChildren().remove(f);
            if (done != null) done.run();
        });
        tl.play();
    }

    private void deal(Runnable done) {
        busy = true;
        for (CardView v : handViews) handPane.getChildren().remove(v);
        handViews.clear();
        refreshAll();
        setHint("Dealing round " + round + "...");
        List<Card> hand = new ArrayList<>(player(0).getHand());
        Point2D deck = toLocal(board, board.deckCenter());
        int n = hand.size();
        for (int i = 0; i < n; i++) {
            CardView v = makeHandCard(hand.get(i));
            double[] s = slot(i, n);
            v.setBaseRotate(s[2]);
            v.relocate(s[0], s[1]);
            handViews.add(v);
            Point2D target = new Point2D(MID_X + s[0] + CardView.BASE_W / 2, HAND_Y + s[1] + v.getH() / 2);
            CardView flyer = new CardView(hand.get(i), CardView.BASE_W, false);
            later(i * 130, () -> fly(flyer, deck, target, 62 / CardView.BASE_W, 1, 0, s[2], 420, true,
                    () -> handPane.getChildren().add(v)));
        }
        for (int j = 1; j < 4; j++) {
            int jj = j;
            later(120 + j * 110, () -> {
                shownHands[jj] = player(jj).getHand().size();
                opponents[jj].refresh(shownHands[jj], safeCount(jj));
            });
        }
        later(n * 130 + 560, () -> {
            refreshAll();
            done.run();
        });
    }

    // ================================================================ overlays

    private void openOverlay(VBox panel, boolean dismissable) {
        closeOverlay(false);
        hideTooltip();
        overlay = Overlays.wrap(panel, dismissable ? () -> closeOverlay(true) : null);
        getChildren().add(overlay);
    }

    private void closeOverlay(boolean resume) {
        if (overlay == null) return;
        getChildren().remove(overlay);
        overlay = null;
        if (resume) {
            List<Runnable> rs = new ArrayList<>(paused);
            paused.clear();
            for (Runnable r : rs) r.run();
        }
    }

    private void showRules() {
        openOverlay(Overlays.rules(() -> closeOverlay(true)), true);
    }

    private void showMenu() {
        if (over) return;
        VBox p = Overlays.panel("OPTIONS", 440);
        StackPane[] speed = new StackPane[1];
        speed[0] = Theme.button(speedText(), Theme.ORANGE, 320, 64, 26, () -> {
            Theme.speed = Theme.speed < 1 ? 1.0 : 0.5;
            Theme.setButtonText(speed[0], speedText());
        });
        StackPane[] sound = new StackPane[1];
        sound[0] = Theme.button(soundText(), Theme.ORANGE, 320, 64, 26, () -> {
            Sound.enabled = !Sound.enabled;
            Theme.setButtonText(sound[0], soundText());
        });
        p.getChildren().addAll(
                Theme.button("Resume", Theme.BLUE, 320, 64, 26, () -> closeOverlay(true)),
                speed[0],
                sound[0],
                Theme.button("New game", Theme.GREEN, 320, 64, 26, () -> app.newGame(player(0).getName())),
                Theme.button("Main menu", Theme.RED, 320, 64, 26, app::showTitle));
        openOverlay(p, true);
    }

    private static String speedText() {
        return Theme.speed < 1 ? "Speed: Fast" : "Speed: Normal";
    }

    private static String soundText() {
        return Sound.enabled ? "Sound: On" : "Sound: Off";
    }

    private void gameOver(Colour winner) {
        over = true;
        busy = true;
        int wi = indexOf(winner);
        presenting = wi;
        refreshAll();
        boolean won = wi == 0;
        Sound.play(won ? Sound.Fx.WIN : Sound.Fx.LOSE);
        setHint(won ? "You win!" : player(wi).getName() + " wins.");
        VBox p = Overlays.panel(won ? "YOU WIN!" : player(wi).getName().toUpperCase() + " WINS", 620);
        ((Label) p.getChildren().get(0)).setTextFill(won ? Theme.GOLD : Theme.of(winner));
        p.getChildren().add(Theme.label(won ? "All four marbles safe and sound." : "So close. Shuffle up and try again?", 22, Theme.MUTED));
        VBox rows = new VBox(8);
        rows.setPadding(new Insets(14));
        rows.setStyle(Theme.insetCss());
        for (int i = 0; i < 4; i++) {
            Label name = Theme.label(i == 0 ? player(0).getName() + " (you)" : player(i).getName(), 22, Color.WHITE);
            name.setMinWidth(260);
            HBox row = new HBox(14, new Circle(9, Theme.of(player(i).getColour())), name,
                    Theme.label("Safe " + safeCount(i) + "/4", 22, Theme.GOLD));
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
        p.getChildren().add(rows);
        p.getChildren().add(Theme.label("Finished in round " + round, 18, Theme.MUTED));
        HBox buttons = new HBox(16, Theme.button("Play again", Theme.BLUE, 230, 66, 26, () -> app.newGame(player(0).getName())),
                Theme.button("Main menu", Theme.RED, 230, 66, 26, app::showTitle));
        buttons.setAlignment(Pos.CENTER);
        p.getChildren().add(buttons);
        openOverlay(p, false);
        Color c = won ? Theme.GOLD : Theme.of(winner);
        // a little celebration on either side of the panel
        String[] bits = { "+1", "*", "+", "*" };
        for (int i = 0; i < 16; i++) {
            PauseTransition t = new PauseTransition(Duration.millis(i * 130));
            double x = (i % 2 == 0 ? 120 : 1180) + Math.random() * 300;
            t.setOnFinished(e -> Theme.popText(overlay != null ? overlay : fx, bits[(int) (Math.random() * bits.length)],
                    x, 140 + Math.random() * 620, c, 44));
            t.play();
        }
    }

    // ================================================================ keys

    public void handleKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.ESCAPE) {
            if (overlay != null) {
                if (!over) closeOverlay(true);
            } else {
                showMenu();
            }
            return;
        }
        if (overlay != null) return;
        if (k == KeyCode.ENTER || k == KeyCode.SPACE) playSelected();
        else if (k == KeyCode.D || k == KeyCode.DELETE) discardSelected();
        else if (k == KeyCode.R) showRules();
        else if (k.isDigitKey()) {
            int i = e.getText().isEmpty() ? -1 : e.getText().charAt(0) - '1';
            if (i >= 0 && i < handViews.size()) toggleCard(handViews.get(i));
        }
    }
}
