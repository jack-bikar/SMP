package games.coob.smp.nickname;

import games.coob.smp.config.ConfigFile;
import games.coob.smp.menu.NickMenu;
import games.coob.smp.settings.Settings;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Nicknames: stored in nicknames.yml and shown in chat, the tab list,
 * join/leave messages, death messages and the locator bar. Nothing runs in
 * the background; names are applied when they change and when players join.
 */
public final class NicknameManager extends ConfigFile implements Listener {

	@Getter
	private static final NicknameManager instance = new NicknameManager();

	/** Letters, numbers and underscores, with single spaces between words. */
	private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+( [A-Za-z0-9_]+)*");
	private static final int MIN_LENGTH = 3;
	private static final long PROMPT_MILLIS = 60_000;

	private Map<UUID, Nickname> nicknames;
	/** Players asked to type their new nickname in chat -> when the prompt expires. */
	private final Map<UUID, Long> prompts = new ConcurrentHashMap<>();

	private NicknameManager() {
		super("nicknames.yml");
	}

	public static boolean isEnabled() {
		return Settings.NicknameSection.ENABLED;
	}

	// -------------------------------------------------------------------------
	// Storage
	// -------------------------------------------------------------------------

	@Override
	protected void onLoad() {
		nicknames = new HashMap<>();
		ConfigurationSection section = getConfig().getConfigurationSection("Players");
		if (section == null)
			return;

		for (String id : section.getKeys(false)) {
			ConfigurationSection data = section.getConfigurationSection(id);
			if (data == null)
				continue;
			try {
				Nickname nickname = new Nickname();
				nickname.setText(data.getString("Text"));
				for (String hex : data.getStringList("Colors")) {
					TextColor color = TextColor.fromHexString(hex);
					if (color != null)
						nickname.getColors().add(color);
				}
				nickname.setRainbow(data.getBoolean("Rainbow"));
				nickname.setBold(data.getBoolean("Bold"));
				nickname.setItalic(data.getBoolean("Italic"));
				nickname.setUnderlined(data.getBoolean("Underlined"));
				nicknames.put(UUID.fromString(id), nickname);
			} catch (IllegalArgumentException ignored) {
				// Bad entry, skip it
			}
		}
	}

	@Override
	protected void onSave() {
		getConfig().set("Players", null);
		for (Map.Entry<UUID, Nickname> entry : nicknames.entrySet()) {
			Nickname nickname = entry.getValue();
			if (nickname.isEmpty())
				continue;
			String path = "Players." + entry.getKey() + ".";
			getConfig().set(path + "Text", nickname.getText());
			List<String> colors = new ArrayList<>();
			for (TextColor color : nickname.getColors())
				colors.add(color.asHexString());
			getConfig().set(path + "Colors", colors);
			getConfig().set(path + "Rainbow", nickname.isRainbow());
			getConfig().set(path + "Bold", nickname.isBold());
			getConfig().set(path + "Italic", nickname.isItalic());
			getConfig().set(path + "Underlined", nickname.isUnderlined());
		}
	}

	/** The player's nickname settings to change (created empty if they have none yet). */
	public Nickname get(Player player) {
		return nicknames.computeIfAbsent(player.getUniqueId(), id -> new Nickname());
	}

	/** The player's nickname settings for reading only (nothing is created). */
	public Nickname peek(Player player) {
		Nickname nickname = nicknames.get(player.getUniqueId());
		return nickname != null ? nickname : new Nickname();
	}

	public boolean hasNickname(Player player) {
		Nickname nickname = nicknames.get(player.getUniqueId());
		return nickname != null && !nickname.isEmpty();
	}

	/** Saves and shows the player's changed nickname. */
	public void update(Player player) {
		Nickname nickname = nicknames.get(player.getUniqueId());
		if (nickname != null && nickname.isEmpty())
			nicknames.remove(player.getUniqueId());
		save();
		apply(player);
	}

	public void reset(UUID playerId) {
		prompts.remove(playerId);
		if (nicknames.remove(playerId) != null)
			save();
		removeNameTag(playerId);
		Player player = Bukkit.getPlayer(playerId);
		if (player != null)
			apply(player);
	}

