package systems.glam.sdk;

public final class GlamUtil {

  public static String parseFixLengthString(final byte[] chars) {
    for (int i = chars.length - 1, c; i >= 0; --i) {
      c = chars[i] & 0xFF;
      if (c != 0 && !Character.isWhitespace(c)) {
        return new String(chars, 0, i + 1);
      }
    }
    return "";
  }

  private GlamUtil() {
  }
}
