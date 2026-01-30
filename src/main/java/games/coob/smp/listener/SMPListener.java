package games.coob.smp.listener;

import de.slikey.effectlib.effect.*;
import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.hologram.BukkitHologram;
import games.coob.smp.model.DeathChestRegistry;
import games.coob.smp.model.Effects;
import games.coob.smp.settings.Settings;
import games.coob.smp.tracking.LocatorBarManager;
import games.coob.smp.tracking.TrackingRegistry;
import games.coob.smp.util.ColorUtil;
import games.coob.smp.util.EntityUtil;
import games.coob.smp.util.PluginUtil;
import games.coob.smp.util.SchedulerUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SMPListener implements Listener { // TODO add the register events annotation

    private static final SMPListener instance = new SMPListener();

    public static SMPListener getInstance() {
        return instance;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        PlayerCache cache = PlayerCache.from(player);

        // Hide locator bar by default (will be enabled when tracking starts)
        if (Settings.LocatorSection.ENABLE_TRACKING) {
            LocatorBarManager.disableReceive(player);

            // If player was tracking something, re-register them
            if (cache.getTrackingLocation() != null) {
                TrackingRegistry.startTracking(player.getUniqueId());
            }
        }
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

        EntityUtil.trackFlying(projectile, (loc) -> {
            if (Settings.ProjectileSection.ENABLE_TRAILS) {
                Particle particle = null;
                switch (Settings.ProjectileSection.ACTIVE_TRAIL) {
                    case "soul_fire_flame" -> particle = Particle.SOUL_FIRE_FLAME;
                    case "water_bubble" -> particle = Particle.BUBBLE;
                    case "nautilus" -> particle = Particle.NAUTILUS;
                    case "flame" -> particle = Particle.FLAME;
                    case "smoke" -> particle = Particle.LARGE_SMOKE;
                    case "electrik_spark" -> particle = Particle.ELECTRIC_SPARK;
                    case "enchantment" -> particle = Particle.ENCHANT;
                    case "honey" -> particle = Particle.DRIPPING_HONEY;
                    case "flash" -> particle = Particle.FLASH;
                    case "heart" -> particle = Particle.HEART;
                    case "music" -> particle = Particle.NOTE;
                    case "glow" -> particle = Particle.GLOW;
                }
                if (particle != null) {
                    loc.getWorld().spawnParticle(particle, loc, 1);
                }
            }
        });
    }

    @EventHandler
    public void onPlayerDamageByEntity(final EntityDamageByEntityEvent event) {
        final Entity entity = event.getEntity();

        if (entity instanceof final Player player) {
            final PlayerCache cache = PlayerCache.from(player);

            SchedulerUtil.runTimer(0, 20, new Runnable() {
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    cache.setSecondsAfterDamage(cache.getSecondsAfterDamage() + 1);

                    if (ticks >= 10) {
                        // Task completed
                    }
                }
            });
        }
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Location location = player.getLocation().clone().add(0, 1, 0);

        // TODO only use if the server has the EffectLib plugin
        if (Settings.DeathEffectSection.ENABLE_DEATH_EFFECTS && PluginUtil.isPluginEnabled("EffectLib")) {
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
                    // Effects.getEffectManager().display(Particle);

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
        final DeathChestRegistry deathChestRegistry = DeathChestRegistry.getInstance();

        if (cache.isDrawingAxe()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
            cache.setDrawingAxe(false);
        }

        if (cache.isInCombat()) {
            cache.setInCombat(false);
            player.setHealth(0);
        }

        // Remove from tracking registry
        TrackingRegistry.stopTracking(player.getUniqueId());

        // Disable waypoint transmission so others can't track this player anymore
        LocatorBarManager.disableTransmit(player);

        // Notify and clean up players who were tracking this player
        for (java.util.UUID trackerUUID : TrackingRegistry.getActiveTrackers()) {
            Player tracker = Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline())
                continue;

            PlayerCache trackerCache = PlayerCache.from(tracker);
            if (player.getUniqueId().equals(trackerCache.getTargetByUUID())) {
                trackerCache.setTrackingLocation(null);
                trackerCache.setTargetByUUID(null);
                trackerCache.setCachedPortalTarget(null);
                TrackingRegistry.stopTracking(trackerUUID);

                LocatorBarManager.disableReceive(tracker);
                LocatorBarManager.clearTarget(tracker);

                ColorUtil.sendMessage(tracker, "&c" + player.getName() + " &chas gone offline. Tracking stopped.");
            }
        }

        for (final BukkitHologram hologram : deathChestRegistry.getCachedHolograms())
            if (player.hasMetadata(hologram.getUniqueId().toString()))
                player.removeMetadata(hologram.getUniqueId().toString(), SMPPlugin.getInstance());

    }

    @EventHandler
    public void onPlayerDamage(final EntityDamageByEntityEvent event) {
        final Entity entity = event.getEntity();

        if (entity instanceof final Player player) {
            final PlayerCache cache = PlayerCache.from(player);

            cache.setInCombat(true);
            SchedulerUtil.runLater(20L * Settings.CombatSection.SECONDS_TILL_PLAYER_LEAVES_COMBAT,
                    () -> cache.setInCombat(false));
        }
    }

}