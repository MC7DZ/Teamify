package gg.MC7DZ.teamify.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plays sounds using the modern Registry-based lookup (Minecraft/Paper sound
 * keys like "entity.player.levelup" or "ui.button.click"), instead of the
 * old {@code Sound.valueOf("ENTITY_PLAYER_LEVELUP")} enum approach.
 * <p>
 * Config values are plain Minecraft sound-event names (same names you'd see
 * in a /playsound command, minus the "minecraft:" namespace - it's added
 * automatically). See gui.open-sound / gui.success-sound / gui.error-sound
 * in config.yml for examples.
 */
public final class SoundUtil {

    private SoundUtil() {
    }

    /**
     * Resolves a sound key (e.g. "entity.player.levelup") via the Bukkit
     * sound Registry. Returns null if the key doesn't match a known sound.
     */
    public static Sound resolve(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim().toLowerCase();
        NamespacedKey namespacedKey = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (namespacedKey == null) return null;
        return Registry.SOUNDS.get(namespacedKey);
    }

    /** Plays a sound key at the player's own location, at default volume/pitch. */
    public static void play(Player player, String key) {
        play(player, key, 1.0f, 1.0f);
    }

    public static void play(Player player, String key, float volume, float pitch) {
        if (player == null) return;
        Sound sound = resolve(key);
        if (sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
