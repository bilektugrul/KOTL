package me.despical.kotl.events;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.clip.placeholderapi.PlaceholderAPI;
import me.despical.kotl.ConfigPreferences;
import me.despical.kotl.Main;
import me.despical.kotl.arena.Arena;
import me.despical.kotl.arena.ArenaRegistry;
import me.despical.kotl.user.User;

/**
 * @author Despical
 * <p>
 * Created at 22.06.2020
 */
public class ChatEvents implements Listener {

	private Main plugin;
	private String[] regexChars = new String[] { "$", "\\" };

	public ChatEvents(Main plugin) {
		this.plugin = plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	@EventHandler
	public void onChatIngame(AsyncPlayerChatEvent event) {
		Arena arena = ArenaRegistry.getArena(event.getPlayer());
		if (arena == null) {
			return;
		}
		if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.CHAT_FORMAT_ENABLED)) {
			event.setCancelled(true);
			Iterator<Player> iterator = event.getRecipients().iterator();
			List<Player> remove = new ArrayList<>();
			while (iterator.hasNext()) {
				Player player = iterator.next();
				remove.add(player);
			}
			for (Player player : remove) {
				event.getRecipients().remove(player);
			}
			remove.clear();
			String message;
			String eventMessage = event.getMessage();
			for (String regexChar : regexChars) {
				if (eventMessage.contains(regexChar)) {
					eventMessage = eventMessage.replaceAll(Pattern.quote(regexChar), "");
				}
			}
			message = formatChatPlaceholders(plugin.getChatManager().colorMessage("In-Game.Chat-Format"),
				plugin.getUserManager().getUser(event.getPlayer()), eventMessage);
			for (Player player : arena.getPlayers()) {
				player.sendMessage(message);
			}
			Bukkit.getConsoleSender().sendMessage(message);
		} else {
			event.getRecipients().clear();
			event.getRecipients().addAll(new ArrayList<>(arena.getPlayers()));
		}
	}

	private String formatChatPlaceholders(String message, User user, String saidMessage) {
		String formatted = message;
		formatted = plugin.getChatManager().colorRawMessage(formatted);
		formatted = StringUtils.replace(formatted, "%player%", user.getPlayer().getName());
		formatted = StringUtils.replace(formatted, "%message%", ChatColor.stripColor(saidMessage));
		if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			formatted = PlaceholderAPI.setPlaceholders(user.getPlayer(), formatted);
		}
		return formatted;
	}
}