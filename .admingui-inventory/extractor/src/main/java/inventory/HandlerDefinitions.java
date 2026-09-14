package inventory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * JSFTemplating handler definitions read from the generated {@code META-INF/jsftemplating/Handler.map} files of the
 * admin console modules and from the JSFTemplating jar of the built distribution.
 */
final class HandlerDefinitions {

    static final String HANDLER_MAP = "META-INF/jsftemplating/Handler.map";

    record Io(String name, String type, Boolean required, String defaultValue) {
    }

    /** {@code module} is null for definitions that come from the JSFTemplating jar. */
    record Definition(String id, String origin, Path module, String className, String method, List<Io> inputs, List<Io> outputs) {
    }

    private HandlerDefinitions() {
    }

    /** Definitions in reading order: admingui modules (sorted by directory), then the JSFTemplating jar. Ids may repeat. */
    static List<Definition> read(AdminguiTree tree) throws IOException {
        List<Definition> result = new ArrayList<>();
        for (Path module : tree.modules()) {
            Path map = module.resolve("target/classes").resolve(HANDLER_MAP);
            if (Files.isRegularFile(map)) {
                try (InputStream in = Files.newInputStream(map)) {
                    result.addAll(parse(load(in), "admingui", module));
                }
            }
        }
        Path jar = tree.distributedJsftemplatingJar();
        if (Files.isRegularFile(jar)) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(HANDLER_MAP);
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        result.addAll(parse(load(in), "jsftemplating", null));
                    }
                }
            }
        }
        return result;
    }

    private static List<Definition> parse(Properties map, String origin, Path module) {
        List<Definition> result = new ArrayList<>();
        for (String key : new TreeSet<>(map.stringPropertyNames())) {
            if (!key.endsWith(".class")) {
                continue;
            }
            String id = key.substring(0, key.length() - ".class".length());
            result.add(new Definition(id, origin, module, map.getProperty(key), map.getProperty(id + ".method"),
                    ios(map, id, "input"), ios(map, id, "output")));
        }
        return result;
    }

    private static List<Io> ios(Properties map, String id, String kind) {
        List<Io> result = new ArrayList<>();
        for (int i = 0; map.getProperty(id + "." + kind + "[" + i + "].name") != null; i++) {
            String prefix = id + "." + kind + "[" + i + "].";
            String required = map.getProperty(prefix + "required");
            result.add(new Io(map.getProperty(prefix + "name"), map.getProperty(prefix + "type"),
                    required == null ? null : Boolean.valueOf(required), map.getProperty(prefix + "defaultValue")));
        }
        return result;
    }

    private static Properties load(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        return properties;
    }
}