	// -------------------------------------------------------------------------
	// Showing names
	// -------------------------------------------------------------------------

	/** Sets the chat and tab list name (and the name tag, if enabled). */
	public void apply(Player player) {
		if (!isEnabled() || !hasNickname(player)) {
			player.displayName(null);
			player.playerListName(null);
			removeNameTag(player);
			return;
		}
		Nickname nickname = nicknames.get(player.getUniqueId());
		Component name = nickname.render(player.getName());
		player.displayName(name);
		player.playerListName(name);

		if (Settings.NicknameSection.SHOW_ABOVE_HEAD && nickname.getText() != null) {
			showNameTag(player, name);
		} else {
			removeNameTag(player);
		}
	}

	// -------------------------------------------------------------------------
	// Name tag above the head: "Nickname (AccountName)" via a scoreboard team prefix.
	// The game draws it itself, so there is nothing to update or keep attached.
	// -------------------------------------------------------------------------

	private static final String TEAM_PREFIX = "smp_nick_";

	private static String teamName(UUID playerId) {
		return TEAM_PREFIX + playerId.toString().replace("-", "").substring(0, 12);
	}

	private static void showNameTag(Player player, Component nickname) {
		Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
		Team team = scoreboard.getTeam(teamName(player.getUniqueId()));
		if (team == null)
			team = scoreboard.registerNewTeam(teamName(player.getUniqueId()));
		team.prefix(nickname.append(Component.text(" (", NamedTextColor.GRAY)));
		team.suffix(Component.text(")", NamedTextColor.GRAY));
		if (!team.hasEntry(player.getName()))
			team.addEntry(player.getName());
	}

	private static void removeNameTag(Player player) {
		removeNameTag(player.getUniqueId());
	}

