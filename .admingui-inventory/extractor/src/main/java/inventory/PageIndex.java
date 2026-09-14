package inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Page files of the admin console with the URLs they are served at, their includes, and the integration points
 * declared by the plugins. Shared by the screen (S2) and operation (S5) inventories.
 *
 * <p>A page file is served at its canonical URL {@code /<console-config id>/<path>} (plugin resources),
 * {@code /<path>} (modules without a console-config and the WAR), or its path below {@code META-INF/resources/}.
 * The console also resolves {@code /<path>} for plugin resources through the class path (confirmed on a running
 * server, docs/as-is/navigation.md), so both forms are registered as aliases.
 */
final class PageIndex {

    static final List<String> PAGE_EXTENSIONS = List.of(".jsf", ".inc", ".layout", ".xhtml");

    /** Page includes; the closing quote is optional because some pages omit it (JSFTemplating reads to the end of the line). */
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#include\\s+\"([^\"\\r\\n]+\\.(?:jsf|inc|layout|xhtml))\"?");
    private static final Pattern TEMPLATE = Pattern.compile("<!composition\\s+template=\"([^\"#]+)\"");

    record Page(String url, String module, String source, String text, Set<String> includes) {
    }

    private final Map<String, Page> pages = new TreeMap<>();
    private final Map<String, Set<String>> aliases = new TreeMap<>();
    private final List<Map<String, Object>> integrationPoints = new ArrayList<>();
    private final Map<String, Set<String>> includers = new TreeMap<>();
    /** Canonical page URL to the canonical URL of its (literal) template. */
    private final Map<String, String> templates = new TreeMap<>();
    /** Canonical template URL to the canonical URLs of the pages using it. */
    private final Map<String, Set<String>> templateUsers = new TreeMap<>();

    private PageIndex() {
    }

    static PageIndex load(AdminguiTree tree) throws Exception {
        PageIndex index = new PageIndex();
        for (Path module : tree.modules()) {
            String moduleName = module.getFileName().toString();
            Path resources = module.resolve("src/main/resources");
            String consoleId = null;
            Path config = resources.resolve("META-INF/admingui/console-config.xml");
            if (Files.isRegularFile(config)) {
                Element root = Pom.Xml.parse(config).getDocumentElement();
                consoleId = root.getAttribute("id");
                NodeList points = root.getElementsByTagName("integration-point");
                for (int i = 0; i < points.getLength(); i++) {
                    index.integrationPoints.add(integrationPoint((Element) points.item(i), consoleId));
                }
            }
            index.addPages(tree, moduleName, resources, consoleId);
            if (moduleName.equals("war")) {
                index.addPages(tree, moduleName, module.resolve("src/main/webapp"), null);
            }
        }
        index.pages.forEach((url, page) -> page.includes().forEach(
                target -> index.resolveAlias(target).forEach(t -> index.includers.computeIfAbsent(t, k -> new TreeSet<>()).add(url))));
        index.pages.forEach((url, page) -> {
            Matcher template = TEMPLATE.matcher(page.text());
            if (template.find()) {
                for (String target : index.resolveAlias(resolve(template.group(1), directory(url)))) {
                    index.templates.put(url, target);
                    index.templateUsers.computeIfAbsent(target, k -> new TreeSet<>()).add(url);
                }
            }
        });
        return index;
    }

