package mc.mod.vexlUtils.command;

import mc.mod.vexlUtils.VexlUtils;
import mc.mod.vexlUtils.disguise.DisguiseCommandLogic;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VCommand implements CommandExecutor, TabCompleter {

    private final VexlUtils plugin;

    public VCommand(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "vault" -> handleVault(player, args);
                case "shop" -> plugin.getMessages().send(player, "&ePlace a sign: Line1 [Shop]/[Sell], Line2 amount, Line3 price. Then punch it holding the item to sell. Shift-right-click your own to edit amount/price.");
                case "warp" -> plugin.getMessages().send(player, "&ePlace a sign with [Warp] on the top line to create a personal warp. Use /pw <name> to visit public ones. Shift-right-click your own warp sign to toggle public/private.");
                case "disguise" -> DisguiseCommandLogic.disguise(plugin, player, Arrays.copyOfRange(args, 1, args.length));
                case "undisguise" -> DisguiseCommandLogic.undisguise(plugin, player);
                case "maintenance" -> handleMaintenance(player, args);
                case "admin" -> {
                    if (!player.hasPermission("vexlutils.admin")) {
                        plugin.getMessages().error(player, "You don't have permission to do that.");
                    } else {
                        plugin.getAdminGUI().open(player);
                    }
                }
                case "help" -> sendHelp(player);
                default -> plugin.getMessages().error(player, "Unknown subcommand. Try /vexl help");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling /vexl " + String.join(" ", args) + " for " + player.getName() + ": " + e.getMessage());
            plugin.getMessages().error(player, "Something went wrong running that command.");
        }
        return true;
    }

    private void sendHelp(Player player) {
        int max = plugin.getVaultManager().getMaxVaultsFor(player);
        plugin.getMessages().raw(player, ChatColor.GOLD + "" + ChatColor.BOLD + "--- VexlUtils (/vexl or /v) ---");
        plugin.getMessages().raw(player, ChatColor.YELLOW + "/vexl vault [1-" + max + "]" + ChatColor.GRAY + " (or /pv [n]) - open a personal vault");
        plugin.getMessages().raw(player, ChatColor.YELLOW + "/vexl shop" + ChatColor.GRAY + " - how to make/edit a sign shop");
        plugin.getMessages().raw(player, ChatColor.YELLOW + "/vexl warp" + ChatColor.GRAY + " - how to make a player warp (or /pw <name>)");
        plugin.getMessages().raw(player, ChatColor.YELLOW + "/disguise <mob> [player]" + ChatColor.GRAY + " - disguise as any mob");
        plugin.getMessages().raw(player, ChatColor.YELLOW + "/undisguise" + ChatColor.GRAY + " - remove your disguise");
    }

    // ---------- vault ----------
    private void handleVault(Player player, String[] args) {
        int vaultNumber = 1;
        if (args.length >= 2) {
            try {
                vaultNumber = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                plugin.getMessages().error(player, "Usage: /vexl vault [number] [player]");
                return;
            }
        }

        // admin: /vexl vault <n> <player>
        if (args.length >= 3) {
            if (!player.hasPermission("vexlutils.vault.admin")) {
                plugin.getMessages().error(player, "You don't have permission to open other players' vaults.");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.getMessages().error(player, "Player not found: " + args[2]);
                return;
            }
            plugin.getVaultManager().openVault(player, target.getUniqueId(), vaultNumber);
            return;
        }

        if (!plugin.getVaultManager().canOpen(player, vaultNumber)) {
            int max = plugin.getVaultManager().getMaxVaultsFor(player);
            plugin.getMessages().error(player, "You don't have access to vault #" + vaultNumber + ". Your max is " + max + ".");
            return;
        }
        plugin.getVaultManager().openVault(player, player.getUniqueId(), vaultNumber);
    }

    // ---------- maintenance ----------
    private void handleMaintenance(Player player, String[] args) {
        if (!player.hasPermission("vexlutils.maintenance.admin")) {
            plugin.getMessages().error(player, "You don't have permission to do that.");
            return;
        }
        if (args.length < 2) {
            boolean enabled = plugin.getMaintenanceManager().isEnabled();
            plugin.getMessages().send(player, "&eMaintenance mode is currently " + (enabled ? "&aON" : "&cOFF") + "&e. Usage: /vexl maintenance <on|off>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "on" -> {
                plugin.getMaintenanceManager().setEnabled(true);
                plugin.getMessages().success(player, "Maintenance mode enabled.");
            }
            case "off" -> {
                plugin.getMaintenanceManager().setEnabled(false);
                plugin.getMessages().success(player, "Maintenance mode disabled.");
            }
            default -> plugin.getMessages().error(player, "Usage: /vexl maintenance <on|off>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("vault", "shop", "warp", "disguise", "undisguise", "maintenance", "admin", "help"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "vault" -> {
                    if (sender instanceof Player player) {
                        int max = Math.min(plugin.getVaultManager().getMaxVaultsFor(player), 50);
                        for (int i = 1; i <= max; i++) options.add(String.valueOf(i));
                    }
                }
                case "disguise" -> options.addAll(DisguiseCommandLogic.allMobTypes());
                case "maintenance" -> options.addAll(List.of("on", "off"));
            }
        }

        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(current)).collect(Collectors.toList());
    }
}
