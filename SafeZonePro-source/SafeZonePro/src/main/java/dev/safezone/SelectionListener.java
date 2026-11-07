package dev.safezone;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Wand clicks: left = pos1, right = pos2. */
public final class SelectionListener implements Listener {

    private final SafeZonePlugin plugin;

    public SelectionListener(SafeZonePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.selection().isWand(e.getItem())) return;
        Player p = e.getPlayer();
        if (!plugin.isAdmin(p) || e.getClickedBlock() == null) return;

        Action a = e.getAction();
        int index;
        if (a == Action.LEFT_CLICK_BLOCK) index = 0;
        else if (a == Action.RIGHT_CLICK_BLOCK) index = 1;
        else return;

        e.setCancelled(true);
        var loc = e.getClickedBlock().getLocation();
        plugin.selection().set(p.getUniqueId(), index, loc);
        plugin.send(p, "pos-set", "%n%", String.valueOf(index + 1),
                "%x%", String.valueOf(loc.getBlockX()), "%y%", String.valueOf(loc.getBlockY()),
                "%z%", String.valueOf(loc.getBlockZ()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.isAdmin(e.getPlayer()) && plugin.selection().isWand(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true);
        }
    }
}
