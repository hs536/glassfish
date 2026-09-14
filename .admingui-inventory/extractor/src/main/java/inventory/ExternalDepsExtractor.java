package inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes inventory/external-deps.yaml (調査計画.md S12): what the admin console uses outside appserver/admingui, and
 * what outside appserver/admingui refers to the admin console.
 *
 * <p>Outbound: Java imports of packages that are neither the console's own nor the JDK's, grouped by package prefix;
 * HK2 service lookups by class literal; system properties read. Maven dependencies are in inventory/modules.yaml and
 * REST calls in inventory/operations.yaml.
 *
 * <p>Inbound: text files outside appserver/admingui (and outside target/) that mention the console by application
 * name, package, provider id or artifact id, classified as main code, build, tests or documentation. Whether each
 * dependency is needed by the new UI is judged in docs/as-is/external-deps.md, not here.
 */
final class ExternalDepsExtractor {

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([a-z][\\w]*(?:\\.[\\w*]+)+)\\s*;");
    private static final List<String> OWN_OR_JDK = List.of("java.", "javax.xml.", "javax.naming.", "javax.security.auth.",
            "javax.net.ssl.", "javax.script.", "javax.imageio.", "org.w3c.", "org.xml.", "org.glassfish.admingui.");
    private static final Pattern HK2_SERVICE = Pattern.compile("getService\\(\\s*([A-Z]\\w*)\\.class");
    private static final Pattern SYSTEM_PROPERTY = Pattern.compile("System\\.getProperty\\(\\s*\"([^\"]+)\"");
    private static final Pattern INBOUND_TERM = Pattern.compile(
            "__admingui|org\\.glassfish\\.api\\.admingui|org\\.glassfish\\.admingui|GFConsoleAuthModule|gf-admingui-connector"
                    + "|console-(?:common(?:-full-plugin)?|core|plugin-service|community-branding-plugin|[a-z]+(?:-[a-z]+)*-plugin)\\b|\\badmingui\\b");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".java", ".xml", ".properties", ".adoc", ".md", ".bnd", ".MF",
            ".jsf", ".inc", ".html", ".txt", ".sh", ".bat", ".json", ".yml", ".yaml", ".1", ".js", ".policy");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", ".git", "node_modules");
    private static final int LINES_PER_FILE = 5;

    private final AdminguiTree tree;

    ExternalDepsExtractor(Path root) {
        this.tree = new AdminguiTree(root);
    }

    void write(Path output) throws Exception {
        Map<String, Map<String, Object>> imports = new TreeMap<>();
        Map<String, Set<String>> services = new TreeMap<>();
        Map<String, Set<String>> properties = new TreeMap<>();
        for (Path module : tree.modules()) {
            for (Path java : tree.mainJavaSources(module)) {
                String text = AdminguiTree.read(java);
                String relative = tree.relative(java);
                Matcher imported = IMPORT.matcher(text);
                while (imported.find()) {
                    String name = imported.group(1);
                    if (OWN_OR_JDK.stream().anyMatch(name::startsWith)) {
                        continue;
                    }
                    Map<String, Object> group = imports.computeIfAbsent(packageGroup(name), k -> newGroup());
                    add(group, "classes", name);
                    add(group, "files", relative);
                    add(group, "modules", module.getFileName().toString());
                }
                collect(HK2_SERVICE, text, relative, services);
                collect(SYSTEM_PROPERTY, text, relative, properties);
            }
        }

        List<Object> inbound = new ArrayList<>();
        Map<String, Integer> byCategory = new TreeMap<>();
        Map<String, Integer> byArea = new TreeMap<>();
        for (Path file : inboundCandidates()) {
            String text = AdminguiTree.read(file);
            Matcher term = INBOUND_TERM.matcher(text);
            Set<String> terms = new TreeSet<>();
            List<Integer> lines = new ArrayList<>();
            int count = 0;
            while (term.find()) {
                terms.add(term.group());
                count++;
                int line = lineOf(text, term.start());
                if (lines.size() < LINES_PER_FILE && !lines.contains(line)) {
                    lines.add(line);
                }
            }
            if (count == 0) {
                continue;
            }
            String relative = tree.relative(file);
            String category = category(relative);
            byCategory.merge(category, 1, Integer::sum);
            byArea.merge(area(relative), 1, Integer::sum);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", relative);
            entry.put("category", category);
            entry.put("terms", new ArrayList<>(terms));
            entry.put("occurrences", count);
            entry.put("firstLines", lines);
            inbound.add(entry);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("importedPackageGroups", imports.size());
        summary.put("hk2ServiceLookups", services.size());
        summary.put("systemPropertiesRead", properties.size());
        summary.put("inboundFiles", inbound.size());
        summary.put("inboundFilesByCategory", byCategory);
        summary.put("inboundFilesByArea", byArea);

        Map<String, Object> outbound = new LinkedHashMap<>();
        Map<String, Object> importEntries = new TreeMap<>();
        imports.forEach((group, value) -> importEntries.put(group, finish(value)));
        outbound.put("javaImportsByPackage", importEntries);
        outbound.put("hk2ServiceLookups", listValues(services));
        outbound.put("systemProperties", listValues(properties));
        outbound.put("mavenDependencies", "inventory/modules.yaml");
        outbound.put("restCalls", "inventory/operations.yaml");

        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", "glassfish");
        source.put("commit", ModulesExtractor.Git.head(tree.root()));
        document.put("source", source);
        document.put("summary", summary);
        document.put("outbound", outbound);
        document.put("inbound", inbound);

        Files.createDirectories(output.getParent());
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/inventory/extractor (external-deps). Do not edit by hand.\n"
                        + "Textual extraction. Whether each dependency is needed by the new UI is judged in docs/as-is/external-deps.md."),
                StandardCharsets.UTF_8);
    }

    /** Text files outside appserver/admingui and outside build output, sorted by path. */
    private List<Path> inboundCandidates() throws IOException {
        List<Path> result = new ArrayList<>();
        Path admingui = tree.admingui();
        Files.walkFileTree(tree.root(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return SKIPPED_DIRECTORIES.contains(dir.getFileName().toString()) || dir.equals(admingui)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot)) && attrs.size() < 5_000_000) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort((a, b) -> tree.relative(a).compareTo(tree.relative(b)));
        return result;
    }

    static String packageGroup(String name) {
        String[] parts = name.split("\\.");
        int length = parts[0].equals("org") || parts[0].equals("com") ? 3 : 2;
        StringBuilder group = new StringBuilder();
        for (int i = 0; i < Math.min(length, parts.length - 1); i++) {
            group.append(i == 0 ? "" : ".").append(parts[i]);
        }
        return group.toString();
    }

    static String category(String path) {
        if (path.startsWith("docs/") || path.endsWith(".1")) {
            return "documentation";
        }
        if (path.startsWith("appserver/tests/") || path.contains("/src/test/")) {
            return "tests";
        }
        if (path.endsWith("pom.xml") || path.contains("/assembly/") || path.contains("/featuresets/")) {
            return "build";
        }
        return "main";
    }

    private static String area(String path) {
        String[] parts = path.split("/");
        return parts.length >= 3 ? parts[0] + "/" + parts[1] : parts[0];
    }

    private static void collect(Pattern pattern, String text, String file, Map<String, Set<String>> into) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            into.computeIfAbsent(matcher.group(1), k -> new TreeSet<>()).add(file);
        }
    }

    private static Map<String, Object> newGroup() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("modules", new TreeSet<String>());
        group.put("files", new TreeSet<String>());
        group.put("classes", new TreeSet<String>());
        return group;
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Object> group, String key, String value) {
        ((Set<String>) group.get(key)).add(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> finish(Map<String, Object> group) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modules", new ArrayList<>((Set<String>) group.get("modules")));
        result.put("fileCount", ((Set<String>) group.get("files")).size());
        result.put("classes", new ArrayList<>((Set<String>) group.get("classes")));
        return result;
    }

    private static Map<String, Object> listValues(Map<String, Set<String>> map) {
        Map<String, Object> result = new TreeMap<>();
        map.forEach((key, files) -> result.put(key, new ArrayList<>(files)));
        return result;
    }

    private static int lineOf(String text, int position) {
        int line = 1;
        for (int i = 0; i < position; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
