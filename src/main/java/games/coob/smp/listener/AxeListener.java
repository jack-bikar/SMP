package games.coob.smp.listener;

import games.coob.smp.PlayerCache;
import games.coob.smp.util.ArmourStandUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
import org.bukkit.util.Consumer;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.mineacademy.fo.debug.LagCatcher;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompProperty;
import org.mineacademy.fo.remain.Remain;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AxeListener implements Listener { // TODO figure out why there's lag

	@Getter
	private static final AxeListener instance = new AxeListener();

	final List<Location> standLocations = new ArrayList<>();

	private double descendSpeed = 0;

	private static final List<CompMaterial> passableBlocks = new ArrayList<>();

	private static final List<CompMaterial> breakableBlocks = new ArrayList<>();

	static {
		breakableBlocks.add(CompMaterial.TALL_GRASS);
		breakableBlocks.add(CompMaterial.TALL_SEAGRASS);
		breakableBlocks.add(CompMaterial.VINE);
		breakableBlocks.add(CompMaterial.SUGAR_CANE);
		breakableBlocks.add(CompMaterial.FLOWER_POT);
		breakableBlocks.add(CompMaterial.CORNFLOWER);
		breakableBlocks.add(CompMaterial.CHORUS_FLOWER);
		breakableBlocks.add(CompMaterial.SUNFLOWER);
		breakableBlocks.add(CompMaterial.DANDELION);
		breakableBlocks.add(CompMaterial.POPPY);
		breakableBlocks.add(CompMaterial.BLUE_ORCHID);
		breakableBlocks.add(CompMaterial.ALLIUM);
		breakableBlocks.add(CompMaterial.AZURE_BLUET);
		breakableBlocks.add(CompMaterial.ORANGE_TULIP);
		breakableBlocks.add(CompMaterial.PINK_TULIP);
		breakableBlocks.add(CompMaterial.RED_TULIP);
		breakableBlocks.add(CompMaterial.WHITE_TULIP);
		breakableBlocks.add(CompMaterial.OXEYE_DAISY);
		breakableBlocks.add(CompMaterial.LILY_OF_THE_VALLEY);
		breakableBlocks.add(CompMaterial.LILY_PAD);
		breakableBlocks.add(CompMaterial.WITHER_ROSE);
		breakableBlocks.add(CompMaterial.SPORE_BLOSSOM);
		breakableBlocks.add(CompMaterial.KELP);
		breakableBlocks.add(CompMaterial.BIG_DRIPLEAF);
		breakableBlocks.add(CompMaterial.SMALL_DRIPLEAF);
		breakableBlocks.add(CompMaterial.BIG_DRIPLEAF_STEM);
		breakableBlocks.add(CompMaterial.LARGE_FERN);
		breakableBlocks.add(CompMaterial.LILAC);
		breakableBlocks.add(CompMaterial.ROSE_BUSH);
		breakableBlocks.add(CompMaterial.PEONY);
		passableBlocks.add(CompMaterial.AIR);
		passableBlocks.add(CompMaterial.GRASS);
		passableBlocks.add(CompMaterial.FERN);
		passableBlocks.add(CompMaterial.SEAGRASS);
		passableBlocks.add(CompMaterial.WATER);
		passableBlocks.add(CompMaterial.LAVA);
		passableBlocks.addAll(breakableBlocks);
	}

	@EventHandler
	public void noManipulate(final PlayerArmorStandManipulateEvent event) {
		if (event.getRightClicked().hasMetadata("Throwing_Axe"))
			event.setCancelled(true); //
	}

	@EventHandler
	public void axeThrow(final PlayerInteractEvent event) {
		final Player player = event.getPlayer();
		final Location destination = player.getLocation().clone().add(0, 0.24, 0).add(player.getLocation().getDirection());
		final Vector vector = destination.subtract(player.getLocation()).toVector();

		if (player.getInventory().getItemInOffHand().getType() == CompMaterial.SHIELD.getMaterial())
			return;

		if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			final ItemStack item = event.getItem();

			if (item != null && item.getType().toString().endsWith("_AXE")) {
				final PlayerCache cache = PlayerCache.from(player);

				if (!cache.isDrawingAxe()) {
					player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 4, false, false, false));
					player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 250, false, false, false));
					cache.setDrawingAxe(true);

					new BukkitRunnable() {
						private int timeDrawingAxe;

						@Override
						public void run() {
							timeDrawingAxe++;

							if (!cache.isDrawingAxe())
								this.cancel();

							if (timeDrawingAxe <= 2) {
								Remain.sendActionBar(player, "&b▊&8▊▊▊▊");
								descendSpeed = -0.10;
							} else if (timeDrawingAxe <= 4) {
								Remain.sendActionBar(player, "&b▊▊&8▊▊▊");
								descendSpeed = -0.08;
							} else if (timeDrawingAxe <= 6) {
								Remain.sendActionBar(player, "&b▊▊▊&8▊▊");
								descendSpeed = -0.7;
							} else if (timeDrawingAxe <= 8) {
								Remain.sendActionBar(player, "&b▊▊▊▊&8▊");
								descendSpeed = -0.06;
							} else {
								Remain.sendActionBar(player, "&b▊▊▊▊▊");
								descendSpeed = -0.05;
							}
						}
					}.runTaskTimer(SimplePlugin.getInstance(), 0, 10);
				} else {
					swingArm(player);
					throwAxe(player, vector.add(new Vector(0, 0.4, 0)), item);
					player.getInventory().setItemInMainHand(null);
					player.removePotionEffect(PotionEffectType.SLOW);
					player.removePotionEffect(PotionEffectType.JUMP);
					cache.setDrawingAxe(false);
				}
			}
		}
	}

	public void throwAxe(final Player player, final Vector vector, final ItemStack itemStack) {
		LagCatcher.start("Trowing");
		final Location playerLocation = player.getLocation();
		final Consumer<ArmorStand> consumer = armorStand -> {
			armorStand.setVisible(false);
			armorStand.setSmall(true);
			armorStand.setArms(false);
			CompProperty.GRAVITY.apply(armorStand, false);
			armorStand.setMarker(true);
			armorStand.getEquipment().setItemInMainHand(itemStack);
			armorStand.setMetadata("Throwing_Axe", new FixedMetadataValue(SimplePlugin.getInstance(), ""));
		};

		final ArmorStand stand = player.getWorld().spawn(playerLocation, ArmorStand.class, consumer);
		standLocations.add(stand.getLocation());

		new BukkitRunnable() {
			@Override
			public void run() {
				for (final Entity entity : stand.getNearbyEntities(1, 1, 1)) {
					if (!entity.getUniqueId().equals(player.getUniqueId()) && !(entity instanceof ArmorStand) && entity instanceof Damageable) {
						if (itemStack.getType() == Material.WOODEN_AXE)
							((Damageable) entity).damage(10.5);
						else if (itemStack.getType() == Material.GOLDEN_AXE)
							((Damageable) entity).damage(11);
						else if (itemStack.getType() == Material.STONE_AXE)
							((Damageable) entity).damage(13.5);
						else if (itemStack.getType() == Material.IRON_AXE)
							((Damageable) entity).damage(14);
						else if (itemStack.getType() == Material.DIAMOND_AXE)
							((Damageable) entity).damage(15);
						else if (itemStack.getType() == Material.NETHERITE_AXE)
							((Damageable) entity).damage(16);

						stand.getLocation().getWorld().dropItemNaturally(stand.getLocation(), itemStack);
						stand.remove();

						this.cancel();
					}
				}

				final Location standLocation = stand.getLocation();
				final EulerAngle rotation = stand.getRightArmPose().add(20, 0, 0);

				stand.setRightArmPose(rotation);
				vector.add(new Vector(0, descendSpeed, 0));
				stand.teleport(standLocation.clone().add(vector.normalize().multiply(0.8)));

				final Location armTipLocation = ArmourStandUtil.getSmallArmTip(stand);
				final Block block = player.getWorld().getBlockAt(armTipLocation);

				if (passableBlocks.contains(CompMaterial.fromMaterial(block.getType()))) {
					if (breakableBlocks.contains(CompMaterial.fromMaterial(block.getType())))
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
							final Vector destination = armTipLocation.toVector().subtract(lastArmTipLocation.toVector()).normalize();

							if (lastArmTipLocation.getBlock().getType().isSolid())
								lastArmTipLocation.add(destination.multiply(-0.02));
						}

						blockFace = initialLocation.getBlock().getFace(lastArmTipLocation.getBlock());
					}

					System.out.println(blockFace);

					if (blockFace.toString().contains("EAST") || blockFace.toString().contains("WEST") || blockFace.toString().contains("NORTH") || blockFace.toString().contains("SOUTH")) {
						stand.setRightArmPose(new EulerAngle(5, 0, 0));

						for (int i = 0; i < 80; i++) {
							final Vector destination = lastAirLocation.getDirection().normalize();
							final Location location = ArmourStandUtil.getSmallArmTip(stand);

							if (passableBlocks.contains(CompMaterial.fromMaterial(location.getBlock().getType())))
								stand.teleport(lastAirLocation.add(destination.multiply(0.01)));
							else
								stand.teleport(lastAirLocation.add(destination.multiply(-0.01)));
						}

						stand.teleport(lastAirLocation.subtract(lastAirLocation.getDirection().normalize().multiply(0.125)));

						if (!passableBlocks.contains(CompMaterial.fromMaterial(stand.getLocation().getBlock().getRelative(BlockFace.SELF).getType()))) {
							armTipLocation.getWorld().dropItemNaturally(lastAirLocation, itemStack);
							stand.remove();
						}
					} else {
						armTipLocation.getWorld().dropItemNaturally(lastAirLocation, itemStack);
						stand.remove();
					}

					this.cancel();
					LagCatcher.end("Trowing", true);
				}
			}
		}.runTaskTimer(SimplePlugin.getInstance(), 1, 2);
	}

	private void swingArm(final Player player) {
		// Remain.sendPacket(player, new PacketPlayOutAnimation((EntityPlayer) entity, 0));
		// PacketUtil.addSendingListener(PacketType.Play.Client.ARM_ANIMATION, event -> {});
		// TODO add for every version
		/*if (MinecraftVersion.equals(MinecraftVersion.V.v1_18)) {
			final net.minecraft.world.entity.Entity entity = ((CraftEntity) player).getHandle();
			final PacketPlayOutAnimation animate = new PacketPlayOutAnimation(entity, 0);

			Remain.sendPacket(player, animate);
		}*/
	}

	// TODO take the axe out when right clicking it (when in wall)
}

