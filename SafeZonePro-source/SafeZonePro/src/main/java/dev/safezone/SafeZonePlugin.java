package dev.safezone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SafeZonePlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private ZoneManager zones;
    private SelectionManager selection;
    private ProtectionListener protection;
    private ZoneTask zoneTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        zones = new ZoneManager(this);
        zones.load();
        selection = new SelectionManager(this);
        protection = new ProtectionListener(this);
        protection.reload();
        zoneTask = new ZoneTask(this);

        getServer().getPluginManager().registerEvents(protection, this);
        getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
        getServer().getPluginManager().registerEvents(zoneTask, this);

        ZoneCommand cmd = new ZoneCommand(this);
        getCommand("safezone").setExecutor(cmd);
        getCommand("safezone").setTabCompleter(cmd);

        zoneTask.runTaskTimer(this, 20L, 5L);
        getLogger().info("Loaded " + zones.all().size() + " zone(s).");
    }

    @Override
    public void onDisable() {
        if (zoneTask != null) zoneTask.restoreAll();
    }

    public void reloadAll() {
        reloadConfig();
        zones.load();
        protection.reload();
    }

    public boolean isAdmin(Player p) { return p.isOp() || p.hasPermission("safezone.admin"); }

    public ZoneManager zones() { return zones; }
    public SelectionManager selection() { return selection; }

    public Component color(String s) { return LEGACY.deserialize(s); }

    /** Message without prefix (titles, action bars). */
    public Component msg(String key, String... repl) {
        String raw = getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < repl.length; i += 2) raw = raw.replace(repl[i], repl[i + 1]);
        return LEGACY.deserialize(raw);
    }

    /** Chat message with prefix. */
    public void send(CommandSender to, String key, String... repl) {
        to.sendMessage(color(getConfig().getString("messages.prefix", "")).append(msg(key, repl)));
    }
}
