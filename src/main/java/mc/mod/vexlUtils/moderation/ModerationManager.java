package mc.mod.vexlUtils.moderation;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModerationManager implements Listener {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)^(\\d+)([smhdw])$");

    public static class Punishment {
        public String targetName;
        public String staffName;
        public String reason;
        public long expires; // -1 = permanent
        public long issued;

        public boolean isExpired() {
            return expires != -1 && System.currentTimeMillis() >= expires;
        }
    }

    public static class Warning {
        public String staffName;
        public String reason;
        public long issued;
    }

    private final VexlUtils plugin;
    private final File file;
    private final Map<UUID, Punishment> bans = new HashMap<>();
    private final Map<UUID, Punishment> mutes = new HashMap<>();
    private final Map<UUID, List<Warning>> warnings = new HashMap<>();

    public ModerationManager(VexlUtils plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "moderation.yml");
        load();
    }

    /** Returns millis duration, -1 for permanent, or null if unparsable. */
    public static Long parseDuration(String input) {
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent") || input.equals("-1")) {
            return -1L;
        }
        Matcher m = DURATION_PATTERN.matcher(input);
        if (!m.matches()) {
            return null;
        }
        long amount = Long.parseLong(m.group(1));
        long unitMillis = switch (m.group(2).toLowerCase()) {
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            default -> -2L;
        };
        if (unitMillis < 0) {
            return null;
        }
        return amount * unitMillis;
    }

    public static String formatDuration(long expiresAtOrDurationMillis) {
        if (expiresAtOrDurationMillis == -1) {
            return "permanently";
        }
        long remaining = expiresAtOrDurationMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return "a moment";
        }
        long days = remaining / 86_400_000L;
        long hours = (remaining % 86_400_000L) / 3_600_000L;
        long minutes = (remaining % 3_600_000L) / 60_000L;
        long seconds = (remaining % 60_000L) / 1000L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    // ---------- bans ----------

    public void ban(OfflinePlayer target, String staffName, String reason, long durationMillis) {
        Punishment p = new Punishment();
        p.targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        p.staffName = staffName;
        p.reason = reason;
        p.issued = System.currentTimeMillis();
        p.expires = durationMillis == -1 ? -1 : p.issued + durationMillis;
        bans.put(target.getUniqueId(), p);
        save();

        Player online = target.getPlayer();
        if (online != null) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    "&cYou have been banned.\n&7Reason: &f" + reason + "\n&7Duration: &f" +
                            (durationMillis == -1 ? "Permanent" : formatDuration(p.expires)));
            online.kickPlayer(msg);
        }
    }

    public void unban(UUID uuid) {
        bans.remove(uuid);
        save();
    }

    public Punishment isBanned(UUID uuid) {
        Punishment p = bans.get(uuid);
        if (p == null) {
            return null;
        }
        if (p.isExpired()) {
            bans.remove(uuid);
            save();
            return null;
        }
        return p;
    }

    // ---------- mutes ----------

    public void mute(OfflinePlayer target, String staffName, String reason, long durationMillis) {
        Punishment p = new Punishment();
        p.targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        p.staffName = staffName;
        p.reason = reason;
        p.issued = System.currentTimeMillis();
        p.expires = durationMillis == -1 ? -1 : p.issued + durationMillis;
        mutes.put(target.getUniqueId(), p);
        save();
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        save();
    }

    public Punishment isMuted(UUID uuid) {
        Punishment p = mutes.get(uuid);
        if (p == null) {
            return null;
        }
        if (p.isExpired()) {
            mutes.remove(uuid);
            save();
            return null;
        }
        return p;
    }

    // ---------- warnings ----------

    public int warn(OfflinePlayer target, String staffName, String reason) {
        Warning w = new Warning();
        w.staffName = staffName;
        w.reason = reason;
        w.issued = System.currentTimeMillis();
        List<Warning> list = warnings.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        list.add(w);
        save();
        return list.size();
    }

    public List<Warning> getWarnings(UUID uuid) {
        return warnings.getOrDefault(uuid, new ArrayList<>());
    }

    // ---------- broadcast ----------

    public void broadcast(CommandSender staff, boolean silent, String message) {
        if (silent && staff.hasPermission("vexlutils.moderation.silent")) {
            staff.sendMessage(ChatColor.GREEN + message + ChatColor.GRAY + " (silent)");
            return;
        }
        String colored = ChatColor.GREEN + message;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(colored);
        }
        plugin.getLogger().info(message);
    }

    // ---------- enforcement ----------

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        Punishment ban = isBanned(event.getPlayer().getUniqueId());
        if (ban == null) {
            return;
        }
        String msg = ChatColor.translateAlternateColorCodes('&',
                "&cYou are banned.\n&7Reason: &f" + ban.reason + "\n&7Expires: &f" +
                        (ban.expires == -1 ? "Never" : formatDuration(ban.expires)));
        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, msg);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Punishment mute = isMuted(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        plugin.getMessages().error(event.getPlayer(), "You are muted. Reason: " + mute.reason +
                " (expires: " + (mute.expires == -1 ? "never" : formatDuration(mute.expires)) + ")");
    }

    // ---------- persistence ----------

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            loadSection(yaml, "bans", bans);
            loadSection(yaml, "mutes", mutes);

            if (yaml.isConfigurationSection("warnings")) {
                for (String uuidStr : yaml.getConfigurationSection("warnings").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        List<Warning> list = new ArrayList<>();
                        List<Map<?, ?>> raw = yaml.getMapList("warnings." + uuidStr);
                        for (Map<?, ?> entry : raw) {
                            Warning w = new Warning();
                            w.staffName = String.valueOf(entry.get("staff"));
                            w.reason = String.valueOf(entry.get("reason"));
                            w.issued = entry.get("issued") instanceof Number n ? n.longValue() : 0L;
                            list.add(w);
                        }
                        warnings.put(uuid, list);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load moderation.yml: " + e.getMessage());
        }
    }

    private void loadSection(YamlConfiguration yaml, String key, Map<UUID, Punishment> target) {
        if (!yaml.isConfigurationSection(key)) {
            return;
        }
        for (String uuidStr : yaml.getConfigurationSection(key).getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Punishment p = new Punishment();
                p.targetName = yaml.getString(key + "." + uuidStr + ".name", uuidStr);
                p.staffName = yaml.getString(key + "." + uuidStr + ".staff", "unknown");
                p.reason = yaml.getString(key + "." + uuidStr + ".reason", "No reason given");
                p.issued = yaml.getLong(key + "." + uuidStr + ".issued", 0);
                p.expires = yaml.getLong(key + "." + uuidStr + ".expires", -1);
                target.put(uuid, p);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            saveSection(yaml, "bans", bans);
            saveSection(yaml, "mutes", mutes);

            for (Map.Entry<UUID, List<Warning>> entry : warnings.entrySet()) {
                List<Map<String, Object>> serialized = new ArrayList<>();
                for (Warning w : entry.getValue()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("staff", w.staffName);
                    m.put("reason", w.reason);
                    m.put("issued", w.issued);
                    serialized.add(m);
                }
                yaml.set("warnings." + entry.getKey(), serialized);
            }

            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save moderation.yml: " + e.getMessage());
        }
    }

    private void saveSection(YamlConfiguration yaml, String key, Map<UUID, Punishment> source) {
        for (Map.Entry<UUID, Punishment> entry : source.entrySet()) {
            String path = key + "." + entry.getKey();
            Punishment p = entry.getValue();
            yaml.set(path + ".name", p.targetName);
            yaml.set(path + ".staff", p.staffName);
            yaml.set(path + ".reason", p.reason);
            yaml.set(path + ".issued", p.issued);
            yaml.set(path + ".expires", p.expires);
        }
    }
}
