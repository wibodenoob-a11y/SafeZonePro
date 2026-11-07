package dev.safezone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SelectionManager {

    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public SelectionManager(SafeZonePlugin plugin) {
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("SafeZone Wand", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Left click a block: position 1", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right click a block: position 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Then: /sz create <name>", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setUnbreakable(true);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(m);
        return item;
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_AXE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    /** index 0 = pos1, 1 = pos2 */
    public void set(UUID id, int index, Location loc) {
        Location[] s = selections.computeIfAbsent(id, k -> new Location[2]);
        s[index] = loc.getBlock().getLocation();
    }

    public Location[] get(UUID id) { return selections.get(id); }
    public void clear(UUID id) { selections.remove(id); }

    public boolean complete(UUID id) {
        Location[] s = selections.get(id);
        return s != null && s[0] != null && s[1] != null;
    }

    public boolean expandVertical(UUID id) {
        if (!complete(id)) return false;
        Location[] s = selections.get(id);
        var w = s[0].getWorld();
        s[0].setY(w.getMinHeight());
        s[1].setY(w.getMaxHeight() - 1);
        return true;
    }
}
