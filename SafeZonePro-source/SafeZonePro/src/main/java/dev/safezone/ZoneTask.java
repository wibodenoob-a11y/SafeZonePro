package dev.safezone;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One light task (every 5 ticks) that handles: entry/exit effects, fall distance, player
 * collision, ejecting riders, mob push-back, boat cleanup, border particles and wand preview.
 */
public final class ZoneTask extends BukkitRunnable implements Listener {

    private final SafeZonePlugin plugin;
    private final Map<UUID, String> current = new HashMap<>();
    private final Set<UUID> noCollide = new HashSet<>();
    private int tick = 0;

    public ZoneTask(SafeZonePlugin plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) handlePlayer(p);

        if (tick % 2 == 0) { // every 10 ticks
            for (Zone z : plugin.zones().all()) handleEntities(z);
            drawBorders();
            drawSelections();
        }
    }

    public void restoreAll() {
        for (UUID id : noCollide) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setCollidable(true);
        }
        noCollide.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        current.remove(id);
        if (noCollide.remove(id)) e.getPlayer().setCollidable(true);
    }

    // ------------------------------------------------------------ per player

    private void handlePlayer(Player p) {
        UUID id = p.getUniqueId();
        Zone z = plugin.zones().zoneAt(p.getLocation());
        String now = z == null ? null : z.getName();
        String prev = current.get(id);

        if (!Objects.equals(prev, now)) {
            if (now == null) current.remove(id); else current.put(id, now);
            if (z != null) onEnter(p, z);
            else if (prev != null) p.sendActionBar(plugin.msg("exit-actionbar", "%zone%", prev));
        }

        boolean admin = plugin.isAdmin(p);
        if (z != null && !admin) {
            if (!z.allows("fall-damage")) p.setFallDistance(0f);
            if (!z.allows("vehicles") && p.isInsideVehicle()) p.leaveVehicle();
            if (!z.allows("knockback")) {
                // No collision = bumping into someone moves nobody
                if (noCollide.add(id)) p.setCollidable(false);
                return;
            }
        }
        if (noCollide.remove(id)) p.setCollidable(true);
    }

    private void onEnter(Player p, Zone z) {
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("entry.sound", true)) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
        }
        if (c.getBoolean("entry.particles", true)) {
            Location l = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 20, 0.5, 0.8, 0.5, 0);
            p.getWorld().spawnParticle(Particle.END_ROD, l, 12, 0.4, 0.8, 0.4, 0.02);
        }
        if (c.getBoolean("entry.title", true)) {
            p.showTitle(Title.title(
                    plugin.msg("entry-title", "%zone%", z.getName()),
                    plugin.msg("entry-subtitle", "%zone%", z.getName()),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(1500), Duration.ofMillis(500))));
        }
    }

    // ------------------------------------------------------------ per zone: mobs + vehicles

    private void handleEntities(Zone z) {
        World w = Bukkit.getWorld(z.getWorld());
        if (w == null) return;
        BoundingBox box = z.box();

        if (!z.allows("mobs")) {
            double margin = plugin.getConfig().getDouble("mobs.push-margin", 3.0);
            BoundingBox wide = box.clone().expand(margin);
            for (Entity e : w.getNearbyEntities(wide, en -> en instanceof Mob && en instanceof Enemy)) {
                Mob m = (Mob) e;
                if (box.contains(m.getLocation().toVector())) expel(m, z, w);
                else pushBack(m, box);
            }
        }

        if (!z.allows("vehicles")) {
            for (Entity e : w.getNearbyEntities(box, en -> en instanceof Boat || en instanceof Minecart)) {
                e.eject();
                e.remove();
            }
        }
    }

    /** Mob is inside the zone: teleport it just outside the nearest edge. */
    private void expel(Mob m, Zone z, World w) {
        Location l = m.getLocation();
        double dMinX = l.getX() - z.getMinX(), dMaxX = z.getMaxX() + 1.0 - l.getX();
        double dMinZ = l.getZ() - z.getMinZ(), dMaxZ = z.getMaxZ() + 1.0 - l.getZ();
        double best = Math.min(Math.min(dMinX, dMaxX), Math.min(dMinZ, dMaxZ));
        double x = l.getX(), zz = l.getZ();
        if (best == dMinX) x = z.getMinX() - 2.5;
        else if (best == dMaxX) x = z.getMaxX() + 3.5;
        else if (best == dMinZ) zz = z.getMinZ() - 2.5;
        else zz = z.getMaxZ() + 3.5;

        double y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(zz)) + 1.0;
        m.setTarget(null);
        m.getPathfinder().stopPathfinding();
        m.teleport(new Location(w, x, y, zz, l.getYaw(), l.getPitch()));
    }

    /** Mob is near the zone: shove it away and cancel its current path. */
    private void pushBack(Mob m, BoundingBox box) {
        Vector pos = m.getLocation().toVector();
        Vector closest = new Vector(
                Math.max(box.getMinX(), Math.min(box.getMaxX(), pos.getX())),
                Math.max(box.getMinY(), Math.min(box.getMaxY(), pos.getY())),
                Math.max(box.getMinZ(), Math.min(box.getMaxZ(), pos.getZ())));
        Vector away = pos.clone().subtract(closest);
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) away = pos.clone().subtract(box.getCenter()).setY(0);
        if (away.lengthSquared() < 1.0E-4) return;
        away.normalize().multiply(0.45).setY(0.15);
        m.setTarget(null);
        m.getPathfinder().stopPathfinding();
        m.setVelocity(away);
    }

    // ------------------------------------------------------------ visuals

    private Color borderColor() {
        try {
            String hex = plugin.getConfig().getString("border.color", "#9fffe0").replace("#", "");
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (Exception ex) {
            return Color.fromRGB(0x9fffe0);
        }
    }

    private void drawBorders() {
        if (!plugin.getConfig().getBoolean("border.enabled", true)) return;
        double range = plugin.getConfig().getDouble("border.view-distance", 16);
        Particle.DustOptions dust = new Particle.DustOptions(borderColor(), 1.2f);
        double phase = (tick % 8) * 0.19;

        for (Player p : Bukkit.getOnlinePlayers()) {
            List<Zone> list = plugin.zones().inWorld(p.getWorld().getName());
            if (list.isEmpty()) continue;
            Location l = p.getLocation();

            for (Zone z : list) {
                if (l.getX() < z.getMinX() - range || l.getX() > z.getMaxX() + 1 + range
                        || l.getZ() < z.getMinZ() - range || l.getZ() > z.getMaxZ() + 1 + range) continue;

                double y0 = Math.max(z.getMinY(), Math.floor(l.getY()) - 2);
                double y1 = Math.min(z.getMaxY() + 1.0, l.getY() + 4);
                if (y1 < y0) continue;

                // walls at constant X
                for (double wx : new double[]{z.getMinX(), z.getMaxX() + 1.0}) {
                    if (Math.abs(l.getX() - wx) > range) continue;
                    double a0 = Math.max(z.getMinZ(), l.getZ() - range);
                    double a1 = Math.min(z.getMaxZ() + 1.0, l.getZ() + range);
                    for (double a = a0; a <= a1; a += 2.0)
                        for (double y = y0 + phase; y <= y1; y += 1.5) spark(p, wx, y, a, dust);
                }
                // walls at constant Z
                for (double wz : new double[]{z.getMinZ(), z.getMaxZ() + 1.0}) {
                    if (Math.abs(l.getZ() - wz) > range) continue;
                    double a0 = Math.max(z.getMinX(), l.getX() - range);
                    double a1 = Math.min(z.getMaxX() + 1.0, l.getX() + range);
                    for (double a = a0; a <= a1; a += 2.0)
                        for (double y = y0 + phase; y <= y1; y += 1.5) spark(p, a, y, wz, dust);
                }
            }
        }
    }

    private void spark(Player p, double x, double y, double z, Particle.DustOptions dust) {
        p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
        if (ThreadLocalRandom.current().nextInt(12) == 0) p.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    /** Gold outline of the wand selection, only for the player holding the wand. */
    private void drawSelections() {
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(0xffc83c), 1.3f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.selection().isWand(p.getInventory().getItemInMainHand())) continue;
            Location[] s = plugin.selection().get(p.getUniqueId());
            if (s == null || (s[0] == null && s[1] == null)) continue;
            Location a = s[0] != null ? s[0] : s[1];
            Location b = s[1] != null ? s[1] : s[0];
            if (a.getWorld() == null || !a.getWorld().equals(b.getWorld()) || !a.getWorld().equals(p.getWorld())) continue;

            double x0 = Math.min(a.getBlockX(), b.getBlockX()), x1 = Math.max(a.getBlockX(), b.getBlockX()) + 1.0;
            double y0 = Math.min(a.getBlockY(), b.getBlockY()), y1 = Math.max(a.getBlockY(), b.getBlockY()) + 1.0;
            double z0 = Math.min(a.getBlockZ(), b.getBlockZ()), z1 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1.0;
            box(p, x0, y0, z0, x1, y1, z1, gold);
        }
    }

    private void box(Player p, double x0, double y0, double z0, double x1, double y1, double z1, Particle.DustOptions d) {
        double[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (double y : ys) for (double z : zs) line(p, x0, y, z, x1, y, z, d);
        for (double x : xs) for (double z : zs) line(p, x, y0, z, x, y1, z, d);
        for (double x : xs) for (double y : ys) line(p, x, y, z0, x, y, z1, d);
    }

    private void line(Player p, double x0, double y0, double z0, double x1, double y1, double z1, Particle.DustOptions d) {
        double len = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        int n = (int) Math.min(48, Math.max(1, Math.ceil(len)));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            p.spawnParticle(Particle.DUST, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, 1, 0, 0, 0, 0, d);
        }
    }
}
