package runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the screen entries of inventory/screens.yaml. The file is written by tools/inventory/extractor with a fixed
 * layout, so the fields are read line by line instead of with a YAML library.
 */
final class ScreenList {

    record Screen(String url, String module, String kind, boolean reachable) {
    }

    private static final Pattern URL = Pattern.compile("^  - url: \"(.*)\"$");
    private static final Pattern FIELD = Pattern.compile("^    (module|kind|reachable): \"?([^\"]*)\"?$");

    private ScreenList() {
    }

    static List<Screen> read(Path screensYaml) throws IOException {
        List<Screen> screens = new ArrayList<>();
        boolean inScreens = false;
        String url = null;
        String module = null;
        String kind = null;
        for (String line : Files.readAllLines(screensYaml, StandardCharsets.UTF_8)) {
            if (!line.startsWith(" ") && !line.startsWith("#")) {
                inScreens = line.equals("screens:");
                continue;
            }
            if (!inScreens) {
                continue;
            }
            Matcher start = URL.matcher(line);
            if (start.matches()) {
                url = start.group(1);
                module = null;
                kind = null;
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (url != null && field.matches()) {
                switch (field.group(1)) {
                    case "module" -> module = field.group(2);
                    case "kind" -> kind = field.group(2);
                    default -> {
                        screens.add(new Screen(url, module, kind, Boolean.parseBoolean(field.group(2))));
                        url = null;
                    }
                }
            }
        }
        return screens;
    }
}
