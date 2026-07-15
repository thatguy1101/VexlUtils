package mc.mod.vexlUtils.gambling;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiHolder implements InventoryHolder {

    public enum Screen { MAIN, BET_SELECT, COINFLIP_PICK, DICE_PICK, ROULETTE_PICK, BLACKJACK_TABLE, MINES_TABLE }

    private final Screen screen;
    private final String game;   // slots / coinflip / dice / roulette - only set once known
    private final double bet;    // chosen bet, once known
    private Inventory inventory;

    public GuiHolder(Screen screen, String game, double bet) {
        this.screen = screen;
        this.game = game;
        this.bet = bet;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Screen getScreen() {
        return screen;
    }

    public String getGame() {
        return game;
    }

    public double getBet() {
        return bet;
    }
}
