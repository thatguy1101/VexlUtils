package mc.mod.vexlUtils.vault;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VaultManager {

    public static final String INV_TITLE_PREFIX = "Vault #";

    private final VexlUtils plugin;
    private final File vaultsFolder;
    // uuid -> vaultNumber -> inventory (cached while online / recently used)
    private final Map<UUID, Map<Integer, Inventory>> openVaults = new HashMap<>();

    public VaultManager(VexlUtils plugin) {
        this.plugin = plugin;
        this.vaultsFolder = new File(plugin.getDataFolder(), "vaults");
        if (!vaultsFolder.exists()) {
            vaultsFolder.mkdirs();
        }
    }

    public int getHardCap() {
        return plugin.getConfig().getInt("vaults.max-vault-number", 99999);
    }

    public int getRows() {
        return Math.max(1, Math.min(6, plugin.getConfig().getInt("vaults.rows", 6)));
    }

    /** Highest vault number this player is allowed to open, based on vexlutils.vault.amount.<n> nodes. */
    public int getMaxVaultsFor(Player player) {
        if (player.hasPermission("vexlutils.vault.*") || player.hasPermission("vexlutils.vault.admin")) {
            return getHardCap();
        }

        int max = player.hasPermission("vexlutils.vault.use") ? plugin.getConfig().getInt("vaults.default-vaults", 1) : 0;

        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission();
            if (node.regionMatches(true, 0, "vexlutils.vault.amount.", 0, "vexlutils.vault.amount.".length())) {
                String suffix = node.substring("vexlutils.vault.amount.".length());
                try {
                    int amount = Integer.parseInt(suffix);
                    max = Math.max(max, amount);
                } catch (NumberFormatException ignored) {
                    // not a numeric suffix, skip
                }
            }
        }
        return Math.min(max, getHardCap());
    }

    public boolean canOpen(Player player, int vaultNumber) {
        if (vaultNumber < 1 || vaultNumber > getHardCap()) {
            return false;
        }
        return vaultNumber <= getMaxVaultsFor(player);
    }

    /**
     * Opens (loading from disk if needed) the given vault number for the target UUID,
     * and shows it to the viewer.
     */
    public void openVault(Player viewer, UUID owner, int vaultNumber) {
        Inventory inv = getOrLoad(owner, vaultNumber);
        viewer.openInventory(inv);
    }

    private Inventory getOrLoad(UUID owner, int vaultNumber) {
        Map<Integer, Inventory> playerVaults = openVaults.computeIfAbsent(owner, k -> new HashMap<>());
        Inventory inv = playerVaults.get(vaultNumber);
        if (inv != null) {
            return inv;
        }

        VaultHolder holder = new VaultHolder(owner, vaultNumber);
        inv = Bukkit.createInventory(holder, getRows() * 9, INV_TITLE_PREFIX + vaultNumber);
        holder.setInventory(inv);

        File file = fileFor(owner);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String path = "vault-" + vaultNumber;
            if (yaml.isList(path)) {
                java.util.List<?> raw = yaml.getList(path);
                if (raw != null) {
                    for (int i = 0; i < raw.size() && i < inv.getSize(); i++) {
                        Object o = raw.get(i);
                        if (o instanceof ItemStack) {
                            inv.setItem(i, (ItemStack) o);
                        }
                    }
                }
            }
        }

        playerVaults.put(vaultNumber, inv);
        return inv;
    }

    public void saveVault(UUID owner, int vaultNumber, Inventory inv) {
        File file = fileFor(owner);
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        java.util.List<ItemStack> contents = new java.util.ArrayList<>();
        for (ItemStack item : inv.getContents()) {
            contents.add(item);
        }
        yaml.set("vault-" + vaultNumber, contents);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save vault " + vaultNumber + " for " + owner + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, Map<Integer, Inventory>> entry : openVaults.entrySet()) {
            for (Map.Entry<Integer, Inventory> vaultEntry : entry.getValue().entrySet()) {
                saveVault(entry.getKey(), vaultEntry.getKey(), vaultEntry.getValue());
            }
        }
    }

    private File fileFor(UUID owner) {
        return new File(vaultsFolder, owner.toString() + ".yml");
    }

    public static class VaultHolder implements InventoryHolder {
        private final UUID owner;
        private final int vaultNumber;
        private Inventory inventory;

        public VaultHolder(UUID owner, int vaultNumber) {
            this.owner = owner;
            this.vaultNumber = vaultNumber;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public UUID getOwner() {
            return owner;
        }

        public int getVaultNumber() {
            return vaultNumber;
        }
    }
}
