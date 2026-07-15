package mc.mod.vexlUtils.shop;

import mc.mod.vexlUtils.VexlUtils;
import mc.mod.vexlUtils.util.Keys;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

public class SignShopListener implements Listener {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final VexlUtils plugin;

    public SignShopListener(VexlUtils plugin) {
        this.plugin = plugin;
    }

    // ---------- Creation / Editing ----------

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line0raw = event.getLine(0);
        String line0 = ChatColor.stripColor(line0raw == null ? "" : line0raw).trim();
        Player player = event.getPlayer();

        if (!line0.equalsIgnoreCase("[shop]") && !line0.equalsIgnoreCase("[sell]")) {
            return;
        }

        Block signBlock = event.getBlock();
        UUID existingOwner = getOwner(signBlock);
        boolean isEdit = existingOwner != null;

        if (isEdit) {
            boolean owns = existingOwner.equals(player.getUniqueId());
            if (!owns && !player.hasPermission("vexlutils.shop.edit.others")) {
                plugin.getMessages().error(player, "That's not your shop.");
                event.setCancelled(true);
                return;
            }
        } else if (!player.hasPermission("vexlutils.shop.create")) {
            plugin.getMessages().error(player, "You don't have permission to make shop signs.");
            event.setLine(0, ChatColor.RED + line0);
            return;
        }

        // Line1: amount, Line2: price. Line3 is auto-filled with owner name.
        Integer amount = parseIntOrNull(event.getLine(1));
        Double price = parseDoubleOrNull(event.getLine(2));

        if (amount == null || amount <= 0 || price == null || price < 0) {
            plugin.getMessages().error(player, "Bad sign shop format. Use:");
            plugin.getMessages().raw(player, ChatColor.RED + "Line1: [Shop]/[Sell]  Line2: <amount>  Line3: <price>");
            event.setLine(0, ChatColor.RED + "[bad shop]");
            return;
        }

        boolean sellMode = line0.equalsIgnoreCase("[sell]");
        UUID ownerId = isEdit ? existingOwner : player.getUniqueId();
        String ownerName = isEdit ? resolveOwnerName(existingOwner, player) : player.getName();

        event.setLine(0, sellMode ? ChatColor.GOLD + "[Sell]" : ChatColor.GREEN + "[Shop]");
        event.setLine(1, (sellMode ? "Buying: " : "Selling: ") + amount);
        event.setLine(2, "$" + trimPrice(price));
        event.setLine(3, ownerName);

