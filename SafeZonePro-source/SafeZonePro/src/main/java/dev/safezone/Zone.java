package dev.safezone;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A cuboid protected region. Bounds are inclusive block coordinates. */
public final class Zone {

    /** Flag value true = ALLOWED (protection off). Default for every flag is false = protected. */
    public static final List<String> FLAGS =
            List.of("build", "pvp", "mobs", "tnt", "knockback", "fall-damage", "vehicles", "doors");

    private final String name;
    private String world;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<String, Boolean> flags = new LinkedHashMap<>();

    public Zone(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        setBounds(x1, y1, z1, x2, y2, z2);
    }

    public static Zone of(String name, Location a, Location b) {
        return new Zone(name, a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public void redefine(Location a, Location b) {
        this.world = a.getWorld().getName();
        setBounds(a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    private void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
        minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
        minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
    }

    public boolean contains(Location l) {
        World w = l.getWorld();
        return w != null && w.getName().equals(world) && contains(l.getX(), l.getY(), l.getZ());
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1.0 && y >= minY && y < maxY + 1.0 && z >= minZ && z < maxZ + 1.0;
    }

    public BoundingBox box() {
        return new BoundingBox(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    public boolean allows(String flag) { return flags.getOrDefault(flag, false); }
    public void setFlag(String flag, boolean allowed) { flags.put(flag, allowed); }
    public Map<String, Boolean> getFlags() { return flags; }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public String sizeString() {
        return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
    }
}
