package application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import engine.Game;
import engine.board.Cell;
import engine.board.CellType;
import engine.board.SafeZone;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import model.Colour;
import model.card.Card;
import model.card.standard.Four;
import model.card.standard.Seven;
import model.card.standard.Standard;
import model.card.wild.Burner;
import model.card.wild.Saver;
import model.player.Marble;
import model.player.Player;

/**
 * The 100-cell track drawn as a square ring. Player i's Base cell (track index i*25) sits in the
 * middle of side i (bottom, right, top, left), its Entry two cells before it, and its Safe Zone
 * branching inward from the Entry. The fire pit and the deck sit in the middle of the board.
 */
public class BoardView extends Pane {
    public static final double SIZE = 720, PIT_W = 92;
    private static final double D = 25, M = (SIZE - 25 * D) / 2;
    private static final double[][] DIR = { { 1, 0 }, { 0, -1 }, { -1, 0 }, { 0, 1 } };
    private static final double[][] IN = { { 0, -1 }, { -1, 0 }, { 0, 1 }, { 1, 0 } };
    private static final Point2D PIT = new Point2D(315, 352), DECK = new Point2D(428, 352);

    public static final int TRACK = 0, SAFE = 1, HOME = 2;

    /** Where a marble is: on the track (a = index), in a safe zone (a = player, b = cell) or at home (a = player, b = slot). */
    public static final class Loc {
        final int kind, a, b;

