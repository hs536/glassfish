package inventory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes inventory/screens.yaml (調査計画.md S2): every JSFTemplating/Facelets page file with its URL, kind,
 * template, includes, referenced pages, message bundles and help key, plus a reachability estimate.
 *
 * <p>Everything is textual; URL rules are those of {@link PageIndex}. A page name without a leading slash is tried
 * relative to the referencing file, to the pages that include it (included content is inlined into those pages), and
 * to the context root.
 */
final class ScreensExtractor {

    private static final List<String> CODE_EXTENSIONS = List.of(".java", ".js", ".properties");
    private static final List<String> NON_PAGE_PREFIXES = List.of("/resource/", "/theme/", "/html/", "/download/", "/faces/");
    private static final int INCLUDER_DEPTH = 3;

    private static final Pattern TEMPLATE = Pattern.compile("<!composition\\s+template=\"([^\"]+)\"");
    /**
     * Absolute page paths, optionally after a context path expression such as #{request.contextPath},
     * #{requestScope.contextPath} or #{facesContext.externalContext.requestContextPath}; paths built from other EL
     * values are skipped.
     */
    static final Pattern ABSOLUTE_REFERENCE = Pattern.compile("(?<![\\w.}])(?:#\\{[A-Za-z.]*[cC]ontextPath\\})?(/[A-Za-z0-9_\\-./]+\\.(?:jsf|xhtml))");
    /** Quoted page names without a leading slash (attributes, window.open(...), page session values). */
    private static final Pattern RELATIVE_REFERENCE = Pattern.compile("['\"]([A-Za-z0-9_\\-][A-Za-z0-9_\\-./]*\\.jsf)");
    private static final Pattern RELATIVE_FACELETS = Pattern.compile("['\"]([A-Za-z0-9_\\-]+\\.xhtml)['\"]");
    /** Markup that makes a file a complete page rather than a partial. */
    private static final Pattern PAGE_MARKUP = Pattern.compile("<!composition\\b|<sun:page\\b|<sun:html\\b|<html\\b");
    private static final Pattern BUNDLE = Pattern.compile("setResourceBundle\\(\\s*key=\"([^\"]+)\"\\s+bundle=\"([^\"]+)\"");
    private static final Pattern HELP_KEY = Pattern.compile("id=\"helpKey\"\\s+value=\"([^\"]+)\"");
    private static final Pattern GUI_TITLE = Pattern.compile("guiTitle=\"([^\"]+)\"");

    private final AdminguiTree tree;
    private PageIndex index;

    /** Raw references (includes, templates, absolute pages) by the canonical URL of the file that makes them. */
    private final Map<String, Set<String>> rawEdges = new TreeMap<>();
    /** Files (repository-relative) referencing each raw URL. */
    private final Map<String, Set<String>> referenceSources = new TreeMap<>();

    ScreensExtractor(Path root) {
        this.tree = new AdminguiTree(root);
    }

