package peakfinds.web;

/** HTML escaping. Escape every piece of user-supplied text before putting it in a page (blocks XSS). */
public final class Html {
    private Html() {}

    /** Turns &, <, >, " and ' into safe HTML entities. Null becomes an empty string. */
    public static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
