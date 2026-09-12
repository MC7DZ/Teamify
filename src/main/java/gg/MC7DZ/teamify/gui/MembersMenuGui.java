package gg.MC7DZ.teamify.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
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
import org.bukkit.profile.PlayerTextures;

public class MembersMenuGui extends GuiHolder {
   private final Team team;
   private final Map<Integer, UUID> slotToMember = new HashMap<>();
   private int backButtonSlot = -1;

   public MembersMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.members-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Team Members"));
      int size = cfg.getInt("size", 54);
      boolean showOnline = cfg.getBoolean("show-online-status", true);
      String onlineColor = cfg.getString("online-name-color", "<green>");
      String offlineColor = cfg.getString("offline-name-color", "<gray>");
      String itemNameFormat = cfg.getString("item-name-format", "{color}{name}");
      List<String> itemLoreConfig = cfg.getStringList("item-lore");
      String offlineHeadTexture = cfg.getString("offline-head-texture");
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

      List<Entry<UUID, TeamRole>> sortedMembers = new ArrayList<>(this.team.getMembers().entrySet());
      sortedMembers.sort(
         Comparator.<Entry<UUID, TeamRole>, Boolean>comparing(e -> Bukkit.getOfflinePlayer(e.getKey()).isOnline(), Comparator.reverseOrder())
            .thenComparing(e -> {
               String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
               return n != null ? n : "";
            }, String.CASE_INSENSITIVE_ORDER)
      );
      int slot = 0;

      for (Entry<UUID, TeamRole> entry : sortedMembers) {
         while (slot < size && reservedSlots.contains(slot)) {
            slot++;
         }

         if (slot >= size) {
            break;
         }

         UUID uuid = entry.getKey();
         TeamRole role = entry.getValue();
         OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
         boolean online = op.isOnline();
         ItemStack head = new ItemStack(Material.PLAYER_HEAD);
         SkullMeta meta = (SkullMeta)head.getItemMeta();
         if (meta != null) {
            boolean useMirror = offlineHeadTexture == null || offlineHeadTexture.isEmpty() || offlineHeadTexture.equalsIgnoreCase("mirror");
            if (!online && !useMirror) {
               this.applyOfflineTexture(meta, offlineHeadTexture);
            } else {
               meta.setPlayerProfile(Bukkit.createProfile(uuid));
            }

            String color = showOnline ? (online ? onlineColor : offlineColor) : "<white>";
            String statusColor = online ? "<green>" : "<red>";
            String status = online ? "Online" : "Offline";
            String playerName = op.getName() != null ? op.getName() : "Unknown";
            Component displayName = this.plugin
               .getConfigManager()
               .color(
                  itemNameFormat.replace("{color}", color)
                     .replace("{name}", playerName)
                     .replace("{role}", role.name())
                     .replace("{kills}", String.valueOf(this.team.getKills(uuid)))
                     .replace("{status_color}", statusColor)
                     .replace("{status}", status)
               );
            List<Component> loreComponents = new ArrayList<>();

            for (String line : itemLoreConfig) {
               String processedLine = line.replace("{color}", color)
                  .replace("{name}", playerName)
                  .replace("{role}", role.name())
                  .replace("{kills}", String.valueOf(this.team.getKills(uuid)))
                  .replace("{status_color}", statusColor)
                  .replace("{status}", status);
               loreComponents.add(this.plugin.getConfigManager().color(processedLine));
            }

            meta.displayName(displayName);
            meta.lore(loreComponents);
            head.setItemMeta(meta);
         }

         inv.setItem(slot, head);
         this.slotToMember.put(slot, uuid);
         slot++;
      }

      this.setInventory(inv);
   }

   private void applyOfflineTexture(SkullMeta meta, String base64Texture) {
      PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
      PlayerTextures textures = profile.getTextures();

      try {
         byte[] decodedBytes = Base64.getDecoder().decode(base64Texture);
         String decodedString = new String(decodedBytes);
         JsonObject json = JsonParser.parseString(decodedString).getAsJsonObject();
         String textureUrl = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
         textures.setSkin(new URL(textureUrl));
      } catch (Exception e) {
         System.err.println("Error decoding or parsing offline head texture: " + e.getMessage());
      }

      profile.setTextures(textures);
      meta.setPlayerProfile(profile);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         new MainMenuGui(p, this.team).open();
      } else {
         UUID target = this.slotToMember.get(slot);
         if (target != null) {
            new MemberActionsMenuGui(p, this.team, target).open();
         }
      }
   }
}
