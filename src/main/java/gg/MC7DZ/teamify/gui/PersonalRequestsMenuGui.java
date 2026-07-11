package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PersonalRequestsMenuGui extends GuiHolder {

    private final Map<Integer, UUID> slotToTeamId = new HashMap<>(); // Map to store team ID for each invite item
    private int backButtonSlot = -1;

    public PersonalRequestsMenuGui(Player viewer) {
        super(viewer);
        build();
    }

    @Override
    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.personal-requests-menu");
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Personal Requests"));
        int size = cfg.getInt("size", 54); // Increased default size to 54

        Inventory inv = Bukkit.createInventory(this, size, title);

        // Fill empty slots if configured
        java.util.Set<Integer> reservedSlots = new java.util.HashSet<>();
        if (cfg.getBoolean("fill-empty-slots", true)) {
            Material filler;
            try {
                filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
            } catch (IllegalArgumentException e) {
                filler = Material.GRAY_STAINED_GLASS_PANE;
            }
            List<Integer> fillerSlots = cfg.getIntegerList("filler-slots");
            if (fillerSlots != null && !fillerSlots.isEmpty()) {
                fillSlots(inv, filler, fillerSlots);
                reservedSlots.addAll(fillerSlots);
            } else {
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
                }
            }
        }

        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null) {
            // Handle back button
            if (itemsCfg.contains("back")) {
                backButtonSlot = itemsCfg.getInt("back.slot", -1);
                if (backButtonSlot != -1) {
                    setBackButton(inv, backButtonSlot);
                    reservedSlots.add(backButtonSlot);
                }
            }
        }

        // Display pending team invites
        List<Team> invites = plugin.getTeamManager().getPendingTeamInvites(getViewer().getUniqueId());
        int currentSlot = 0;
        for (Team team : invites) {
            while (currentSlot < size && reservedSlots.contains(currentSlot)) {
                currentSlot++;
            }
            if (currentSlot >= size) break; // No more space in inventory

            ItemStack inviteItem;
            Component itemName = plugin.getConfigManager().color("<aqua>Invite from <white>" + team.getName());
            List<Component> itemLore = new ArrayList<>();
            itemLore.add(plugin.getConfigManager().color("<gray>Click to accept or deny."));

            if (team.hasCustomItem()) {
                inviteItem = GuiItem.withOverrides(team.getCustomItem(), itemName, itemLore);
            } else {
                inviteItem = GuiItem.simple(Material.PAPER, itemName, itemLore.toArray(new Component[0]));
            }
            inv.setItem(currentSlot, inviteItem);
            slotToTeamId.put(currentSlot, team.getId());
            currentSlot++;
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            new PlayerSettingsMenuGui(p).open();
            return;
        }

        UUID teamId = slotToTeamId.get(slot);
        if (teamId != null) {
            Team team = plugin.getTeamManager().getTeam(teamId);
            if (team != null) {
                // Open the InviteMenuGui for this specific team
                new InviteMenuGui(p, team).open();
            } else {
                p.sendMessage(plugin.getConfigManager().getMessage("team-not-found"));
                rebuild(); // Refresh the GUI in case the team was disbanded
            }
        }
    }
}