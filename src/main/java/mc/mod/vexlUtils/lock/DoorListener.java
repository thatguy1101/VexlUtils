package mc.mod.vexlUtils.lock;

import mc.mod.vexlUtils.VexlUtils;
import mc.mod.vexlUtils.util.Keys;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * A blank sign placed against a door/gate/trapdoor auto-locks it as [Private] (owner only).
 * Shift-right-click your own lock sign to toggle it [Public] (anyone can use it) or back.
 * Only one sign is ever involved per door - there's no separate "public door" sign type.
 */
public class DoorListener implements Listener {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final VexlUtils plugin;

    public DoorListener(VexlUtils plugin) {
        this.plugin = plugin;
    }

    // ---------- Creation ----------

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Block signBlock = event.getBlock();
        Player player = event.getPlayer();

        boolean blank = isBlank(event.getLine(0)) && isBlank(event.getLine(1)) && isBlank(event.getLine(2)) && isBlank(event.getLine(3));
        boolean autoLockEnabled = plugin.getConfig().getBoolean("locks.auto-lock-on-blank-sign", true);
        if (!blank || !autoLockEnabled) {
            return;
        }
        if (!player.hasPermission("vexlutils.lock.create") || !isAttachedToDoor(signBlock)) {
            return;
        }

        event.setLine(0, ChatColor.DARK_RED + "[Private]");
        event.setLine(1, player.getName());
        plugin.getMessages().success(player, "Door locked. Only you can open it - shift-right-click to make it public.");

        UUID ownerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (signBlock.getState() instanceof Sign sign) {
                sign.getPersistentDataContainer().set(Keys.owner(), PersistentDataType.STRING, ownerId.toString());
                sign.update(true, false);
            }
        });
    }

    // ---------- Use / toggle via sign ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (block.getState() instanceof Sign sign) {
            String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
            if (!line0.equalsIgnoreCase("[private]") && !line0.equalsIgnoreCase("[public]")) {
                return;
            }
            event.setCancelled(true);
            handleLockSign(event.getPlayer(), sign, block, line0.equalsIgnoreCase("[public]"));
            return;
        }

        if (block.getBlockData() instanceof Openable && plugin.getConfig().getBoolean("doors.sync-double-doors", true)) {
            Block finalBlock = block;
            plugin.getServer().getScheduler().runTask(plugin, () -> syncNeighbour(finalBlock));
        }
    }

    private void handleLockSign(Player player, Sign sign, Block signBlock, boolean isPublic) {
        String ownerStr = sign.getPersistentDataContainer().get(Keys.owner(), PersistentDataType.STRING);
        boolean isOwner = ownerStr != null && ownerStr.equals(player.getUniqueId().toString());
        boolean bypass = player.hasPermission("vexlutils.lock.bypass");

        if (player.isSneaking() && isOwner) {
            boolean nowPublic = !isPublic;
            sign.setLine(0, nowPublic ? ChatColor.DARK_AQUA + "[Public]" : ChatColor.DARK_RED + "[Private]");
            sign.update(true, false);
            plugin.getMessages().send(player, "&eThis door is now " + (nowPublic ? "&apublic" : "&cprivate") + "&e.");
            return;
        }

        if (!isPublic && !isOwner && !bypass) {
            if (!player.hasPermission("vexlutils.lock.use")) {
                plugin.getMessages().error(player, "You can't use this.");
                return;
            }
            plugin.getMessages().error(player, "This is locked" + (sign.getLine(1).isEmpty() ? "." : " by " + sign.getLine(1) + "."));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }

        if (!player.hasPermission("vexlutils.lock.use")) {
            plugin.getMessages().error(player, "You can't use this.");
            return;
        }

        toggleWithSync(player, findAdjacentDoor(signBlock));
    }

    private Block findAdjacentDoor(Block signBlock) {
        for (BlockFace face : NEIGHBOURS) {
            Block target = signBlock.getRelative(face);
            if (target.getBlockData() instanceof Openable) {
                return target;
            }
        }
        return null;
    }

    private void toggleWithSync(Player player, Block door) {
        if (door == null) {
            plugin.getMessages().error(player, "No door/gate/trapdoor found next to this sign.");
            return;
        }
        try {
            Openable openable = (Openable) door.getBlockData();
            openable.setOpen(!openable.isOpen());
            door.setBlockData(openable);
            syncNeighbour(door);
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to toggle door near " + door + ": " + e.getMessage());
            plugin.getMessages().error(player, "Something went wrong toggling that door.");
        }
    }

    /**
     * Mirrors a door's open/closed state onto a horizontally-adjacent block of the same
     * material (the classic "other half" of a double door/gate).
     * NOTE: sets block data directly rather than re-firing PlayerInteractEvent, so this can
     * bypass region-protection plugins on the neighbouring block. See config comment.
     */
    private void syncNeighbour(Block door) {
        try {
            if (!(door.getBlockData() instanceof Openable openable)) {
                return;
            }
            for (BlockFace face : HORIZONTAL) {
                Block neighbour = door.getRelative(face);
                if (neighbour.getType() == door.getType() && neighbour.getBlockData() instanceof Openable neighbourOpenable) {
                    if (neighbourOpenable.isOpen() != openable.isOpen()) {
                        neighbourOpenable.setOpen(openable.isOpen());
                        neighbour.setBlockData(neighbourOpenable);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync double door at " + door + ": " + e.getMessage());
        }
    }

    private boolean isAttachedToDoor(Block signBlock) {
        for (BlockFace face : NEIGHBOURS) {
            if (signBlock.getRelative(face).getBlockData() instanceof Openable) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String line) {
        return line == null || line.trim().isEmpty();
    }
}
