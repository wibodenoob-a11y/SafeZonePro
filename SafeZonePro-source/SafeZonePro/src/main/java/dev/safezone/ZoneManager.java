package dev.safezone;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ZoneManager {

    private final SafeZonePlugin plugin;
    private final File file;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<String, List<Zone>> byWorld = new HashMap<>();

    public ZoneManager(SafeZonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
    }

    public void load() {
        zones.clear();
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = y.getConfigurationSection("zones");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    if (s == null) continue;
                    Zone z = new Zone(s.getString("name", key), s.getString("world", "world"),
                            s.getInt("min.x"), s.getInt("min.y"), s.getInt("min.z"),
                            s.getInt("max.x"), s.getInt("max.y"), s.getInt("max.z"));
                    ConfigurationSection f = s.getConfigurationSection("flags");
                    if (f != null) for (String fk : f.getKeys(false)) z.setFlag(fk, f.getBoolean(fk));
                    zones.put(key.toLowerCase(Locale.ROOT), z);
                }
            }
        }
        rebuild();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Zone> e : zones.entrySet()) {
            Zone z = e.getValue();
            String p = "zones." + e.getKey() + ".";
            y.set(p + "name", z.getName());
            y.set(p + "world", z.getWorld());
            y.set(p + "min.x", z.getMinX()); y.set(p + "min.y", z.getMinY()); y.set(p + "min.z", z.getMinZ());
            y.set(p + "max.x", z.getMaxX()); y.set(p + "max.y", z.getMaxY()); y.set(p + "max.z", z.getMaxZ());
            for (Map.Entry<String, Boolean> f : z.getFlags().entrySet()) y.set(p + "flags." + f.getKey(), f.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save zones.yml: " + ex.getMessage());
        }
    }

    private void rebuild() {
        byWorld.clear();
        for (Zone z : zones.values()) byWorld.computeIfAbsent(z.getWorld(), k -> new ArrayList<>()).add(z);
    }

    public Zone get(String name) { return zones.get(name.toLowerCase(Locale.ROOT)); }

    public void add(Zone z) {
        zones.put(z.getName().toLowerCase(Locale.ROOT), z);
        rebuild();
        save();
    }

    public boolean remove(String name) {
        boolean r = zones.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (r) { rebuild(); save(); }
        return r;
    }

    public Collection<Zone> all() { return zones.values(); }

    public List<String> names() {
        List<String> n = new ArrayList<>();
        for (Zone z : zones.values()) n.add(z.getName());
        return n;
    }

    public List<Zone> inWorld(String world) {
        return byWorld.getOrDefault(world, Collections.emptyList());
    }

    public Zone zoneAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        List<Zone> list = byWorld.get(l.getWorld().getName());
        if (list == null) return null;
        double x = l.getX(), y = l.getY(), z = l.getZ();
        for (Zone zone : list) if (zone.contains(x, y, z)) return zone;
        return null;
    }
}
