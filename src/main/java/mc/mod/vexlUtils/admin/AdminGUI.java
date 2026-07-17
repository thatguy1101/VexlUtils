package mc.mod.vexlUtils.admin;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminGUI implements Listener {

    private final VexlUtils plugin;

    private final Map<Integer, String> chatSlots = new LinkedHashMap<>();

    private static final int MAINTENANCE_SLOT = 4;
    private static final int RELOAD_SLOT = 8;
    private static final int CLOSE_SLOT = 49;

    public AdminGUI(VexlUtils plugin) {
        this.plugin = plugin;
        chatSlots.put(37, "swear-filter");
        chatSlots.put(38, "spam");
        chatSlots.put(39, "anti-repeat");
        chatSlots.put(40, "caps");
    }

    public void open(Player player) {
        AdminGuiHolder holder = new AdminGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u2726 VexlUtils Admin \u2726");
        holder.setInventory(inv);
        render(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
    }

    private void render(Inventory inv) {
        inv.clear();

        boolean maint = plugin.getMaintenanceManager().isEnabled();
        inv.setItem(MAINTENANCE_SLOT, item(
                maint ? Material.RED_WOOL : Material.LIME_WOOL,
                (maint ? ChatColor.RED : ChatColor.GREEN) + "" + ChatColor.BOLD + "Maintenance Mode: " + (maint ? "ON" : "OFF"),
                "&7Blocks joins without", "&7vexlutils.maintenance.bypass", "", "&eClick to toggle"));

        inv.setItem(RELOAD_SLOT, item(Material.BOOK, ChatColor.AQUA + "" + ChatColor.BOLD + "Reload Config", "&7Reloads config.yml", "", "&eClick to reload"));

        inv.setItem(28, item(Material.NAME_TAG, ChatColor.GOLD + "" + ChatColor.BOLD + "Chat Filters", "&7Toggle each chat moderation feature."));
        for (Map.Entry<Integer, String> entry : chatSlots.entrySet()) {
            boolean enabled = plugin.getConfig().getBoolean("chat." + entry.getValue() + ".enabled", true);
            inv.setItem(entry.getKey(), item(
                    enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    (enabled ? ChatColor.GREEN : ChatColor.GRAY) + "" + ChatColor.BOLD + capitalize(entry.getValue().replace("-", " ")) + ": " + (enabled ? "ON" : "OFF"),
                    "&eClick to toggle"));
        }

        String economyName = plugin.getEconomy() != null ? plugin.getEconomy().getName() : "Not hooked";
        inv.setItem(46, item(Material.GOLD_INGOT, ChatColor.YELLOW + "" + ChatColor.BOLD + "Economy", "&7Provider: " + economyName));

        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close", ""));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("vexlutils.admin")) {
            return;
        }
        int slot = event.getRawSlot();
        Inventory inv = event.getInventory();

        try {
            if (slot == MAINTENANCE_SLOT) {
                plugin.getMaintenanceManager().toggle();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, plugin.getMaintenanceManager().isEnabled() ? 0.7f : 1.3f);
                render(inv);
            } else if (slot == RELOAD_SLOT) {
                plugin.reloadConfig();
                plugin.getMessages().reload();
                plugin.getMessages().success(player, "Config reloaded.");
                render(inv);
            } else if (slot == CLOSE_SLOT) {
                player.closeInventory();
            } else if (chatSlots.containsKey(slot)) {
                String key = "chat." + chatSlots.get(slot) + ".enabled";
                boolean current = plugin.getConfig().getBoolean(key, true);
                plugin.getConfig().set(key, !current);
                plugin.saveConfig();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
                render(inv);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Admin GUI click error: " + e.getMessage());
            plugin.getMessages().error(player, "Something went wrong.");
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                meta.setLore(java.util.Arrays.stream(lore)
                        .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
