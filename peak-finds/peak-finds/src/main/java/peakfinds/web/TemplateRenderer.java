package peakfinds.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills HTML templates from src/main/resources/templates. A template contains {{name}} markers;
 * render() swaps each marker for the value given in the map (unknown markers become empty).
 * Values are inserted as-is, so escape user text with Html.esc() first. Replacement happens in a single
 * pass, so a value that itself contains "{{x}}" is not expanded again.
 */
public final class TemplateRenderer {
    private static final Pattern MARKER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private TemplateRenderer() {}

    /** Loads templates/{name} and fills in the values. */
    public static String render(String name, Map<String, String> values) {
        String template = CACHE.computeIfAbsent(name, TemplateRenderer::load);
        Matcher m = MARKER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 256);
        while (m.find()) {
            String value = values.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String load(String name) {
        try (InputStream in = TemplateRenderer.class.getResourceAsStream("/templates/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing template: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read template " + name, e);
        }
    }
}
