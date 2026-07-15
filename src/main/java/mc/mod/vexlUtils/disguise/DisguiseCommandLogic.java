package mc.mod.vexlUtils.disguise;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class DisguiseCommandLogic {

    private DisguiseCommandLogic() {
    }

    public static void disguise(VexlUtils plugin, Player sender, String[] args) {
        if (!sender.hasPermission("vexlutils.disguise")) {
            plugin.getMessages().error(sender, "You don't have permission to disguise.");
            return;
        }
        if (args.length < 1) {
            plugin.getMessages().error(sender, "Usage: /disguise <mob type> [player]");
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getMessages().error(sender, "Unknown mob type: " + args[0]);
            return;
        }

        Player target = sender;
        if (args.length >= 2) {
            if (!sender.hasPermission("vexlutils.disguise.others")) {
                plugin.getMessages().error(sender, "You don't have permission to disguise other players.");
                return;
            }
            Player found = Bukkit.getPlayerExact(args[1]);
            if (found == null) {
                plugin.getMessages().error(sender, "Player not found: " + args[1]);
                return;
            }
            target = found;
        }

        if (!plugin.getDisguiseManager().disguise(target, type)) {
            plugin.getMessages().error(sender, "Can't disguise as " + type.name() + " (not a living mob type).");
        } else if (!target.equals(sender)) {
            plugin.getMessages().success(sender, "Disguised " + target.getName() + " as a " + type.name().toLowerCase() + ".");
        }
    }

    public static void undisguise(VexlUtils plugin, Player player) {
        if (!plugin.getDisguiseManager().isDisguised(player)) {
            plugin.getMessages().error(player, "You're not disguised.");
            return;
        }
        plugin.getDisguiseManager().undisguise(player);
    }

    /** Every living, non-player mob type in the game - used for tab completion. */
    public static List<String> allMobTypes() {
        List<String> types = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type != EntityType.PLAYER) {
                types.add(type.name().toLowerCase());
            }
        }
        return types;
    }

    public static List<String> matchingMobTypes(String prefix) {
        String lower = prefix.toLowerCase();
        return allMobTypes().stream().filter(t -> t.startsWith(lower)).collect(Collectors.toList());
    }
}
