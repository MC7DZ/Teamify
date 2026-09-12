package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class RequestsMenuGui extends GuiHolder {
   private final Team team;
   private final Map<Integer, UUID> slotToJoinRequest = new HashMap<>();
   private final Map<Integer, UUID> slotToAllyRequest = new HashMap<>();
   private int backButtonSlot = -1;

   public RequestsMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      this.slotToJoinRequest.clear();
      this.slotToAllyRequest.clear();
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.requests-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Requests"));
      int size = cfg.getInt("size", 54);
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
      if (itemsCfg != null && itemsCfg.contains("back")) {
         this.backButtonSlot = itemsCfg.getInt("back.slot", -1);
         if (this.backButtonSlot != -1) {
            this.setBackButton(inv, this.backButtonSlot);
         }
      }

      if (this.backButtonSlot != -1) {
         reservedSlots.add(this.backButtonSlot);
      }

      int slot = 0;
      ConfigurationSection joinItemCfg = cfg.getConfigurationSection("join-request-item");

      for (UUID requester : new ArrayList<>(this.team.getPendingJoinRequests())) {
         while (slot < size && reservedSlots.contains(slot)) {
            slot++;
         }

         if (slot >= size) {
            break;
         }

         OfflinePlayer op = Bukkit.getOfflinePlayer(requester);
         String playerName = op.getName() != null ? op.getName() : "Unknown";
         inv.setItem(slot, this.buildJoinRequestItem(joinItemCfg, requester, playerName));
         this.slotToJoinRequest.put(slot, requester);
         slot++;
      }

      ConfigurationSection allyItemCfg = cfg.getConfigurationSection("ally-request-item");

      for (UUID otherId : new ArrayList<>(this.team.getPendingAllyInvites())) {
         while (slot < size && reservedSlots.contains(slot)) {
            slot++;
         }

         if (slot >= size) {
            break;
         }

         Team other = this.plugin.getTeamManager().getTeam(otherId);
         if (other != null) {
            inv.setItem(slot, this.buildAllyRequestItem(allyItemCfg, other));
            this.slotToAllyRequest.put(slot, otherId);
            slot++;
         }
      }

      this.setInventory(inv);
   }

   private ItemStack buildJoinRequestItem(ConfigurationSection cfg, UUID requester, String playerName) {
      Material mat = Material.PLAYER_HEAD;
      if (cfg != null) {
         try {
            mat = Material.valueOf(cfg.getString("material", "PLAYER_HEAD").toUpperCase());
         } catch (IllegalArgumentException ignored) {
         }
      }

      String nameFormat = cfg != null ? cfg.getString("name", "<green><bold>{player}") : "<green><bold>{player}";
      List<String> loreFormat = cfg != null ? cfg.getStringList("lore") : new ArrayList<>();
      boolean glow = cfg != null && cfg.getBoolean("glow", false);
      Component name = this.plugin.getConfigManager().color(nameFormat.replace("{player}", playerName));
      List<Component> lore = loreFormat.stream()
         .map(line -> this.plugin.getConfigManager().color(line.replace("{player}", playerName)))
         .collect(Collectors.toList());
      ItemStack item;
      if (mat == Material.PLAYER_HEAD) {
         item = GuiItem.playerHead(requester.toString(), name, glow, lore.toArray(new Component[0]));
      } else {
         item = GuiItem.simple(mat, name, glow, null, lore.toArray(new Component[0]));
      }

      return item;
   }

   private ItemStack buildAllyRequestItem(ConfigurationSection cfg, Team other) {
      Material mat = Material.ENDER_EYE;
      if (cfg != null) {
         try {
            mat = Material.valueOf(cfg.getString("material", "ENDER_EYE").toUpperCase());
         } catch (IllegalArgumentException ignored) {
         }
      }

      String nameFormat = cfg != null ? cfg.getString("name", "<aqua><bold>{team}") : "<aqua><bold>{team}";
      List<String> loreFormat = cfg != null ? cfg.getStringList("lore") : new ArrayList<>();
      boolean glow = cfg != null && cfg.getBoolean("glow", false);
      Component name = this.applyTeamPlaceholders(nameFormat, other);
      List<Component> lore = loreFormat.stream().map(line -> this.applyTeamPlaceholders(line, other)).collect(Collectors.toList());
      return GuiItem.simple(mat, name, glow, null, lore.toArray(new Component[0]));
   }

   private Component applyTeamPlaceholders(String text, Team other) {
      return this.plugin
         .getConfigManager()
         .color(
            text.replace("{team}", other.getColoredName())
               .replace("{tag}", other.getTag())
               .replace("{level}", String.valueOf(other.getLevel()))
               .replace("{members}", String.valueOf(other.getSize()))
         );
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         new MainMenuGui(p, this.team).open();
      } else {
         ConfigManager cm = this.plugin.getConfigManager();
         TeamManager tm = this.plugin.getTeamManager();
         UUID requester = this.slotToJoinRequest.get(slot);
         if (requester != null) {
            this.handleJoinRequestClick(p, cm, tm, requester, clickType);
         } else {
            UUID otherId = this.slotToAllyRequest.get(slot);
            if (otherId != null) {
               this.handleAllyRequestClick(p, cm, tm, otherId, clickType);
            }
         }
      }
   }

   private void handleJoinRequestClick(Player p, ConfigManager cm, TeamManager tm, UUID requester, ClickType clickType) {
      TeamRole role = this.team.getRole(p.getUniqueId());
      if (role != null && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-invite", false)) {
         OfflinePlayer op = Bukkit.getOfflinePlayer(requester);
         String playerName = op.getName() != null ? op.getName() : "Unknown";
         if (clickType.isRightClick()) {
            this.team.removeJoinRequest(requester);
            tm.saveTeam(this.team);
            p.sendMessage(cm.getPrefix().append(cm.color("<gray>You denied the join request from <white>" + playerName + "<gray>.")));
            Player online = op.getPlayer();
            if (online != null) {
               online.sendMessage(cm.getMessage("join-request-denied", "team", this.team.getName()));
            }

            new RequestsMenuGui(p, this.team).open();
         } else if (clickType.isLeftClick()) {
            this.team.removeJoinRequest(requester);
            if (tm.isInTeam(requester)) {
               tm.saveTeam(this.team);
               p.sendMessage(cm.getPrefix().append(cm.color("<red>That player already joined another team.")));
               new RequestsMenuGui(p, this.team).open();
            } else {
               int maxMembers = cm.getMaxMembers();
               if (maxMembers > 0 && this.team.getSize() >= maxMembers) {
                  tm.saveTeam(this.team);
                  p.sendMessage(cm.getMessage("team-full"));
                  new RequestsMenuGui(p, this.team).open();
               } else {
                  tm.addMember(this.team, requester, TeamRole.MEMBER);
                  tm.saveTeam(this.team);
                  this.plugin.getVisibilityManager().refreshTeamAndAllies(this.team);

                  for (UUID memberId : this.team.getMembers().keySet()) {
                     Player member = Bukkit.getPlayer(memberId);
                     if (member != null) {
                        if (memberId.equals(requester)) {
                           member.sendMessage(cm.getMessage("join-request-accepted", "team", this.team.getName()));
                        } else {
                           member.sendMessage(cm.getMessage("player-joined-broadcast", "player", playerName));
                        }
                     }
                  }

                  new RequestsMenuGui(p, this.team).open();
               }
            }
         }
      } else {
         p.sendMessage(cm.getMessage("not-enough-permission-role"));
      }
   }

   private void handleAllyRequestClick(Player p, ConfigManager cm, TeamManager tm, UUID otherId, ClickType clickType) {
      if (!cm.isAlliesEnabled()) {
         p.sendMessage(cm.getMessage("allies-disabled"));
      } else {
         TeamRole role = this.team.getRole(p.getUniqueId());
         if (role != null && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
            Team other = tm.getTeam(otherId);
            if (other == null) {
               this.team.removeAllyInvite(otherId);
               tm.saveTeam(this.team);
               new RequestsMenuGui(p, this.team).open();
            } else if (clickType.isRightClick()) {
               this.team.removeAllyInvite(otherId);
               tm.saveTeam(this.team);
               p.sendMessage(cm.getPrefix().append(cm.color("<gray>You denied the alliance request from <white>" + other.getName() + "<gray>.")));
               new RequestsMenuGui(p, this.team).open();
            } else if (clickType.isLeftClick()) {
               this.team.removeAllyInvite(otherId);
               other.removeAllyInvite(this.team.getId());
               tm.setAllied(this.team, other);
               this.plugin.getVisibilityManager().refreshTeamAndAllies(this.team);
               this.plugin.getVisibilityManager().refreshTeamAndAllies(other);

               for (UUID memberId : this.team.getMembers().keySet()) {
                  Player online = Bukkit.getPlayer(memberId);
                  if (online != null) {
                     online.sendMessage(cm.getMessage("ally-added", "team", other.getName()));
                  }
               }

               for (UUID memberId : other.getMembers().keySet()) {
                  Player online = Bukkit.getPlayer(memberId);
                  if (online != null) {
                     online.sendMessage(cm.getMessage("ally-added", "team", this.team.getName()));
                  }
               }

               new RequestsMenuGui(p, this.team).open();
            }
         } else {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
         }
      }
   }
}
