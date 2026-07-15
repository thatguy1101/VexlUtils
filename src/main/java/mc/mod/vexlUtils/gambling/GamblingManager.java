package mc.mod.vexlUtils.gambling;

import mc.mod.vexlUtils.VexlUtils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class GamblingManager {

    private final VexlUtils plugin;

    public GamblingManager(VexlUtils plugin) {
        this.plugin = plugin;
    }

    private boolean economyCheck(Player player) {
        if (plugin.getEconomy() == null) {
            plugin.getMessages().error(player, "Economy isn't set up on this server (need Vault + an economy plugin, e.g. CMI).");
            return false;
        }
        return true;
    }

    private boolean betInRange(Player player, double bet) {
        double min = plugin.getConfig().getDouble("gambling.min-bet", 10);
        double max = plugin.getConfig().getDouble("gambling.max-bet", 10000);
        if (bet < min || bet > max) {
            plugin.getMessages().error(player, "Bet must be between $" + (long) min + " and $" + (long) max + ".");
            return false;
        }
        return true;
    }

    private boolean takeBet(Player player, double bet) {
        try {
            if (!plugin.getEconomy().has(player, bet)) {
                plugin.getMessages().error(player, "You don't have $" + (long) bet + ".");
                return false;
            }
            EconomyResponse resp = plugin.getEconomy().withdrawPlayer(player, bet);
            if (!resp.transactionSuccess()) {
                plugin.getMessages().error(player, "Transaction failed: " + resp.errorMessage);
                return false;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Economy withdraw failed for " + player.getName() + ": " + e.getMessage());
            plugin.getMessages().error(player, "Something went wrong talking to the economy plugin. Nothing was charged.");
            return false;
        }
    }

    public void deposit(Player player, double amount) {
        payout(player, amount);
    }

    private void payout(Player player, double amount) {
        try {
            plugin.getEconomy().depositPlayer(player, amount);
        } catch (Exception e) {
            plugin.getLogger().severe("PAYOUT FAILED for " + player.getName() + " amount $" + amount + ": " + e.getMessage());
            plugin.getMessages().error(player, "Your winnings failed to deposit! Screenshot this and tell an admin: $" + (long) amount);
        }
    }

    private boolean permCheck(Player player, String game, String node) {
        if (!player.hasPermission("vexlutils.gambling") || !player.hasPermission(node)) {
            plugin.getMessages().error(player, "You don't have permission to play " + game + ".");
            return false;
        }
        return true;
    }

    public boolean prepare(Player player, double bet) {
        return economyCheck(player) && betInRange(player, bet) && takeBet(player, bet);
    }

    public void slots(Player player, double bet) {
        if (!permCheck(player, "slots", "vexlutils.gambling.slots") || !prepare(player, bet)) {
            return;
        }

        String[] symbols = {"\uD83C\uDF52", "\uD83C\uDF4B", "\uD83D\uDD14", "\u2B50", "7\u20E3"};
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String a = symbols[rng.nextInt(symbols.length)];
        String b = symbols[rng.nextInt(symbols.length)];
        String c = symbols[rng.nextInt(symbols.length)];

        plugin.getMessages().raw(player, "&7-------------------------------");
        plugin.getMessages().raw(player, "&e  [ " + a + " | " + b + " | " + c + " ]");

        double winChance = plugin.getConfig().getDouble("gambling.slots.win-chance", 0.35);
        boolean forcedWin = a.equals(b) && b.equals(c);
        boolean win = forcedWin || rng.nextDouble() < winChance;

        if (!win) {
            plugin.getMessages().error(player, "No match. You lost $" + (long) bet + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            plugin.getMessages().raw(player, "&7-------------------------------");
            return;
        }

        double jackpotChance = plugin.getConfig().getDouble("gambling.slots.jackpot-chance", 0.03);
        boolean jackpot = forcedWin || rng.nextDouble() < jackpotChance;
        double multiplier = jackpot
                ? plugin.getConfig().getDouble("gambling.slots.jackpot-multiplier", 5.0)
                : plugin.getConfig().getDouble("gambling.slots.payout-multiplier", 2.0);

        double winnings = bet * multiplier;
        payout(player, winnings);

        if (jackpot) {
            plugin.getMessages().raw(player, "&6&lJACKPOT! You won $" + (long) winnings + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            plugin.getMessages().success(player, "You won $" + (long) winnings + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        plugin.getMessages().raw(player, "&7-------------------------------");
    }

    public void coinflip(Player player, double bet, String call) {
        if (!permCheck(player, "coinflip", "vexlutils.gambling.coinflip")) {
            return;
        }
        String normalized = call.equalsIgnoreCase("h") ? "heads" : call.equalsIgnoreCase("t") ? "tails" : call.toLowerCase();
        if (!normalized.equals("heads") && !normalized.equals("tails")) {
            plugin.getMessages().error(player, "Call heads or tails.");
            return;
        }
        if (!prepare(player, bet)) {
            return;
        }

        boolean heads = ThreadLocalRandom.current().nextBoolean();
        String result = heads ? "heads" : "tails";
        plugin.getMessages().raw(player, "&eThe coin landed on &l" + result + "&e!");

        if (result.equals(normalized)) {
            double multiplier = plugin.getConfig().getDouble("gambling.coinflip.payout-multiplier", 1.9);
            double winnings = bet * multiplier;
            payout(player, winnings);
            plugin.getMessages().success(player, "You won $" + (long) winnings + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else {
            plugin.getMessages().error(player, "You lost $" + (long) bet + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    public void dice(Player player, double bet, int pick) {
        if (!permCheck(player, "dice", "vexlutils.gambling.dice")) {
            return;
        }
        if (pick < 1 || pick > 6) {
            plugin.getMessages().error(player, "Pick a number 1-6.");
            return;
        }
        if (!prepare(player, bet)) {
            return;
        }

        int roll = ThreadLocalRandom.current().nextInt(1, 7);
        plugin.getMessages().raw(player, "&eThe die landed on &l" + roll + "&e!");

        if (roll == pick) {
            double multiplier = plugin.getConfig().getDouble("gambling.dice.payout-multiplier", 5.5);
            double winnings = bet * multiplier;
            payout(player, winnings);
            plugin.getMessages().success(player, "You won $" + (long) winnings + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            plugin.getMessages().error(player, "You lost $" + (long) bet + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    public void roulette(Player player, double bet, String colorPick) {
        if (!permCheck(player, "roulette", "vexlutils.gambling.roulette")) {
            return;
        }
        String color = colorPick.toLowerCase();
        if (!color.equals("red") && !color.equals("black") && !color.equals("green")) {
            plugin.getMessages().error(player, "Pick red, black, or green.");
            return;
        }
        if (!prepare(player, bet)) {
            return;
        }

        double greenChance = plugin.getConfig().getDouble("gambling.roulette.green-chance", 0.10);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String outcome = rng.nextDouble() < greenChance ? "green" : (rng.nextBoolean() ? "red" : "black");

        plugin.getMessages().raw(player, "&eThe wheel landed on &l" + outcome + "&e!");

        if (outcome.equals(color)) {
            double multiplier = color.equals("green")
                    ? plugin.getConfig().getDouble("gambling.roulette.green-multiplier", 8.0)
                    : plugin.getConfig().getDouble("gambling.roulette.red-black-multiplier", 1.9);
            double winnings = bet * multiplier;
            payout(player, winnings);
            plugin.getMessages().success(player, "You won $" + (long) winnings + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            plugin.getMessages().error(player, "You lost $" + (long) bet + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    public void wheel(Player player, double bet) {
        if (!permCheck(player, "wheel", "vexlutils.gambling.wheel") || !prepare(player, bet)) {
            return;
        }

        org.bukkit.configuration.ConfigurationSection segments = plugin.getConfig().getConfigurationSection("gambling.wheel.segments");
        if (segments == null || segments.getKeys(false).isEmpty()) {
            plugin.getMessages().error(player, "Wheel isn't configured. Refunding your bet.");
            payout(player, bet);
            return;
        }

        double totalWeight = 0;
        java.util.Map<Double, Integer> table = new java.util.LinkedHashMap<>();
        for (String key : segments.getKeys(false)) {
            try {
                double multiplier = Double.parseDouble(key);
                int weight = segments.getInt(key);
                table.put(multiplier, weight);
                totalWeight += weight;
            } catch (NumberFormatException ignored) {
                // skip bad keys
            }
        }
        if (table.isEmpty() || totalWeight <= 0) {
            plugin.getMessages().error(player, "Wheel isn't configured properly. Refunding your bet.");
            payout(player, bet);
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double chosenMultiplier = 0;
        double cumulative = 0;
        for (java.util.Map.Entry<Double, Integer> entry : table.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                chosenMultiplier = entry.getKey();
                break;
            }
        }

        plugin.getMessages().raw(player, "&eThe wheel landed on &l" + chosenMultiplier + "x&e!");

        if (chosenMultiplier <= 0) {
            plugin.getMessages().error(player, "You lost $" + (long) bet + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        double winnings = bet * chosenMultiplier;
        payout(player, winnings);
        if (chosenMultiplier > 1.0) {
            plugin.getMessages().success(player, "You won $" + (long) winnings + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            plugin.getMessages().send(player, "&7Got back $" + (long) winnings + ".");
        }
    }
}
