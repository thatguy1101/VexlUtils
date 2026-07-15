package mc.mod.vexlUtils.warp;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class WarpListener implements Listener {

    private final VexlUtils plugin;

    public WarpListener(VexlUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line0 = event.getLine(0);
        if (line0 == null || !line0.trim().equalsIgnoreCase("[warp]")) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("vexlutils.warp.create")) {
            plugin.getMessages().error(player, "You don't have permission to create warps.");
            event.setLine(0, ChatColor.RED + "[warp]");
            return;
        }

        String name = event.getLine(1);
        if (name == null || name.trim().isEmpty()) {
            name = player.getName();
        }
        name = name.trim();

        if (plugin.getWarpManager().get(name) != null && !plugin.getWarpManager().get(name).owner.equals(player.getUniqueId())) {
            plugin.getMessages().error(player, "A warp named '" + name + "' already exists and isn't yours. Pick another name.");
            event.setLine(0, ChatColor.RED + "[bad warp]");
            return;
        }

        WarpManager.Warp warp = plugin.getWarpManager().createOrUpdate(player, name, event.getBlock().getLocation().add(0.5, 0, 0.5));

        boolean sameAsOwner = name.equalsIgnoreCase(player.getName());
        event.setLine(0, ChatColor.DARK_PURPLE + "[Warp]");
        event.setLine(1, name);
        event.setLine(2, sameAsOwner ? "" : player.getName());
        event.setLine(3, warp.isPublic ? ChatColor.GREEN + "Public" : ChatColor.RED + "Private");

        plugin.getMessages().success(player, "Warp '" + name + "' created. " + (warp.isPublic ? "It's public." : "It's private - shift-click to make it public."));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) {
            return;
        }
        String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
        if (!line0.equalsIgnoreCase("[warp]")) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String name = sign.getLine(1);
        WarpManager.Warp warp = plugin.getWarpManager().get(name);
        if (warp == null) {
            plugin.getMessages().error(player, "This warp sign is broken (warp data missing).");
            return;
        }

        boolean owns = warp.owner.equals(player.getUniqueId());

        if (player.isSneaking() && owns) {
            boolean nowPublic = plugin.getWarpManager().togglePublic(name);
            sign.setLine(3, nowPublic ? ChatColor.GREEN + "Public" : ChatColor.RED + "Private");
            sign.update(true, false);
            plugin.getMessages().send(player, "&eWarp '" + name + "' is now " + (nowPublic ? "&apublic" : "&cprivate") + "&e.");
            return;
        }

        if (!player.hasPermission("vexlutils.warp.use")) {
            plugin.getMessages().error(player, "You can't use warps.");
            return;
        }
        if (!plugin.getWarpManager().canUse(player, warp)) {
            plugin.getMessages().error(player, "This warp is private.");
            return;
        }

        Location loc = plugin.getWarpManager().toLocation(warp);
        if (loc == null) {
            plugin.getMessages().error(player, "That warp's world isn't loaded.");
            return;
        }
        player.teleport(loc);
        plugin.getMessages().success(player, "Warped to '" + name + "'.");
    }

    @EventHandler
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign sign)) {
            return;
        }
        String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
        if (!line0.equalsIgnoreCase("[warp]")) {
            return;
        }

        Player player = event.getPlayer();
        String name = sign.getLine(1);
        WarpManager.Warp warp = plugin.getWarpManager().get(name);
        if (warp == null) {
            return; // already gone / corrupt entry, let it break normally
        }

        boolean owns = warp.owner.equals(player.getUniqueId());
        if (!owns && !player.hasPermission("vexlutils.warp.admin")) {
            plugin.getMessages().error(player, "That's not your warp.");
            event.setCancelled(true);
            return;
        }

        plugin.getWarpManager().remove(name);
        plugin.getMessages().send(player, "&eWarp '" + name + "' removed.");
    }
}
