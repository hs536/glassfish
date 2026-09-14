package inventory;

import java.util.Collection;
import java.util.Map;

/** Small deterministic YAML writer for maps, lists and scalars (block style, strings always quoted). */
final class Yaml {

    private Yaml() {
    }

    static String write(Map<String, ?> document, String headerComment) {
        StringBuilder sb = new StringBuilder();
        headerComment.lines().forEach(line -> sb.append("# ").append(line).append('\n'));
        writeMap(document, sb, 0);
        return sb.toString();
    }

    private static void writeMap(Map<?, ?> map, StringBuilder sb, int indent) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            pad(sb, indent);
            sb.append(entry.getKey()).append(':');
            writeValue(entry.getValue(), sb, indent);
        }
    }

    private static void writeValue(Object value, StringBuilder sb, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append(" {}\n");
            } else {
                sb.append('\n');
                writeMap(map, sb, indent + 1);
            }
        } else if (value instanceof Collection<?> list) {
            if (list.isEmpty()) {
                sb.append(" []\n");
                return;
            }
            sb.append('\n');
            for (Object item : list) {
                pad(sb, indent + 1);
                sb.append('-');
                if (item instanceof Map<?, ?> map && !map.isEmpty()) {
                    // First key on the dash line, the rest aligned below it
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (first) {
                            sb.append(' ');
                            first = false;
                        } else {
                            pad(sb, indent + 2);
                        }
                        sb.append(entry.getKey()).append(':');
                        writeValue(entry.getValue(), sb, indent + 2);
                    }
                } else {
                    writeValue(item, sb, indent + 1);
                }
            }
        } else {
            sb.append(' ').append(scalar(value)).append('\n');
        }
    }

    private static String scalar(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }
}
