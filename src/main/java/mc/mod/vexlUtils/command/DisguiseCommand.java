package mc.mod.vexlUtils.command;

import mc.mod.vexlUtils.VexlUtils;
import mc.mod.vexlUtils.disguise.DisguiseCommandLogic;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Handles both /disguise and /undisguise - registered as the executor for each in plugin.yml. */
public class DisguiseCommand implements CommandExecutor, TabCompleter {

    private final VexlUtils plugin;

    public DisguiseCommand(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("undisguise")) {
            DisguiseCommandLogic.undisguise(plugin, player);
        } else {
            DisguiseCommandLogic.disguise(plugin, player, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("undisguise")) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            return DisguiseCommandLogic.matchingMobTypes(args[0]);
        }
        if (args.length == 2) {
            String current = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
