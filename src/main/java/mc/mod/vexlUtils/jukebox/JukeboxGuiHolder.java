package mc.mod.vexlUtils.jukebox;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class JukeboxGuiHolder implements InventoryHolder {

    private final Block jukeboxBlock;
    private final String searchFilter; // null = no filter, showing full list
    private Inventory inventory;

    public JukeboxGuiHolder(Block jukeboxBlock, String searchFilter) {
        this.jukeboxBlock = jukeboxBlock;
        this.searchFilter = searchFilter;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Block getJukeboxBlock() {
        return jukeboxBlock;
    }

    public String getSearchFilter() {
        return searchFilter;
    }
}
