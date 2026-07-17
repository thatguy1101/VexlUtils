package mc.mod.vexlUtils.maintenance;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MaintenanceManager implements Listener {

    private final VexlUtils plugin;
    private final File stateFile;
    private boolean enabled;

    public MaintenanceManager(VexlUtils plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "maintenance.yml");
        load();
    }

    private void load() {
        if (!stateFile.exists()) {
            enabled = false;
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
            enabled = yaml.getBoolean("enabled", false);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load maintenance.yml: " + e.getMessage());
            enabled = false;
        }
    }

    private void save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("enabled", enabled);
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save maintenance.yml: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
        if (enabled) {
            kickNonBypassed();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    private void kickNonBypassed() {
        String kickMsg = ChatColor.translateAlternateColorCodes('&',
                String.join("\n", plugin.getConfig().getStringList("maintenance.kick-message")));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("vexlutils.maintenance.bypass")) {
                player.kickPlayer(kickMsg.isEmpty() ? ChatColor.RED + "Server is now under maintenance." : kickMsg);
            }
        }
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!enabled) {
            return;
        }
        String line1 = plugin.getConfig().getString("maintenance.motd-line1", "&5&lVEXLSMP");
        String line2 = plugin.getConfig().getString("maintenance.motd-line2", "&cServer is under maintenance");
        event.setMotd(ChatColor.translateAlternateColorCodes('&', line1) + "\n" + ChatColor.translateAlternateColorCodes('&', line2));
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!enabled) {
            return;
        }
        if (event.getPlayer().hasPermission("vexlutils.maintenance.bypass")) {
            return;
        }
        List<String> lines = plugin.getConfig().getStringList("maintenance.kick-message");
        String kickMsg = lines.isEmpty()
                ? ChatColor.RED + "Server is currently under maintenance."
                : ChatColor.translateAlternateColorCodes('&', String.join("\n", lines));
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMsg);
    }
}
