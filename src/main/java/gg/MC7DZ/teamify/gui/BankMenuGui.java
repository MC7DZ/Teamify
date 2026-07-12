package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BankMenuGui extends GuiHolder {

    private final Team team;
    private int balanceSlot;
    private int depositSlot;
    private int withdrawSlot;
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public BankMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.bank-menu");
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray><bold>Team Bank"));
        int size = cfg.getInt("size", 54);
        
        // Load slots from gui.yml items section
        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null) {
            balanceSlot = itemsCfg.getInt("balance.slot", 22);
            depositSlot = itemsCfg.getInt("deposit.slot", 20);
            withdrawSlot = itemsCfg.getInt("withdraw.slot", 24);
            backButtonSlot = itemsCfg.getInt("back.slot", 45);
        } else {
            // Fallback to default hardcoded slots if items section is missing
            balanceSlot = 22;
            depositSlot = 20;
            withdrawSlot = 24;
            backButtonSlot = 45;
        }

        Inventory inv = Bukkit.createInventory(this, size, title);

        // Fill empty slots if configured
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
            } else {
                // Fallback to filling all empty slots if no specific filler-slots are defined
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
                }
            }
        }

        // Handle back button
        if (itemsCfg != null && itemsCfg.contains("back")) {
            setBackButton(inv, backButtonSlot);
        }

        String balanceStr = plugin.getEconomyManager().format(team.getBankBalance());
        // Balance item
        if (itemsCfg != null && itemsCfg.contains("balance")) {
            placeConfigItem(inv, balanceSlot, itemsCfg.getConfigurationSection("balance"), "amount", balanceStr);
        } else {
            inv.setItem(balanceSlot, GuiItem.simple(Material.GOLD_INGOT,
                    plugin.getConfigManager().color("<gold><bold>Team Bank"),
                    plugin.getConfigManager().color("<gray>Balance: <white>" + balanceStr)));
        }

        // Deposit item
        if (itemsCfg != null && itemsCfg.contains("deposit")) {
            placeConfigItem(inv, depositSlot, itemsCfg.getConfigurationSection("deposit"));
        } else {
            inv.setItem(depositSlot, GuiItem.simple(Material.LIME_DYE,
                    plugin.getConfigManager().color("<green>Deposit"),
                    plugin.getConfigManager().color("<gray>Click and type an amount in chat"),
                    plugin.getConfigManager().color("<gray>to move money from your balance"),
                    plugin.getConfigManager().color("<gray>into the team bank.")));
        }

        TeamRole role = team.getRole(getViewer().getUniqueId());
        boolean canWithdraw = role != null
                && plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)
                && getViewer().hasPermission("teams.bank.withdraw");

        // Withdraw item
        if (canWithdraw) {
            if (itemsCfg != null && itemsCfg.contains("withdraw")) {
                placeConfigItem(inv, withdrawSlot, itemsCfg.getConfigurationSection("withdraw"));
            } else {
                inv.setItem(withdrawSlot, GuiItem.simple(Material.RED_DYE,
                        plugin.getConfigManager().color("<red>Withdraw"),
                        plugin.getConfigManager().color("<gray>Click and type an amount in chat"),
                        plugin.getConfigManager().color("<gray>to move money from the team bank"),
                        plugin.getConfigManager().color("<gray>into your balance.")));
            }
        } else {
            inv.setItem(withdrawSlot, GuiItem.simple(Material.BARRIER,
                    plugin.getConfigManager().color("<red>Withdraw Locked"),
                    plugin.getConfigManager().color("<gray>Your role doesn't allow"),
                    plugin.getConfigManager().color("<gray>withdrawing from the team bank.")));
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            new MainMenuGui(p, team).open(); // Go back to MainMenuGui
            return;
        }

        if (!plugin.getEconomyManager().isEnabled()) {
            p.sendMessage(plugin.getConfigManager().getMessage("bank-no-economy"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        if (slot == depositSlot) {
            p.closeInventory();
            plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.BANK_DEPOSIT);
            p.sendMessage(plugin.getConfigManager().getPrefix().append(
                    plugin.getConfigManager().color("<green>Type the amount to deposit in chat (or \"cancel\").")));
            SoundUtil.play(p, plugin.getConfigManager().getGuiOpenSound());
        } else if (slot == withdrawSlot) {
            TeamRole role = team.getRole(p.getUniqueId());
            boolean canWithdraw = role != null
                    && plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)
                    && p.hasPermission("teams.bank.withdraw");
            if (!canWithdraw) {
                p.sendMessage(plugin.getConfigManager().getMessage("bank-cant-withdraw-role"));
                SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
                return;
            }
            p.closeInventory();
            plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.BANK_WITHDRAW);
            p.sendMessage(plugin.getConfigManager().getPrefix().append(
                    plugin.getConfigManager().color("<green>Type the amount to withdraw in chat (or \"cancel\").")));
            SoundUtil.play(p, plugin.getConfigManager().getGuiOpenSound());
        }
    }

    private Material parse(String s, Material fallback) {
        try {
            return Material.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}