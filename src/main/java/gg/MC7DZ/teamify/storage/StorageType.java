package gg.MC7DZ.teamify.storage;

public enum StorageType {
   YAML,
   SQLITE,
   MYSQL;

   public static StorageType fromConfig(String value) {
      if (value == null) {
         return YAML;
      }

      try {
         return StorageType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return YAML;
      }
   }
}
