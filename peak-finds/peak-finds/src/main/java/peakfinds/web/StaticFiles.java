package peakfinds.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Serves CSS, JavaScript and images from src/main/resources/static under the /static/ URL. */
public final class StaticFiles {
    private static final Map<String, String> TYPES = Map.of(
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "svg", "image/svg+xml",
            "ico", "image/x-icon");

    private StaticFiles() {}

    /** Returns the file, or null if it does not exist or the path is unsafe. */
    public static Response serve(String urlPath) {
        String relative = urlPath.substring("/static/".length());
        int dot = relative.lastIndexOf('.');
        String type = dot < 0 ? null : TYPES.get(relative.substring(dot + 1).toLowerCase());
        // Block path tricks like ../ and only serve known file types.
        if (type == null || relative.contains("..") || relative.contains("\\") || relative.startsWith("/")) {
            return null;
        }
        try (InputStream in = StaticFiles.class.getResourceAsStream("/static/" + relative)) {
            if (in == null) {
                return null;
            }
            return Response.bytes(200, type, in.readAllBytes()).header("Cache-Control", "public, max-age=3600");
        } catch (IOException e) {
            return null;
        }
    }
}
