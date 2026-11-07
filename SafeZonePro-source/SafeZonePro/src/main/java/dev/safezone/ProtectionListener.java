package dev.safezone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Every rule that only applies to non-admins (OP or safezone.admin bypass all of it). */
public final class ProtectionListener implements Listener {

    private final SafeZonePlugin plugin;
    private final Set<Material> whitelist = EnumSet.noneOf(Material.class);
    private boolean forceWhitelist;
    private boolean lockAll;
    private final Map<UUID, Long> lastFeedback = new HashMap<>();

    public ProtectionListener(SafeZonePlugin plugin) { this.plugin = plugin; }

    public void reload() {
        whitelist.clear();
        for (String s : plugin.getConfig().getStringList("settings.whitelist")) {
            Material m = Material.matchMaterial(s);
            if (m != null) whitelist.add(m);
            else plugin.getLogger().warning("Unknown whitelist material: " + s);
        }
        forceWhitelist = plugin.getConfig().getBoolean("settings.force-whitelist", true);
        lockAll = plugin.getConfig().getBoolean("settings.lock-all-block-interactions", false);
    }

    // ------------------------------------------------------------ helpers

    /** The zone at loc if it exists AND denies the flag, otherwise null. */
    private Zone denied(Location loc, String flag) {
        Zone z = plugin.zones().zoneAt(loc);
        return (z != null && !z.allows(flag)) ? z : null;
    }

    private boolean blocks(Player p, Location loc, String flag) {
        return !plugin.isAdmin(p) && denied(loc, flag) != null;
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Glass break sound + red particles at the player's hand + action bar. */
    public void feedback(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastFeedback.get(p.getUniqueId());
        if (last != null && now - last < 400) return;
        lastFeedback.put(p.getUniqueId(), now);

        Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.7)).subtract(0, 0.4, 0);
        p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.0f);
        p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, hand, 3, 0.15, 0.15, 0.15, 0);
        p.getWorld().spawnParticle(Particle.SMOKE, hand, 8, 0.15, 0.15, 0.15, 0.01);
        p.sendActionBar(plugin.msg("blocked"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { lastFeedback.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------ building

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocks(e.getPlayer(), e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
            feedback(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocks(e.getPlayer(), e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
            feedback(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (blocks(e.getPlayer(), e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
            feedback(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (blocks(e.getPlayer(), e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
            feedback(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        Location loc = e.getEntity().getLocation();
        if (e instanceof HangingBreakByEntityEvent by) {
            Player p = resolvePlayer(by.getRemover());
            if (p != null && blocks(p, loc, "build")) {
                e.setCancelled(true);
                feedback(p);
                return;
            }
        }
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && denied(loc, "tnt") != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (blocks(e.getPlayer(), e.getRightClicked().getLocation(), "build")) {
            e.setCancelled(true);
            feedback(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame && blocks(e.getPlayer(), e.getRightClicked().getLocation(), "build")) {
            e.setCancelled(true);
            feedback(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p != null) {
            if (blocks(p, e.getBlock().getLocation(), "build")) {
                e.setCancelled(true);
                feedback(p);
            }
        } else if (denied(e.getBlock().getLocation(), "build") != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (denied(e.getBlock().getLocation(), "build") != null) e.setCancelled(true);
    }

    /** Endermen, withers etc. changing blocks (falling sand is left alone). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player || e.getEntity() instanceof FallingBlock) return;
        if (denied(e.getBlock().getLocation(), "build") != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobBreakDoor(EntityBreakDoorEvent e) {
        if (denied(e.getBlock().getLocation(), "doors") != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------ interaction

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) return;

        Action action = e.getAction();
        Block b = e.getClickedBlock();

        // Trampling farmland / turtle eggs
        if (action == Action.PHYSICAL) {
            if (b != null && denied(b.getLocation(), "build") != null) e.setCancelled(true);
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK && b != null) {
            Zone z = plugin.zones().zoneAt(b.getLocation());
            if (z != null) {
                Material type = b.getType();

                // 1) Whitelist FIRST: anvil, crafting table, ender chest, grindstone.
                //    These are never cancelled by us, and wrongly-cancelled clicks are undone.
                if (whitelist.contains(type)) {
                    if (forceWhitelist && (e.isCancelled() || e.useInteractedBlock() == Event.Result.DENY)) {
                        e.setCancelled(false);
                        e.setUseInteractedBlock(Event.Result.ALLOW);
                    }
                    return;
                }

                // 2) Locked doors / trapdoors / gates
                boolean doorLike = Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type);
                if ((doorLike && !z.allows("doors")) || (lockAll && !z.allows("build"))) {
                    e.setCancelled(true);
                    feedback(p);
                    return;
                }
            }
        }

        // 3) Boats / minecarts being placed
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            ItemStack item = e.getItem();
            if (item != null && isVehicleItem(item.getType())) {
                Location where = b != null ? b.getLocation() : p.getLocation();
                if (denied(where, "vehicles") != null) {
                    e.setCancelled(true);
                    feedback(p);
                }
            }
        }
    }

    private boolean isVehicleItem(Material m) {
        return Tag.ITEMS_BOATS.isTagged(m) || m.name().endsWith("MINECART");
    }

    // ------------------------------------------------------------ combat rules

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player damager = resolvePlayer(e.getDamager());
        if (damager == null || plugin.isAdmin(damager)) return;

        // Decorations
        if (e.getEntity() instanceof ArmorStand || e.getEntity() instanceof ItemFrame) {
            if (denied(e.getEntity().getLocation(), "build") != null) {
                e.setCancelled(true);
                feedback(damager);
            }
            return;
        }

        // PvP: blocked if attacker OR victim stands in a zone with pvp denied
        if (e.getEntity() instanceof Player victim) {
            if (denied(damager.getLocation(), "pvp") != null || denied(victim.getLocation(), "pvp") != null) {
                e.setCancelled(true);   // CombatTag ignores cancelled hits, so nobody gets tagged
                feedback(damager);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || plugin.isAdmin(p)) return;
        Zone z = plugin.zones().zoneAt(p.getLocation());
        if (z == null) return;

        switch (e.getCause()) {
            case FALL -> { if (!z.allows("fall-damage")) e.setCancelled(true); }
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> { if (!z.allows("tnt")) e.setCancelled(true); }
            default -> { }
        }
    }

    /** Fires for wind charges, hit knockback, explosions and other "custom item" pushes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) return;
        if (denied(p.getLocation(), "knockback") != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------ explosions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> denied(b.getLocation(), "tnt") != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> denied(b.getLocation(), "tnt") != null);
    }

    // ------------------------------------------------------------ mobs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Enemy)) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.COMMAND || r == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (denied(e.getLocation(), "mobs") != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p) || plugin.isAdmin(p)) return;
        if (denied(p.getLocation(), "mobs") != null || denied(e.getEntity().getLocation(), "mobs") != null) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ vehicles

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent e) {
        if ((e.getVehicle() instanceof Boat || e.getVehicle() instanceof Minecart)
                && denied(e.getVehicle().getLocation(), "vehicles") != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p && blocks(p, e.getVehicle().getLocation(), "vehicles")) {
            e.setCancelled(true);
            feedback(p);
        }
    }

    /** Covers every kind of mount: boats, minecarts, horses, pigs... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent e) {
        if (e.getEntity() instanceof Player p && blocks(p, e.getMount().getLocation(), "vehicles")) {
            e.setCancelled(true);
            feedback(p);
        }
    }
}
