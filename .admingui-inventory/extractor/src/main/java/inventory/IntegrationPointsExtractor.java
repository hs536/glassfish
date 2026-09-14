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

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Writes inventory/integration-points.yaml (調査計画.md S6): console plugins, the integration points they declare, and
 * where each integration point type is consumed.
 *
 * <p>Providers are all {@code META-INF/admingui/console-config.xml} files under {@code appserver/} (outside target/).
 * Page consumers are calls of the PluginHandlers handlers with a literal {@code type}; Java consumers are string
 * literals of the form {@code "org.glassfish...admingui:<type>"}. A literal parentId counts as found when an element
 * with that id appears in the files of a consuming screen or in the content of another point of the same type.
 */
final class IntegrationPointsExtractor {

    private static final String CONFIG = "src/main/resources/META-INF/admingui/console-config.xml";
    private static final int CONTAINER_DEPTH = 3;
    private static final Set<String> INCLUDING_HANDLERS = Set.of("includeIntegrations", "includeFirstIntegrationPoint");
    private static final Pattern PAGE_CONSUMER = Pattern.compile(
            "(?<![\\w.])(includeIntegrations|includeFirstIntegrationPoint|getContentOfIntegrationPoints|getAppEditIntegrationPoint|getIntegrationPoints)\\s*\\(");
    private static final Pattern JAVA_TYPE_LITERAL = Pattern.compile("\"(org\\.glassfish(?:\\.[\\w]+)*\\.admingui:[A-Za-z]\\w*)\"");
    private static final Pattern PROVIDER_CLASS = Pattern.compile("class\\s+(\\w+)\\s+implements\\s+ConsoleProvider\\b");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", ".git", "node_modules");

    private final AdminguiTree tree;

    IntegrationPointsExtractor(Path root) {
        this.tree = new AdminguiTree(root);
    }

    void write(Path output) throws Exception {
        PageIndex index = PageIndex.load(tree);
        Path modulesDirectory = tree.distributedJsftemplatingJar().getParent();

        List<Map<String, Object>> providers = new ArrayList<>();
        Map<String, List<Map<String, Object>>> pointsByType = new TreeMap<>();
        Map<String, Path> resourcesByConfigId = new TreeMap<>();
        Map<String, String> sortKeys = new TreeMap<>();
        List<String> sortCollisions = new ArrayList<>();
        List<Path> modules = new ArrayList<>();
        for (Path config : configs(tree.root().resolve("appserver"))) {
            Path module = config.getParent().getParent().getParent().getParent().getParent().getParent();
            modules.add(module);
            Path resources = module.resolve("src/main/resources");
            Element root = Pom.Xml.parse(config).getDocumentElement();
            String configId = root.getAttribute("id");
            resourcesByConfigId.put(configId, resources);
            String artifactId = Pom.read(module.resolve("pom.xml")).artifactId();

            Map<String, Object> provider = new LinkedHashMap<>();
            provider.put("consoleConfigId", configId);
            provider.put("module", tree.relative(module));
            provider.put("artifactId", artifactId);
            provider.put("providerClasses", providerClasses(module));
            provider.put("distributedIn", distributedIn(modulesDirectory, artifactId));
            NodeList elements = root.getElementsByTagName("integration-point");
            provider.put("integrationPoints", elements.getLength());
            providers.add(provider);

            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                Map<String, Object> point = new LinkedHashMap<>();
                String content = element.getAttribute("content");
                point.put("consoleConfigId", configId);
                point.put("id", element.getAttribute("id"));
                point.put("parentId", emptyToNull(element.getAttribute("parentId")));
                point.put("priority", emptyToNull(element.getAttribute("priority")));
                point.put("content", content);
                point.put("contentFormat", contentFormat(content));
                point.put("contentUrl", PageIndex.contentUrl(content, configId));
                pointsByType.computeIfAbsent(element.getAttribute("type"), k -> new ArrayList<>()).add(point);

                // IntegrationPointComparator orders by parentId, priority and id; equal points collapse in its TreeSet.
                String label = element.getAttribute("type") + " " + configId + "/" + element.getAttribute("id");
                String sortKey = element.getAttribute("type") + "|" + (element.hasAttribute("parentId") ? element.getAttribute("parentId") : "null")
                        + "|" + (element.getAttribute("priority").isEmpty() ? "0" : element.getAttribute("priority")) + "|" + element.getAttribute("id");
                String previous = sortKeys.putIfAbsent(sortKey, label);
                if (previous != null) {
                    sortCollisions.add(label + " collides with " + previous);
                }
            }
        }

