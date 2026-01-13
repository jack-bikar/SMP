package games.coob.smp.listener;

import de.slikey.effectlib.effect.*;
import games.coob.smp.hologram.BukkitHologram;
import games.coob.smp.hologram.HologramRegistry;
import games.coob.smp.PlayerCache;
import games.coob.smp.model.Effects;
import games.coob.smp.settings.Settings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.EntityUtil;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompParticle;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SMPListener implements Listener { // TODO add the register events annotation

	@Getter
	private static final Listener instance = new SMPListener();

	@EventHandler
	public void onJoin(final PlayerJoinEvent event) {
		final Player player = event.getPlayer();

		PlayerCache.from(player); // Load player's cache
		displayHealth(player);
	}

	/**
	 * Make players take knockback when getting hit by a projectile
	 *
	 * @param event
	 */
	@EventHandler
	public void onProjectileHit(final ProjectileHitEvent event) {
		final Projectile projectile = event.getEntity();

		if (event.getHitEntity() instanceof final Player player) {
			if (projectile instanceof Snowball || projectile instanceof Egg || projectile instanceof FishHook) {
				if (player.getGameMode() != GameMode.CREATIVE) {
					player.damage(0.05, projectile);
					player.setVelocity(projectile.getVelocity().multiply(Settings.ProjectileSection.KNOCKBACK));

					if (Settings.ProjectileSection.ENABLE_HEADSHOT)
						if (player.getLocation().getY() - projectile.getLocation().getY() <= -1.45)
							player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 1, true));
				}
			}
		}
	}

	/**
	 * Display a trail for projectiles when launching them
	 *
	 * @param event
	 */
	@EventHandler
	public void onProjectileLaunch(final ProjectileLaunchEvent event) {
		final Projectile projectile = event.getEntity();

		EntityUtil.trackFlying(projectile, () -> {
			final Location location = projectile.getLocation();

			if (Settings.ProjectileSection.ENABLE_TRAILS) {
				switch (Settings.ProjectileSection.ACTIVE_TRAIL) {
					case "soul_fire_flame" -> CompParticle.SOUL_FIRE_FLAME.spawn(location);
					case "water_bubble" -> CompParticle.WATER_BUBBLE.spawn(location);
					case "nautilus" -> CompParticle.NAUTILUS.spawn(location);
					case "flame" -> CompParticle.FLAME.spawn(location);
					case "smoke" -> CompParticle.SMOKE_LARGE.spawn(location);
					case "electrik_spark" -> CompParticle.ELECTRIC_SPARK.spawn(location);
					case "enchantment" -> CompParticle.ENCHANTMENT_TABLE.spawn(location);
					case "honey" -> CompParticle.DRIPPING_HONEY.spawn(location);
					case "flash" -> CompParticle.FLASH.spawn(location);
					case "heart" -> CompParticle.HEART.spawn(location);
					case "music" -> CompParticle.NOTE.spawn(location);
					case "glow" -> CompParticle.GLOW.spawn(location);
				}
			}
		});
	}

	@EventHandler
	public void onAsyncPlayerChat(final AsyncPlayerChatEvent event) {
		final Player player = event.getPlayer();

		if (event.getMessage().contains("fuck"))
			event.setMessage("potato");

		if (player.getLevel() == 10)
			event.setFormat(Common.colorize("&b" + event.getPlayer().getName() + "&7: &f" + event.getMessage()));
		else if (player.getLevel() == 25)
			event.setFormat(Common.colorize("&5" + event.getPlayer().getName() + "&7: &f" + event.getMessage()));
		else if (player.getLevel() == 50)
			event.setFormat(Common.colorize("&6" + event.getPlayer().getName() + "&7: &f" + event.getMessage()));
	}

	@EventHandler
	public void onServerListPing(final ServerListPingEvent event) {
		event.setMotd(Common.colorize(Settings.MOTDSection.MOTD_TEXT));
	}

	@EventHandler
	public void onPlayerDamageByEntity(final EntityDamageByEntityEvent event) {
		final Entity entity = event.getEntity();

		if (entity instanceof Player player) {
			final PlayerCache cache = PlayerCache.from(player);

			new BukkitRunnable() {

				@Override
				public void run() {
					cache.setSecondsAfterDamage(cache.getSecondsAfterDamage() + 1);

					if (cache.getSecondsAfterDamage() == 10)
						this.cancel();
				}
			}.runTaskTimer(SimplePlugin.getInstance(), 0, 20);
		}
	}

	@EventHandler
	public void onPlayerDeath(final PlayerDeathEvent event) {
		final Player player = event.getEntity();
		final Location location = player.getLocation().clone().add(0, 1, 0);

		// TODO only use if the server has the EffectLib plugin
		if (Settings.DeathEffectSection.ENABLE_DEATH_EFFECTS && Common.doesPluginExist("EffectLib")) {
			switch (Settings.DeathEffectSection.ACTIVE_DEATH_EFFECT) {
				case "grid" -> {
					final GridEffect effect = new GridEffect(Effects.getEffectManager());

					// The effect won't stop
					effect.iterations = -1;
					// Edit the effect
					// effect.particles = 40;
					// effect.particle = Particle.DRAGON_BREATH;
					effect.pitch = .5f;
					effect.setLocation(location);
					//Effects.getEffectManager().display(Particle);

					effect.run();
				}
				case "sky_rocket" -> {
					final SkyRocketEffect effect = new SkyRocketEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "big_bang" -> {
					final BigBangEffect effect = new BigBangEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "tornado" -> {
					final TornadoEffect effect = new TornadoEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "disco_ball" -> {
					final DiscoBallEffect effect = new DiscoBallEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "bleed" -> {
					final BleedEffect effect = new BleedEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "vortex" -> {
					final VortexEffect effect = new VortexEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "star" -> {
					final StarEffect effect = new StarEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "cloud" -> {
					final CloudEffect effect = new CloudEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "dragon" -> {
					final DragonEffect effect = new DragonEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
				case "fountain" -> {
					final FountainEffect effect = new FountainEffect(Effects.getEffectManager());

					effect.iterations = -1;
					effect.pitch = .5f;
					effect.setLocation(location);
					effect.run();
				}
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(final PlayerQuitEvent event) {
		final Player player = event.getPlayer();
		final PlayerCache cache = PlayerCache.from(player);
		final HologramRegistry registry = HologramRegistry.getInstance();

		if (cache.isDrawingAxe()) {
			player.removePotionEffect(PotionEffectType.SLOW);
			player.removePotionEffect(PotionEffectType.JUMP);
			cache.setDrawingAxe(false);
		}

		if (cache.isInCombat()) {
			cache.setInCombat(false);
			player.setHealth(0);
		}

		for (final BukkitHologram hologram : registry.getLoadedHolograms())
			if (player.hasMetadata(hologram.getUniqueId().toString()))
				player.removeMetadata(hologram.getUniqueId().toString(), SimplePlugin.getInstance());

	}

	@EventHandler
	public void onPlayerDamage(final EntityDamageByEntityEvent event) {
		final Entity entity = event.getEntity();

		if (entity instanceof Player player) {
			final PlayerCache cache = PlayerCache.from(player);

			cache.setInCombat(true);
			Common.runLater(20 * Settings.CombatSection.SECONDS_TILL_PLAYER_LEAVES_COMBAT, () -> cache.setInCombat(false));
		}
	}

	private void displayHealth(final Player player) {
		final ScoreboardManager manager = Bukkit.getScoreboardManager();

		if (manager != null) {
			final Scoreboard board = manager.getNewScoreboard();
			final Objective objective = board.registerNewObjective("showhealth", Criterias.HEALTH, Common.colorize("&c❤"));

			objective.setDisplaySlot(DisplaySlot.BELOW_NAME);

			player.setHealth(player.getHealth());
			player.setScoreboard(board);
		}
	}
}