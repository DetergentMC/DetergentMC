package io.detergentmc.legacy;

/**
 * Text-component flattening for the legacy generation: modern chat
 * and disconnect payloads are JSON text components, while legacy
 * protocol 78 carries plain strings. The extraction is deliberately
 * simple: it collects every {@code "text"} value (including nested
 * {@code extra} children) and unquotes a bare JSON string literal.
 * Translation keys ({@code "translate"}) cannot be resolved without
 * the client's language files, so the key itself is used as the text.
 */
public final class LegacyText {

    private LegacyText() {
    }

    /** Extracts displayable plain text from a JSON text component. */
    public static String jsonToPlain(String json) {
        if (json == null) {
            return "";
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.charAt(0) == '"') {
            StringBuilder out = new StringBuilder();
            int end = parseJsonString(trimmed, 0, out);
            return end > 0 ? out.toString() : json;
        }
        if (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[') {
            return json;
        }
        StringBuilder out = new StringBuilder();
        collectText(trimmed, out);
        if (out.length() == 0) {
            // Fall back to the translate key, if any, rather than nothing.
            collectKey(trimmed, "translate", out);
        }
        return out.length() > 0 ? out.toString() : json;
    }

    private static void collectText(String json, StringBuilder out) {
        int i = 0;
        while (i < json.length()) {
            int at = json.indexOf("\"text\"", i);
            if (at < 0) {
                return;
            }
            int colon = json.indexOf(':', at + 6);
            if (colon < 0) {
                return;
            }
            int p = colon + 1;
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
                p++;
            }
            if (p >= json.length() || json.charAt(p) != '"') {
                i = at + 6;
                continue;
            }
            int end = parseJsonString(json, p, out);
            i = end > p ? end : at + 6;
        }
    }

    private static void collectKey(String json, String key, StringBuilder out) {
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) {
            return;
        }
        int colon = json.indexOf(':', at + key.length() + 2);
        if (colon < 0) {
            return;
        }
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        if (p < json.length() && json.charAt(p) == '"') {
            parseJsonString(json, p, out);
        }
    }

    /**
     * Parses a JSON string literal starting at {@code start} (which
     * must point at the opening quote), appending the decoded value.
     *
     * @return index just past the closing quote, or -1 on malformed input
     */
    private static int parseJsonString(String json, int start, StringBuilder out) {
        int p = start + 1;
        while (p < json.length()) {
            char c = json.charAt(p);
            if (c == '\\' && p + 1 < json.length()) {
                char next = json.charAt(p + 1);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (p + 5 < json.length()) {
                            try {
                                out.append((char) Integer.parseInt(
                                        json.substring(p + 2, p + 6), 16));
                            } catch (NumberFormatException e) {
                                // skip malformed escape
                            }
                            p += 4;
                        }
                    }
                    default -> out.append(next);
                }
                p += 2;
            } else if (c == '"') {
                return p + 1;
            } else {
                out.append(c);
                p++;
            }
        }
        return -1;
    }
}
