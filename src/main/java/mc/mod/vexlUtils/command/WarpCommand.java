package mc.mod.vexlUtils.command;

import mc.mod.vexlUtils.VexlUtils;
import mc.mod.vexlUtils.warp.WarpManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final VexlUtils plugin;

    public WarpCommand(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("vexlutils.warp.use")) {
            plugin.getMessages().error(player, "You don't have permission to use warps.");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessages().error(player, "Usage: /playerwarp <name> (aliases: /pwarp, /pw)");
            return true;
        }

        String name = args[0];
        WarpManager.Warp warp = plugin.getWarpManager().get(name);
        if (warp == null) {
            plugin.getMessages().error(player, "No warp named '" + name + "'.");
            return true;
        }
        if (!plugin.getWarpManager().canUse(player, warp)) {
            plugin.getMessages().error(player, "That warp is private.");
            return true;
        }

        Location loc = plugin.getWarpManager().toLocation(warp);
        if (loc == null) {
            plugin.getMessages().error(player, "That warp's world isn't loaded.");
            return true;
        }
        player.teleport(loc);
        plugin.getMessages().success(player, "Warped to '" + name + "'.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return new ArrayList<>();
        }
        String current = args[0].toLowerCase();
        return plugin.getWarpManager().namesVisibleTo(player).stream()
                .filter(n -> n.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }
}
