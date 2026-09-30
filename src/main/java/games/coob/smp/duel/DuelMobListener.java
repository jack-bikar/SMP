package games.coob.smp.duel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Keeps team mobs ({@link DuelMobs}) inside the rules of their duel:
 * <ul>
 * <li>They only target and hurt the other side (its players still fighting, and
 * its mobs), never their own side or anyone outside the duel.</li>
 * <li>Only the other side's players can hurt them, and nothing can before the
 * fight starts.</li>
 * <li>No loot or XP, no burning in daylight, no breaking or placing blocks, no
 * turning into another mob, and players can't tame, leash or trade with them.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DuelMobListener implements Listener {

	private static final DuelMobListener instance = new DuelMobListener();

	public static DuelMobListener getInstance() {
		return instance;
	}

	// -------------------------------------------------------------------------
	// Targeting and damage
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTarget(final EntityTargetEvent event) {
		ActiveDuel duel = DuelManager.getInstance().getDuelOfMob(event.getEntity());
		if (duel == null || event.getTarget() == null)
			return;
		if (!duel.isFighting() || !duel.isMobEnemy(duel.getMobSide(event.getEntity()), event.getTarget()))
			event.setCancelled(true);
	}

	/**
	 * Runs before {@link DuelListener}'s lethal-hit check, so a cancelled hit here
	 * never eliminates anyone.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamageByEntity(final EntityDamageByEntityEvent event) {
		Entity victim = event.getEntity();
		Entity attacker = source(event.getDamager());

		// A duel mob hitting something: only the other side
		ActiveDuel attackerDuel = attacker != null ? DuelManager.getInstance().getDuelOfMob(attacker) : null;
		if (attackerDuel != null) {
			if (!attackerDuel.isFighting() || !attackerDuel.isMobEnemy(attackerDuel.getMobSide(attacker), victim))
				event.setCancelled(true);
			return;
		}

		// Something hitting a duel mob: only players of the other side, while the fight is on
		ActiveDuel victimDuel = DuelManager.getInstance().getDuelOfMob(victim);
		if (victimDuel == null)
			return;
		if (!victimDuel.isFighting()) {
			event.setCancelled(true);
			return;
		}
		if (attacker instanceof Player player && (!victimDuel.isFightingPlayer(player)
				|| victimDuel.getSide(player) != victimDuel.getMobSide(victim).other()))
			event.setCancelled(true);
	}

	/** No damage of any kind (fall, suffocation...) before the fight starts or once it is decided. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(final EntityDamageEvent event) {
		ActiveDuel duel = DuelManager.getInstance().getDuelOfMob(event.getEntity());
		if (duel != null && !duel.isFighting())
			event.setCancelled(true);
	}

	/** Witch potions only affect the other side. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPotionSplash(final PotionSplashEvent event) {
		if (!(event.getPotion().getShooter() instanceof Entity thrower))
			return;
		ActiveDuel duel = DuelManager.getInstance().getDuelOfMob(thrower);
		if (duel == null)
			return;
		DuelSide side = duel.getMobSide(thrower);
		for (LivingEntity affected : event.getAffectedEntities()) {
			if (!duel.isMobEnemy(side, affected))
				event.setIntensity(affected, 0);
		}
	}

	/** The player or mob really behind a hit: the shooter of a projectile, the lighter of TNT... */
	private static Entity source(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
			return shooter;
		if (damager instanceof TNTPrimed tnt && tnt.getSource() != null)
			return tnt.getSource();
		if (damager instanceof EvokerFangs fangs && fangs.getOwner() != null)
			return fangs.getOwner();
		if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity thrower)
			return thrower;
		return damager;
	}

	// -------------------------------------------------------------------------
	// Mob behaviour
	// -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(final EntityDeathEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) == null)
			return;
		event.getDrops().clear();
		event.setDroppedExp(0);
	}

	/** Zombies and skeletons don't burn in daylight (lava and fire aspect still set them alight). */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCombust(final EntityCombustEvent event) {
		if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent)
			return;
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) != null)
			event.setCancelled(true);
	}

	/** Zombie to drowned, piglin to zombified piglin, skeleton to stray... */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTransform(final EntityTransformEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) != null)
			event.setCancelled(true);
	}

	/** Ravagers trampling leaves, zombies breaking doors... */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onChangeBlock(final EntityChangeBlockEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) != null)
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreakDoor(final EntityBreakDoorEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) != null)
			event.setCancelled(true);
	}

	/** Snow golem trails. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockForm(final EntityBlockFormEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) != null)
			event.setCancelled(true);
	}

	/** Creepers still hurt the other side, but never break blocks. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onExplode(final EntityExplodeEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getEntity()) != null)
			event.blockList().clear();
	}

	/** No taming, leashing, trading, shearing or name tags. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInteract(final PlayerInteractEntityEvent event) {
		if (DuelManager.getInstance().getDuelOfMob(event.getRightClicked()) != null)
			event.setCancelled(true);
	}

	/** Arrows and tridents shot by mobs are removed with the rest after the duel. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileLaunch(final ProjectileLaunchEvent event) {
		if (!(event.getEntity().getShooter() instanceof Entity shooter))
			return;
		ActiveDuel duel = DuelManager.getInstance().getDuelOfMob(shooter);
		if (duel != null)
			duel.trackSpawnedEntity(event.getEntity().getUniqueId());
	}
}
