package engine.board;

import java.util.ArrayList;
import java.util.Collections;

import model.Colour;
import model.card.Card;
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

/**
 * Lists the legal ways the active player can play a card and gives each a rough score of how good it is.
 * Nothing on the board is changed. Used by the CPU to pick a move and by the UI to tell playable cards apart.
 * Hidden traps aren't known, so a listed move can still end with the marble trapped.
 */
public final class Moves {
    private Moves() {}

    public static final class Play {
        public final Card card;
        public final ArrayList<Marble> marbles;
        /** Steps for the first marble of a split Seven, otherwise 0. */
        public final int split;
        public final double score;

        Play(Card card, ArrayList<Marble> marbles, int split, double score) {
            this.card = card;
            this.marbles = marbles;
            this.split = split;
            this.score = score;
        }
    }

    /**
     * @param homeMarbles     how many marbles the player still has at home (for fielding)
     * @param nextHasCards    whether the next player holds a card (Ten's discard)
     * @param othersHaveCards whether any other player holds a card (Queen's discard)
     * @return the legal plays, best first
     */
    public static ArrayList<Play> find(Board board, Card card, Colour me, int homeMarbles, boolean nextHasCards, boolean othersHaveCards) {
        ArrayList<Play> plays = new ArrayList<>();
        ArrayList<Marble> own = new ArrayList<>(), others = new ArrayList<>();
        for (Cell c : board.getSafeZoneCells(me))
            if (c.getMarble() != null) own.add(c.getMarble());
        for (Cell c : board.getTrack()) {
            Marble m = c.getMarble();
            if (m == null) continue;
            if (m.getColour() == me) own.add(m);
            else others.add(m);
        }

        if (card instanceof Burner) {
            for (Marble m : others)
                if (board.canBurn(m)) plays.add(play(card, 0, 45 + progress(board, m) / 2.0, m));
        } else if (card instanceof Saver) {
            for (Marble m : own)
                if (onTrack(board, m)) plays.add(play(card, 0, 70 + progress(board, m) / 3.0, m));
        } else if (card instanceof Standard) {
            int rank = ((Standard) card).getRank();

            // no-marble options
            if ((card instanceof Ace || card instanceof King) && homeMarbles > 0 && board.canField(me)) {
                Marble onBase = board.getTrack().get(board.getBaseIndex(me)).getMarble();
                plays.add(play(card, 0, 50 + (onBase != null ? 60 : 0)));
            }
            if (card instanceof Queen && othersHaveCards) plays.add(play(card, 0, 12));
            if (card instanceof Ten && nextHasCards) plays.add(play(card, 0, 14));

            // one marble: Five may move anyone's marble, everything else only your own
            ArrayList<Marble> movers = new ArrayList<>(own);
            if (card instanceof Five) movers.addAll(others);
            int steps = card instanceof Four ? -4 : rank;
            boolean destroy = card instanceof King;
            for (Marble m : movers) {
                ArrayList<Cell> path = board.previewPath(m, steps, destroy);
                if (path != null) plays.add(play(card, 0, moveScore(board, m, path, steps, destroy, me), m));
            }

            // two marbles
            if (card instanceof Jack) {
                for (Marble a : own)
                    for (Marble b : others)
                        if (onTrack(board, a) && onTrack(board, b) && board.canSwap(a, b)) {
                            int gain = relative(board, me, board.getTrack().indexOf(cellOf(board, b)))
                                    - relative(board, me, board.getTrack().indexOf(cellOf(board, a)));
                            plays.add(play(card, 0, gain > 0 ? 15 + gain / 2.0 : -10 + gain / 5.0, a, b));
                        }
            }
            if (card instanceof Seven) {
                for (int i = 0; i < own.size(); i++)
                    for (int j = i + 1; j < own.size(); j++)
                        for (int s = 1; s <= 6; s++) {
                            Marble a = own.get(i), b = own.get(j);
                            if (!board.canSplit(a, s, b, 7 - s)) continue;
                            double score = moveScore(board, a, board.previewPath(a, s, false), s, false, me)
                                    + moveScore(board, b, board.previewPath(b, 7 - s, false), 7 - s, false, me) - 25;
                            plays.add(play(card, s, score, a, b));
                        }
            }
        }
        plays.sort((x, y) -> Double.compare(y.score, x.score));
        return plays;
    }

    private static Play play(Card card, int split, double score, Marble... marbles) {
        ArrayList<Marble> list = new ArrayList<>();
        Collections.addAll(list, marbles);
        return new Play(card, list, split, score);
    }

    /** Heuristic value of moving one marble along a previewed path. */
    private static double moveScore(Board board, Marble m, ArrayList<Cell> path, int steps, boolean destroy, Colour me) {
        if (path == null) return -100;
        Cell target = path.get(path.size() - 1);
        boolean mine = m.getColour() == me;
        double score;
        if (!mine) score = target.getCellType() == CellType.SAFE ? -60 : 2; // pushing an opponent forward rarely helps
        else if (steps < 0) score = progress(board, m) == 0 ? 60 : 4;     // backing off your Base lands you near home
        else {
            score = 20 + steps;
            if (target.getCellType() == CellType.SAFE) score += 100;
            if (progress(board, m) == 0) score += 6;                       // frees your Base for the next marble
        }
        if (destroy) {
            for (int i = 1; i < path.size(); i++) {
                Marble hit = path.get(i).getMarble();
                if (hit != null) score += hit.getColour() == me ? -70 : 35 + progress(board, hit) / 5.0;
            }
        } else if (target.getMarble() != null) {
            Marble hit = target.getMarble();
            score += hit.getColour() == me ? -70 : 60 + progress(board, hit) / 5.0;
        }
        return score;
    }

    private static boolean onTrack(Board board, Marble m) {
        return board.getPositionInPath(board.getTrack(), m) != -1;
    }

    private static Cell cellOf(Board board, Marble m) {
        return board.getTrack().get(board.getPositionInPath(board.getTrack(), m));
    }

    /** How far a track cell is along the given colour's journey (0 at its Base). */
    private static int relative(Board board, Colour colour, int trackIndex) {
        return (trackIndex - board.getBaseIndex(colour) + 100) % 100;
    }

    /** How far a marble is along its own journey: 0 at its Base, up to 98 on the track, 100+ in its safe zone. */
    public static int progress(Board board, Marble m) {
        int pos = board.getPositionInPath(board.getTrack(), m);
        if (pos != -1) return relative(board, m.getColour(), pos);
        int safe = board.getPositionInPath(board.getSafeZoneCells(m.getColour()), m);
        return safe == -1 ? -1 : 100 + safe;
    }
}
