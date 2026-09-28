package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.duel.DuelManager;
import games.coob.smp.model.DeathMessages;
import games.coob.smp.model.Effects;
import games.coob.smp.settings.Settings;
import games.coob.smp.task.LocatorTask;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackedTarget;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.tracking.WaypointPacketSender;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SMPListener implements Listener {

    private static final SMPListener instance = new SMPListener();

    /** Trails stop after this many ticks even if the projectile is still flying. */
    private static final int MAX_TRAIL_TICKS = 200;

    public static SMPListener getInstance() {
        return instance;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // Hide the locator bar by default; it is enabled when tracking starts
        if (Settings.LocatorSection.ENABLE_TRACKING) {
            PlayerCache cache = PlayerCache.from(player);
            LocatorBarManager.initializePlayer(player);
            LocatorBarManager.disableReceive(player);
            LocatorBarManager.disableTransmit(player);

            // If player was tracking something, re-register them and show the bar
            if (cache.isTracking()) {
                TrackingRegistry.startTracking(player.getUniqueId());
                LocatorBarManager.enableReceive(player);
            }
        }
    }

    /**
     * Server list MOTD.
     */
    @EventHandler
    public void onServerListPing(final ServerListPingEvent event) {
        if (!Settings.MotdSection.ENABLE_MOTD || Settings.MotdSection.LINES.isEmpty())
            return;

        String online = String.valueOf(event.getNumPlayers());
        String max = String.valueOf(event.getMaxPlayers());
        Component motd = Component.empty();
        for (int i = 0; i < Settings.MotdSection.LINES.size(); i++) {
            String line = Settings.MotdSection.LINES.get(i).replace("{online}", online).replace("{max}", max);
            if (i > 0)
                motd = motd.append(Component.newline());
            motd = motd.append(ColorUtil.toComponent(line));
        }
        event.motd(motd);
    }

    /**
     * Make players take knockback when getting hit by a snowball, egg or fishing hook
     */
    @EventHandler
    public void onProjectileHit(final ProjectileHitEvent event) {
        final Projectile projectile = event.getEntity();

        if (event.getHitEntity() instanceof final Player player
                && (projectile instanceof Snowball || projectile instanceof Egg || projectile instanceof FishHook)
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && knockbackAllowed(projectile, player)) {
            player.damage(0.05, projectile);
            player.setVelocity(projectile.getVelocity().multiply(Settings.ProjectileSection.KNOCKBACK));

            if (Settings.ProjectileSection.ENABLE_HEADSHOT
                    && player.getLocation().getY() - projectile.getLocation().getY() <= -1.45)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 1, true));
        }
    }

    /** Respects PvP being off and duels (no pushing duelists from outside, or vice versa). */
    private static boolean knockbackAllowed(Projectile projectile, Player victim) {
        if (!(projectile.getShooter() instanceof Player shooter) || shooter.equals(victim))
            return true;
        if (!victim.getWorld().getPVP())
            return false;
        DuelManager duels = DuelManager.getInstance();
        return duels.getActiveDuel(shooter) == duels.getActiveDuel(victim);
    }

    /**
     * Display a trail for projectiles while they fly
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(final ProjectileLaunchEvent event) {
        if (!Settings.ProjectileSection.ENABLE_TRAILS)
            return;

        final Particle particle = getTrailParticle(Settings.ProjectileSection.ACTIVE_TRAIL);
        if (particle == null)
            return;

        final Projectile projectile = event.getEntity();
        final BukkitTask[] task = new BukkitTask[1];
        final int[] ticks = { 0 };

        task[0] = SchedulerUtil.runTimer(1, 1, () -> {
            if (!projectile.isValid() || ticks[0]++ >= MAX_TRAIL_TICKS
                    || (projectile instanceof AbstractArrow arrow && arrow.isInBlock())) {
                task[0].cancel();
                return;
            }
            projectile.getWorld().spawnParticle(particle, projectile.getLocation(), 1, 0, 0, 0, 0);
        });
    }

    private static Particle getTrailParticle(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "soul_fire_flame" -> Particle.SOUL_FIRE_FLAME;
            case "water_bubble" -> Particle.BUBBLE;
            case "nautilus" -> Particle.NAUTILUS;
            case "flame" -> Particle.FLAME;
            case "smoke" -> Particle.LARGE_SMOKE;
            case "electrik_spark" -> Particle.ELECTRIC_SPARK;
            case "enchantment" -> Particle.ENCHANT;
            case "honey" -> Particle.DRIPPING_HONEY;
            case "heart" -> Particle.HEART;
            case "music" -> Particle.NOTE;
            case "glow" -> Particle.GLOW;
            // Flash needs a colour in newer versions; the white sparkle looks the same
            case "flash" -> Particle.END_ROD;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();

        // Always set death location for /track death (independent of death storage)
        PlayerCache.from(player).setDeathLocation(player.getLocation());

        if (Settings.DeathEffectSection.ENABLE_DEATH_EFFECTS) {
            final Location location = player.getLocation().add(0, 1, 0);
            Effects.play(Settings.DeathEffectSection.ACTIVE_DEATH_EFFECT, location,
                    Settings.DeathEffectSection.DURATION_SECONDS);
        }
    }

    /**
     * Replaces the vanilla death message with a custom one from death-messages.yml.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathMessage(final PlayerDeathEvent event) {
        // Null or empty means another plugin (vanish, a duel) hid the message; leave it hidden
        if (!Settings.DeathMessageSection.ENABLED || event.deathMessage() == null
                || PlainTextComponentSerializer.plainText().serialize(event.deathMessage()).isBlank())
            return;

        final Player player = event.getEntity();
        final Entity causing = event.getDamageSource().getCausingEntity();
        Player killer = player.getKiller();
        if (killer == null && causing instanceof Player causingPlayer)
            killer = causingPlayer;
        final EntityDamageEvent lastDamage = player.getLastDamageCause();

        final Component message = DeathMessages.getInstance().build(player, killer, causing,
                lastDamage != null ? lastDamage.getCause() : null);
        if (message != null)
            event.deathMessage(message);
    }

    /**
     * Runs last so every other quit handler can still read the player's data.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();

        // Remove from tracking registry and clean up boss bar
        TrackingRegistry.stopTracking(player.getUniqueId());
        LocatorTask.cleanupPlayer(player.getUniqueId());
        LocatorBarManager.cleanupPlayer(player.getUniqueId());
        WaypointPacketSender.forget(player.getUniqueId());

        // Custom tracking: disable waypoint transmission so others can't track this player anymore
        if (Settings.LocatorSection.ENABLE_TRACKING)
            LocatorBarManager.disableTransmit(player);

        // Notify players who were tracking this player
        for (UUID trackerUUID : TrackingRegistry.getActiveTrackers().toArray(new UUID[0])) {
            Player tracker = Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline())
                continue;

            PlayerCache trackerCache = PlayerCache.from(tracker);
            TrackedTarget target = trackerCache.getTrackedTarget(player.getUniqueId());

            if (target != null) {
                trackerCache.removeTrackedPlayer(player.getUniqueId());
                WaypointPacketSender.removeWaypoint(tracker,
                        WaypointPacketSender.generateWaypointId(trackerUUID, player.getUniqueId()));
                ColorUtil.sendMessage(tracker, "&c" + player.getName() + " &chas gone offline. Tracking stopped.");

                // If not tracking anything anymore, stop completely
                if (!trackerCache.isTracking()) {
                    TrackingRegistry.stopTracking(trackerUUID);
                    LocatorBarManager.disableReceive(tracker);
                    LocatorBarManager.clearTarget(tracker);
                    LocatorTask.cleanupPlayer(trackerUUID);
                    WaypointPacketSender.clearWaypoint(tracker);
                }
            }
        }

        PlayerCache.unload(player.getUniqueId());
    }
}