        Map<String, List<Map<String, Object>>> pageConsumers = new TreeMap<>();
        for (PageIndex.Page page : index.pages()) {
            List<int[]> comments = commentRanges(page.text());
            Matcher call = PAGE_CONSUMER.matcher(page.text());
            while (call.find()) {
                String arguments = OperationsExtractor.arguments(page.text(), call.end() - 1);
                String type = arguments == null ? null : OperationsExtractor.attributes(arguments).get("type");
                if (type == null) {
                    continue;
                }
                Map<String, Object> consumer = new LinkedHashMap<>();
                consumer.put("handler", call.group(1));
                consumer.put("url", page.url());
                consumer.put("source", page.source());
                consumer.put("line", lineOf(page.text(), call.start()));
                consumer.put("root", OperationsExtractor.attributes(arguments).get("root"));
                int position = call.start();
                consumer.put("commentedOut", comments.stream().anyMatch(r -> r[0] <= position && position < r[1]));
                consumer.put("screens", new ArrayList<>(index.screens(page.url(), CONTAINER_DEPTH)));
                pageConsumers.computeIfAbsent(type, k -> new ArrayList<>()).add(consumer);
            }
        }

        Map<String, List<Map<String, Object>>> javaConsumers = new TreeMap<>();
        Set<Path> javaModules = new TreeSet<>(tree.modules());
        javaModules.addAll(modules);
        for (Path module : javaModules) {
            for (Path java : tree.mainJavaSources(module)) {
                String text = AdminguiTree.read(java);
                Matcher literal = JAVA_TYPE_LITERAL.matcher(text);
                while (literal.find()) {
                    Map<String, Object> consumer = new LinkedHashMap<>();
                    consumer.put("source", tree.relative(java));
                    consumer.put("line", lineOf(text, literal.start()));
                    javaConsumers.computeIfAbsent(literal.group(1), k -> new ArrayList<>()).add(consumer);
                }
            }
        }

