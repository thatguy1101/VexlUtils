package mc.mod.vexlUtils.util;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    private final VexlUtils plugin;
    private String prefix;

    public MessageUtil(VexlUtils plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.prefix = color(plugin.getConfig().getString("prefix", "&8[&bVexl&3Utils&8] &r"));
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Sends a normal message, colorized, with the plugin prefix. */
    public void send(CommandSender to, String message) {
        to.sendMessage(prefix + color(message));
    }

    /** Sends an error message (auto red unless the message defines its own colors). */
    public void error(CommandSender to, String message) {
        to.sendMessage(prefix + ChatColor.RED + color(message));
    }

    /** Sends a success message (auto green). */
    public void success(CommandSender to, String message) {
        to.sendMessage(prefix + ChatColor.GREEN + color(message));
    }

    /** Raw prefixed line with no forced color, for messages with their own gold/yellow accents etc. */
    public void raw(CommandSender to, String message) {
        to.sendMessage(color(message));
    }

    public String getPrefix() {
        return prefix;
    }
}
