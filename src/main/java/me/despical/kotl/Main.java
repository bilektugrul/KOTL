package me.despical.kotl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;

import me.despical.commonsbox.configuration.ConfigUtils;
import me.despical.commonsbox.database.MysqlDatabase;
import me.despical.commonsbox.serializer.InventorySerializer;
import me.despical.kotl.api.StatsStorage;
import me.despical.kotl.arena.Arena;
import me.despical.kotl.arena.ArenaEvents;
import me.despical.kotl.arena.ArenaRegistry;
import me.despical.kotl.commands.CommandHandler;
import me.despical.kotl.events.ChatEvents;
import me.despical.kotl.events.Events;
import me.despical.kotl.events.JoinEvent;
import me.despical.kotl.events.QuitEvent;
import me.despical.kotl.handler.ChatManager;
import me.despical.kotl.handler.PlaceholderManager;
import me.despical.kotl.user.User;
import me.despical.kotl.user.UserManager;
import me.despical.kotl.user.data.MysqlManager;
import me.despical.kotl.utils.CuboidSelector;
import me.despical.kotl.utils.Debugger;
import me.despical.kotl.utils.ExceptionLogHandler;
import me.despical.kotl.utils.MessageUtils;
import me.despical.kotl.utils.UpdateChecker;

/**
 * @author Despical
 * <p>
 * Created at 20.06.2020
 */
public class Main extends JavaPlugin {

	private ExceptionLogHandler exceptionLogHandler;
	private String version;
	private boolean forceDisable = false;
	private HookManager hookManager;
	private ConfigPreferences configPreferences;
	private MysqlDatabase database;
	private UserManager userManager;
	private CommandHandler commandHandler;
	private CuboidSelector cuboidSelector;
	private ChatManager chatManager;
	
	@Override
	public void onEnable() {
		if (!validateIfPluginShouldStart()) {
			return;
		}
		exceptionLogHandler = new ExceptionLogHandler(this);
		saveDefaultConfig();
		if (getDescription().getVersion().contains("d")) {
			Debugger.setEnabled(true);
		} else {
			Debugger.setEnabled(getConfig().getBoolean("Debug-Messages", false));
		}
		Debugger.debug(Level.INFO, "Initialization start");
		if (getConfig().getBoolean("Developer-Mode", false)) {
			Debugger.deepDebug(true);
			Debugger.debug(Level.INFO, "Deep debug enabled");
			for (String listenable : new ArrayList<>(getConfig().getStringList("Listanable-Performances"))) {
				Debugger.monitorPerformance(listenable);
			}
		}
		long start = System.currentTimeMillis();
		
		configPreferences = new ConfigPreferences(this);
		setupFiles();
		initializeClasses();
		checkUpdate();

		Debugger.debug(Level.INFO, "Plugin loaded! Hooking into soft-dependencies in a while!");
		Bukkit.getScheduler().runTaskLater(this, () -> hookManager = new HookManager(), 20L * 5);
		
		Debugger.debug(Level.INFO, "Initialization finished took {0} ms", System.currentTimeMillis() - start);
	}
	
	private boolean validateIfPluginShouldStart() {
		version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		if (!(version.equalsIgnoreCase("v1_9_R1") || version.equalsIgnoreCase("v1_9_R2")
			|| version.equalsIgnoreCase("v1_10_R1") || version.equalsIgnoreCase("v1_11_R1")
			|| version.equalsIgnoreCase("v1_12_R1") || version.equalsIgnoreCase("v1_13_R1")
			|| version.equalsIgnoreCase("v1_13_R2") || version.equalsIgnoreCase("v1_14_R1")
			|| version.equalsIgnoreCase("v1_15_R1") /**|| version.equalsIgnoreCase("v1_16_R1") Still waiting for dependency update*/)) {
			MessageUtils.thisVersionIsNotSupported();
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Your server version is not supported by King of the Ladder!");
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Sadly, we must shut off. Maybe you consider changing your server version?");
			forceDisable = true;
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
		try {
			Class.forName("org.spigotmc.SpigotConfig");
		} catch (Exception e) {
			MessageUtils.thisVersionIsNotSupported();
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Your server software is not supported by King of the Ladder!");
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "We support only Spigot and Spigot forks only! Shutting off...");
			forceDisable = true;
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
		return true;
	}
	
	@Override
	public void onDisable() {
		if (forceDisable) {
			return;
		}
		Debugger.debug(Level.INFO, "System disable initialized");
		long start = System.currentTimeMillis();
		
		Bukkit.getLogger().removeHandler(exceptionLogHandler);
		saveAllUserStatistics();
		if (hookManager != null && hookManager.isFeatureEnabled(HookManager.HookFeature.HOLOGRAPHIC_DISPLAYS)) {
			for (Hologram hologram : HologramsAPI.getHolograms(this)) {
				hologram.delete();
			}
		}
		if (configPreferences.getOption(ConfigPreferences.Option.DATABASE_ENABLED)) {
			getMysqlDatabase().shutdownConnPool();
		}
		for (Arena arena : ArenaRegistry.getArenas()) {
			for (Player player : arena.getPlayers()) {
				if (getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
					InventorySerializer.loadInventory(this, player);
				} else {
					player.getInventory().clear();
					player.getInventory().setArmorContents(null);
				}
				player.teleport(arena.getEndLocation());
				arena.doBarAction(Arena.BarAction.REMOVE, player);
			}
			arena.getPlayers().clear();
		}
		Debugger.debug(Level.INFO, "System disable finished took {0} ms", System.currentTimeMillis() - start);
	}
	
	private void initializeClasses() {
		if (configPreferences.getOption(ConfigPreferences.Option.DATABASE_ENABLED)) {
			FileConfiguration config = ConfigUtils.getConfig(this, "mysql");
			database = new MysqlDatabase(config.getString("user"), config.getString("password"), config.getString("address"));
		}
		userManager = new UserManager(this);
		registerSoftDependenciesAndServices();
		commandHandler = new CommandHandler(this);
		chatManager = new ChatManager(this);
		cuboidSelector = new CuboidSelector(this);
		ArenaRegistry.registerArenas();
		new ChatEvents(this);
		new Events(this);
		new ArenaEvents(this);
		new JoinEvent(this);
		new QuitEvent(this);
	}
	
	private void registerSoftDependenciesAndServices() {
		Debugger.debug(Level.INFO, "Hooking into soft dependencies");
		long start = System.currentTimeMillis();

		startPluginMetrics();
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			Debugger.debug(Level.INFO, "Hooking into PlaceholderAPI");
			new PlaceholderManager().register();
		}
		Debugger.debug(Level.INFO, "Hooked into soft dependencies took {0} ms", System.currentTimeMillis() - start);
	}
	
