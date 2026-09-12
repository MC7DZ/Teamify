package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public class InviteMenuGui extends GuiHolder {
   private final Team team;
   private int acceptSlot;
   private int denySlot;
   private int backButtonSlot = -1;

   public InviteMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.invite-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Pending Invites"));
      int size = cfg.getInt("size", 54);
      ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
      if (itemsCfg != null) {
         this.acceptSlot = itemsCfg.getInt("accept.slot", 30);
         this.denySlot = itemsCfg.getInt("deny.slot", 32);
         this.backButtonSlot = itemsCfg.getInt("back.slot", 45);
      } else {
         this.acceptSlot = 30;
         this.denySlot = 32;
         this.backButtonSlot = 45;
      }

      Inventory inv = Bukkit.createInventory(this, size, title);
      if (cfg.getBoolean("fill-empty-slots", true)) {
         Material filler;
         try {
            filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
         } catch (IllegalArgumentException e) {
            filler = Material.GRAY_STAINED_GLASS_PANE;
         }

         List<Integer> fillerSlots = cfg.getIntegerList("filler-slots");
         if (fillerSlots != null && !fillerSlots.isEmpty()) {
            this.fillSlots(inv, filler, fillerSlots);
         } else {
            for (int i = 0; i < size; i++) {
               inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
            }
         }
      }

      if (itemsCfg != null && itemsCfg.contains("back")) {
         this.setBackButton(inv, this.backButtonSlot);
      }

      if (itemsCfg != null && itemsCfg.contains("invite-info")) {
         this.placeConfigItem(inv, itemsCfg.getInt("invite-info.slot", 22), itemsCfg.getConfigurationSection("invite-info"), "team", this.team.getName());
      } else {
         inv.setItem(
            22,
            GuiItem.simple(
               Material.PAPER,
               this.plugin.getConfigManager().color("<aqua>Invite from <white>" + this.team.getName()),
               this.plugin.getConfigManager().color("<gray>Click accept or deny below.")
            )
         );
      }

      if (itemsCfg != null && itemsCfg.contains("accept")) {
         this.placeConfigItem(inv, this.acceptSlot, itemsCfg.getConfigurationSection("accept"));
      } else {
         inv.setItem(this.acceptSlot, GuiItem.simple(Material.LIME_DYE, this.plugin.getConfigManager().color("<green>Accept")));
      }

      if (itemsCfg != null && itemsCfg.contains("deny")) {
         this.placeConfigItem(inv, this.denySlot, itemsCfg.getConfigurationSection("deny"));
      } else {
         inv.setItem(this.denySlot, GuiItem.simple(Material.RED_DYE, this.plugin.getConfigManager().color("<red>Deny")));
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      UUID uuid = p.getUniqueId();
      if (slot == this.backButtonSlot) {
         p.closeInventory();
      } else {
         if (slot == this.acceptSlot) {
            p.closeInventory();
            if (!this.team.hasInvite(uuid)) {
               p.sendMessage(this.plugin.getConfigManager().getMessage("invite-received"));
               return;
            }

            if (this.plugin.getTeamManager().isInTeam(uuid)) {
               p.sendMessage(this.plugin.getConfigManager().getMessage("already-in-team"));
               return;
            }

            int maxMembers = this.plugin.getConfigManager().getMaxMembers();
            if (maxMembers > 0 && this.team.getSize() >= maxMembers) {
               p.sendMessage(this.plugin.getConfigManager().getMessage("team-full"));
               return;
            }

            this.team.removeInvite(uuid);
            this.plugin.getTeamManager().addMember(this.team, uuid, TeamRole.MEMBER);
            this.plugin.getTeamManager().saveTeam(this.team);
            this.plugin.getVisibilityManager().refreshTeamAndAllies(this.team);
            p.sendMessage(
               this.plugin
                  .getConfigManager()
                  .getPrefix()
                  .append(this.plugin.getConfigManager().color("<green>You joined <aqua>" + this.team.getName() + "<green>!"))
            );

            for (UUID memberId : this.team.getMembers().keySet()) {
               if (!memberId.equals(uuid)) {
                  Player member = Bukkit.getPlayer(memberId);
                  if (member != null) {
                     member.sendMessage(this.plugin.getConfigManager().getMessage("player-joined-broadcast", "player", p.getName()));
                  }
               }
            }
         } else if (slot == this.denySlot) {
            p.closeInventory();
            this.team.removeInvite(uuid);
            p.sendMessage(
               this.plugin
                  .getConfigManager()
                  .getPrefix()
                  .append(this.plugin.getConfigManager().color("<gray>You declined the invite from <white>" + this.team.getName() + "<gray>."))
            );
         }
      }
   }
}
