package mc.mod.vexlUtils.gambling;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GamblingGUI implements Listener {

    private final VexlUtils plugin;
    private final Map<UUID, String> awaitingCustomBet = new ConcurrentHashMap<>();
    private final Map<UUID, BlackjackSession> blackjackSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MinesSession> minesSessions = new ConcurrentHashMap<>();

    private static final List<String> GAMES = List.of("slots", "coinflip", "dice", "roulette", "blackjack", "mines", "wheel");

    public GamblingGUI(VexlUtils plugin) {
        this.plugin = plugin;
    }

    // ---------- Main menu ----------

    public void openMain(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.MAIN, null, 0);
        Inventory inv = Bukkit.createInventory(holder, 36, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "VEXL Vegas");
        holder.setInventory(inv);

        inv.setItem(10, item(Material.GOLD_INGOT, ChatColor.GREEN + "Slots", "&7Pull the lever.", "&7Match 3 for a payout."));
        inv.setItem(11, item(Material.GOLD_NUGGET, ChatColor.YELLOW + "Coinflip", "&7Call heads or tails.", "&7Simple 50/50."));
        inv.setItem(12, item(Material.BONE, ChatColor.AQUA + "Dice", "&7Pick a number 1-6.", "&7Match the roll for a big payout."));
        inv.setItem(13, item(Material.FIRE_CHARGE, ChatColor.RED + "Roulette", "&7Bet on red, black, or green.", "&7Green is rare but pays huge."));
        inv.setItem(14, item(Material.IRON_SWORD, ChatColor.DARK_GREEN + "Blackjack", "&7Beat the dealer to 21.", "&7Hit or stand."));
        inv.setItem(15, item(Material.TNT, ChatColor.DARK_RED + "Mines", "&7Reveal tiles, avoid bombs.", "&7Cash out anytime."));
        inv.setItem(16, item(Material.END_ROD, ChatColor.LIGHT_PURPLE + "Wheel", "&7One spin, one multiplier.", "&7Odds are weighted - check config."));
        inv.setItem(31, item(Material.BARRIER, ChatColor.RED + "Close", ""));

        player.openInventory(inv);
    }

    // ---------- Bet select (shared by every game) ----------

    public void openBetSelect(Player player, String game) {
        if (!GAMES.contains(game)) {
            plugin.getMessages().error(player, "Unknown game.");
            return;
        }
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.BET_SELECT, game, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Bet - " + capitalize(game));
        holder.setInventory(inv);

        List<?> quickBets = plugin.getConfig().getList("gambling.quick-bets", List.of(100, 500, 1000, 5000, 10000));
        int[] slots = {10, 11, 12, 13, 14};
        for (int i = 0; i < quickBets.size() && i < slots.length; i++) {
            double amount = ((Number) quickBets.get(i)).doubleValue();
            inv.setItem(slots[i], item(Material.EMERALD, ChatColor.GREEN + "$" + (long) amount, "&7Click to bet this amount."));
        }
        inv.setItem(16, item(Material.PAPER, ChatColor.YELLOW + "Custom Amount", "&7Click, then type an amount in chat."));
        inv.setItem(22, item(Material.ARROW, ChatColor.GRAY + "Back", ""));

        player.openInventory(inv);
    }

    // ---------- Pick screens (coinflip / dice / roulette) ----------

    public void openCoinflipPick(Player player, double bet) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.COINFLIP_PICK, "coinflip", bet);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Coinflip - $" + (long) bet);
        holder.setInventory(inv);
        inv.setItem(11, item(Material.SUNFLOWER, ChatColor.YELLOW + "Heads", ""));
        inv.setItem(15, item(Material.COAL, ChatColor.DARK_GRAY + "Tails", ""));
        inv.setItem(22, item(Material.ARROW, ChatColor.GRAY + "Back", ""));
        player.openInventory(inv);
    }

    public void openDicePick(Player player, double bet) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.DICE_PICK, "dice", bet);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Dice - $" + (long) bet);
        holder.setInventory(inv);
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < 6; i++) {
            inv.setItem(slots[i], item(Material.BONE, ChatColor.AQUA + "" + (i + 1), "&7Payout x" + plugin.getConfig().getDouble("gambling.dice.payout-multiplier", 5.5)));
        }
        inv.setItem(22, item(Material.ARROW, ChatColor.GRAY + "Back", ""));
        player.openInventory(inv);
    }

    public void openRoulettePick(Player player, double bet) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.ROULETTE_PICK, "roulette", bet);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Roulette - $" + (long) bet);
        holder.setInventory(inv);
        inv.setItem(11, item(Material.RED_CONCRETE, ChatColor.RED + "Red", "&7Payout x" + plugin.getConfig().getDouble("gambling.roulette.red-black-multiplier", 1.9)));
        inv.setItem(13, item(Material.BLACK_CONCRETE, ChatColor.DARK_GRAY + "Black", "&7Payout x" + plugin.getConfig().getDouble("gambling.roulette.red-black-multiplier", 1.9)));
        inv.setItem(15, item(Material.GREEN_CONCRETE, ChatColor.GREEN + "Green", "&7Rare! Payout x" + plugin.getConfig().getDouble("gambling.roulette.green-multiplier", 8.0)));
        inv.setItem(22, item(Material.ARROW, ChatColor.GRAY + "Back", ""));
        player.openInventory(inv);
    }

    // ---------- Blackjack ----------

    private void openBlackjackTable(Player player, BlackjackSession session) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.BLACKJACK_TABLE, "blackjack", session.bet);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Blackjack - $" + (long) session.bet);
        holder.setInventory(inv);
        player.openInventory(inv);
        refreshBlackjackTable(player);

        // natural blackjack resolves immediately
        if (session.isNatural(session.playerHand)) {
            resolveBlackjack(player);
        }
    }

    private void refreshBlackjackTable(Player player) {
        BlackjackSession session = blackjackSessions.get(player.getUniqueId());
        if (session == null || !(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)
                || holder.getScreen() != GuiHolder.Screen.BLACKJACK_TABLE) {
            return;
        }
        Inventory inv = player.getOpenInventory().getTopInventory();
        inv.clear();

        // dealer row
        inv.setItem(1, cardItem(session.dealerHand.get(0), "Dealer"));
        if (session.dealerRevealed) {
            for (int i = 1; i < session.dealerHand.size(); i++) {
                inv.setItem(1 + i, cardItem(session.dealerHand.get(i), "Dealer"));
            }
            inv.setItem(4, item(Material.NAME_TAG, ChatColor.GRAY + "Dealer total: " + BlackjackSession.total(session.dealerHand), ""));
        } else {
            inv.setItem(2, item(Material.GRAY_DYE, ChatColor.DARK_GRAY + "?", "&7Hidden until you stand."));
        }

        // player row
        for (int i = 0; i < session.playerHand.size() && i < 7; i++) {
            inv.setItem(9 + i, cardItem(session.playerHand.get(i), "You"));
        }
        inv.setItem(17, item(Material.NAME_TAG, ChatColor.GRAY + "Your total: " + BlackjackSession.total(session.playerHand), ""));

        // controls
        if (!session.finished) {
            inv.setItem(19, item(Material.LIME_DYE, ChatColor.GREEN + "Hit", "&7Draw another card."));
            inv.setItem(21, item(Material.RED_DYE, ChatColor.RED + "Stand", "&7Let the dealer play."));
        }
    }

    private ItemStack cardItem(int value, String owner) {
        String label = value == 11 ? "Ace" : String.valueOf(value);
        return item(Material.PAPER, ChatColor.WHITE + owner + " card: " + label, "");
    }

    private void resolveBlackjack(Player player) {
        BlackjackSession session = blackjackSessions.get(player.getUniqueId());
        if (session == null) return;
        session.finished = true;
        session.playDealerOut();
        refreshBlackjackTable(player);

        int playerTotal = BlackjackSession.total(session.playerHand);
        int dealerTotal = BlackjackSession.total(session.dealerHand);
        boolean playerNatural = session.isNatural(session.playerHand) && session.playerHand.size() == 2;
        boolean dealerNatural = session.isNatural(session.dealerHand) && session.dealerHand.size() == 2;

        double bet = session.bet;
        if (playerTotal > 21) {
            plugin.getMessages().error(player, "Bust! You lost $" + (long) bet + ".");
        } else if (playerNatural && dealerNatural) {
            plugin.getMessages().send(player, "&ePush - both blackjack. Bet refunded.");
            plugin.getGamblingManager().deposit(player, bet);
        } else if (playerNatural) {
            double win = bet * plugin.getConfig().getDouble("gambling.blackjack.blackjack-multiplier", 2.5);
            plugin.getGamblingManager().deposit(player, win);
            plugin.getMessages().success(player, "Blackjack! You won $" + (long) win + "!");
        } else if (dealerTotal > 21 || playerTotal > dealerTotal) {
            double win = bet * plugin.getConfig().getDouble("gambling.blackjack.payout-multiplier", 2.0);
            plugin.getGamblingManager().deposit(player, win);
            plugin.getMessages().success(player, "You won $" + (long) win + "!");
        } else if (playerTotal == dealerTotal) {
            plugin.getMessages().send(player, "&ePush. Bet refunded.");
            plugin.getGamblingManager().deposit(player, bet);
        } else {
            plugin.getMessages().error(player, "Dealer wins. You lost $" + (long) bet + ".");
        }

        blackjackSessions.remove(player.getUniqueId());
    }

    // ---------- Mines ----------

    private void openMinesTable(Player player, MinesSession session) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.MINES_TABLE, "mines", session.bet);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "Mines - $" + (long) session.bet);
        holder.setInventory(inv);
        player.openInventory(inv);
        refreshMinesTable(player);
    }

    private int[] minesGridSlots() {
        int[] slots = new int[25];
        int idx = 0;
        for (int row = 1; row <= 5; row++) {
            for (int col = 2; col <= 6; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }

    private void refreshMinesTable(Player player) {
        MinesSession session = minesSessions.get(player.getUniqueId());
        if (session == null || !(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)
                || holder.getScreen() != GuiHolder.Screen.MINES_TABLE) {
            return;
        }
        Inventory inv = player.getOpenInventory().getTopInventory();
        inv.clear();

        int[] gridSlots = minesGridSlots();
        for (int i = 0; i < gridSlots.length; i++) {
            int slot = gridSlots[i];
            if (session.revealed.contains(i)) {
                if (session.bombs.contains(i)) {
                    inv.setItem(slot, item(Material.TNT, ChatColor.RED + "BOOM", ""));
                } else {
                    inv.setItem(slot, item(Material.EMERALD, ChatColor.GREEN + "Safe", ""));
                }
            } else if (session.finished) {
                // reveal bombs on game end
                if (session.bombs.contains(i)) {
                    inv.setItem(slot, item(Material.TNT, ChatColor.RED + "Bomb", ""));
                } else {
                    inv.setItem(slot, item(Material.GRAY_CONCRETE, " ", ""));
                }
            } else {
                inv.setItem(slot, item(Material.LIGHT_GRAY_CONCRETE, ChatColor.GRAY + "" + ChatColor.BOLD + "Tile", "&7Click to reveal.", "&8A safe tile or a bomb..."));
            }
        }

        if (!session.finished) {
            double perTile = plugin.getConfig().getDouble("gambling.mines.multiplier-per-tile", 0.25);
            double currentMult = session.currentMultiplier(perTile);
            inv.setItem(49, item(Material.GOLD_INGOT, ChatColor.GOLD + "Cash Out (x" + String.format("%.2f", currentMult) + ")",
                    "&7Current payout: $" + (long) (session.bet * currentMult)));
        }
    }

    private void mineReveal(Player player, int gridIndex) {
        MinesSession session = minesSessions.get(player.getUniqueId());
        if (session == null || session.finished) return;

        boolean bomb = session.reveal(gridIndex);
        refreshMinesTable(player);

        if (bomb) {
            plugin.getMessages().error(player, "Boom! You hit a bomb and lost $" + (long) session.bet + ".");
            minesSessions.remove(player.getUniqueId());
        }
    }

    private void mineCashOut(Player player) {
        MinesSession session = minesSessions.get(player.getUniqueId());
        if (session == null || session.finished) return;
        session.finished = true;
        double perTile = plugin.getConfig().getDouble("gambling.mines.multiplier-per-tile", 0.25);
        double winnings = session.bet * session.currentMultiplier(perTile);
        plugin.getGamblingManager().deposit(player, winnings);
        plugin.getMessages().success(player, "Cashed out for $" + (long) winnings + "!");
        refreshMinesTable(player);
        minesSessions.remove(player.getUniqueId());
    }

    // ---------- Routing ----------

    public void routeToGame(Player player, String game, double bet) {
        switch (game) {
            case "slots" -> {
                player.closeInventory();
                plugin.getGamblingManager().slots(player, bet);
            }
            case "coinflip" -> openCoinflipPick(player, bet);
            case "dice" -> openDicePick(player, bet);
            case "roulette" -> openRoulettePick(player, bet);
            case "wheel" -> {
                player.closeInventory();
                plugin.getGamblingManager().wheel(player, bet);
            }
            case "blackjack" -> {
                if (!player.hasPermission("vexlutils.gambling.blackjack")) {
                    plugin.getMessages().error(player, "You don't have permission to play blackjack.");
                    return;
                }
                if (!plugin.getGamblingManager().prepare(player, bet)) {
                    return;
                }
                BlackjackSession session = new BlackjackSession(bet);
                blackjackSessions.put(player.getUniqueId(), session);
                openBlackjackTable(player, session);
            }
            case "mines" -> {
                if (!player.hasPermission("vexlutils.gambling.mines")) {
                    plugin.getMessages().error(player, "You don't have permission to play mines.");
                    return;
                }
                if (!plugin.getGamblingManager().prepare(player, bet)) {
                    return;
                }
                int gridSize = plugin.getConfig().getInt("gambling.mines.grid-size", 25);
                int bombCount = plugin.getConfig().getInt("gambling.mines.bomb-count", 5);
                MinesSession session = new MinesSession(bet, gridSize, bombCount);
                minesSessions.put(player.getUniqueId(), session);
                openMinesTable(player, session);
            }
            default -> plugin.getMessages().error(player, "Unknown game.");
        }
    }

    // ---------- Click handling ----------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
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
        int slot = event.getRawSlot();

        try {
            switch (holder.getScreen()) {
                case MAIN -> handleMainClick(player, slot);
                case BET_SELECT -> handleBetSelectClick(player, holder, slot, clicked);
                case COINFLIP_PICK -> handleCoinflipClick(player, holder, slot);
                case DICE_PICK -> handleDiceClick(player, holder, slot);
                case ROULETTE_PICK -> handleRouletteClick(player, holder, slot);
                case BLACKJACK_TABLE -> handleBlackjackClick(player, slot);
                case MINES_TABLE -> handleMinesClick(player, slot);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Gambling GUI click error: " + e.getMessage());
            plugin.getMessages().error(player, "Something went wrong. Try again.");
        }
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 10 -> openBetSelect(player, "slots");
            case 11 -> openBetSelect(player, "coinflip");
            case 12 -> openBetSelect(player, "dice");
            case 13 -> openBetSelect(player, "roulette");
            case 14 -> openBetSelect(player, "blackjack");
            case 15 -> openBetSelect(player, "mines");
            case 16 -> openBetSelect(player, "wheel");
            case 31 -> player.closeInventory();
            default -> {}
        }
    }

    private void handleBetSelectClick(Player player, GuiHolder holder, int slot, ItemStack clicked) {
        if (slot == 22) {
            openMain(player);
            return;
        }
        if (slot == 16) {
            awaitingCustomBet.put(player.getUniqueId(), holder.getGame());
            player.closeInventory();
            plugin.getMessages().send(player, "&eType your bet amount in chat (or 'cancel').");
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName().isEmpty()) {
            return;
        }
        String display = ChatColor.stripColor(meta.getDisplayName());
        if (!display.startsWith("$")) {
            return;
        }
        double bet;
        try {
            bet = Double.parseDouble(display.substring(1));
        } catch (NumberFormatException e) {
            return;
        }
        routeToGame(player, holder.getGame(), bet);
    }

    private void handleCoinflipClick(Player player, GuiHolder holder, int slot) {
        if (slot == 22) {
            openBetSelect(player, "coinflip");
            return;
        }
        if (slot == 11) {
            player.closeInventory();
            plugin.getGamblingManager().coinflip(player, holder.getBet(), "heads");
        } else if (slot == 15) {
            player.closeInventory();
            plugin.getGamblingManager().coinflip(player, holder.getBet(), "tails");
        }
    }

    private void handleDiceClick(Player player, GuiHolder holder, int slot) {
        if (slot == 22) {
            openBetSelect(player, "dice");
            return;
        }
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                player.closeInventory();
                plugin.getGamblingManager().dice(player, holder.getBet(), i + 1);
                return;
            }
        }
    }

    private void handleRouletteClick(Player player, GuiHolder holder, int slot) {
        if (slot == 22) {
            openBetSelect(player, "roulette");
            return;
        }
        String color = switch (slot) {
            case 11 -> "red";
            case 13 -> "black";
            case 15 -> "green";
            default -> null;
        };
        if (color != null) {
            player.closeInventory();
            plugin.getGamblingManager().roulette(player, holder.getBet(), color);
        }
    }

    private void handleBlackjackClick(Player player, int slot) {
        BlackjackSession session = blackjackSessions.get(player.getUniqueId());
        if (session == null || session.finished) return;

        if (slot == 19) {
            session.hit();
            if (BlackjackSession.total(session.playerHand) > 21) {
                resolveBlackjack(player);
            } else {
                refreshBlackjackTable(player);
            }
        } else if (slot == 21) {
            resolveBlackjack(player);
        }
    }

    private void handleMinesClick(Player player, int slot) {
        if (slot == 49) {
            mineCashOut(player);
            return;
        }
        int[] gridSlots = minesGridSlots();
        for (int i = 0; i < gridSlots.length; i++) {
            if (gridSlots[i] == slot) {
                mineReveal(player, i);
                return;
            }
        }
    }

    // ---------- Auto-resolve on close ----------

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder.getScreen() == GuiHolder.Screen.BLACKJACK_TABLE) {
            BlackjackSession session = blackjackSessions.get(player.getUniqueId());
            if (session != null && !session.finished) {
                Bukkit.getScheduler().runTask(plugin, () -> resolveBlackjack(player));
            }
        } else if (holder.getScreen() == GuiHolder.Screen.MINES_TABLE) {
            MinesSession session = minesSessions.get(player.getUniqueId());
            if (session != null && !session.finished) {
                Bukkit.getScheduler().runTask(plugin, () -> mineCashOut(player));
            }
        }
    }

    // ---------- Custom bet chat capture ----------

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String game = awaitingCustomBet.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        event.setCancelled(true);
        awaitingCustomBet.remove(player.getUniqueId());
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                plugin.getMessages().send(player, "&7Cancelled.");
                return;
            }
            double bet;
            try {
                bet = Double.parseDouble(message);
            } catch (NumberFormatException e) {
                plugin.getMessages().error(player, "That's not a number. Bet cancelled.");
                return;
            }
            if (bet <= 0) {
                plugin.getMessages().error(player, "Bet must be positive. Bet cancelled.");
                return;
            }
            routeToGame(player, game, bet);
        });
    }

    // ---------- Helpers ----------

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                meta.setLore(java.util.Arrays.stream(lore)
                        .filter(l -> !l.isEmpty())
                        .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
