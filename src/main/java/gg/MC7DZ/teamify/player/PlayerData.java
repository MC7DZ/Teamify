package gg.MC7DZ.teamify.player;

import java.util.UUID;

public class PlayerData {
   private final UUID uuid;
   private String name;
   private boolean hidden;

   public PlayerData(UUID uuid, String name) {
      this(uuid, name, false);
   }

   public PlayerData(UUID uuid, String name, boolean hidden) {
      this.uuid = uuid;
      this.name = name;
      this.hidden = hidden;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }
}