    private void addPages(AdminguiTree tree, String module, Path resourceRoot, String consoleId) throws IOException {
        if (!Files.isDirectory(resourceRoot)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(resourceRoot)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> PAGE_EXTENSIONS.stream().anyMatch(e -> p.getFileName().toString().endsWith(e)))
                    .sorted()
                    .toList();
        }
        for (Path file : files) {
            String relative = resourceRoot.relativize(file).toString().replace('\\', '/');
            String url = url(relative, consoleId);
            if (pages.containsKey(url)) {
                continue;
            }
            alias(url, url);
            alias("/" + relative, url);
            String text = AdminguiTree.read(file);
            Set<String> includes = new TreeSet<>();
            Matcher include = INCLUDE.matcher(text);
            while (include.find()) {
                includes.add(resolve(include.group(1), directory(url)));
            }
            pages.put(url, new Page(url, module, tree.relative(file), text, includes));
        }
    }

    Collection<Page> pages() {
        return pages.values();
    }

    Page page(String url) {
        return pages.get(url);
    }

    boolean contains(String url) {
        return pages.containsKey(url);
    }

    List<Map<String, Object>> integrationPoints() {
        return integrationPoints;
    }

    /** Canonical URLs a requested URL resolves to (empty when it matches no page file). */
    Set<String> resolveAlias(String url) {
        return aliases.getOrDefault(url, Set.of());
    }

    /** Canonical URLs of the files that include the given canonical URL. */
    Set<String> includers(String url) {
        return includers.getOrDefault(url, Set.of());
    }

    /** The given page first, then, transitively up to the depth, the pages that include it. */
    Set<String> withIncluders(String url, int depth) {
        Set<String> result = new LinkedHashSet<>();
        collectIncluders(url, depth, result);
        return result;
    }

    private void collectIncluders(String url, int depth, Set<String> result) {
        if (!result.add(url) || depth == 0) {
            return;
        }
        for (String includer : includers(url)) {
            collectIncluders(includer, depth - 1, result);
        }
    }

    /** The given page first, then, transitively, the page files it includes. */
    Set<String> withIncludes(String url) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(url));
        while (!pending.isEmpty()) {
            String next = pending.poll();
            if (!pages.containsKey(next) || !result.add(next)) {
                continue;
            }
            pages.get(next).includes().forEach(target -> pending.addAll(resolveAlias(target)));
        }
        return result;
    }

    /** The given file first, then, up to the depth, the files that include it or use it as their template. */
    Set<String> containers(String url, int depth) {
        Set<String> result = new LinkedHashSet<>();
        Deque<Map.Entry<String, Integer>> pending = new ArrayDeque<>();
        pending.add(Map.entry(url, 0));
        while (!pending.isEmpty()) {
            Map.Entry<String, Integer> next = pending.poll();
            if (!result.add(next.getKey()) || next.getValue() == depth) {
                continue;
            }
            includers(next.getKey()).forEach(u -> pending.add(Map.entry(u, next.getValue() + 1)));
            templateUsers.getOrDefault(next.getKey(), Set.of()).forEach(u -> pending.add(Map.entry(u, next.getValue() + 1)));
        }
        return result;
    }

    /** Everything a screen is made of: the file, its includes, and its template with the template's includes. */
    Set<String> composition(String url) {
        Set<String> result = new TreeSet<>(withIncludes(url));
        for (String file : new ArrayList<>(result)) {
            String template = templates.get(file);
            if (template != null) {
                result.addAll(withIncludes(template));
            }
        }
        return result;
    }

    /** The files that make up the screens a page file belongs to (containers up to the depth). */
    Set<String> screenScope(String url, int depth) {
        Set<String> scope = new TreeSet<>();
        for (String container : containers(url, depth)) {
            scope.addAll(composition(container));
        }
        return scope;
    }

    /** Screens (.jsf pages) a file belongs to: the file itself if it is a .jsf, otherwise the .jsf files containing it. */
    Set<String> screens(String url, int depth) {
        Set<String> result = new TreeSet<>();
        for (String candidate : containers(url, depth)) {
            if (candidate.endsWith(".jsf")) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void alias(String alias, String canonical) {
        aliases.computeIfAbsent(alias, k -> new TreeSet<>()).add(canonical);
    }

    private static Map<String, Object> integrationPoint(Element element, String consoleId) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("consoleConfigId", consoleId);
        point.put("id", element.getAttribute("id"));
        point.put("type", element.getAttribute("type"));
        point.put("parentId", element.getAttribute("parentId").isEmpty() ? null : element.getAttribute("parentId"));
        point.put("priority", element.getAttribute("priority").isEmpty() ? null : element.getAttribute("priority"));
        String content = element.getAttribute("content");
        point.put("content", content);
        point.put("contentUrl", contentUrl(content, consoleId));
        return point;
    }

    /**
     * The URL an integration point content names, or null when it is not a page.
     *
     * <ul>
     * <li>{@code path|bundle|key} (drop-down types): the path is redirected to as {@code /<path>}</li>
     * <li>{@code appType:path} (editAppPage): the path is redirected to as {@code /<path>}</li>
     * <li>otherwise the content is included as {@code /<console-config id>/<content>}</li>
     * </ul>
     */
    static String contentUrl(String content, String consoleId) {
        String path = content;
        boolean rootRelative = false;
        if (path.contains("|")) {
            path = path.substring(0, path.indexOf('|'));
            rootRelative = true;
        } else if (path.contains(":") && !path.contains("://")) {
            path = path.substring(path.indexOf(':') + 1);
            rootRelative = true;
        }
        if (path.contains("://") || PAGE_EXTENSIONS.stream().noneMatch(path::endsWith)) {
            return null;
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return rootRelative ? "/" + path : "/" + consoleId + "/" + path;
    }

    private static String url(String relative, String consoleId) {
        if (relative.startsWith("META-INF/resources/")) {
            return "/" + relative.substring("META-INF/resources/".length());
        }
        return consoleId == null || consoleId.isEmpty() ? "/" + relative : "/" + consoleId + "/" + relative;
    }

    static String directory(String url) {
        return url.substring(0, url.lastIndexOf('/') + 1);
    }

    static String resolve(String path, String directory) {
        return normalize(path.startsWith("/") ? path : directory + path);
    }

    static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                parts.pollLast();
            } else {
                parts.addLast(part);
            }
        }
        return "/" + String.join("/", parts);
    }
}
