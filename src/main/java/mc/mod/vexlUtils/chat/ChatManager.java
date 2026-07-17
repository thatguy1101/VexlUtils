package mc.mod.vexlUtils.chat;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat moderation only - never touches message formatting/prefixes, just filters content.
 * Runs at LOW priority so it processes the raw message before format plugins (e.g. CMI) touch it.
 */
public class ChatManager implements Listener {

    private final VexlUtils plugin;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<UUID, String> lastMessageText = new HashMap<>();

    public ChatManager(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        if (player.hasPermission("vexlutils.chat.bypass")) {
            return;
        }

        String message = event.getMessage();

        // ---- spam cooldown ----
        if (plugin.getConfig().getBoolean("chat.spam.enabled", true) && !player.hasPermission("vexlutils.chat.spam.bypass")) {
            long cooldownMs = plugin.getConfig().getLong("chat.spam.cooldown-ms", 1500);
            long now = System.currentTimeMillis();
            Long last = lastMessageTime.get(player.getUniqueId());
            if (last != null && now - last < cooldownMs) {
                event.setCancelled(true);
                plugin.getMessages().error(player, "You're sending messages too fast.");
                return;
            }
            lastMessageTime.put(player.getUniqueId(), now);
        }

        // ---- repeat message blocking ----
        if (plugin.getConfig().getBoolean("chat.anti-repeat.enabled", true) && !player.hasPermission("vexlutils.chat.repeat.bypass")) {
            String normalized = message.trim().toLowerCase();
            String lastMsg = lastMessageText.get(player.getUniqueId());
            if (normalized.equals(lastMsg)) {
                event.setCancelled(true);
                plugin.getMessages().error(player, "Don't repeat yourself.");
                return;
            }
            lastMessageText.put(player.getUniqueId(), normalized);
        }

        // ---- caps limiting ----
        if (plugin.getConfig().getBoolean("chat.caps.enabled", true) && !player.hasPermission("vexlutils.chat.caps.bypass")) {
            int minLength = plugin.getConfig().getInt("chat.caps.min-length", 8);
            double maxPercent = plugin.getConfig().getDouble("chat.caps.max-percent", 0.6);
            if (message.length() >= minLength) {
                long upper = message.chars().filter(Character::isUpperCase).count();
                long letters = message.chars().filter(Character::isLetter).count();
                if (letters > 0 && (double) upper / letters > maxPercent) {
                    message = message.toLowerCase();
                }
            }
        }

        // ---- swear filter ----
        if (plugin.getConfig().getBoolean("chat.swear-filter.enabled", true) && !player.hasPermission("vexlutils.chat.swear.bypass")) {
            List<String> words = plugin.getConfig().getStringList("chat.swear-filter.words");
            for (String word : words) {
                if (word == null || word.isBlank()) continue;
                Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    message = matcher.replaceAll("*".repeat(Math.max(3, word.length())));
                }
            }
        }

        event.setMessage(message);
    }
}
