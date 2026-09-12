package gg.MC7DZ.teamify.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {
   private SoundUtil() {
   }

   public static Sound resolve(String key) {
      if (key != null && !key.isBlank()) {
         String normalized = key.trim().toLowerCase();
         NamespacedKey namespacedKey = normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);
         return namespacedKey == null ? null : (Sound)Registry.SOUNDS.get(namespacedKey);
      } else {
         return null;
      }
   }

   public static void play(Player player, String key) {
      play(player, key, 1.0F, 1.0F);
   }

   public static void play(Player player, String key, float volume, float pitch) {
      if (player != null) {
         Sound sound = resolve(key);
         if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
         }
      }
   }
}
