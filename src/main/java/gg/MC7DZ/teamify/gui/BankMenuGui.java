package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import gg.MC7DZ.teamify.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BankMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Team team;
    private int balanceSlot;
    private int depositSlot;
    private int withdrawSlot;

    public BankMenuGui(Teamify plugin, Player viewer, Team team) {
        super(viewer);
        this.plugin = plugin;
        this.team = team;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.bank-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8&lTeam Bank"));
        int size = cfg.getInt("size", 27);
        balanceSlot = cfg.getInt("balance-slot", 13);
        depositSlot = cfg.getInt("deposit-slot", 11);
        withdrawSlot = cfg.getInt("withdraw-slot", 15);

        Inventory inv = Bukkit.createInventory(this, size, title);

        Material fillerMat = parse(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        if (cfg.getBoolean("fill-empty-slots", true)) {
            ItemStack filler = GuiItem.simple(fillerMat, " ");
            for (int i = 0; i < size; i++) inv.setItem(i, filler);
        }

        String balanceStr = plugin.getEconomyManager().format(team.getBankBalance());
        inv.setItem(balanceSlot, GuiItem.simple(parse(cfg.getString("balance-material", "GOLD_INGOT"), Material.GOLD_INGOT),
                "&6&lTeam Bank",
                "&7Balance: &f" + balanceStr));

        inv.setItem(depositSlot, GuiItem.simple(parse(cfg.getString("deposit-material", "LIME_DYE"), Material.LIME_DYE),
                "&aDeposit",
                "&7Click and type an amount in chat",
                "&7to move money from your balance",
                "&7into the team bank."));

        TeamRole role = team.getRole(getViewer().getUniqueId());
        boolean canWithdraw = role != null
                && plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)
                && getViewer().hasPermission("teams.bank.withdraw");

        if (canWithdraw) {
            inv.setItem(withdrawSlot, GuiItem.simple(parse(cfg.getString("withdraw-material", "RED_DYE"), Material.RED_DYE),
                    "&cWithdraw",
                    "&7Click and type an amount in chat",
                    "&7to move money from the team bank",
                    "&7into your balance."));
        } else {
            inv.setItem(withdrawSlot, GuiItem.simple(Material.BARRIER,
                    "&cWithdraw Locked",
                    "&7Your role doesn't allow",
                    "&7withdrawing from the team bank."));
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();
        if (!plugin.getEconomyManager().isEnabled()) {
            p.sendMessage(plugin.getConfigManager().getMessage("bank-no-economy"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        if (slot == depositSlot) {
            p.closeInventory();
            plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.BANK_DEPOSIT);
            p.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().color("&aType the amount to deposit in chat (or \"cancel\")."));
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
            p.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().color("&aType the amount to withdraw in chat (or \"cancel\")."));
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