	private void startPluginMetrics() {
		Metrics metrics = new Metrics(this);
		metrics.addCustomChart(new Metrics.SimplePie("database_enabled", () -> String.valueOf(configPreferences.getOption(ConfigPreferences.Option.DATABASE_ENABLED))));
		metrics.addCustomChart(new Metrics.SimplePie("update_notifier", () -> {
			if (getConfig().getBoolean("Update-Notifier.Enabled", true)) {
				if (getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true)) {
					return "Enabled with beta notifier";
				} else {
					return "Enabled";
				}
			} else {
				if (getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true)) {
					return "Beta notifier only";
				} else {
					return "Disabled";
				}
			}
		}));
	}
	
	private void checkUpdate() {
		if (!getConfig().getBoolean("Update-Notifier.Enabled", true)) {
			return;
		}
		UpdateChecker.init(this, 1).requestUpdateCheck().whenComplete((result, exception) -> {
			if (!result.requiresUpdate()) {
				return;
			}
			if (result.getNewestVersion().contains("b")) {
				if (getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true)) {
					Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_AQUA + "[KOTL] " + ChatColor.AQUA + "Your software is ready for update! However it's a BETA VERSION. Proceed with caution.");
					Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_AQUA + "[KOTL] " + ChatColor.AQUA + "Current version %old%, latest version %new%".replace("%old%", getDescription().getVersion())
						.replace("%new%", result.getNewestVersion()));
				}
				return;
			}
			MessageUtils.updateIsHere();
			Bukkit.getConsoleSender().sendMessage("[KOTL] Found a new version avaible: v" + result.getNewestVersion());
			Bukkit.getConsoleSender().sendMessage("[KOTL] Download it on SpigotMC:");
			Bukkit.getConsoleSender().sendMessage("[KOTL] NOT IMPLEMENTED YET");
		});
	}
	
	private void setupFiles() {
		for (String fileName : Arrays.asList("arenas", "stats", "mysql")) {
			File file = new File(getDataFolder() + File.separator + fileName + ".yml");
			if (!file.exists()) {
				saveResource(fileName + ".yml", false);
			}
		}
	}
	
	public HookManager getHookManager() {
		return hookManager;
	}
	
	public ConfigPreferences getConfigPreferences() {
		return configPreferences;
	}
	
	public MysqlDatabase getMysqlDatabase() {
		return database;
	}
	
	public CommandHandler getCommandHandler() {
		return commandHandler;
	}
	
	public CuboidSelector getCuboidSelector() {
		return cuboidSelector;
	}
	
	public ChatManager getChatManager() {
		return chatManager;
	}
	
	public UserManager getUserManager() {
		return userManager;
	}
	
	private void saveAllUserStatistics() {
		for (Player player : getServer().getOnlinePlayers()) {
			User user = userManager.getUser(player);

			for (StatsStorage.StatisticType stat : StatsStorage.StatisticType.values()) {
				if (userManager.getDatabase() instanceof MysqlManager) {
					((MysqlManager) userManager.getDatabase()).getDatabase().executeUpdate("UPDATE playerstats SET " + stat.getName() + "=" + user.getStat(stat)
						+ " WHERE UUID='" + user.getPlayer().getUniqueId().toString() + "';");
					Debugger.debug(Level.INFO, "Executed MySQL: " + "UPDATE playerstats SET " + stat.getName() + "=" + user.getStat(stat) + " WHERE UUID='" + user.getPlayer().getUniqueId().toString() + "';");
					continue;
				}
				userManager.getDatabase().saveStatistic(user, stat);
			}
		}
	}
}