package mc.mod.vexlUtils.warp;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WarpManager {

    private final VexlUtils plugin;
    private final File file;
    private final Map<String, Warp> warps = new HashMap<>(); // key: lowercase warp name

    public WarpManager(VexlUtils plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
        load();
    }

    public static class Warp {
        public UUID owner;
        public String displayName;
        public String world;
        public double x, y, z;
        public float yaw, pitch;
        public boolean isPublic;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                try {
                    Warp warp = new Warp();
                    warp.owner = UUID.fromString(yaml.getString(key + ".owner"));
                    warp.displayName = yaml.getString(key + ".display-name", key);
                    warp.world = yaml.getString(key + ".world");
                    warp.x = yaml.getDouble(key + ".x");
                    warp.y = yaml.getDouble(key + ".y");
                    warp.z = yaml.getDouble(key + ".z");
                    warp.yaw = (float) yaml.getDouble(key + ".yaw");
                    warp.pitch = (float) yaml.getDouble(key + ".pitch");
                    warp.isPublic = yaml.getBoolean(key + ".public");
                    warps.put(key.toLowerCase(), warp);
                } catch (Exception e) {
                    plugin.getLogger().warning("Skipping corrupt warp entry '" + key + "': " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load warps.yml: " + e.getMessage());
        }
    }

    public void saveAll() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Warp> entry : warps.entrySet()) {
                String key = entry.getKey();
                Warp warp = entry.getValue();
                yaml.set(key + ".owner", warp.owner.toString());
                yaml.set(key + ".display-name", warp.displayName);
                yaml.set(key + ".world", warp.world);
                yaml.set(key + ".x", warp.x);
                yaml.set(key + ".y", warp.y);
                yaml.set(key + ".z", warp.z);
                yaml.set(key + ".yaw", warp.yaw);
                yaml.set(key + ".pitch", warp.pitch);
                yaml.set(key + ".public", warp.isPublic);
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save warps.yml: " + e.getMessage());
        }
    }

    public Warp createOrUpdate(Player owner, String name, Location location) {
        Warp warp = new Warp();
        warp.owner = owner.getUniqueId();
        warp.displayName = name;
        warp.world = location.getWorld().getName();
        warp.x = location.getX();
        warp.y = location.getY();
        warp.z = location.getZ();
        warp.yaw = location.getYaw();
        warp.pitch = location.getPitch();
        warp.isPublic = plugin.getConfig().getBoolean("warps.default-public", false);
        warps.put(name.toLowerCase(), warp);
        saveAll();
        return warp;
    }

    public Warp get(String name) {
        return warps.get(name.toLowerCase());
    }

    /** Returns the display name of a warp this player already owns, or null if they own none. */
    public String getWarpOwnedBy(UUID owner) {
        for (Warp w : warps.values()) {
            if (w.owner.equals(owner)) {
                return w.displayName;
            }
        }
        return null;
    }

    public void remove(String name) {
        warps.remove(name.toLowerCase());
        saveAll();
    }

    public boolean togglePublic(String name) {
        Warp warp = warps.get(name.toLowerCase());
        if (warp == null) return false;
        warp.isPublic = !warp.isPublic;
        saveAll();
        return warp.isPublic;
    }

    public List<String> publicWarpNames() {
        return warps.values().stream()
                .filter(w -> w.isPublic)
                .map(w -> w.displayName)
                .collect(Collectors.toList());
    }

    public List<String> namesVisibleTo(Player player) {
        List<String> names = new ArrayList<>();
        for (Warp w : warps.values()) {
            if (w.isPublic || w.owner.equals(player.getUniqueId()) || player.hasPermission("vexlutils.warp.admin")) {
                names.add(w.displayName);
            }
        }
        return names;
    }

    public boolean canUse(Player player, Warp warp) {
        return warp.isPublic || warp.owner.equals(player.getUniqueId()) || player.hasPermission("vexlutils.warp.admin");
    }

    public Location toLocation(Warp warp) {
        World world = plugin.getServer().getWorld(warp.world);
        if (world == null) {
            return null;
        }
        return new Location(world, warp.x, warp.y, warp.z, warp.yaw, warp.pitch);
    }

    public String ownerName(Warp warp) {
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(warp.owner);
        return op.getName() != null ? op.getName() : "unknown";
    }
}
