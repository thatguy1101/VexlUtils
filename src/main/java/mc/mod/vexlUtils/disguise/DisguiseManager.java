package mc.mod.vexlUtils.disguise;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisguiseManager implements Listener {

    private final VexlUtils plugin;
    private final Map<UUID, Disguise> disguised = new HashMap<>();
    private final Map<UUID, UUID> shadowMobOwners = new HashMap<>();

    public DisguiseManager(VexlUtils plugin) {
        this.plugin = plugin;
    }

    public boolean isDisguised(Player player) {
        return disguised.containsKey(player.getUniqueId());
    }

    public boolean disguise(Player player, EntityType type) {
        if (!type.isAlive() || type == EntityType.PLAYER) {
            return false;
        }
        undisguise(player);

        boolean invulnerable = plugin.getConfig().getBoolean("disguise.mob-invulnerable", true);
        LivingEntity mob;
        try {
            Location loc = player.getLocation();
            mob = (LivingEntity) player.getWorld().spawnEntity(loc, type);
            mob.setCustomName(player.getName());
            mob.setCustomNameVisible(true);
            mob.setInvulnerable(invulnerable);
            mob.setSilent(false);
            mob.setAI(false);
            mob.setCollidable(false);
            mob.setGravity(false);
            if (mob instanceof Mob m) {
                m.setTarget(null);
            }
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn disguise mob for " + player.getName() + ": " + e.getMessage());
            plugin.getMessages().error(player, "Couldn't create that disguise.");
            return false;
        }

        shadowMobOwners.put(mob.getUniqueId(), player.getUniqueId());

        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) {
                try {
                    other.hidePlayer(plugin, player);
                } catch (Exception ignored) {}
            }
        }

        boolean hideSelf = plugin.getConfig().getBoolean("disguise.hide-self", true);
        if (hideSelf) {
            try {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
            } catch (Exception ignored) {}
        }

        int interval = Math.max(1, plugin.getConfig().getInt("disguise.sync-interval", 1));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || mob.isDead()) {
                undisguise(player);
                return;
            }
            mob.teleport(player.getLocation());
            mob.setSwimming(player.isSwimming());
            // Fixed: removed mob.setSneaking() since it doesn't exist for generic LivingEntity
        }, 0L, interval);

        disguised.put(player.getUniqueId(), new Disguise(mob, task));
        plugin.getMessages().success(player, "You are now disguised as a " + type.name().toLowerCase().replace('_', ' ') + ".");
        return true;
    }

    public void undisguise(Player player) {
        Disguise d = disguised.remove(player.getUniqueId());
        if (d == null) {
            return;
        }
        d.task.cancel();
        shadowMobOwners.remove(d.mob.getUniqueId());
        if (!d.mob.isDead()) {
            d.mob.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, d.mob.getLocation(), 15, 0.3, 0.5, 0.3, 0.02);
            d.mob.remove();
        }
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
        if (plugin.getConfig().getBoolean("disguise.hide-self", true)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        }
        plugin.getMessages().send(player, "&eDisguise removed.");
    }

    public void undisguiseAll() {
        for (UUID uuid : new java.util.ArrayList<>(disguised.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                undisguise(p);
            } else {
                Disguise d = disguised.remove(uuid);
                if (d != null) {
                    shadowMobOwners.remove(d.mob.getUniqueId());
                    d.task.cancel();
                    if (!d.mob.isDead()) {
                        d.mob.remove();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (isTrackedShadowMob(event.getEntity()) && plugin.getConfig().getBoolean("disguise.mob-invulnerable", true)) {
            event.setCancelled(true);
        }
    }

    private boolean isTrackedShadowMob(Entity entity) {
        return entity != null && shadowMobOwners.containsKey(entity.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (UUID uuid : disguised.keySet()) {
            Player disguisedPlayer = plugin.getServer().getPlayer(uuid);
            if (disguisedPlayer != null) {
                event.getPlayer().hidePlayer(plugin, disguisedPlayer);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isDisguised(event.getPlayer())) {
            undisguise(event.getPlayer());
        }
    }

    private static class Disguise {
        final LivingEntity mob;
        final BukkitTask task;

        Disguise(LivingEntity mob, BukkitTask task) {
            this.mob = mob;
            this.task = task;
        }
    }
}