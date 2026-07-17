package mc.mod.vexlUtils;

import mc.mod.vexlUtils.admin.AdminGUI;
import mc.mod.vexlUtils.chat.ChatManager;
import mc.mod.vexlUtils.command.AdminCommand;
import mc.mod.vexlUtils.command.DisguiseCommand;
import mc.mod.vexlUtils.command.PvCommand;
import mc.mod.vexlUtils.command.VCommand;
import mc.mod.vexlUtils.command.WarpCommand;
import mc.mod.vexlUtils.disguise.DisguiseManager;
import mc.mod.vexlUtils.jukebox.JukeboxListener;
import mc.mod.vexlUtils.lock.DoorListener;
import mc.mod.vexlUtils.maintenance.MaintenanceManager;
import mc.mod.vexlUtils.shop.SignShopListener;
import mc.mod.vexlUtils.util.MessageUtil;
import mc.mod.vexlUtils.vault.VaultListener;
import mc.mod.vexlUtils.vault.VaultManager;
import mc.mod.vexlUtils.warp.WarpListener;
import mc.mod.vexlUtils.warp.WarpManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VexlUtils extends JavaPlugin implements Listener {

    private static VexlUtils instance;

    private Economy economy;
    private MessageUtil messages;
    private VaultManager vaultManager;
    private DisguiseManager disguiseManager;
    private WarpManager warpManager;
    private MaintenanceManager maintenanceManager;
    private AdminGUI adminGUI;

    @Override
    public void onEnable() {
        instance = this;

        try {
            saveDefaultConfig();
        } catch (Exception e) {
            getLogger().severe("Failed to save/load config.yml: " + e.getMessage());
        }

        this.messages = new MessageUtil(this);

        if (!setupEconomy()) {
            getLogger().warning("No Vault economy provider found yet (need Vault + an economy plugin, e.g. CMI). " +
                    "Paid shops disabled until one registers.");
        }
        // catch economy plugins (like CMI) that register their provider a moment after us
        getServer().getPluginManager().registerEvents(this, this);
        // belt-and-suspenders: some economy plugins (e.g. CMI) can register their Vault provider
        // slightly after plugin enable order would suggest, so retry a couple of times too.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (economy == null && setupEconomy()) {
                getLogger().info("Economy hooked on delayed retry.");
            }
        }, 20L);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (economy == null && setupEconomy()) {
                getLogger().info("Economy hooked on delayed retry.");
            }
        }, 100L);

        this.vaultManager = new VaultManager(this);
        this.disguiseManager = new DisguiseManager(this);
        this.warpManager = new WarpManager(this);
        this.maintenanceManager = new MaintenanceManager(this);
        this.adminGUI = new AdminGUI(this);

        VCommand vCommand = new VCommand(this);
        registerCommand("vexl", vCommand, vCommand);

        PvCommand pvCommand = new PvCommand(this);
        registerCommand("pv", pvCommand, pvCommand);

        DisguiseCommand disguiseCommand = new DisguiseCommand(this);
        registerCommand("disguise", disguiseCommand, disguiseCommand);
        registerCommand("undisguise", disguiseCommand, disguiseCommand);

        WarpCommand warpCommand = new WarpCommand(this);
        registerCommand("playerwarp", warpCommand, warpCommand);

        AdminCommand adminCommand = new AdminCommand(this, adminGUI);
        registerCommand("va", adminCommand, null);
        registerCommand("vadmin", adminCommand, null);

        getServer().getPluginManager().registerEvents(new SignShopListener(this), this);
        getServer().getPluginManager().registerEvents(new DoorListener(this), this);
        getServer().getPluginManager().registerEvents(new VaultListener(this), this);
        getServer().getPluginManager().registerEvents(new WarpListener(this), this);
        getServer().getPluginManager().registerEvents(new JukeboxListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatManager(this), this);
        getServer().getPluginManager().registerEvents(maintenanceManager, this);
        getServer().getPluginManager().registerEvents(disguiseManager, this);
        getServer().getPluginManager().registerEvents(adminGUI, this);

        getLogger().info("VexlUtils enabled. /vexl (alias /v), /pv, /disguise, /undisguise, /playerwarp (aliases /pwarp, /pw), /va (alias /vadmin) are live.");
    }

    @Override
    public void onDisable() {
        if (vaultManager != null) {
            vaultManager.saveAll();
        }
        if (disguiseManager != null) {
            disguiseManager.undisguiseAll();
        }
        if (warpManager != null) {
            warpManager.saveAll();
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml - this shouldn't happen.");
            return;
        }
        cmd.setExecutor(executor);
        if (tabCompleter != null) {
            cmd.setTabCompleter(tabCompleter);
        }
    }

    private boolean setupEconomy() {
        try {
            if (getServer().getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            this.economy = rsp.getProvider();
            getLogger().info("Hooked economy provider: " + economy.getName());
            return true;
        } catch (Exception e) {
            getLogger().warning("Error hooking economy: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (economy == null && event.getProvider().getService().equals(Economy.class)) {
            setupEconomy();
        }
    }

    public static VexlUtils get() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public MessageUtil getMessages() {
        return messages;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public DisguiseManager getDisguiseManager() {
        return disguiseManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public AdminGUI getAdminGUI() {
        return adminGUI;
    }
}