    void write(Path output) throws Exception {
        index = PageIndex.load(tree);
        Set<String> rawRoots = new TreeSet<>(List.of("/index.jsf", "/login.jsf", "/loginError.jsf"));

        Map<String, Map<String, Object>> screens = new TreeMap<>();
        for (PageIndex.Page page : index.pages()) {
            screens.put(page.url(), describe(page));
        }
        for (Path module : tree.modules()) {
            for (Path code : tree.files(module, CODE_EXTENSIONS)) {
                Matcher reference = ABSOLUTE_REFERENCE.matcher(AdminguiTree.read(code));
                while (reference.find()) {
                    String url = PageIndex.normalize(reference.group(1));
                    rawRoots.add(url);
                    referenceSources.computeIfAbsent(url, k -> new TreeSet<>()).add(tree.relative(code));
                }
            }
        }
        for (PageIndex.Page page : index.pages()) {
            resolveRelativeReferences(page);
        }

        Map<String, Set<String>> integrationTypesByUrl = new TreeMap<>();
        List<String> unresolvedIntegration = new ArrayList<>();
        for (Map<String, Object> point : index.integrationPoints()) {
            Object raw = point.get("contentUrl");
            Set<String> targets = raw == null ? Set.of() : index.resolveAlias(raw.toString());
            point.put("contentResolvesTo", new ArrayList<>(targets));
            if (raw != null) {
                rawRoots.add(raw.toString());
                if (targets.isEmpty()) {
                    unresolvedIntegration.add(point.get("consoleConfigId") + ":" + point.get("id") + " -> " + raw);
                }
                targets.forEach(t -> integrationTypesByUrl.computeIfAbsent(t, k -> new TreeSet<>()).add(point.get("type").toString()));
            }
        }

        Set<String> reachable = reachable(rawRoots);
        List<String> unreachableScreens = new ArrayList<>();
        Map<String, Integer> kinds = new TreeMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : screens.entrySet()) {
            String url = entry.getKey();
            Map<String, Object> screen = entry.getValue();
            screen.put("kind", kind(url, integrationTypesByUrl.containsKey(url)));
            Set<String> types = integrationTypesByUrl.get(url);
            screen.put("integrationPointTypes", types == null ? List.of() : new ArrayList<>(types));
            screen.put("includedBy", new ArrayList<>(index.includers(url)));
            screen.put("references", new ArrayList<>(rawEdges.getOrDefault(url, Set.of())));
            screen.put("reachable", reachable.contains(url));
            kinds.merge(String.valueOf(screen.get("kind")), 1, Integer::sum);
            if (!reachable.contains(url) && List.of("page", "partial", "integration", "facelets").contains(screen.get("kind"))) {
                unreachableScreens.add(url);
            }
        }

        Map<String, Object> unresolved = new TreeMap<>();
        Map<String, Object> resolvedOnlyWithoutPrefix = new TreeMap<>();
        referenceSources.forEach((url, sources) -> {
            if (NON_PAGE_PREFIXES.stream().anyMatch(url::startsWith)) {
                return;
            }
            Set<String> targets = index.resolveAlias(url);
            if (targets.isEmpty()) {
                unresolved.put(url, new ArrayList<>(sources));
            } else if (!index.contains(url)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("resolvesTo", new ArrayList<>(targets));
                entry.put("referencedFrom", new ArrayList<>(sources));
                resolvedOnlyWithoutPrefix.put(url, entry);
            }
        });

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("files", screens.size());
        summary.put("byKind", kinds);
        summary.put("integrationPoints", index.integrationPoints().size());
        summary.put("unreachableScreens", unreachableScreens.size());
        summary.put("unresolvedReferences", unresolved.size());
        summary.put("referencesUsingNonCanonicalUrl", resolvedOnlyWithoutPrefix.size());
        summary.put("unresolvedIntegrationContent", unresolvedIntegration.size());

        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", "glassfish");
        source.put("commit", ModulesExtractor.Git.head(tree.root()));
        source.put("roots", "/index.jsf, /login.jsf, /loginError.jsf, integration point content, and pages referenced from .java/.js/.properties");
        document.put("source", source);
        document.put("summary", summary);
        document.put("unreachableScreens", unreachableScreens);
        document.put("unresolvedReferences", unresolved);
        document.put("referencesUsingNonCanonicalUrl", resolvedOnlyWithoutPrefix);
        document.put("unresolvedIntegrationContent", unresolvedIntegration);
        document.put("integrationPoints", index.integrationPoints());
        document.put("screens", new ArrayList<>(screens.values()));

