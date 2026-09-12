package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class RelationsMenuGui extends GuiHolder {
   private final Team team;
   private final Map<Integer, UUID> slotToTeam = new HashMap<>();
   private int backButtonSlot = -1;

   public RelationsMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.relations-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Allies"));
      int size = cfg.getInt("size", 54);
      Material allyMat = this.parse(cfg.getString("ally-material", "LIME_WOOL"), Material.LIME_WOOL);
      String itemNameFormat = cfg.getString("item-name-format", "{name}");
      List<String> itemLoreConfig = cfg.getStringList("item-lore");
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

      for (Entry<UUID, RelationType> entry : this.team.getRelations().entrySet()) {
         while (slot < size && reservedSlots.contains(slot)) {
            slot++;
         }

         if (slot >= size) {
            break;
         }

         UUID otherId = entry.getKey();
         RelationType type = entry.getValue();
         if (type == RelationType.ALLY) {
            Team other = this.plugin.getTeamManager().getTeam(otherId);
            if (other != null) {
               Component displayName = this.plugin
                  .getConfigManager()
                  .color(
                     itemNameFormat.replace("{name}", other.getColoredName())
                        .replace("{tag}", other.getTag())
                        .replace("{level}", String.valueOf(other.getLevel()))
                        .replace("{members}", String.valueOf(other.getSize()))
                        .replace("{online}", String.valueOf(this.countVisibleOnline(other)))
                        .replace("{relation}", type.name())
                  );
               List<Component> lore = itemLoreConfig.stream()
                  .map(
                     line -> this.plugin
                        .getConfigManager()
                        .color(
                           line.replace("{name}", other.getColoredName())
                              .replace("{tag}", other.getTag())
                              .replace("{level}", String.valueOf(other.getLevel()))
                              .replace("{members}", String.valueOf(other.getSize()))
                              .replace("{online}", String.valueOf(this.countVisibleOnline(other)))
                              .replace("{relation}", type.name())
                        )
                  )
                  .collect(Collectors.toList());
               ItemStack item = other.hasCustomItem()
                  ? GuiItem.withOverrides(other.getCustomItem(), displayName, lore)
                  : GuiItem.simple(allyMat, displayName, lore.toArray(new Component[0]));
               inv.setItem(slot, item);
               this.slotToTeam.put(slot, otherId);
               slot++;
            }
         }
      }

      this.setInventory(inv);
   }

   private int countVisibleOnline(Team allyTeam) {
      int count = 0;

      for (UUID memberId : allyTeam.getMembers().keySet()) {
         if (Bukkit.getOfflinePlayer(memberId).isOnline()) {
            count++;
         }
      }

      return count;
   }

   private Material parse(String s, Material fallback) {
      try {
         return Material.valueOf(s.toUpperCase());
      } catch (IllegalArgumentException e) {
         return fallback;
      }
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         new MainMenuGui(p, this.team).open();
      } else {
         UUID otherId = this.slotToTeam.get(slot);
         if (otherId != null) {
            Team other = this.plugin.getTeamManager().getTeam(otherId);
            if (other != null) {
               ConfigManager cm = this.plugin.getConfigManager();
               TeamManager tm = this.plugin.getTeamManager();
               if (!cm.isAlliesEnabled()) {
                  p.sendMessage(cm.getMessage("allies-disabled"));
               } else {
                  TeamRole role = this.team.getRole(p.getUniqueId());
                  if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
                     p.sendMessage(cm.getMessage("not-enough-permission-role"));
                  } else if (!tm.areAllied(this.team, other)) {
                     p.sendMessage(cm.getMessage("not-allied", "team", other.getName()));
                  } else {
                     Runnable doAllyLeave = () -> {
                        tm.removeAlly(this.team, other);
                        this.plugin.getVisibilityManager().refreshTeamAndAllies(this.team);
                        this.plugin.getVisibilityManager().refreshTeamAndAllies(other);

                        for (UUID memberId : this.team.getMembers().keySet()) {
                           Player online = Bukkit.getPlayer(memberId);
                           if (online != null) {
                              online.sendMessage(cm.getMessage("ally-removed", "team", other.getName()));
                           }
                        }

                        for (UUID memberId : other.getMembers().keySet()) {
                           Player online = Bukkit.getPlayer(memberId);
                           if (online != null) {
                              online.sendMessage(cm.getMessage("ally-removed", "team", this.team.getName()));
                           }
                        }

                        new RelationsMenuGui(p, this.team).open();
                     };
                     new ConfirmMenuGui(p, doAllyLeave, () -> new RelationsMenuGui(p, this.team).open()).open();
                  }
               }
            }
         }
      }
   }
}
