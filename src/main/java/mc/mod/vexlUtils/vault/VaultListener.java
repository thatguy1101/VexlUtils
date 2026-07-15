package mc.mod.vexlUtils.vault;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class VaultListener implements Listener {

    private final VexlUtils plugin;

    public VaultListener(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof VaultManager.VaultHolder vaultHolder) {
            plugin.getVaultManager().saveVault(vaultHolder.getOwner(), vaultHolder.getVaultNumber(), event.getInventory());
        }
    }
}