        Files.createDirectories(output.getParent());
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/inventory/extractor (screens). Do not edit by hand.\n"
                        + "All relations are textual; EL-computed URLs and templates are not resolved. reachable is an estimate.\n"
                        + "Interpretation and notes belong in docs/as-is/navigation.md."),
                StandardCharsets.UTF_8);
    }

    private Map<String, Object> describe(PageIndex.Page page) {
        String url = page.url();
        String text = page.text();
        String directory = PageIndex.directory(url);
        Set<String> out = rawEdges.computeIfAbsent(url, k -> new TreeSet<>());

        for (String target : page.includes()) {
            addReference(out, target, page.source());
        }
        Matcher template = TEMPLATE.matcher(text);
        String templateValue = template.find() ? template.group(1) : null;
        if (templateValue != null && !templateValue.contains("#{")) {
            out.add(PageIndex.resolve(templateValue, directory));
        }
        Matcher reference = ABSOLUTE_REFERENCE.matcher(text);
        while (reference.find()) {
            addReference(out, PageIndex.normalize(reference.group(1)), page.source());
        }
        Matcher facelets = RELATIVE_FACELETS.matcher(text);
        while (facelets.find()) {
            addReference(out, PageIndex.resolve(facelets.group(1), directory), page.source());
        }

        Map<String, String> bundles = new TreeMap<>();
        Matcher bundle = BUNDLE.matcher(text);
        while (bundle.find()) {
            bundles.put(bundle.group(1), bundle.group(2));
        }
        Matcher helpKey = HELP_KEY.matcher(text);
        Matcher title = GUI_TITLE.matcher(text);

        Map<String, Object> screen = new LinkedHashMap<>();
        screen.put("url", url);
        screen.put("source", page.source());
        screen.put("module", page.module());
        screen.put("kind", null);
        screen.put("template", templateValue);
        screen.put("title", title.find() ? title.group(1) : null);
        screen.put("helpKey", helpKey.find() ? helpKey.group(1) : null);
        screen.put("bundles", bundles);
        screen.put("includes", new ArrayList<>(page.includes()));
        return screen;
    }

    /** Relative page names resolve against the file, then the pages including it, then the context root. */
    private void resolveRelativeReferences(PageIndex.Page page) {
        Set<String> names = new TreeSet<>();
        Matcher relativeReference = RELATIVE_REFERENCE.matcher(page.text());
        while (relativeReference.find()) {
            names.add(relativeReference.group(1));
        }
        List<String> directories = index.withIncluders(page.url(), INCLUDER_DEPTH).stream()
                .map(PageIndex::directory)
                .distinct()
                .toList();
        Set<String> out = rawEdges.computeIfAbsent(page.url(), k -> new TreeSet<>());
        for (String name : names) {
            String chosen = null;
            for (String directory : directories) {
                String candidate = PageIndex.resolve(name, directory);
                if (!index.resolveAlias(candidate).isEmpty()) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null && !index.resolveAlias(PageIndex.normalize("/" + name)).isEmpty()) {
                chosen = PageIndex.normalize("/" + name);
            }
            addReference(out, chosen != null ? chosen : PageIndex.resolve(name, PageIndex.directory(page.url())), page.source());
        }
    }

    private String kind(String url, boolean integrationContent) {
        return kind(index, url, integrationContent);
    }

    /** A .jsf is a page when it or a page file it includes (transitively) carries page markup. */
    static String kind(PageIndex index, String url, boolean integrationContent) {
        if (url.endsWith(".layout")) {
            return "layout";
        }
        if (url.endsWith(".inc")) {
            return "fragment";
        }
        if (url.endsWith(".xhtml")) {
            return "facelets";
        }
        boolean pageMarkup = index.withIncludes(url).stream()
                .filter(u -> u.equals(url) || u.endsWith(".inc") || u.endsWith(".jsf"))
                .anyMatch(u -> PAGE_MARKUP.matcher(index.page(u).text()).find());
        if (pageMarkup) {
            return "page";
        }
        return integrationContent ? "integration" : "partial";
    }

    private void addReference(Set<String> out, String target, String source) {
        out.add(target);
        referenceSources.computeIfAbsent(target, k -> new TreeSet<>()).add(source);
    }

    /** Canonical URLs reachable from the raw roots through includes, templates and references. */
    private Set<String> reachable(Set<String> rawRoots) {
        Set<String> seen = new TreeSet<>();
        Deque<String> pending = new ArrayDeque<>();
        rawRoots.forEach(r -> pending.addAll(index.resolveAlias(r)));
        while (!pending.isEmpty()) {
            String url = pending.poll();
            if (!seen.add(url)) {
                continue;
            }
            for (String raw : rawEdges.getOrDefault(url, Set.of())) {
                pending.addAll(index.resolveAlias(raw));
            }
        }
        return seen;
    }
}