        boolean hadChestAlready = isEdit && findLinkedChest(getChestString(signBlock)) != null;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(signBlock.getState() instanceof Sign sign)) {
                return;
            }
            PersistentDataContainer pdc = sign.getPersistentDataContainer();
            pdc.set(Keys.owner(), PersistentDataType.STRING, ownerId.toString());
            pdc.set(Keys.amount(), PersistentDataType.INTEGER, amount);
            pdc.set(Keys.price(), PersistentDataType.DOUBLE, price);
            pdc.set(Keys.mode(), PersistentDataType.STRING, sellMode ? "sell" : "shop");

            if (!hadChestAlready) {
                Block chestBlock = findAdjacentChest(signBlock);
                if (chestBlock != null) {
                    pdc.set(Keys.chest(), PersistentDataType.STRING, locationToString(chestBlock.getLocation()));
                }
            }
            sign.update(true, false);
            refreshStockColor(sign);
        });

        boolean willHaveChest = hadChestAlready || findAdjacentChest(signBlock) != null;
        plugin.getMessages().success(player, isEdit
                ? "Shop updated. Punch it with an item to change what it sells."
                : "Sign shop created. Now punch it while holding the item you want to sell.");
        if (!willHaveChest) {
            plugin.getMessages().send(player, "&eNo chest found next to this sign - it'll trade with unlimited stock. Place a chest against it to track real inventory.");
        }
    }

    private String resolveOwnerName(UUID uuid, Player fallback) {
        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : fallback.getName();
    }

    // ---------- Punch to set item / Use ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return; // avoid double-firing for off-hand
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) {
            return;
        }
        String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
        if (!line0.equalsIgnoreCase("[shop]") && !line0.equalsIgnoreCase("[sell]")) {
            return;
        }

        Player player = event.getPlayer();
        UUID owner = getOwner(block);
        boolean owns = owner != null && owner.equals(player.getUniqueId());
        boolean canEdit = owns ? player.hasPermission("vexlutils.shop.edit") : player.hasPermission("vexlutils.shop.edit.others");

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!canEdit) {
                plugin.getMessages().error(player, "That's not your shop.");
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                plugin.getMessages().send(player, "&eHold the item you want this shop to trade, then punch the sign again.");
                return;
            }
            setItem(sign, hand.getType());
            plugin.getMessages().success(player, "Shop item set to " + hand.getType().name() + ".");
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (player.isSneaking() && canEdit && plugin.getConfig().getBoolean("shops.allow-owner-edit", true)) {
                try {
                    player.openSign(sign);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to open sign editor: " + e.getMessage());
                    plugin.getMessages().error(player, "Couldn't open the sign editor.");
                }
                return;
            }
            handleTransaction(player, sign, line0.equalsIgnoreCase("[sell]"));
        }
    }

    /** When a chest is placed next to an existing chest-less shop sign, link them automatically. */
    @EventHandler
    public void onChestPlace(BlockPlaceEvent event) {
        if (!(event.getBlockPlaced().getState() instanceof Chest)) {
            return;
        }
        Block chestBlock = event.getBlockPlaced();
        for (BlockFace face : NEIGHBOURS) {
            Block neighbour = chestBlock.getRelative(face);
            if (!(neighbour.getState() instanceof Sign sign)) {
                continue;
            }
            String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
            if (!line0.equalsIgnoreCase("[shop]") && !line0.equalsIgnoreCase("[sell]")) {
                continue;
            }
            PersistentDataContainer pdc = sign.getPersistentDataContainer();
            if (pdc.has(Keys.chest(), PersistentDataType.STRING)) {
                continue;
            }
            pdc.set(Keys.chest(), PersistentDataType.STRING, locationToString(chestBlock.getLocation()));
            sign.update(true, false);
            refreshStockColor(sign);
            plugin.getMessages().send(event.getPlayer(), "&aLinked that chest to the nearby shop sign.");
        }
    }

    private void setItem(Sign sign, Material material) {
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        pdc.set(Keys.material(), PersistentDataType.STRING, material.name());

        if (!plugin.getConfig().getBoolean("shops.show-floating-item", true)) {
            sign.update(true, false);
            refreshStockColor(sign);
            return;
        }

        try {
            String oldUuid = pdc.get(Keys.display(), PersistentDataType.STRING);
            if (oldUuid != null) {
                Entity old = org.bukkit.Bukkit.getEntity(UUID.fromString(oldUuid));
                if (old != null) {
                    old.remove();
                }
            }

            Block chestBlock = findLinkedChest(pdc.get(Keys.chest(), PersistentDataType.STRING));
            Location displayLoc = (chestBlock != null ? chestBlock.getLocation() : sign.getLocation()).add(0.5, 1.15, 0.5);

            ItemDisplay display = sign.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(material));
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(true);
                Transformation t = new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(0.5f, 0.5f, 0.5f),
                        new AxisAngle4f(0, 0, 0, 1)
                );
                d.setTransformation(t);
            });

            pdc.set(Keys.display(), PersistentDataType.STRING, display.getUniqueId().toString());
            sign.update(true, false);
            refreshStockColor(sign);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn shop item display: " + e.getMessage());
            sign.update(true, false);
        }
    }

    private void handleTransaction(Player player, Sign sign, boolean sellMode) {
        if (!player.hasPermission("vexlutils.shop.use")) {
            plugin.getMessages().error(player, "You can't use this.");
            return;
        }
        if (plugin.getEconomy() == null) {
            plugin.getMessages().error(player, "Economy isn't set up on this server (need Vault + an economy plugin, e.g. CMI).");
            return;
        }

        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        String materialName = pdc.get(Keys.material(), PersistentDataType.STRING);
        Integer listedAmount = pdc.get(Keys.amount(), PersistentDataType.INTEGER);
        Double listedPrice = pdc.get(Keys.price(), PersistentDataType.DOUBLE);

        if (materialName == null) {
            plugin.getMessages().error(player, "This shop doesn't have an item set yet.");
            return;
        }
        if (listedAmount == null || listedPrice == null) {
            plugin.getMessages().error(player, "This shop is misconfigured (missing amount/price).");
            return;
        }

        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Shop at " + sign.getLocation() + " has invalid stored material: " + materialName);
            plugin.getMessages().error(player, "This shop's item is invalid. Ask the owner to punch it with a valid item.");
            return;
        }

        Block chestBlock = findLinkedChest(pdc.get(Keys.chest(), PersistentDataType.STRING));
        Inventory chestInv = getChestInventory(chestBlock);
        double unitPrice = listedPrice / listedAmount;

        try {
            if (sellMode) {
                int playerHas = countInInventory(player.getInventory(), material);
                int capacity = chestInv != null ? spaceFor(chestInv, material) : Integer.MAX_VALUE;
                int actual = Math.min(listedAmount, Math.min(playerHas, capacity));

                if (chestInv != null && capacity <= 0) {
                    plugin.getMessages().error(player, "This shop is full and can't accept more right now.");
                    return;
                }
                if (playerHas <= 0) {
                    plugin.getMessages().error(player, "You don't have any " + material.name() + " to sell.");
                    return;
                }
                if (actual <= 0) {
                    plugin.getMessages().error(player, "This shop can't take any right now.");
                    return;
                }

                double payout = unitPrice * actual;
                player.getInventory().removeItem(new ItemStack(material, actual));
                if (chestInv != null) {
                    chestInv.addItem(new ItemStack(material, actual));
                }
                plugin.getEconomy().depositPlayer(player, payout);

                String note = actual < listedAmount ? " (partial - shop had limited space)" : "";
                plugin.getMessages().success(player, "Sold " + actual + "x " + material.name() + " for $" + trimPrice(payout) + note);
            } else {
                int stock = chestInv != null ? countInInventory(chestInv, material) : Integer.MAX_VALUE;
                if (chestInv != null && stock <= 0) {
                    plugin.getMessages().error(player, "This shop is out of stock.");
                    return;
                }
                int actual = Math.min(listedAmount, stock);
                double cost = unitPrice * actual;

                if (!plugin.getEconomy().has(player, cost)) {
                    plugin.getMessages().error(player, "You need $" + trimPrice(cost) + " to buy this.");
                    return;
                }
                if (player.getInventory().firstEmpty() == -1) {
                    plugin.getMessages().error(player, "Your inventory is full.");
                    return;
                }
                EconomyResponse response = plugin.getEconomy().withdrawPlayer(player, cost);
                if (!response.transactionSuccess()) {
                    plugin.getMessages().error(player, "Payment failed: " + response.errorMessage);
                    return;
                }
                if (chestInv != null) {
                    chestInv.removeItem(new ItemStack(material, actual));
                }
                player.getInventory().addItem(new ItemStack(material, actual));

                String note = actual < listedAmount ? " (partial - shop had less in stock)" : "";
                plugin.getMessages().success(player, "Bought " + actual + "x " + material.name() + " for $" + trimPrice(cost) + note);
            }
            refreshStockColor(sign);
        } catch (Exception e) {
            plugin.getLogger().warning("Shop transaction failed for " + player.getName() + ": " + e.getMessage());
            plugin.getMessages().error(player, "Something went wrong with that transaction. Nothing was charged - tell an admin if items vanished.");
        }
    }

    /** Recolors line0 red if the shop is out of stock (buy) or out of space (sell). No-op for unlinked (unlimited) shops. */
    private void refreshStockColor(Sign sign) {
        try {
            PersistentDataContainer pdc = sign.getPersistentDataContainer();
            String materialName = pdc.get(Keys.material(), PersistentDataType.STRING);
            String modeStr = pdc.get(Keys.mode(), PersistentDataType.STRING);
            if (materialName == null || modeStr == null) {
                return;
            }
            Block chestBlock = findLinkedChest(pdc.get(Keys.chest(), PersistentDataType.STRING));
            Inventory chestInv = getChestInventory(chestBlock);
            if (chestInv == null) {
                return;
            }

            Material material = Material.valueOf(materialName);
            boolean sellMode = modeStr.equals("sell");
            boolean depleted = sellMode
                    ? spaceFor(chestInv, material) <= 0
                    : countInInventory(chestInv, material) <= 0;

            String label = sellMode ? "[Sell]" : "[Shop]";
            sign.setLine(0, depleted ? ChatColor.DARK_RED + label : (sellMode ? ChatColor.GOLD + label : ChatColor.GREEN + label));
            sign.update(true, false);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh shop stock color: " + e.getMessage());
        }
    }

    // ---------- Break protection ----------

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign sign)) {
            return;
        }
        String line0 = ChatColor.stripColor(sign.getLine(0)).trim();
        if (!line0.equalsIgnoreCase("[shop]") && !line0.equalsIgnoreCase("[sell]")) {
            return;
        }

        Player player = event.getPlayer();
        UUID owner = getOwner(event.getBlock());
        boolean owns = owner != null && owner.equals(player.getUniqueId());

        if (owns && !player.hasPermission("vexlutils.shop.remove")) {
            plugin.getMessages().error(player, "You don't have permission to remove shops.");
            event.setCancelled(true);
            return;
        }
        if (!owns && !player.hasPermission("vexlutils.shop.edit.others")) {
            plugin.getMessages().error(player, "That's not your shop.");
            event.setCancelled(true);
            return;
        }

        try {
            String uuidStr = sign.getPersistentDataContainer().get(Keys.display(), PersistentDataType.STRING);
            if (uuidStr != null) {
                Entity display = org.bukkit.Bukkit.getEntity(UUID.fromString(uuidStr));
                if (display != null) {
                    display.remove();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clean up shop display entity: " + e.getMessage());
        }
    }

    // ---------- Helpers ----------

    private Block findAdjacentChest(Block signBlock) {
        for (BlockFace face : NEIGHBOURS) {
            Block neighbour = signBlock.getRelative(face);
            if (neighbour.getState() instanceof Chest) {
                return neighbour;
            }
        }
        return null;
    }

    private Block findLinkedChest(String storedLocation) {
        if (storedLocation == null) {
            return null;
        }
        Block block = stringToBlock(storedLocation);
        return (block != null && block.getState() instanceof Chest) ? block : null;
    }

    private String getChestString(Block signBlock) {
        if (!(signBlock.getState() instanceof Sign sign)) {
            return null;
        }
        return sign.getPersistentDataContainer().get(Keys.chest(), PersistentDataType.STRING);
    }

    private Inventory getChestInventory(Block chestBlock) {
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest chest)) {
            return null;
        }
        return chest.getInventory();
    }

    private int countInInventory(Inventory inv, Material material) {
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private int spaceFor(Inventory inv, Material material) {
        int space = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                space += maxStack;
            } else if (stack.getType() == material && stack.getAmount() < maxStack) {
                space += (maxStack - stack.getAmount());
            }
        }
        return space;
    }

    private String locationToString(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private Block stringToBlock(String s) {
        try {
            String[] parts = s.split(";");
            World world = plugin.getServer().getWorld(parts[0]);
            if (world == null) return null;
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (Exception e) {
            return null;
        }
    }

    private UUID getOwner(Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return null;
        }
        String raw = sign.getPersistentDataContainer().get(Keys.owner(), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        try {
            return s == null ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String s) {
        try {
            return s == null ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimPrice(double price) {
        if (price == Math.floor(price)) {
            return String.valueOf((long) price);
        }
        return String.format("%.2f", price);
    }
}
