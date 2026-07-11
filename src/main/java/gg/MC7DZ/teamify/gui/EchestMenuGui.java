package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.util.SoundUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single shared inventory per team, opened via /team echest or the main menu.
 * Always a full 54-slot (6-row) chest, but only config.yml's echest.slots of
 * those slots are actually usable - the rest are locked and filled with a filler
 * item. Only one team member may have this open at a time; teammates who try to
 * open it while it is in use receive a message and are denied.
 */
public class EchestMenuGui extends GuiHolder {

    private static final int SIZE = 54;

    /**
     * Maps team UUID → UUID of the player currently viewing that team's echest.
     * Cleared when the viewer closes the inventory.
     */
    private static final Map<UUID, UUID> activeViewers = new HashMap<>();

    /**
     * Checks whether the given team's echest is currently open by another player.
     * Returns that player's UUID if so, or null if the chest is free.
     */
    public static UUID getActiveViewer(UUID teamId) {
        UUID viewerId = activeViewers.get(teamId);
        if (viewerId == null) return null;
        Player viewer = Bukkit.getPlayer(viewerId);
        // Treat as free if the viewer went offline or closed without us knowing
        if (viewer == null || !viewer.isOnline()) {
            activeViewers.remove(teamId);
            return null;
        }
        return viewerId;
    }

    // -------------------------------------------------------------------------

    private final Team team;
    private final int usableSlots;

    public EchestMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        this.usableSlots = plugin.getConfigManager().getEchestSlots();
        build();
    }

    @Override
    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.echest-menu");
        Component title = plugin.getConfigManager().color(
                cfg != null ? cfg.getString("title", "<dark_gray><bold>Team Enderchest") : "<dark_gray><bold>Team Enderchest");

        Material lockedFiller;
        try {
            lockedFiller = Material.valueOf((cfg != null ? cfg.getString("locked-filler-item", "GRAY_STAINED_GLASS_PANE")
                    : "GRAY_STAINED_GLASS_PANE").toUpperCase());
        } catch (IllegalArgumentException e) {
            lockedFiller = Material.GRAY_STAINED_GLASS_PANE;
        }

        Inventory inv = Bukkit.createInventory(this, SIZE, title);
        ItemStack[] contents = team.getEchestContents();

        for (int i = 0; i < SIZE; i++) {
            if (i < usableSlots) {
                setEditableSlot(i, true);
                inv.setItem(i, contents[i]);
            } else {
                setEditableSlot(i, false);
                inv.setItem(i, GuiItem.simple(lockedFiller, Component.text(" ")));
            }
        }

        setInventory(inv);
    }

    /** Copies whatever's currently in the usable slots back into the team's stored contents. */
    private void syncContentsToTeam() {
        Inventory inv = getInventory();
        if (inv == null) return;
        ItemStack[] contents = team.getEchestContents();
        for (int i = 0; i < usableSlots; i++) {
            contents[i] = inv.getItem(i);
        }
        team.setEchestContents(contents);
        plugin.getTeamManager().saveTeam(team);
    }

    @Override
    public void open() {
        // Register this player as the active viewer before opening
        activeViewers.put(team.getId(), getViewer().getUniqueId());
        getViewer().openInventory(getInventory());
        // Play ender chest open sound
        SoundUtil.play(getViewer(), "block.ender_chest.open");
    }

    @Override
    public void onGuiClose() {
        syncContentsToTeam();
        // Release the lock only if this instance still owns it
        activeViewers.remove(team.getId(), getViewer().getUniqueId());
        // Play ender chest close sound
        SoundUtil.play(getViewer(), "block.ender_chest.close");
        super.onGuiClose();
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        // No-op: usable slots are marked editable so GuiListener lets normal
        // chest interaction through untouched; locked slots stay cancelled.
    }
}