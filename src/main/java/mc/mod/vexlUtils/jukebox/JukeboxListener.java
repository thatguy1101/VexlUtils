package mc.mod.vexlUtils.jukebox;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JukeboxListener implements Listener {

    private final VexlUtils plugin;
    private final Map<UUID, String> awaitingSearch = new ConcurrentHashMap<>(); // player -> jukebox location key
    private final Map<String, Block> pendingSearchBlock = new ConcurrentHashMap<>(); // jukebox location key -> block
    private final Map<String, BukkitTask> activeSongTasks = new ConcurrentHashMap<>(); // jukebox location key -> destroy task

    // known disc lengths in ticks (20 ticks/sec), approximate real song lengths. Unknown discs fall back to 200s.
    private static final Map<String, Long> DISC_TICKS = new LinkedHashMap<>();
    static {
        DISC_TICKS.put("MUSIC_DISC_13", 178L * 20);
        DISC_TICKS.put("MUSIC_DISC_CAT", 185L * 20);
        DISC_TICKS.put("MUSIC_DISC_BLOCKS", 345L * 20);
        DISC_TICKS.put("MUSIC_DISC_CHIRP", 185L * 20);
        DISC_TICKS.put("MUSIC_DISC_FAR", 174L * 20);
        DISC_TICKS.put("MUSIC_DISC_MALL", 197L * 20);
        DISC_TICKS.put("MUSIC_DISC_MELLOHI", 96L * 20);
        DISC_TICKS.put("MUSIC_DISC_STAL", 150L * 20);
        DISC_TICKS.put("MUSIC_DISC_STRAD", 188L * 20);
        DISC_TICKS.put("MUSIC_DISC_WARD", 251L * 20);
        DISC_TICKS.put("MUSIC_DISC_11", 71L * 20);
        DISC_TICKS.put("MUSIC_DISC_WAIT", 238L * 20);
        DISC_TICKS.put("MUSIC_DISC_PIGSTEP", 149L * 20);
        DISC_TICKS.put("MUSIC_DISC_OTHERSIDE", 195L * 20);
        DISC_TICKS.put("MUSIC_DISC_5", 178L * 20);
        DISC_TICKS.put("MUSIC_DISC_RELIC", 218L * 20);
        DISC_TICKS.put("MUSIC_DISC_CREATOR", 176L * 20);
        DISC_TICKS.put("MUSIC_DISC_CREATOR_MUSIC_BOX", 73L * 20);
        DISC_TICKS.put("MUSIC_DISC_PRECIPICE", 299L * 20);
    }

    public JukeboxListener(VexlUtils plugin) {
        this.plugin = plugin;
    }

    private List<Material> allDiscs() {
        List<Material> discs = new ArrayList<>();
        for (String name : DISC_TICKS.keySet()) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                discs.add(mat);
            }
        }
        return discs;
    }

    private String niceName(Material material) {
        String name = material.name().replace("MUSIC_DISC_", "").replace("_", " ").toLowerCase();
        return java.util.Arrays.stream(name.split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(name);
    }

    private String locationKey(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    // ---------- Open / interact ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Jukebox)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("vexlutils.jukebox.use")) {
            return; // let vanilla behaviour happen for players without permission
        }

        event.setCancelled(true);
        openDiscPicker(player, block, null);
    }

    private void openDiscPicker(Player player, Block jukeboxBlock, String searchFilter) {
        JukeboxGuiHolder holder = new JukeboxGuiHolder(jukeboxBlock, searchFilter);
        String title = searchFilter != null ? "Search: " + searchFilter : "What would you like to play?";
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + title);
        holder.setInventory(inv);

        Jukebox jukebox = (Jukebox) jukeboxBlock.getState();
        if (jukebox.isPlaying()) {
            Material playing = jukebox.getPlaying();
            inv.setItem(4, item(Material.NOTE_BLOCK, ChatColor.AQUA + "" + ChatColor.BOLD + "Now Playing:", "&f" + (playing != null ? niceName(playing) : "?"), "", "&eClick a disc below to switch"));
        }

        List<Material> discs = allDiscs();
        if (searchFilter != null && !searchFilter.isBlank()) {
            String lower = searchFilter.toLowerCase();
            discs.removeIf(m -> !niceName(m).toLowerCase().contains(lower));
        }

        int[] gridSlots = new int[45]; // rows 1-5 (slots 9-53), leave row 0 for header/search
        for (int i = 0; i < 45; i++) gridSlots[i] = 9 + i;

        for (int i = 0; i < discs.size() && i < gridSlots.length; i++) {
            Material disc = discs.get(i);
            inv.setItem(gridSlots[i], item(disc, ChatColor.WHITE + "" + ChatColor.BOLD + niceName(disc), "&7Click to play this."));
        }
        if (discs.isEmpty()) {
            inv.setItem(31, item(Material.BARRIER, ChatColor.RED + "No discs match your search.", ""));
        }

        inv.setItem(0, item(Material.COMPASS, ChatColor.YELLOW + "" + ChatColor.BOLD + "Search", "&7Click, then type a name in chat."));
        if (searchFilter != null) {
            inv.setItem(1, item(Material.ARROW, ChatColor.GRAY + "" + ChatColor.BOLD + "Clear Search", ""));
        }
        if (jukebox.isPlaying()) {
            inv.setItem(8, item(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Stop", "&7Ejects the current disc."));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
    }

    // ---------- Click handling ----------

    @EventHandler
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof JukeboxGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        Block jukeboxBlock = holder.getJukeboxBlock();
        if (!(jukeboxBlock.getState() instanceof Jukebox)) {
            plugin.getMessages().error(player, "That jukebox is gone.");
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 0) {
            String key = locationKey(jukeboxBlock);
            awaitingSearch.put(player.getUniqueId(), key);
            pendingSearchBlock.put(key, jukeboxBlock);
            player.closeInventory();
            plugin.getMessages().send(player, "&eType a disc name to search (or 'cancel').");
            return;
        }
        if (slot == 1 && holder.getSearchFilter() != null) {
            openDiscPicker(player, jukeboxBlock, null);
            return;
        }
        if (slot == 8 && ((Jukebox) jukeboxBlock.getState()).isPlaying()) {
            stopAndClear(jukeboxBlock);
            plugin.getMessages().send(player, "&eStopped.");
            player.closeInventory();
            return;
        }
        if (slot == 4) {
            return; // "now playing" info item, not clickable
        }

        if (clicked.getType().name().startsWith("MUSIC_DISC_")) {
            playDisc(player, jukeboxBlock, clicked.getType());
            player.closeInventory();
        }
    }

    // ---------- Disc playback / destruction ----------

    private void playDisc(Player player, Block jukeboxBlock, Material disc) {
        String key = locationKey(jukeboxBlock);
        stopAndClear(jukeboxBlock); // cancel any previous timer for this jukebox first

        Jukebox jukebox = (Jukebox) jukeboxBlock.getState();
        jukebox.setPlaying(disc);
        jukebox.update(true, false);

        plugin.getMessages().success(player, "Now playing: " + niceName(disc));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);

        long duration = DISC_TICKS.getOrDefault(disc.name(), 200L * 20);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeSongTasks.remove(key);
            if (jukeboxBlock.getState() instanceof Jukebox jb && jb.isPlaying() && disc.equals(jb.getPlaying())) {
                jb.stopPlaying();
                jb.setRecord(null);
                jb.update(true, false);
            }
        }, duration);
        activeSongTasks.put(key, task);
    }

    private void stopAndClear(Block jukeboxBlock) {
        String key = locationKey(jukeboxBlock);
        BukkitTask existing = activeSongTasks.remove(key);
        if (existing != null) {
            existing.cancel();
        }
        if (jukeboxBlock.getState() instanceof Jukebox jukebox) {
            jukebox.stopPlaying();
            jukebox.setRecord(null);
            jukebox.update(true, false);
        }
    }

    // ---------- Search chat capture ----------

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String key = awaitingSearch.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        event.setCancelled(true);
        awaitingSearch.remove(player.getUniqueId());
        Block jukeboxBlock = pendingSearchBlock.remove(key);
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (jukeboxBlock == null || !(jukeboxBlock.getState() instanceof Jukebox)) {
                plugin.getMessages().error(player, "That jukebox is gone.");
                return;
            }
            if (message.equalsIgnoreCase("cancel")) {
                plugin.getMessages().send(player, "&7Cancelled.");
                return;
            }
            openDiscPicker(player, jukeboxBlock, message);
        });
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                meta.setLore(java.util.Arrays.stream(lore)
                        .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
