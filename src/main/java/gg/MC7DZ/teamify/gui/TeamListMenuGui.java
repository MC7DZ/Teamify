package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
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

public class TeamListMenuGui extends GuiHolder {
   private final Map<Integer, UUID> slotToTeam = new HashMap<>();
   private int backButtonSlot = -1;

   public TeamListMenuGui(Player viewer) {
      super(viewer);
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.team-list-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>All Teams"));
      int size = cfg.getInt("size", 54);
      String sortBy = cfg.getString("sort-by", "LEVEL");
      String itemNameFormat = cfg.getString("item-name-format", "{color}{name} <gray>[{tag}]");
      List<String> itemLoreConfig = cfg.getStringList("item-lore");
      List<Team> teams = new ArrayList<>(this.plugin.getTeamManager().getTeams());

      Comparator<Team> comparator = switch (sortBy) {
         case "MEMBERS" -> Comparator.comparingInt(Team::getSize).reversed();
         case "NAME" -> Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER);
         default -> Comparator.comparingInt(Team::getLevel).reversed();
      };
      teams.sort(comparator);
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

      for (Team team : teams) {
         while (slot < size && reservedSlots.contains(slot)) {
            slot++;
         }

         if (slot >= size) {
            break;
         }

         Component displayName = this.processPlaceholders(itemNameFormat, team);
         List<Component> lore = itemLoreConfig.stream().map(line -> this.processPlaceholders(line, team)).collect(Collectors.toList());
         ItemStack item = team.hasCustomItem()
            ? GuiItem.withOverrides(team.getCustomItem(), displayName, lore)
            : GuiItem.simple(Material.WHITE_BANNER, displayName, lore.toArray(new Component[0]));
         inv.setItem(slot, item);
         this.slotToTeam.put(slot, team.getId());
         slot++;
      }

      this.setInventory(inv);
   }

   private Component processPlaceholders(String text, Team team) {
      OfflinePlayer owner = Bukkit.getOfflinePlayer(team.getOwner());
      String ownerName = owner.hasPlayedBefore() && owner.getName() != null ? owner.getName() : "Unknown";
      String createdDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(team.getCreatedAt()));
      String description = team.getDescription() != null && !team.getDescription().isEmpty() ? team.getDescription() : "<gray>(none set)";
      return this.plugin
         .getConfigManager()
         .color(
            text.replace("{color}", team.getColor().toString())
               .replace("{name}", team.getName())
               .replace("{level}", String.valueOf(team.getLevel()))
               .replace("{members}", String.valueOf(team.getSize()))
               .replace("{tag}", team.getTag())
               .replace("{owner_name}", ownerName)
               .replace("{bank_balance}", this.plugin.getEconomyManager().format(team.getBankBalance()))
               .replace("{xp}", String.valueOf(team.getXp()))
               .replace("{allies}", String.valueOf(team.getAllyCount()))
               .replace("{homes_count}", String.valueOf(team.getHomes().size()))
               .replace("{kills}", String.valueOf(team.getTotalKills()))
               .replace("{created_date}", createdDate)
               .replace("{description}", description)
         );
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         if (this.plugin.getTeamManager().isInTeam(p.getUniqueId())) {
            new MainMenuGui(p, this.plugin.getTeamManager().getTeamOf(p.getUniqueId())).open();
         } else {
            new PlayerSettingsMenuGui(p).open();
         }
      } else {
         UUID teamId = this.slotToTeam.get(slot);
         if (teamId != null) {
            Team team = this.plugin.getTeamManager().getTeam(teamId);
            if (team != null) {
               p.sendMessage(
                  this.plugin
                     .getConfigManager()
                     .getPrefix()
                     .append(
                        this.plugin
                           .getConfigManager()
                           .color("<aqua>Viewing <white>" + team.getName() + " <gray>| Level " + team.getLevel() + " | " + team.getSize() + " members")
                     )
               );
            }
         }
      }
   }
}