	private static void removeNameTag(UUID playerId) {
		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(playerId));
		if (team != null)
			team.unregister();
	}

	/** Removes all name tag teams (on disable, and leftovers from a crash on enable). */
	public static void removeAllNameTags() {
		for (Team team : new ArrayList<>(Bukkit.getScoreboardManager().getMainScoreboard().getTeams())) {
			if (team.getName().startsWith(TEAM_PREFIX))
				team.unregister();
		}
	}

	public void applyToOnlinePlayers() {
		for (Player player : Bukkit.getOnlinePlayers())
			apply(player);
	}

	/**
	 * The name to show for a player in plugin messages: their nickname, or their
	 * account name in the given colour.
	 */
	public Component nameOf(Player player, NamedTextColor fallbackColor) {
		if (isEnabled() && hasNickname(player))
			// Nicknames without a colour of their own use the fallback colour
			return Component.text("", fallbackColor).append(nicknames.get(player.getUniqueId()).render(player.getName()));
		return Component.text(player.getName(), fallbackColor);
	}

	/** A preview of how a nickname looks. */
	public Component preview(Player player) {
		return peek(player).render(player.getName());
	}

	// -------------------------------------------------------------------------
	// Changing the text
	// -------------------------------------------------------------------------

	/**
	 * Sets the nickname text.
	 *
	 * @return null on success, otherwise why it isn't allowed
	 */
	public String setText(Player player, String text) {
		String problem = checkText(player, text);
		if (problem != null)
			return problem;

		cancelPrompt(player);
		// Typing your own account name just clears the custom text
		get(player).setText(text.equalsIgnoreCase(player.getName()) ? null : text);
		update(player);
		return null;
	}

	private String checkText(Player player, String text) {
		int max = Settings.NicknameSection.MAX_LENGTH;
		if (text.length() < MIN_LENGTH || text.length() > max)
			return "Nicknames must be " + MIN_LENGTH + " to " + max + " characters long.";
		if (!ALLOWED.matcher(text).matches())
			return "Only letters, numbers, underscores and single spaces are allowed.";
		if (text.equalsIgnoreCase(player.getName()))
			return null;

		OfflinePlayer account = Bukkit.getOfflinePlayerIfCached(text);
		if (account != null && !account.getUniqueId().equals(player.getUniqueId()))
			return "That's another player's name.";
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (!online.equals(player) && online.getName().equalsIgnoreCase(text))
				return "That's another player's name.";
		}
		for (OfflinePlayer whitelisted : Bukkit.getWhitelistedPlayers()) {
			if (!whitelisted.getUniqueId().equals(player.getUniqueId()) && text.equalsIgnoreCase(whitelisted.getName()))
				return "That's another player's name.";
		}
		for (Map.Entry<UUID, Nickname> entry : nicknames.entrySet()) {
			String other = entry.getValue().getText();
			if (!entry.getKey().equals(player.getUniqueId()) && other != null && other.equalsIgnoreCase(text))
				return "Someone already uses that nickname.";
		}
		return null;
	}

	/** Forgets a pending "type your nickname in chat" prompt. */
	public void cancelPrompt(Player player) {
		prompts.remove(player.getUniqueId());
	}

	/** Asks the player to type their nickname in chat. */
	public void promptForText(Player player) {
		prompts.put(player.getUniqueId(), System.currentTimeMillis() + PROMPT_MILLIS);
		player.closeInventory();
		ColorUtil.sendMessage(player, "&eType your new nickname in chat, or &6cancel&e. &7(letters, numbers, _ and spaces)");
	}

	/** The next chat message after "Change name" becomes the nickname instead of being sent. */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onChat(AsyncChatEvent event) {
		Long expiry = prompts.remove(event.getPlayer().getUniqueId());
		if (expiry == null || expiry < System.currentTimeMillis())
			return;

		event.setCancelled(true);
		String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
		Player player = event.getPlayer();

		SchedulerUtil.runTask(() -> {
			if (!player.isOnline())
				return;
			if (text.equalsIgnoreCase("cancel")) {
				ColorUtil.sendMessage(player, "&7Nickname unchanged.");
				return;
			}
			String problem = setText(player, text);
			if (problem != null) {
				ColorUtil.sendMessage(player, "&c" + problem);
				return;
			}
			player.sendMessage(Component.text("Your nickname is now ", NamedTextColor.GREEN).append(preview(player)));
			new NickMenu(player).displayTo(player);
		});
	}

	// -------------------------------------------------------------------------
	// Join / quit
	// -------------------------------------------------------------------------

	/** Early, so other plugins and the join message already see the nickname. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoinApply(PlayerJoinEvent event) {
		Player joining = event.getPlayer();
		releaseNameTakenBy(joining);
		apply(joining);
	}

	/**
	 * Someone joined whose account name another player uses as a nickname
	 * (possible if they had never joined before): the real player keeps their name.
	 */
	private void releaseNameTakenBy(Player joining) {
		for (Map.Entry<UUID, Nickname> entry : new ArrayList<>(nicknames.entrySet())) {
			String text = entry.getValue().getText();
			if (entry.getKey().equals(joining.getUniqueId()) || text == null || !text.equalsIgnoreCase(joining.getName()))
				continue;

			entry.getValue().setText(null);
			Player owner = Bukkit.getPlayer(entry.getKey());
			if (owner != null) {
				update(owner);
				ColorUtil.sendMessage(owner, "&e" + joining.getName()
						+ " joined, and that's their real name, so your nickname text was removed. Pick a new one with /nick.");
			} else {
				if (entry.getValue().isEmpty())
					nicknames.remove(entry.getKey());
				save();
			}
		}
	}

	@EventHandler
	public void onJoinMessage(PlayerJoinEvent event) {
		if (isEnabled() && hasNickname(event.getPlayer()))
			event.joinMessage(withNickname(event.joinMessage(), event.getPlayer()));
	}

	/**
	 * Swaps the name in the vanilla join/leave message for the nickname. Messages
	 * set by other plugins (or hidden ones) are left alone.
	 */
	private static Component withNickname(Component message, Player player) {
		if (!(message instanceof TranslatableComponent translatable)
				|| !translatable.key().startsWith("multiplayer.player.") || translatable.arguments().isEmpty())
			return message;
		List<ComponentLike> arguments = new ArrayList<>(translatable.arguments());
		arguments.set(0, player.displayName());
		return translatable.arguments(arguments);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		prompts.remove(event.getPlayer().getUniqueId());
		removeNameTag(event.getPlayer());
		if (isEnabled() && hasNickname(event.getPlayer()))
			event.quitMessage(withNickname(event.quitMessage(), event.getPlayer()));
	}
}
