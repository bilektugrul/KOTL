package me.despical.kotl.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import me.despical.kotl.Main;
import me.despical.kotl.commands.exception.CommandException;

/**
 * @author Despical
 * <p>
 * Created at 22.06.2020
 */
public abstract class SubCommand {
	
	private Main plugin = JavaPlugin.getPlugin(Main.class);
	private String name;
	private String permission;
	private String[] aliases;

	public SubCommand(String name) {
		this(name, new String[0]);
	}
	
	public SubCommand(String name, String... aliases) {
		this.name = name;
		this.aliases = aliases;
	}
	
	public String getName() {
		return name;
	}
		
	public void setPermission(String permission) {
		this.permission = permission;
	}
	
	public String getPermission() {
		return permission;
	}
	
	public Main getPlugin() {
		return plugin;
	}
		
	public final boolean hasPermission(CommandSender sender) {
			if (permission == null) return true;
		return sender.hasPermission(permission);
	}
	
	public abstract String getPossibleArguments();

	public abstract int getMinimumArguments();

	public abstract void execute(CommandSender sender, String label, String[] args) throws CommandException;
	
	public abstract List<String> getTutorial();
	
	public abstract CommandType getType();
	
	public abstract SenderType getSenderType();
	
	public enum CommandType {
		GENERIC, HIDDEN
	}
	
	public enum SenderType {
		PLAYER, BOTH;
	}
	
	public final boolean isValidTrigger(String name) {
		if (this.name.equalsIgnoreCase(name)) {
			return true;
		}
		if (aliases != null) {
			for (String alias : aliases) {
				if (alias.equalsIgnoreCase(name)) {
					return true;
				}
			}
		}	
		return false;
	}
}