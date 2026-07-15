package mc.mod.vexlUtils.gambling;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** A single blackjack round's state. No suits - just card values, ace-aware totals. */
public class BlackjackSession {

    private static final int[] CARD_VALUES = {2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 10, 10, 11};

    public final double bet;
    public final List<Integer> playerHand = new ArrayList<>();
    public final List<Integer> dealerHand = new ArrayList<>();
    public boolean dealerRevealed = false;
    public boolean finished = false;

    public BlackjackSession(double bet) {
        this.bet = bet;
        playerHand.add(draw());
        playerHand.add(draw());
        dealerHand.add(draw());
        dealerHand.add(draw());
    }

    public static int draw() {
        return CARD_VALUES[ThreadLocalRandom.current().nextInt(CARD_VALUES.length)];
    }

    public static int total(List<Integer> hand) {
        int sum = 0;
        int aces = 0;
        for (int c : hand) {
            sum += c;
            if (c == 11) aces++;
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    public boolean isNatural(List<Integer> hand) {
        return hand.size() == 2 && total(hand) == 21;
    }

    public void hit() {
        playerHand.add(draw());
    }

    public void playDealerOut() {
        dealerRevealed = true;
        while (total(dealerHand) < 17) {
            dealerHand.add(draw());
        }
    }
}
