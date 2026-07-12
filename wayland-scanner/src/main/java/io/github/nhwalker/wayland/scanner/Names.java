package io.github.nhwalker.wayland.scanner;

import java.util.Set;

/** The generator's naming rules — wire names to Java names. */
final class Names {

  private static final Set<String> KEYWORDS = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
      "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
      "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
      "interface", "long", "native", "new", "package", "private", "protected", "public",
      "record", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
      "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
      "yield");

  private Names() {}

  /** {@code wl_shm_pool} → {@code WlShmPool}. */
  static String className(String wireName) {
    StringBuilder result = new StringBuilder();
    for (String part : wireName.split("_")) {
      if (!part.isEmpty()) {
        result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    return result.toString();
  }

  /** {@code create_pool} → {@code createPool}; Java keywords get a {@code Name} suffix. */
  static String camelName(String wireName) {
    String[] parts = wireName.split("_");
    StringBuilder result = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].isEmpty()) {
        result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
      }
    }
    String name = result.toString();
    return KEYWORDS.contains(name) ? name + "Name" : name;
  }

  /** {@code argb8888} → {@code ARGB8888}; digit-leading names are prefixed with {@code _}. */
  static String constantName(String wireName) {
    String name = wireName.toUpperCase(java.util.Locale.ROOT);
    return Character.isDigit(name.charAt(0)) ? "_" + name : name;
  }

  /** Enum wire name to nested class name: {@code format} → {@code Format}. */
  static String enumClassName(String wireName) {
    return className(wireName);
  }
}
