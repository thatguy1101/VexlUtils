package mc.mod.vexlUtils.command;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PvCommand implements CommandExecutor, TabCompleter {

    private final VexlUtils plugin;

    public PvCommand(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        int vaultNumber = 1;
        if (args.length >= 1) {
            try {
                vaultNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                plugin.getMessages().error(player, "Usage: /pv [number]");
                return true;
            }
        }

        if (!plugin.getVaultManager().canOpen(player, vaultNumber)) {
            int max = plugin.getVaultManager().getMaxVaultsFor(player);
            plugin.getMessages().error(player, "You don't have access to vault #" + vaultNumber + ". Your max is " + max + ".");
            return true;
        }

        plugin.getVaultManager().openVault(player, player.getUniqueId(), vaultNumber);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // no autocomplete for /pv by request
    }
}
