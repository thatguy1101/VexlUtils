package mc.mod.vexlUtils.command;

import mc.mod.vexlUtils.VexlUtils;
import mc.mod.vexlUtils.admin.AdminGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {

    private final VexlUtils plugin;
    private final AdminGUI adminGUI;

    public AdminCommand(VexlUtils plugin, AdminGUI adminGUI) {
        this.plugin = plugin;
        this.adminGUI = adminGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("vexlutils.admin")) {
            plugin.getMessages().error(player, "You don't have permission to do that.");
            return true;
        }
        adminGUI.open(player);
        return true;
    }
}
