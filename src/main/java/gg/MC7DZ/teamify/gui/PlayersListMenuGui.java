package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.player.PlayerData;
import gg.MC7DZ.teamify.team.Team;
import java.util.Comparator;
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
import org.bukkit.inventory.meta.SkullMeta;

public class PlayersListMenuGui extends GuiHolder {
   private final Map<Integer, UUID> slotToPlayer = new HashMap<>();
   private int backButtonSlot = -1;

   public PlayersListMenuGui(Player viewer) {
      super(viewer);
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.players-list-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Players List"));
      int size = cfg.getInt("size", 54);
      String onlineItemNameFormat = cfg.getString("online-item-name-format", "<green>{player_name} <gray>(Online)");
      String offlineItemNameFormat = cfg.getString("offline-item-name-format", "<red>{player_name} <gray>(Offline)");
      List<String> onlineItemLoreConfig = cfg.getStringList("online-item-lore");
      List<String> offlineItemLoreConfig = cfg.getStringList("offline-item-lore");
      String offlineHeadTexture = cfg.getString("offline-head-texture");
      Inventory inv = Bukkit.createInventory(this, size, title);
      List<PlayerData> allPlayers = this.plugin.getPlayerManager().getAllPlayers().values().stream().filter(pd -> !pd.isHidden()).collect(Collectors.toList());
      allPlayers.sort(
         Comparator.<PlayerData, Boolean>comparing(p -> Bukkit.getOfflinePlayer(p.getUuid()).isOnline(), Comparator.reverseOrder())
            .thenComparing(PlayerData::getName, String.CASE_INSENSITIVE_ORDER)
      );
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

      for (PlayerData playerData : allPlayers) {
         while (slot < size && reservedSlots.contains(slot)) {
            slot++;
         }

         if (slot >= size) {
            break;
         }

         OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerData.getUuid());
         boolean isOnline = offlinePlayer.isOnline();
         Component displayName = isOnline
            ? this.plugin.getConfigManager().color(onlineItemNameFormat.replace("{player_name}", playerData.getName()))
            : this.plugin.getConfigManager().color(offlineItemNameFormat.replace("{player_name}", playerData.getName()));
         List<Component> lore = isOnline
            ? onlineItemLoreConfig.stream().map(line -> this.processPlaceholders(line, playerData, isOnline)).collect(Collectors.toList())
            : offlineItemLoreConfig.stream().map(line -> this.processPlaceholders(line, playerData, isOnline)).collect(Collectors.toList());
         boolean useMirror = offlineHeadTexture == null || offlineHeadTexture.isEmpty() || offlineHeadTexture.equalsIgnoreCase("mirror");
         ItemStack item;
         if (!isOnline && !useMirror) {
            item = GuiItem.playerHead(offlineHeadTexture, displayName, false, lore.toArray(new Component[0]));
         } else {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta)playerHead.getItemMeta();
            meta.setPlayerProfile(Bukkit.createProfile(offlinePlayer.getUniqueId(), offlinePlayer.getName()));
            playerHead.setItemMeta(meta);
            item = GuiItem.withOverrides(playerHead, displayName, lore);
         }

         inv.setItem(slot, item);
         this.slotToPlayer.put(slot, playerData.getUuid());
         slot++;
      }

      this.setInventory(inv);
   }

   private Component processPlaceholders(String text, PlayerData playerData, boolean isOnline) {
      return this.plugin
         .getConfigManager()
         .color(
            text.replace("{player_name}", playerData.getName())
               .replace("{player_uuid}", playerData.getUuid().toString())
               .replace("{online_status}", isOnline ? "<green>Online" : "<red>Offline")
         );
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         Team team = this.plugin.getTeamManager().getTeamOf(p.getUniqueId());
         if (team != null) {
            new MainMenuGui(p, team).open();
         } else {
            new PlayerSettingsMenuGui(p).open();
         }
      } else {
         UUID targetPlayerId = this.slotToPlayer.get(slot);
         if (targetPlayerId != null) {
            if (targetPlayerId.equals(p.getUniqueId())) {
               p.sendMessage(this.plugin.getConfigManager().getPrefix().append(this.plugin.getConfigManager().color("<red>You can't invite yourself.")));
            } else {
               Team team = this.plugin.getTeamManager().getTeamOf(p.getUniqueId());
               if (team == null) {
                  p.sendMessage(this.plugin.getConfigManager().getMessage("no-team"));
               } else {
                  OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerId);
                  String targetName = target.getName() != null ? target.getName() : "Unknown";
                  Runnable doInvite = () -> {
                     this.plugin.getTeamCommand().invitePlayerToTeam(p, team, target);
                     new PlayersListMenuGui(p).open();
                  };
                  p.sendMessage(
                     this.plugin
                        .getConfigManager()
                        .getPrefix()
                        .append(this.plugin.getConfigManager().color("<aqua>Invite <white>" + targetName + " <aqua>to your team?"))
                  );
                  new ConfirmMenuGui(p, doInvite, () -> new PlayersListMenuGui(p).open()).open();
               }
            }
         }
      }
   }
}