        Set<String> types = new TreeSet<>(pointsByType.keySet());
        types.addAll(pageConsumers.keySet());
        types.addAll(javaConsumers.keySet());
        List<String> withoutConsumer = new ArrayList<>();
        List<String> withoutProvider = new ArrayList<>();
        List<String> missingContent = new ArrayList<>();
        List<String> parentNotFound = new ArrayList<>();
        int pointCount = 0;
        int pageConsumerCount = 0;
        int commentedOutCount = 0;
        Map<String, Object> typeEntries = new LinkedHashMap<>();
        for (String type : types) {
            List<Map<String, Object>> points = pointsByType.getOrDefault(type, List.of());
            List<Map<String, Object>> allPages = pageConsumers.getOrDefault(type, List.of());
            List<Map<String, Object>> pages = allPages.stream().filter(p -> !(boolean) p.get("commentedOut")).toList();
            List<Map<String, Object>> javas = javaConsumers.getOrDefault(type, List.of());
            pointCount += points.size();
            pageConsumerCount += pages.size();
            commentedOutCount += allPages.size() - pages.size();
            boolean consumed = !pages.isEmpty() || javas.stream().anyMatch(j -> !isProviderSide(j));
            if (!consumed) {
                withoutConsumer.add(type);
            }
            if (points.isEmpty()) {
                withoutProvider.add(type);
            }

            Set<String> scope = new TreeSet<>();
            pages.forEach(p -> scope.addAll(index.screenScope((String) p.get("url"), CONTAINER_DEPTH)));
            for (Map<String, Object> point : points) {
                String label = type + " " + point.get("consoleConfigId") + "/" + point.get("id");
                Boolean exists = contentExists(point, index, resourcesByConfigId);
                point.put("contentExists", exists);
                if (Boolean.FALSE.equals(exists)) {
                    missingContent.add(label + " " + point.get("content"));
                }
                Boolean parentFound = parentFound(point, points, scope, pages, index, resourcesByConfigId);
                point.put("parentFound", parentFound);
                if (Boolean.FALSE.equals(parentFound)) {
                    parentNotFound.add(label + " parentId=" + point.get("parentId"));
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("consumedBy", new ArrayList<>(consumingHandlers(pages, javas)));
            entry.put("providers", points);
            entry.put("pageConsumers", allPages);
            entry.put("javaReferences", javas);
            typeEntries.put(type, entry);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("consoleConfigs", providers.size());
        summary.put("consoleConfigsNotInDistribution", providers.stream()
                .filter(p -> "not found".equals(p.get("distributedIn"))).map(p -> p.get("module")).toList());
        summary.put("integrationPoints", pointCount);
        summary.put("types", types.size());
        summary.put("pageConsumerCalls", pageConsumerCount);
        summary.put("commentedOutPageConsumerCalls", commentedOutCount);
        summary.put("typesWithoutConsumer", withoutConsumer);
        summary.put("typesWithoutProvider", withoutProvider);
        summary.put("pointsWithMissingContent", missingContent);
        summary.put("pointsWithParentNotFound", parentNotFound);
        summary.put("pointsDroppedBySortCollision", sortCollisions);

        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", "glassfish");
        source.put("commit", ModulesExtractor.Git.head(tree.root()));
        document.put("source", source);
        document.put("summary", summary);
        document.put("consoleConfigs", providers);
        document.put("types", typeEntries);

        Files.createDirectories(output.getParent());
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/inventory/extractor (integration-points). Do not edit by hand.\n"
                        + "Textual extraction: EL types and parent ids are not resolved; parentFound and contentExists are estimates.\n"
                        + "Interpretation and notes belong in docs/as-is/plugins.md."),
                StandardCharsets.UTF_8);
    }

    private static List<Path> configs(Path appserver) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(appserver, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return SKIPPED_DIRECTORIES.contains(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.endsWith(CONFIG) && Files.isRegularFile(file.getParent().getParent().getParent().getParent().getParent().getParent().resolve("pom.xml"))) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort((a, b) -> a.toString().replace('\\', '/').compareTo(b.toString().replace('\\', '/')));
        return result;
    }

    /**
     * Where the built distribution carries the module: {@code modules/} (an OSGi bundle), the admin console WAR's
     * {@code WEB-INF/lib}, "not found", or null when the distribution has not been built.
     */
    private static String distributedIn(Path modulesDirectory, String artifactId) throws IOException {
        if (!Files.isDirectory(modulesDirectory)) {
            return null;
        }
        if (Files.isRegularFile(modulesDirectory.resolve(artifactId + ".jar"))) {
            return "modules";
        }
        Path warLib = modulesDirectory.resolveSibling("lib/install/applications/__admingui/WEB-INF/lib");
        if (Files.isDirectory(warLib)) {
            try (var jars = Files.list(warLib)) {
                if (jars.anyMatch(j -> j.getFileName().toString().startsWith(artifactId + "-"))) {
                    return "admingui WAR";
                }
            }
        }
        return "not found";
    }

    private List<String> providerClasses(Path module) throws IOException {
        List<String> result = new ArrayList<>();
        for (Path java : tree.mainJavaSources(module)) {
            Matcher provider = PROVIDER_CLASS.matcher(AdminguiTree.read(java));
            while (provider.find()) {
                result.add(provider.group(1));
            }
        }
        return result;
    }

