package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.SMPPlugin;
import games.coob.smp.util.ArmourStandUtil;
import games.coob.smp.util.ColorUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.function.Consumer;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AxeListener implements Listener {

	private static final AxeListener instance = new AxeListener();

	public static AxeListener getInstance() {
		return instance;
	}

	final List<Location> standLocations = new ArrayList<>();

	private double descendSpeed = 0;

	private static final Set<Material> passableBlocks = new HashSet<>();
	private static final Set<Material> breakableBlocks = new HashSet<>();

	static {
		breakableBlocks.add(Material.TALL_GRASS);
		breakableBlocks.add(Material.TALL_SEAGRASS);
		breakableBlocks.add(Material.VINE);
		breakableBlocks.add(Material.SUGAR_CANE);
		breakableBlocks.add(Material.FLOWER_POT);
		breakableBlocks.add(Material.CORNFLOWER);
		breakableBlocks.add(Material.CHORUS_FLOWER);
		breakableBlocks.add(Material.SUNFLOWER);
		breakableBlocks.add(Material.DANDELION);
		breakableBlocks.add(Material.POPPY);
		breakableBlocks.add(Material.BLUE_ORCHID);
		breakableBlocks.add(Material.ALLIUM);
		breakableBlocks.add(Material.AZURE_BLUET);
		breakableBlocks.add(Material.ORANGE_TULIP);
		breakableBlocks.add(Material.PINK_TULIP);
		breakableBlocks.add(Material.RED_TULIP);
		breakableBlocks.add(Material.WHITE_TULIP);
		breakableBlocks.add(Material.OXEYE_DAISY);
		breakableBlocks.add(Material.LILY_OF_THE_VALLEY);
		breakableBlocks.add(Material.LILY_PAD);
		breakableBlocks.add(Material.WITHER_ROSE);
		breakableBlocks.add(Material.SPORE_BLOSSOM);
		breakableBlocks.add(Material.KELP);
		breakableBlocks.add(Material.BIG_DRIPLEAF);
		breakableBlocks.add(Material.SMALL_DRIPLEAF);
		breakableBlocks.add(Material.BIG_DRIPLEAF_STEM);
		breakableBlocks.add(Material.LARGE_FERN);
		breakableBlocks.add(Material.LILAC);
		breakableBlocks.add(Material.ROSE_BUSH);
		breakableBlocks.add(Material.PEONY);

		passableBlocks.add(Material.AIR);
		passableBlocks.add(Material.SHORT_GRASS);
		passableBlocks.add(Material.FERN);
		passableBlocks.add(Material.SEAGRASS);
		passableBlocks.add(Material.WATER);
		passableBlocks.add(Material.LAVA);
		passableBlocks.addAll(breakableBlocks);
	}

	@EventHandler
	public void noManipulate(final PlayerArmorStandManipulateEvent event) {
		if (event.getRightClicked().hasMetadata("Throwing_Axe"))
			event.setCancelled(true);
	}

	@EventHandler
	public void axeThrow(final PlayerInteractEvent event) {
		final Player player = event.getPlayer();
		final Location destination = player.getLocation().clone().add(0, 0.24, 0)
				.add(player.getLocation().getDirection());
		final Vector vector = destination.subtract(player.getLocation()).toVector();

		if (player.getInventory().getItemInOffHand().getType() == Material.SHIELD)
			return;

		if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			final ItemStack item = event.getItem();

			if (item != null && item.getType().toString().endsWith("_AXE")) {
				final PlayerCache cache = PlayerCache.from(player);

				if (!cache.isDrawingAxe()) {
					player.addPotionEffect(
							new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 4, false, false, false));
					player.addPotionEffect(
							new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 250, false, false, false));
					cache.setDrawingAxe(true);

					new BukkitRunnable() {
						private int timeDrawingAxe;

						@Override
						public void run() {
							timeDrawingAxe++;

							if (!cache.isDrawingAxe()) {
								this.cancel();
								return;
							}

							String actionBar;
							if (timeDrawingAxe <= 2) {
								actionBar = "&b▊&8▊▊▊▊";
								descendSpeed = -0.10;
							} else if (timeDrawingAxe <= 4) {
								actionBar = "&b▊▊&8▊▊▊";
								descendSpeed = -0.08;
							} else if (timeDrawingAxe <= 6) {
								actionBar = "&b▊▊▊&8▊▊";
								descendSpeed = -0.7;
							} else if (timeDrawingAxe <= 8) {
								actionBar = "&b▊▊▊▊&8▊";
								descendSpeed = -0.06;
							} else {
								actionBar = "&b▊▊▊▊▊";
								descendSpeed = -0.05;
							}

							player.sendActionBar(Component.text(ColorUtil.colorize(actionBar)));
						}
					}.runTaskTimer(SMPPlugin.getInstance(), 0, 10);
				} else {
					swingArm(player);
					throwAxe(player, vector.add(new Vector(0, 0.4, 0)), item);
					player.getInventory().setItemInMainHand(null);
					player.removePotionEffect(PotionEffectType.SLOWNESS);
					player.removePotionEffect(PotionEffectType.JUMP_BOOST);
					cache.setDrawingAxe(false);
				}
			}
		}
	}

	public void throwAxe(final Player player, final Vector vector, final ItemStack itemStack) {
		final Location playerLocation = player.getLocation();
		final Consumer<ArmorStand> consumer = armorStand -> {
			armorStand.setVisible(false);
			armorStand.setSmall(true);
			armorStand.setArms(false);
			armorStand.setGravity(false);
			armorStand.setMarker(true);
			armorStand.getEquipment().setItemInMainHand(itemStack);
			armorStand.setMetadata("Throwing_Axe", new FixedMetadataValue(SMPPlugin.getInstance(), ""));
		};

		final ArmorStand stand = player.getWorld().spawn(playerLocation, ArmorStand.class, consumer);
		standLocations.add(stand.getLocation());

		new BukkitRunnable() {
			@Override
			public void run() {
				for (final Entity entity : stand.getNearbyEntities(1, 1, 1)) {
					if (!entity.getUniqueId().equals(player.getUniqueId()) && !(entity instanceof ArmorStand)
							&& entity instanceof Damageable) {
						double damage = switch (itemStack.getType()) {
							case WOODEN_AXE -> 10.5;
							case GOLDEN_AXE -> 11;
							case STONE_AXE -> 13.5;
							case IRON_AXE -> 14;
							case DIAMOND_AXE -> 15;
							case NETHERITE_AXE -> 16;
							default -> 10;
						};
						((Damageable) entity).damage(damage);

						stand.getLocation().getWorld().dropItemNaturally(stand.getLocation(), itemStack);
						stand.remove();
						this.cancel();
						return;
					}
				}

				final Location standLocation = stand.getLocation();
				final EulerAngle rotation = stand.getRightArmPose().add(20, 0, 0);

				stand.setRightArmPose(rotation);
				vector.add(new Vector(0, descendSpeed, 0));
				stand.teleport(standLocation.clone().add(vector.normalize().multiply(0.8)));

				final Location armTipLocation = ArmourStandUtil.getSmallArmTip(stand);
				final Block block = player.getWorld().getBlockAt(armTipLocation);

				if (passableBlocks.contains(block.getType())) {
					if (breakableBlocks.contains(block.getType()))
						block.breakNaturally();

					standLocations.add(stand.getLocation());
				} else {
					final Location lastAirLocation = standLocations.get(standLocations.size() - 1);

					stand.teleport(lastAirLocation);

					final Location lastArmTipLocation = ArmourStandUtil.getSmallArmTip(stand);
					final Location initialLocation = lastArmTipLocation.clone();
					BlockFace blockFace = lastArmTipLocation.getBlock().getFace(armTipLocation.getBlock());

					if (blockFace == null) {
						for (int i = 0; i < 20; i++) {
							final Vector destination = armTipLocation.toVector().subtract(lastArmTipLocation.toVector())
									.normalize();

							if (lastArmTipLocation.getBlock().getType().isSolid())
								lastArmTipLocation.add(destination.multiply(-0.02));
						}

						blockFace = initialLocation.getBlock().getFace(lastArmTipLocation.getBlock());
					}

					if (blockFace != null
							&& (blockFace.toString().contains("EAST") || blockFace.toString().contains("WEST") ||
									blockFace.toString().contains("NORTH") || blockFace.toString().contains("SOUTH"))) {
						stand.setRightArmPose(new EulerAngle(5, 0, 0));

						for (int i = 0; i < 80; i++) {
							final Vector destination = lastAirLocation.getDirection().normalize();
							final Location location = ArmourStandUtil.getSmallArmTip(stand);

							if (passableBlocks.contains(location.getBlock().getType()))
								stand.teleport(lastAirLocation.add(destination.multiply(0.01)));
							else
								stand.teleport(lastAirLocation.add(destination.multiply(-0.01)));
						}

						stand.teleport(
								lastAirLocation.subtract(lastAirLocation.getDirection().normalize().multiply(0.125)));

						if (!passableBlocks
								.contains(stand.getLocation().getBlock().getRelative(BlockFace.SELF).getType())) {
							armTipLocation.getWorld().dropItemNaturally(lastAirLocation, itemStack);
							stand.remove();
						}
					} else {
						armTipLocation.getWorld().dropItemNaturally(lastAirLocation, itemStack);
						stand.remove();
					}

					this.cancel();
				}
			}
		}.runTaskTimer(SMPPlugin.getInstance(), 1, 2);
	}

	private void swingArm(final Player player) {
		// Animation can be added here if needed
	}
}