        Loc(int kind, int a, int b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Loc)) return false;
            Loc l = (Loc) o;
            return l.kind == kind && l.a == a && l.b == b;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, a, b);
        }
    }

    private final Game game;
    private final Pane cells = new Pane(), trail = new Pane(), pit = new Pane(), preview = new Pane(), marbles = new Pane(),
            previewTop = new Pane(), fx = new Pane();
    private final Map<Marble, MarbleNode> nodes = new HashMap<>();
    private Map<Marble, Loc> current = new HashMap<>();
    private final Label pitLabel, deckLabel;
    private Consumer<Marble> onClick = m -> {};
    private Predicate<Marble> selectable = m -> false;
    private List<Marble> selected = new ArrayList<>();

    public BoardView(Game game) {
        this.game = game;
        Theme.fixSize(this, SIZE, SIZE);
        setStyle("-fx-background-color: rgba(0,0,0,0.30), rgba(8,26,20,0.55);"
                + "-fx-background-insets: 6 0 -6 0, 0; -fx-background-radius: 28;");
        for (Pane layer : new Pane[] { cells, trail, pit, preview, marbles, previewTop, fx }) Theme.fixSize(layer, SIZE, SIZE);
        for (Pane layer : new Pane[] { cells, trail, pit, preview, previewTop, fx }) layer.setMouseTransparent(true);

        drawStatic();
        pitLabel = centeredLabel(PIT.getX(), PIT.getY() + 84);
        deckLabel = centeredLabel(DECK.getX(), PIT.getY() + 84);
        drawDeck();
        getChildren().addAll(cells, trail, pit, preview, marbles, previewTop, fx);

        for (Marble m : snapshot().keySet()) {
            MarbleNode n = new MarbleNode(m);
            nodes.put(m, n);
            marbles.getChildren().add(n);
        }
        sync();
        Theme.tick(this, t -> {
            for (MarbleNode n : nodes.values()) {
                n.ring.setOpacity(0.85 + 0.15 * Math.sin(t * 7));
                n.ring.setRadius(15.5 + Math.sin(t * 7) * 1.5);
            }
            preview.setOpacity(0.6 + 0.4 * Math.sin(t * 5));
            previewTop.setOpacity(0.75 + 0.25 * Math.sin(t * 6));
        });
    }

    // ---------------------------------------------------------------- geometry

    private static Point2D corner(int side) {
        double lo = M, hi = M + 25 * D;
        switch (side) {
            case 0: return new Point2D(lo, hi);
            case 1: return new Point2D(hi, hi);
            case 2: return new Point2D(hi, lo);
            default: return new Point2D(lo, lo);
        }
    }

    private static Point2D sidePoint(int side, double k, double inward) {
        Point2D c = corner(side);
        return new Point2D(c.getX() + (DIR[side][0] * k + IN[side][0] * inward) * D,
                c.getY() + (DIR[side][1] * k + IN[side][1] * inward) * D);
    }

    private static Point2D trackPos(int i) {
        int p = (i + 12) % 100;
        return sidePoint(p / 25, p % 25, 0);
    }

    private static Point2D safePos(int player, int j) {
        return sidePoint(player, 10, j + 1.15);
    }

    private static Point2D homePos(int player, int slot) {
        return sidePoint(player, 15 + (slot % 2) * 1.2, 1.9 + (slot / 2) * 1.2);
    }

    private static Point2D pos(Loc l) {
        if (l.kind == TRACK) return trackPos(l.a);
        if (l.kind == SAFE) return safePos(l.a, l.b);
        return homePos(l.a, l.b);
    }

    public Point2D pitCenter() { return PIT; }
    public Point2D deckCenter() { return DECK; }

    private int playerIndex(Colour c) {
        List<Player> ps = game.getPlayers();
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).getColour() == c) return i;
        return 0;
    }

    // ---------------------------------------------------------------- static drawing

    private Label centeredLabel(double cx, double cy) {
        Label l = Theme.label("", 15, Theme.MUTED);
        l.setAlignment(Pos.CENTER);
        l.setPrefWidth(120);
        l.relocate(cx - 60, cy - 10);
        l.setMouseTransparent(true);
        getChildren().add(l);
        return l;
    }

    private void drawStatic() {
        Rectangle groove = new Rectangle(M, M, 25 * D, 25 * D);
        groove.setArcWidth(26);
        groove.setArcHeight(26);
        groove.setFill(null);
        groove.setStroke(Color.rgb(0, 0, 0, 0.28));
        groove.setStrokeWidth(28);
        cells.getChildren().add(groove);

        List<Cell> track = game.getBoard().getTrack();
        for (int i = 0; i < track.size(); i++) {
            Point2D p = trackPos(i);
            Circle c = new Circle(p.getX(), p.getY(), 8.5);
            CellType type = track.get(i).getCellType();
            if (type == CellType.BASE) {
                Color col = Theme.of(game.getPlayers().get(i / 25).getColour());
                c.setRadius(10.5);
                c.setFill(col.deriveColor(0, 1, 0.3, 0.6));
                c.setStroke(col);
                c.setStrokeWidth(3.5);
            } else if (type == CellType.ENTRY) {
                Color col = Theme.of(game.getPlayers().get(((i + 2) % 100) / 25).getColour());
                c.setFill(Color.rgb(0, 0, 0, 0.35));
                c.setStroke(col);
                c.setStrokeWidth(2);
                c.getStrokeDashArray().addAll(3.0, 3.0);
            } else {
                c.setFill(Color.rgb(0, 0, 0, 0.38));
                c.setStroke(Color.rgb(255, 255, 255, 0.10));
                c.setStrokeWidth(1.5);
            }
            cells.getChildren().add(c);
        }

        for (int pi = 0; pi < 4; pi++) {
            Player player = game.getPlayers().get(pi);
            Color col = Theme.of(player.getColour());
            for (int j = 0; j < 4; j++) {
                Point2D p = safePos(pi, j);
                Rectangle r = new Rectangle(p.getX() - 10, p.getY() - 10, 20, 20);
                r.setArcWidth(8);
                r.setArcHeight(8);
                r.setFill(col.deriveColor(0, 1, 1, 0.22));
                r.setStroke(col);
                r.setStrokeWidth(2);
                cells.getChildren().add(r);
            }
            for (int s = 0; s < 4; s++) {
                Point2D p = homePos(pi, s);
                Circle c = new Circle(p.getX(), p.getY(), 11, Color.rgb(0, 0, 0, 0.25));
                c.setStroke(col.deriveColor(0, 1, 1, 0.55));
                c.setStrokeWidth(1.5);
                c.getStrokeDashArray().addAll(3.0, 3.0);
                cells.getChildren().add(c);
            }
            Label name = Theme.label(pi == 0 ? player.getName() + " (you)" : player.getName(), 17, col);
            name.setAlignment(Pos.CENTER);
            name.setPrefWidth(240);
            Point2D p = sidePoint(pi, 12, -1.25);
            name.relocate(p.getX() - 120, p.getY() - 12);
            name.setRotate(pi == 1 ? -90 : pi == 3 ? 90 : 0);
            cells.getChildren().add(name);
        }

        Rectangle slot = new Rectangle(PIT.getX() - PIT_W / 2 - 6, PIT.getY() - PIT_W * 0.7 - 6, PIT_W + 12, PIT_W * 1.4 + 12);
        slot.setArcWidth(18);
        slot.setArcHeight(18);
        slot.setFill(Color.rgb(0, 0, 0, 0.2));
        slot.setStroke(Color.rgb(255, 255, 255, 0.22));
        slot.setStrokeWidth(2);
        slot.getStrokeDashArray().addAll(6.0, 5.0);
        cells.getChildren().add(slot);
    }

    private void drawDeck() {
        for (int i = 0; i < 3; i++) {
            CardView back = new CardView(null, 62, false);
            back.setSway(false);
            back.relocate(DECK.getX() - 31 - i * 2, DECK.getY() - 43 - i * 2);
            cells.getChildren().add(back);
        }
    }

    // ---------------------------------------------------------------- state

    public Map<Marble, Loc> snapshot() {
        Map<Marble, Loc> m = new HashMap<>();
        List<Cell> track = game.getBoard().getTrack();
        for (int i = 0; i < track.size(); i++)
            if (track.get(i).getMarble() != null) m.put(track.get(i).getMarble(), new Loc(TRACK, i, 0));
        for (SafeZone z : game.getBoard().getSafeZones()) {
            int pi = playerIndex(z.getColour());
            for (int j = 0; j < z.getCells().size(); j++)
                if (z.getCells().get(j).getMarble() != null) m.put(z.getCells().get(j).getMarble(), new Loc(SAFE, pi, j));
        }
        for (int pi = 0; pi < 4; pi++) {
            List<Marble> home = game.getPlayers().get(pi).getMarbles();
            for (int s = 0; s < home.size(); s++) m.put(home.get(s), new Loc(HOME, pi, s));
        }
        return m;
    }

    public Loc locationOf(Marble m) {
        return current.get(m);
    }

    /** Snaps every marble to where the model says it is. */
    public void sync() {
        current = snapshot();
        for (Map.Entry<Marble, Loc> e : current.entrySet()) {
            MarbleNode n = nodes.get(e.getKey());
            if (n == null) continue;
            Point2D p = pos(e.getValue());
            n.setLayoutX(p.getX());
            n.setLayoutY(p.getY());
            n.setScaleX(1);
            n.setScaleY(1);
        }
    }

    public void setOnMarbleClick(Consumer<Marble> c) { onClick = c; }
    public void setSelectable(Predicate<Marble> p) { selectable = p; }

    public void setSelection(List<Marble> sel, boolean numbered) {
        selected = new ArrayList<>(sel);
        for (MarbleNode n : nodes.values()) {
            int idx = selected.indexOf(n.marble);
            n.ring.setVisible(idx >= 0);
            n.badge.setVisible(idx >= 0 && numbered);
            n.badge.setText(String.valueOf(idx + 1));
            if (idx >= 0) n.toFront();
        }
    }

    public void setCounts(int pitCount, int deckCount) {
        pitLabel.setText("FIRE PIT " + pitCount);
        deckLabel.setText("DECK " + deckCount);
    }

    public void pushPit(Card c, double rotate) {
        CardView v = new CardView(c, PIT_W, true);
        v.setBaseRotate(rotate);
        v.relocate(PIT.getX() - PIT_W / 2, PIT.getY() - v.getH() / 2);
        pit.getChildren().add(v);
        while (pit.getChildren().size() > 4) pit.getChildren().remove(0);
        v.setScaleX(1.18);
        v.setScaleY(1.18);
        ScaleTransition st = new ScaleTransition(Theme.ms(180), v);
        st.setToX(1);
        st.setToY(1);
        st.play();
    }

    public void popup(String text, Point2D at, Color colour) {
        Theme.popText(fx, text, at.getX(), at.getY() - 20, colour, 26);
    }

    // ---------------------------------------------------------------- move preview

    private Point2D cellPos(Cell cell) {
        int i = game.getBoard().getTrack().indexOf(cell);
        if (i >= 0) return trackPos(i);
        for (SafeZone z : game.getBoard().getSafeZones()) {
            int j = z.getCells().indexOf(cell);
            if (j >= 0) return safePos(playerIndex(z.getColour()), j);
        }
        return null;
    }

    public void clearPreview() {
        preview.getChildren().clear();
        previewTop.getChildren().clear();
    }

    /** Dots along the cells a marble would pass, and a ghost marble where it would land. */
    public void previewMove(List<Cell> path, Color colour) {
        for (int i = 1; i < path.size(); i++) {
            Point2D p = cellPos(path.get(i));
            if (p == null) continue;
            if (i < path.size() - 1) preview.getChildren().add(new Circle(p.getX(), p.getY(), 3.5, Color.rgb(255, 255, 255, 0.6)));
            else previewGhost(p, colour);
        }
    }

    public void previewGhost(Cell cell, Color colour) {
        Point2D p = cellPos(cell);
        if (p != null) previewGhost(p, colour);
    }

    private void previewGhost(Point2D p, Color colour) {
        Circle g = new Circle(p.getX(), p.getY(), 10.5, colour.deriveColor(0, 1, 1, 0.45));
        g.setStroke(Color.WHITE);
        g.setStrokeWidth(2);
        g.getStrokeDashArray().addAll(4.0, 3.0);
        preview.getChildren().add(g);
    }

    /** A marble the move would knock out. */
    public void previewVictim(Marble m) {
        Loc l = current.get(m);
        if (l == null) return;
        Point2D p = pos(l);
        Circle ring = new Circle(p.getX(), p.getY(), 15, null);
        ring.setStroke(Theme.RED);
        ring.setStrokeWidth(3);
        Text ko = Theme.outlined("KO", 14, Theme.RED);
        ko.setX(p.getX() - ko.getLayoutBounds().getWidth() / 2);
        ko.setY(p.getY() - 19);
        previewTop.getChildren().addAll(ring, ko);
    }

    /** A selected marble that can't make the move. */
    public void previewBlocked(Marble m) {
        Loc l = current.get(m);
        if (l == null) return;
        Point2D p = pos(l);
        for (int s = -1; s <= 1; s += 2) {
            Line line = new Line(p.getX() - 8, p.getY() - 8 * s, p.getX() + 8, p.getY() + 8 * s);
            line.setStroke(Theme.RED);
            line.setStrokeWidth(4);
            previewTop.getChildren().add(line);
        }
    }

    // ---------------------------------------------------------------- last-move trail

    private void leaveTrail(List<Loc> path, Color colour) {
        for (int i = 0; i < path.size() - 1; i++) {
            Point2D p = pos(path.get(i));
            fadeOut(new Circle(p.getX(), p.getY(), 4, colour.deriveColor(0, 1, 1.1, 0.75)));
        }
    }

    private void leaveJumpTrail(Point2D from, Point2D to, Color colour) {
        Line line = new Line(from.getX(), from.getY(), to.getX(), to.getY());
        line.setStroke(colour.deriveColor(0, 1, 1.1, 0.7));
        line.setStrokeWidth(3);
        line.getStrokeDashArray().addAll(6.0, 6.0);
        fadeOut(line);
    }

    private void fadeOut(Node n) {
        trail.getChildren().add(n);
        FadeTransition f = new FadeTransition(Theme.ms(1600), n);
        f.setDelay(Theme.ms(900));
        f.setToValue(0);
        f.setOnFinished(e -> trail.getChildren().remove(n));
        f.play();
    }

    // ---------------------------------------------------------------- animation

    /** Animates every marble from {@code before} to {@code after}, with popups for what happened. */
    public void animate(Map<Marble, Loc> before, Map<Marble, Loc> after, Card card, Colour mover, Runnable done) {
        double step = Theme.ms(80).toMillis();
        List<Marble> changed = new ArrayList<>();
        for (Map.Entry<Marble, Loc> e : after.entrySet())
            if (!e.getValue().equals(before.get(e.getKey()))) changed.add(e.getKey());

        Marble trapped = findTrapped(before, after, changed, card, mover);
        Map<Marble, List<Loc>> walks = new HashMap<>();
        for (Marble m : changed) {
            Loc a = before.get(m), b = after.get(m);
            if (m == trapped) {
                int steps = card instanceof Four ? -4 : ((Standard) card).getRank();
                walks.put(m, trackWalk(a.a, Math.abs(steps), steps < 0 ? -1 : 1));
            } else if (!(card instanceof Saver) && !isSwap(m, before, after)) {
                List<Loc> w = route(a, b);
                if (w != null) walks.put(m, w);
            }
        }

        ParallelTransition all = new ParallelTransition();
        all.getChildren().add(new PauseTransition(Duration.millis(1)));
        for (Marble m : changed) {
            MarbleNode n = nodes.get(m);
            n.toFront();
            Loc a = before.get(m), b = after.get(m);
            List<Loc> walk = walks.get(m);
            if (walk != null) {
                Timeline tl = walkTimeline(n, walk, step);
                Point2D end = pos(walk.get(walk.size() - 1));
                double walkMs = tl.getTotalDuration().toMillis();
                Color trailColour = Theme.of(m.getColour());
                all.getChildren().add(later(walkMs, () -> leaveTrail(walk, trailColour)));
                if (m == trapped) {
                    Animation home = jump(n, end, pos(b), Theme.ms(420).toMillis());
                    all.getChildren().add(new SequentialTransition(tl, home));
                    all.getChildren().add(later(walkMs, () -> {
                        popup("TRAPPED!", end, Theme.RED);
                        Sound.play(Sound.Fx.KNOCK);
                    }));
                } else {
                    all.getChildren().add(tl);
                    boolean back = walk.size() > 1 && walk.get(1).kind == TRACK
                            && (walk.get(0).a - walk.get(1).a + 100) % 100 == 1;
                    String text = (back ? "-" : "+") + (walk.size() - 1);
                    boolean safe = b.kind == SAFE && a.kind != SAFE;
                    all.getChildren().add(later(walkMs, () -> {
                        popup(text, end, Theme.BLUE);
                        if (safe) {
                            Sound.play(Sound.Fx.SAFE);
                            popup("SAFE!", new Point2D(end.getX(), end.getY() - 26), Theme.GOLD);
                        }
                    }));
                }
                continue;
            }
            // jumps: killed marbles wait until a walker reaches them
            double delay = 0;
            for (List<Loc> w : walks.values()) {
                int idx = w.indexOf(a);
                if (idx > 0) delay = Math.max(delay, idx * step);
            }
            Animation j = jump(n, pos(a), pos(b), Theme.ms(440).toMillis());
            j.setDelay(Duration.millis(delay));
            all.getChildren().add(j);
            String text = null;
            Color colour = Theme.RED;
            Sound.Fx fx = null;
            Point2D at = pos(b);
            if (a.kind == HOME && b.kind != HOME) {
                text = "FIELDED!";
                colour = Theme.GREEN;
                fx = Sound.Fx.FIELD;
            } else if (b.kind == HOME && a.kind != HOME) {
                text = card instanceof Burner ? "BURNED!" : "SENT HOME!";
                at = pos(a);
                fx = Sound.Fx.KNOCK;
            } else if (card instanceof Saver && b.kind == SAFE) {
                text = "SAVED!";
                colour = Theme.CYAN;
                fx = Sound.Fx.SAFE;
            } else if (isSwap(m, before, after)) {
                text = "SWAP!";
                colour = Theme.ORANGE;
                fx = Sound.Fx.SWAP;
            }
            if (a.kind != HOME && b.kind != HOME) {
                Point2D from = pos(a), to = pos(b);
                Color trailColour = Theme.of(m.getColour());
                all.getChildren().add(later(delay + Theme.ms(440).toMillis(), () -> leaveJumpTrail(from, to, trailColour)));
            }
            if (text != null) {
                final String t = text;
                final Color c = colour;
                final Point2D p = at;
                final Sound.Fx sfx = fx;
                double when = b.kind == HOME ? delay : delay + Theme.ms(440).toMillis();
                all.getChildren().add(later(when, () -> {
                    popup(t, p, c);
                    Sound.play(sfx);
                }));
            }
        }
        all.setOnFinished(e -> {
            sync();
            setSelection(selected, false);
            done.run();
        });
        all.play();
    }

    private static PauseTransition later(double ms, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(Math.max(1, ms)));
        p.setOnFinished(e -> r.run());
        return p;
    }

    private static boolean isSwap(Marble m, Map<Marble, Loc> before, Map<Marble, Loc> after) {
        Loc a = before.get(m), b = after.get(m);
        if (a.kind != TRACK || b.kind != TRACK) return false;
        for (Map.Entry<Marble, Loc> e : before.entrySet())
            if (e.getKey() != m && e.getValue().equals(b) && a.equals(after.get(e.getKey()))) return true;
        return false;
    }

    /** A plain move that ends with the moving marble back home means it stepped on a hidden trap. */
    private static Marble findTrapped(Map<Marble, Loc> before, Map<Marble, Loc> after, List<Marble> changed, Card card, Colour mover) {
        if (!(card instanceof Standard) || card instanceof Seven) return null;
        List<Marble> sentHome = new ArrayList<>();
        for (Marble m : changed) {
            Loc a = before.get(m), b = after.get(m);
            if (a.kind == TRACK && b.kind == HOME) sentHome.add(m);
            else if (b.kind != HOME || a.kind != HOME) return null; // something arrived somewhere: a normal move
        }
        if (sentHome.size() == 1) return sentHome.get(0);
        for (Marble m : sentHome) if (m.getColour() == mover) return m;
        return null;
    }

    private static List<Loc> trackWalk(int from, int n, int dir) {
        List<Loc> p = new ArrayList<>();
        for (int i = 0; i <= n; i++) p.add(new Loc(TRACK, ((from + dir * i) % 100 + 100) % 100, 0));
        return p;
    }

    private static List<Loc> route(Loc a, Loc b) {
        if (a.kind == TRACK && b.kind == TRACK) {
            int f = (b.a - a.a + 100) % 100, back = (a.a - b.a + 100) % 100;
            if (f > 0 && f <= 13) return trackWalk(a.a, f, 1);
            if (back > 0 && back <= 4) return trackWalk(a.a, back, -1);
            return null;
        }
        if (a.kind == TRACK && b.kind == SAFE) {
            int entry = (b.a * 25 + 98) % 100;
            int f = (entry - a.a + 100) % 100;
            if (f + b.b + 1 > 13) return null;
            List<Loc> p = trackWalk(a.a, f, 1);
            for (int j = 0; j <= b.b; j++) p.add(new Loc(SAFE, b.a, j));
            return p;
        }
        if (a.kind == SAFE && b.kind == SAFE && a.a == b.a && b.b > a.b) {
            List<Loc> p = new ArrayList<>();
            for (int j = a.b; j <= b.b; j++) p.add(new Loc(SAFE, a.a, j));
            return p;
        }
        return null;
    }

    private static Timeline walkTimeline(MarbleNode n, List<Loc> path, double step) {
        Timeline tl = new Timeline();
        for (int i = 0; i < path.size(); i++) {
            Point2D p = pos(path.get(i));
            boolean click = i > 0;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(i * step), e -> {
                        if (click) Sound.play(Sound.Fx.CLACK, 0.5);
                    },
                    new KeyValue(n.layoutXProperty(), p.getX(), Interpolator.EASE_BOTH),
                    new KeyValue(n.layoutYProperty(), p.getY(), Interpolator.EASE_BOTH),
                    new KeyValue(n.scaleXProperty(), 1.0), new KeyValue(n.scaleYProperty(), 1.0)));
            if (i + 1 < path.size())
                tl.getKeyFrames().add(new KeyFrame(Duration.millis((i + 0.5) * step),
                        new KeyValue(n.scaleXProperty(), 1.25), new KeyValue(n.scaleYProperty(), 1.25)));
        }
        return tl;
    }

    private static Animation jump(MarbleNode n, Point2D from, Point2D to, double ms) {
        return new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.layoutXProperty(), from.getX()), new KeyValue(n.layoutYProperty(), from.getY()),
                        new KeyValue(n.scaleXProperty(), 1.0), new KeyValue(n.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(ms / 2), new KeyValue(n.scaleXProperty(), 1.7), new KeyValue(n.scaleYProperty(), 1.7)),
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(n.layoutXProperty(), to.getX(), Interpolator.EASE_BOTH),
                        new KeyValue(n.layoutYProperty(), to.getY(), Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleXProperty(), 1.0), new KeyValue(n.scaleYProperty(), 1.0)));
    }

    // ---------------------------------------------------------------- marbles

    private final class MarbleNode extends Group {
        final Marble marble;
        final Circle ring;
        final Text badge;

        MarbleNode(Marble marble) {
            this.marble = marble;
            Color c = Theme.of(marble.getColour());
            Circle shadow = new Circle(1.5, 3, 10.5, Color.rgb(0, 0, 0, 0.4));
            Circle body = new Circle(10.5);
            body.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.8, true, CycleMethod.NO_CYCLE,
                    new Stop(0, c.interpolate(Color.WHITE, 0.45)), new Stop(0.55, c), new Stop(1, c.darker())));
            body.setStroke(c.darker().darker());
            body.setStrokeWidth(2);
            Rectangle shine = new Rectangle(-6, -6, 4, 4);
            shine.setFill(Color.rgb(255, 255, 255, 0.85));
            ring = new Circle(15);
            ring.setFill(null);
            ring.setStroke(Color.WHITE);
            ring.setStrokeWidth(3.5);
            ring.setEffect(new DropShadow(BlurType.GAUSSIAN, Theme.GOLD, 10, 0.6, 0, 0));
            ring.setVisible(false);
            badge = Theme.outlined("1", 14, Color.WHITE);
            badge.setX(7);
            badge.setY(-8);
            badge.setVisible(false);
            getChildren().addAll(shadow, body, shine, ring, badge);

            setOnMouseEntered(e -> {
                if (!selectable.test(marble)) return;
                setCursor(Cursor.HAND);
                setScaleX(1.25);
                setScaleY(1.25);
            });
            setOnMouseExited(e -> {
                setCursor(Cursor.DEFAULT);
                setScaleX(1);
                setScaleY(1);
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && selectable.test(marble)) onClick.accept(marble);
            });
        }
    }
}
