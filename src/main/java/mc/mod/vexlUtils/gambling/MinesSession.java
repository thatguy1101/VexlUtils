package mc.mod.vexlUtils.gambling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MinesSession {

    public final double bet;
    public final int gridSize;
    public final Set<Integer> bombs = new HashSet<>();
    public final Set<Integer> revealed = new HashSet<>();
    public boolean finished = false;
    public boolean hitBomb = false;

    public MinesSession(double bet, int gridSize, int bombCount) {
        this.bet = bet;
        this.gridSize = gridSize;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < gridSize; i++) indices.add(i);
        Collections.shuffle(indices);
        for (int i = 0; i < bombCount && i < indices.size(); i++) {
            bombs.add(indices.get(i));
        }
    }

    /** Returns true if the revealed tile was a bomb. */
    public boolean reveal(int index) {
        revealed.add(index);
        if (bombs.contains(index)) {
            hitBomb = true;
            finished = true;
            return true;
        }
        return false;
    }

    public double currentMultiplier(double perTile) {
        return 1.0 + (revealed.size() * perTile);
    }
}
