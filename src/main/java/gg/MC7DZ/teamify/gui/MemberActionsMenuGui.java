package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public class MemberActionsMenuGui extends GuiHolder {
   private final Team team;
   private final UUID targetId;
   private int backButtonSlot = -1;
   private int kickSlot = -1;
   private int promoteSlot = -1;
   private int demoteSlot = -1;
   private int transferSlot = -1;

   public MemberActionsMenuGui(Player viewer, Team team, UUID targetId) {
      super(viewer);
      this.team = team;
      this.targetId = targetId;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.member-actions-menu");
      int size = cfg.getInt("size", 54);
      OfflinePlayer target = Bukkit.getOfflinePlayer(this.targetId);
      String targetName = target.getName() != null ? target.getName() : "Unknown";
      TeamRole targetRole = this.team.getRole(this.targetId);
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray><bold>Manage {player}").replace("{player}", targetName));
      Inventory inv = Bukkit.createInventory(this, size, title);
      Set<Integer> reservedSlots = new HashSet<>();
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
            reservedSlots.addAll(fillerSlots);
         } else {
            for (int i = 0; i < size; i++) {
               inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
            }
         }
      }

      ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
      if (itemsCfg != null) {
         if (itemsCfg.contains("back")) {
            this.backButtonSlot = itemsCfg.getInt("back.slot", -1);
            if (this.backButtonSlot != -1) {
               this.setBackButton(inv, this.backButtonSlot);
            }
         }

         if (itemsCfg.contains("kick")) {
            this.kickSlot = itemsCfg.getInt("kick.slot", -1);
            this.placeConfigItem(inv, this.kickSlot, itemsCfg.getConfigurationSection("kick"), "player", targetName, "role", targetRole.name());
         }

         if (itemsCfg.contains("promote")) {
            this.promoteSlot = itemsCfg.getInt("promote.slot", -1);
            this.placeConfigItem(inv, this.promoteSlot, itemsCfg.getConfigurationSection("promote"), "player", targetName, "role", targetRole.name());
         }

         if (itemsCfg.contains("demote")) {
            this.demoteSlot = itemsCfg.getInt("demote.slot", -1);
            this.placeConfigItem(inv, this.demoteSlot, itemsCfg.getConfigurationSection("demote"), "player", targetName, "role", targetRole.name());
         }

         if (itemsCfg.contains("transfer")) {
            this.transferSlot = itemsCfg.getInt("transfer.slot", -1);
            this.placeConfigItem(inv, this.transferSlot, itemsCfg.getConfigurationSection("transfer"), "player", targetName, "role", targetRole.name());
         }
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         new MembersMenuGui(p, this.team).open();
      } else {
         if (slot == this.kickSlot) {
            this.tryKick(p);
         } else if (slot == this.promoteSlot) {
            this.tryPromote(p);
         } else if (slot == this.demoteSlot) {
            this.tryDemote(p);
         } else if (slot == this.transferSlot) {
            this.tryTransfer(p);
         }
      }
   }

   private void tryKick(Player p) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      TeamRole role = this.team.getRole(p.getUniqueId());
      if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-kick", false)) {
         p.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else if (!this.team.isMember(this.targetId)) {
         p.sendMessage(cm.getMessage("player-not-in-team"));
      } else if (this.targetId.equals(p.getUniqueId())) {
         p.sendMessage(cm.getMessage("cant-kick-yourself"));
      } else if (this.targetId.equals(this.team.getOwner())) {
         p.sendMessage(cm.getMessage("cant-kick-owner"));
      } else {
         boolean isOwner = p.getUniqueId().equals(this.team.getOwner());
         TeamRole targetRole = this.team.getRole(this.targetId);
         if (!isOwner && targetRole.getWeight() >= role.getWeight()) {
            p.sendMessage(cm.getMessage("cant-kick-higher-rank"));
         } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(this.targetId);
            String targetName = target.getName() != null ? target.getName() : "Unknown";
            Runnable doKick = () -> {
               tm.removeMember(this.team, this.targetId);
               tm.saveTeam(this.team);
               this.plugin.getVisibilityManager().refreshTeamAndAllies(this.team);
               Player kickedOnline = target.getPlayer();
               if (kickedOnline != null) {
                  this.plugin.getVisibilityManager().refresh(kickedOnline);
                  kickedOnline.sendMessage(cm.getMessage("kicked-from-team", "team", this.team.getName(), "player", p.getName()));
               }

               p.sendMessage(cm.getMessage("player-kicked", "player", targetName));

               for (UUID memberId : this.team.getMembers().keySet()) {
                  if (!memberId.equals(p.getUniqueId())) {
                     Player online = Bukkit.getPlayer(memberId);
                     if (online != null) {
                        online.sendMessage(cm.getMessage("player-kicked-broadcast", "player", targetName, "kicker", p.getName()));
                     }
                  }
               }

               new MembersMenuGui(p, this.team).open();
            };
            new ConfirmMenuGui(p, doKick, () -> new MemberActionsMenuGui(p, this.team, this.targetId).open()).open();
         }
      }
   }

   private void tryPromote(Player p) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      TeamRole role = this.team.getRole(p.getUniqueId());
      if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
         p.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else if (this.targetId.equals(p.getUniqueId())) {
         p.sendMessage(cm.getMessage("cant-promote-yourself"));
      } else if (!this.team.isMember(this.targetId)) {
         p.sendMessage(cm.getMessage("player-not-in-team"));
      } else {
         TeamRole current = this.team.getRole(this.targetId);
         if (current == TeamRole.LEADER) {
            p.sendMessage(cm.getMessage("already-highest-rank"));
         } else {
            TeamRole next = current.next();
            if (next.getWeight() >= role.getWeight()) {
               p.sendMessage(cm.getMessage("cant-promote-own-rank"));
            } else {
               OfflinePlayer target = Bukkit.getOfflinePlayer(this.targetId);
               String targetName = target.getName() != null ? target.getName() : "Unknown";
               Runnable doPromote = () -> {
                  this.team.setRole(this.targetId, next);
                  tm.saveTeam(this.team);
                  p.sendMessage(cm.getMessage("player-promoted", "player", targetName, "role", next.name()));
                  Player targetOnline = target.getPlayer();
                  if (targetOnline != null) {
                     targetOnline.sendMessage(cm.getMessage("promoted-notify", "role", next.name(), "team", this.team.getName()));
                  }

                  for (UUID memberId : this.team.getMembers().keySet()) {
                     if (!memberId.equals(p.getUniqueId()) && !memberId.equals(this.targetId)) {
                        Player online = Bukkit.getPlayer(memberId);
                        if (online != null) {
                           online.sendMessage(cm.getMessage("promoted-broadcast", "player", targetName, "role", next.name()));
                        }
                     }
                  }

                  new MembersMenuGui(p, this.team).open();
               };
               new ConfirmMenuGui(p, doPromote, () -> new MemberActionsMenuGui(p, this.team, this.targetId).open()).open();
            }
         }
      }
   }

   private void tryDemote(Player p) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      TeamRole role = this.team.getRole(p.getUniqueId());
      if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
         p.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else if (this.targetId.equals(p.getUniqueId())) {
         p.sendMessage(cm.getMessage("cant-demote-yourself"));
      } else if (!this.team.isMember(this.targetId)) {
         p.sendMessage(cm.getMessage("player-not-in-team"));
      } else if (this.targetId.equals(this.team.getOwner())) {
         p.sendMessage(cm.getMessage("cant-demote-owner"));
      } else {
         TeamRole current = this.team.getRole(this.targetId);
         if (current.getWeight() >= role.getWeight()) {
            p.sendMessage(cm.getMessage("cant-demote-higher-rank"));
         } else {
            TeamRole newRole = current.previous();
            OfflinePlayer target = Bukkit.getOfflinePlayer(this.targetId);
            String targetName = target.getName() != null ? target.getName() : "Unknown";
            Runnable doDemote = () -> {
               this.team.setRole(this.targetId, newRole);
               tm.saveTeam(this.team);
               p.sendMessage(cm.getMessage("player-demoted", "player", targetName, "role", newRole.name()));
               Player targetOnline = target.getPlayer();
               if (targetOnline != null) {
                  targetOnline.sendMessage(cm.getMessage("demoted-notify", "role", newRole.name(), "team", this.team.getName()));
               }

               for (UUID memberId : this.team.getMembers().keySet()) {
                  if (!memberId.equals(p.getUniqueId()) && !memberId.equals(this.targetId)) {
                     Player online = Bukkit.getPlayer(memberId);
                     if (online != null) {
                        online.sendMessage(cm.getMessage("demoted-broadcast", "player", targetName, "role", newRole.name()));
                     }
                  }
               }

               new MembersMenuGui(p, this.team).open();
            };
            new ConfirmMenuGui(p, doDemote, () -> new MemberActionsMenuGui(p, this.team, this.targetId).open()).open();
         }
      }
   }

   private void tryTransfer(Player p) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!this.team.getOwner().equals(p.getUniqueId())) {
         p.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else if (this.targetId.equals(p.getUniqueId())) {
         p.sendMessage(cm.getPrefix().append(cm.color("<red>You already own this team.")));
      } else if (!this.team.isMember(this.targetId)) {
         p.sendMessage(cm.getMessage("player-not-in-team"));
      } else {
         OfflinePlayer target = Bukkit.getOfflinePlayer(this.targetId);
         String targetName = target.getName() != null ? target.getName() : "Unknown";
         Runnable doTransfer = () -> {
            TeamRole targetOldRole = this.team.getRole(this.targetId);
            this.team.setRole(this.targetId, TeamRole.LEADER);
            this.team.setRole(p.getUniqueId(), targetOldRole);
            this.team.setOwner(this.targetId);
            tm.saveTeam(this.team);
            p.sendMessage(cm.getMessage("ownership-transferred", "player", targetName, "role", targetOldRole.name()));
            Player targetOnline = target.getPlayer();
            if (targetOnline != null) {
               targetOnline.sendMessage(cm.getMessage("ownership-transferred-notify", "team", this.team.getName()));
            }

            for (UUID memberId : this.team.getMembers().keySet()) {
               if (!memberId.equals(p.getUniqueId()) && !memberId.equals(this.targetId)) {
                  Player online = Bukkit.getPlayer(memberId);
                  if (online != null) {
                     online.sendMessage(cm.getMessage("ownership-transferred-broadcast", "player", targetName));
                  }
               }
            }

            new MembersMenuGui(p, this.team).open();
         };
         new ConfirmMenuGui(p, doTransfer, () -> new MemberActionsMenuGui(p, this.team, this.targetId).open()).open();
      }
   }
}
