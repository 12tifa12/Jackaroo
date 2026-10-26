package model.card.standard;

import java.util.ArrayList;

import engine.GameManager;
import engine.board.BoardManager;
import exception.ActionException;
import exception.IllegalMovementException;
import exception.InvalidMarbleException;
import model.player.Marble;

public class Seven extends Standard {

    public Seven(String name, String description, Suit suit, BoardManager boardManager, GameManager gameManager) {
        super(name, description, 7, suit, boardManager, gameManager);
    }

    @Override
    public boolean validateMarbleSize(ArrayList<Marble> marbles) {
        return marbles.size() == 2 || super.validateMarbleSize(marbles);
    }

    @Override
    public void act(ArrayList<Marble> marbles) throws ActionException, InvalidMarbleException {
        if(marbles.size() == 2) {
            // check both halves first so a split never half-happens
            int split = boardManager.getSplitDistance();
            if (!boardManager.canSplit(marbles.get(0), split, marbles.get(1), 7 - split))
                throw new IllegalMovementException("Those two marbles can't move " + split + " and " + (7 - split) + ".");
            boardManager.moveBy(marbles.get(0), boardManager.getSplitDistance(), false);
            boardManager.moveBy(marbles.get(1), 7-boardManager.getSplitDistance(), false);
        }
        
        else
            super.act(marbles);
    }

}
