package dev.safezone;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ZoneCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("wand", "pos1", "pos2", "clear", "expand", "create",
            "redefine", "delete", "list", "info", "tp", "flag", "reload", "help");

    private final SafeZonePlugin plugin;

    public ZoneCommand(SafeZonePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("safezone.admin")) { plugin.send(s, "no-permission"); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            for (String line : plugin.getConfig().getStringList("messages.help")) s.sendMessage(plugin.color(line));
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> wand(s);
            case "pos1" -> pos(s, 0);
            case "pos2" -> pos(s, 1);
            case "clear" -> { Player p = player(s); if (p != null) { plugin.selection().clear(p.getUniqueId()); plugin.send(s, "selection-cleared"); } }
            case "expand" -> expand(s, a);
            case "create" -> create(s, a, false);
            case "redefine" -> create(s, a, true);
            case "delete" -> delete(s, a);
            case "list" -> list(s);
            case "info" -> info(s, a);
            case "tp" -> tp(s, a);
            case "flag" -> flag(s, a);
            case "reload" -> { plugin.reloadAll(); plugin.send(s, "reloaded"); }
            default -> usage(s, "/" + label + " help");
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.send(s, "player-only");
        return null;
    }

    private void usage(CommandSender s, String u) { plugin.send(s, "usage", "%usage%", u); }

    private void wand(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        p.getInventory().addItem(plugin.selection().createWand());
        plugin.send(s, "wand-given");
    }

    private void pos(CommandSender s, int index) {
        Player p = player(s);
        if (p == null) return;
        Location l = p.getLocation().getBlock().getLocation();
        plugin.selection().set(p.getUniqueId(), index, l);
        plugin.send(s, "pos-set", "%n%", String.valueOf(index + 1), "%x%", String.valueOf(l.getBlockX()),
                "%y%", String.valueOf(l.getBlockY()), "%z%", String.valueOf(l.getBlockZ()));
    }

    private void expand(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2 || !a[1].equalsIgnoreCase("vert")) { usage(s, "/sz expand vert"); return; }
        if (!plugin.selection().expandVertical(p.getUniqueId())) { plugin.send(s, "selection-incomplete"); return; }
        plugin.send(s, "expanded");
    }

    private void create(CommandSender s, String[] a, boolean redefine) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { usage(s, "/sz " + (redefine ? "redefine" : "create") + " <name>"); return; }
        String name = a[1];

        if (redefine) {
            Zone z = plugin.zones().get(name);
            if (z == null) { plugin.send(s, "zone-missing"); return; }
            Location[] sel = checkSelection(p);
            if (sel == null) return;
            z.redefine(sel[0], sel[1]);
            plugin.zones().add(z);
            plugin.send(s, "zone-redefined", "%name%", z.getName(), "%size%", z.sizeString());
            return;
        }

        if (!name.matches("[A-Za-z0-9_-]{1,32}")) { plugin.send(s, "invalid-name"); return; }
        if (plugin.zones().get(name) != null) { plugin.send(s, "zone-exists"); return; }
        Location[] sel = checkSelection(p);
        if (sel == null) return;
        Zone z = Zone.of(name, sel[0], sel[1]);
        plugin.zones().add(z);
        plugin.send(s, "zone-created", "%name%", z.getName(), "%size%", z.sizeString());
    }

    private Location[] checkSelection(Player p) {
        if (!plugin.selection().complete(p.getUniqueId())) { plugin.send(p, "selection-incomplete"); return null; }
        Location[] sel = plugin.selection().get(p.getUniqueId());
        if (!sel[0].getWorld().equals(sel[1].getWorld())) { plugin.send(p, "different-worlds"); return null; }
        return sel;
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { usage(s, "/sz delete <name>"); return; }
        Zone z = plugin.zones().get(a[1]);
        if (z == null) { plugin.send(s, "zone-missing"); return; }
        plugin.zones().remove(a[1]);
        plugin.send(s, "zone-deleted", "%name%", z.getName());
    }

    private void list(CommandSender s) {
        plugin.send(s, "list-header", "%count%", String.valueOf(plugin.zones().all().size()));
        for (Zone z : plugin.zones().all()) {
            s.sendMessage(plugin.msg("list-entry", "%name%", z.getName(), "%world%", z.getWorld(), "%size%", z.sizeString()));
        }
    }

    private void info(CommandSender s, String[] a) {
        Zone z = null;
        if (a.length >= 2) z = plugin.zones().get(a[1]);
        else if (s instanceof Player p) z = plugin.zones().zoneAt(p.getLocation());
        if (z == null) { plugin.send(s, "zone-missing"); return; }

        s.sendMessage(plugin.color("&a&l" + z.getName() + " &7(" + z.getWorld() + ")"));
        s.sendMessage(plugin.color("&7From &f" + z.getMinX() + ", " + z.getMinY() + ", " + z.getMinZ()
                + " &7to &f" + z.getMaxX() + ", " + z.getMaxY() + ", " + z.getMaxZ() + " &7(" + z.sizeString() + ")"));
        StringBuilder sb = new StringBuilder("&7Flags: ");
        for (String f : Zone.FLAGS) {
            sb.append(z.allows(f) ? "&c" : "&a").append(f).append(z.allows(f) ? "=allow " : "=deny ");
        }
        s.sendMessage(plugin.color(sb.toString()));
    }

    private void tp(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { usage(s, "/sz tp <name>"); return; }
        Zone z = plugin.zones().get(a[1]);
        var w = org.bukkit.Bukkit.getWorld(z == null ? "" : z.getWorld());
        if (z == null || w == null) { plugin.send(s, "zone-missing"); return; }
        double cx = (z.getMinX() + z.getMaxX() + 1) / 2.0, cz = (z.getMinZ() + z.getMaxZ() + 1) / 2.0;
        p.teleport(new Location(w, cx, w.getHighestBlockYAt((int) cx, (int) cz) + 1.0, cz));
        plugin.send(s, "tp-done", "%name%", z.getName());
    }

    private void flag(CommandSender s, String[] a) {
        if (a.length < 4) { usage(s, "/sz flag <zone> <flag> <allow|deny>"); return; }
        Zone z = plugin.zones().get(a[1]);
        if (z == null) { plugin.send(s, "zone-missing"); return; }
        String f = a[2].toLowerCase(Locale.ROOT);
        if (!Zone.FLAGS.contains(f)) { plugin.send(s, "flag-invalid", "%flags%", String.join(", ", Zone.FLAGS)); return; }
        boolean allow;
        switch (a[3].toLowerCase(Locale.ROOT)) {
            case "allow", "true", "on" -> allow = true;
            case "deny", "false", "off" -> allow = false;
            default -> { usage(s, "/sz flag <zone> <flag> <allow|deny>"); return; }
        }
        z.setFlag(f, allow);
        plugin.zones().save();
        plugin.send(s, "flag-set", "%flag%", f, "%name%", z.getName(), "%value%", allow ? "allow" : "deny");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("safezone.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String sub : SUBS) if (sub.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(sub);
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (List.of("delete", "info", "tp", "flag", "redefine").contains(sub)) {
                for (String n : plugin.zones().names()) if (n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(n);
            } else if (sub.equals("expand")) out.add("vert");
        } else if (a.length == 3 && a[0].equalsIgnoreCase("flag")) {
            for (String f : Zone.FLAGS) if (f.startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(f);
        } else if (a.length == 4 && a[0].equalsIgnoreCase("flag")) {
            out.add("allow"); out.add("deny");
        }
        return out;
    }
}