    /** Ranges of {@code <!-- -->} and JSFTemplating {@code /* *}{@code /} comments (an unclosed comment runs to the end). */
    private static List<int[]> commentRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int xml = text.indexOf("<!--", i);
            int block = text.indexOf("/*", i);
            if (xml < 0 && block < 0) {
                break;
            }
            boolean isXml = block < 0 || (xml >= 0 && xml < block);
            int start = isXml ? xml : block;
            int end = text.indexOf(isXml ? "-->" : "*/", start + 2);
            end = end < 0 ? text.length() : end + (isXml ? 3 : 2);
            ranges.add(new int[] {start, end});
            i = end;
        }
        return ranges;
    }

    /** The content formats read by PluginHandlers and ThemeHandlers. */
    static String contentFormat(String content) {
        if (content == null || content.isEmpty()) {
            return "empty";
        } else if (content.contains("://")) {
            return "url";
        } else if (content.contains("|")) {
            return "value|bundle|key";
        } else if (content.contains(":")) {
            return "appType:page";
        } else if (PageIndex.PAGE_EXTENSIONS.stream().anyMatch(content::endsWith)) {
            return "page";
        } else if (content.endsWith(".properties")) {
            return "properties";
        }
        return "other";
    }

    /** Whether the content resource exists in the sources; null when the format is not a resource. */
    private static Boolean contentExists(Map<String, Object> point, PageIndex index, Map<String, Path> resourcesByConfigId) {
        String format = (String) point.get("contentFormat");
        String url = (String) point.get("contentUrl");
        if (url != null) {
            if (!index.resolveAlias(url).isEmpty()) {
                return true;
            }
            Path resources = resourcesByConfigId.get((String) point.get("consoleConfigId"));
            String path = format.equals("page") ? stripLeadingSlashes((String) point.get("content")) : url.substring(1);
            return Files.isRegularFile(resources.resolve(path));
        }
        if (format.equals("properties")) {
            Path resources = resourcesByConfigId.get((String) point.get("consoleConfigId"));
            return Files.isRegularFile(resources.resolve(stripLeadingSlashes((String) point.get("content"))));
        }
        return null;
    }

    /** Whether a literal parentId is declared where the point is included; null when it cannot be judged. */
    private static Boolean parentFound(Map<String, Object> point, List<Map<String, Object>> points, Set<String> scope,
            List<Map<String, Object>> pages, PageIndex index, Map<String, Path> resourcesByConfigId) throws IOException {
        String parentId = (String) point.get("parentId");
        if (parentId == null || parentId.contains("#{") || parentId.contains("${")
                || pages.stream().noneMatch(p -> INCLUDING_HANDLERS.contains(p.get("handler")))) {
            return null;
        }
        Pattern declaration = Pattern.compile("\\bid\\s*=\\s*\"" + Pattern.quote(parentId) + "\"");
        for (String url : scope) {
            if (declaration.matcher(index.page(url).text()).find()) {
                return true;
            }
        }
        for (Map<String, Object> other : points) {
            String text = contentText(other, index, resourcesByConfigId);
            if (text != null && declaration.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static String contentText(Map<String, Object> point, PageIndex index, Map<String, Path> resourcesByConfigId) throws IOException {
        if (!"page".equals(point.get("contentFormat"))) {
            return null;
        }
        for (String url : index.resolveAlias((String) point.get("contentUrl"))) {
            return index.page(url).text();
        }
        Path file = resourcesByConfigId.get((String) point.get("consoleConfigId")).resolve(stripLeadingSlashes((String) point.get("content")));
        return Files.isRegularFile(file) ? AdminguiTree.read(file) : null;
    }

    private static Set<String> consumingHandlers(List<Map<String, Object>> pages, List<Map<String, Object>> javas) {
        Set<String> result = new TreeSet<>();
        pages.forEach(p -> result.add((String) p.get("handler")));
        javas.stream().filter(j -> !isProviderSide(j)).forEach(j -> result.add("java:" + Path.of((String) j.get("source")).getFileName()));
        return result;
    }

    /** The connector model and plugin service only carry types through; they do not consume a specific one. */
    private static boolean isProviderSide(Map<String, Object> javaReference) {
        String source = (String) javaReference.get("source");
        return source.contains("/gf-admingui-connector/") || source.contains("/plugin-service/");
    }

    private static String stripLeadingSlashes(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
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
