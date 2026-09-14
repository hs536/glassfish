package inventory;

/** Minimal text operations on Java source files. */
final class JavaSource {

    private JavaSource() {
    }

    /**
     * The block from the first '{' at or after {@code from} to its matching '}', skipping comments and string and
     * character literals; null when the block is not closed.
     */
    static String blockAfter(String source, int from) {
        int open = source.indexOf('{', from);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                int end = source.indexOf('\n', i);
                if (end < 0) {
                    return null;
                }
                i = end;
            } else if (c == '/' && next == '*') {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) {
                    return null;
                }
                i = end + 1;
            } else if (c == '"' || c == '\'') {
                for (i++; i < source.length() && source.charAt(i) != c; i++) {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                }
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        return null;
    }
}
