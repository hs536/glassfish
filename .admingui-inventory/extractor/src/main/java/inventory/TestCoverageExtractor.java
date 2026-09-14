package inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes inventory/test-coverage.yaml (調査計画.md S10): the screens the existing admin console tests touch, and the
 * operations reachable from those screens.
 *
 * <p>Everything is textual; no test is run. For each Selenium test method the string literals are read in order,
 * including those of helper methods it calls (same class, the base class, and test classes it instantiates, two
 * levels) and of string constants. A literal ending in {@code .jsf} names a screen. A component id literal
 * ({@code a:b:c}) is resolved as follows:
 *
 * <ul>
 * <li>{@code treeForm:tree:...}: the last tree node id with a {@code url} attribute gives the screen; a trailing
 * unknown segment (a dynamic child) uses the node's {@code childURL}.</li>
 * <li>otherwise: the screens whose files declare every segment that is declared as an id anywhere. When the test
 * has navigated to a screen before and it is among them, that screen is chosen; otherwise up to five screens are
 * accepted and more are reported as ambiguous.</li>
 * </ul>
 */
final class TestCoverageExtractor {

    private static final String SELENIUM_SOURCES = "appserver/tests/admingui/auto-test/src/test/java";
    private static final String QUICKLOOK_SOURCES = "appserver/tests/quicklook/adminconsole/src/test";
    private static final String BASE_CLASS = "BaseSeleniumTestClass";
    private static final int HELPER_DEPTH = 2;
    private static final int AMBIGUITY_LIMIT = 5;
    private static final Set<String> SCREEN_KINDS = Set.of("page", "integration", "partial");

    private static final Pattern ID_ATTRIBUTE = Pattern.compile("\\bid\\s*=\\s*\"([A-Za-z_][\\w$.-]*)\"");
    private static final Pattern TAG = Pattern.compile("<[A-Za-z!][\\w:]*\\s[^>]*>");
    private static final Pattern NODE_ATTRIBUTE = Pattern.compile("\\b(id|url|childURL)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern CLASS_NAME = Pattern.compile("\\bclass\\s+(\\w+)");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|private|protected|static|final|synchronized)\\s+)+[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*\\(");
    private static final Pattern CONSTANT = Pattern.compile("(?:static\\s+final|final\\s+static)\\s+String\\s+(\\w+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*;");
    private static final Pattern INSTANCE = Pattern.compile("\\b(\\w+)\\s+(\\w+)\\s*=\\s*new\\s+(\\w+)\\s*\\(\\s*\\)");
    private static final Pattern TOKEN = Pattern.compile(
            "\"((?:[^\"\\\\]|\\\\.)*)\"|(?<![\\w.])([A-Z][A-Z0-9_]+)\\b(?!\\s*\\()|(?<![\\w.])(\\w+)\\s*\\.\\s*(\\w+)\\s*\\(|(?<![\\w.])(\\w+)\\s*\\(");
    private static final Pattern ID_LITERAL = Pattern.compile("[A-Za-z_][\\w$.-]*(?::[\\w$.-]*)+");
    private static final Pattern PAGE_LITERAL = Pattern.compile("([A-Za-z0-9_./-]+\\.jsf)(?:\\?.*)?");
    /** An active {@code @Test} annotation (commented-out annotations start with {@code //}). */
    private static final Pattern TEST_ANNOTATION = Pattern.compile("(?m)^\\s*@Test\\b");
    private static final Pattern INCLUDED_INTEGRATION = Pattern.compile("(?:includeIntegrations|includeFirstIntegrationPoint)\\s*\\(\\s*type=\"([^\"]+)\"");

    private record Method(String name, boolean test, int line, String body) {
    }

    private record TestClass(String name, String source, Map<String, Method> methods, Map<String, String> constants) {
    }

    private final AdminguiTree tree;
    private PageIndex index;
    private final Map<String, Set<String>> screenIds = new TreeMap<>();
    private final Set<String> declaredIds = new TreeSet<>();
    /** Tree node id to the files defining it and the screens each definition points to. */
    private final Map<String, Map<String, Set<String>>> nodeUrls = new TreeMap<>();
    private final Map<String, Map<String, Set<String>>> childUrls = new TreeMap<>();
    private final Map<String, Set<String>> fileIds = new TreeMap<>();
    private final Map<String, String> kinds = new TreeMap<>();
    private final Map<String, TestClass> classes = new TreeMap<>();
    private final Map<String, Set<String>> globalConstants = new TreeMap<>();

    TestCoverageExtractor(Path root) {
        this.tree = new AdminguiTree(root);
    }

    void write(Path output) throws Exception {
        index = PageIndex.load(tree);
        indexScreens();
        readClasses(tree.root().resolve(SELENIUM_SOURCES));

        Map<String, Integer> resolution = new TreeMap<>();
        Map<String, Set<String>> testsByScreen = new TreeMap<>();
        Map<String, Integer> unresolved = new TreeMap<>();
        List<Object> tests = new ArrayList<>();
        int methodsWithScreens = 0;
        for (TestClass testClass : classes.values()) {
            for (Method method : testClass.methods().values()) {
                if (!method.test()) {
                    continue;
                }
                Set<String> screens = new TreeSet<>();
                Set<String> used = new TreeSet<>();
                int ambiguous = 0;
                Set<String> current = new TreeSet<>();
                for (String literal : literals(testClass, method, HELPER_DEPTH, new TreeSet<>(), used)) {
                    Result result = resolve(literal, current);
                    if (result == null) {
                        continue;
                    }
                    resolution.merge(result.kind(), 1, Integer::sum);
                    switch (result.kind()) {
                        case "ambiguous" -> ambiguous++;
                        case "unresolved" -> unresolved.merge(literal, 1, Integer::sum);
                        default -> {
                            screens.addAll(result.screens());
                            if (result.screens().size() == 1 || result.kind().equals("tree") || result.kind().equals("page")) {
                                current = new TreeSet<>(result.screens());
                            }
                        }
                    }
                }
                if (!screens.isEmpty()) {
                    methodsWithScreens++;
                }
                String label = testClass.name() + "." + method.name();
                screens.forEach(s -> testsByScreen.computeIfAbsent(s, k -> new TreeSet<>()).add(label));
                Map<String, Object> test = new LinkedHashMap<>();
                test.put("class", testClass.name());
                test.put("method", method.name());
                test.put("source", testClass.source() + ":" + method.line());
                test.put("screens", new ArrayList<>(screens));
                test.put("ambiguousIds", ambiguous);
                test.put("usesOtherClasses", new ArrayList<>(used));
                tests.add(test);
            }
        }

        List<Object> quicklook = new ArrayList<>();
        Path quicklookSources = tree.root().resolve(QUICKLOOK_SOURCES);
        for (Path java : javaFiles(quicklookSources)) {
            String text = AdminguiTree.read(java);
            Matcher literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
            while (literal.find()) {
                Result result = pageLiteral(literal.group(1));
                if (result != null) {
                    Map<String, Object> check = new LinkedHashMap<>();
                    check.put("source", tree.relative(java) + ":" + lineOf(text, literal.start()));
                    check.put("literal", literal.group(1));
                    check.put("screens", new ArrayList<>(result.screens()));
                    quicklook.add(check);
                }
            }
        }

        Map<String, Integer> screensByKind = new TreeMap<>();
        Map<String, Integer> coveredByKind = new TreeMap<>();
        Map<String, Map<String, Integer>> byModule = new TreeMap<>();
        List<String> uncoveredPages = new ArrayList<>();
        Map<String, Object> covered = new TreeMap<>();
        for (Map.Entry<String, String> screen : kinds.entrySet()) {
            String url = screen.getKey();
            String kind = screen.getValue();
            if (!kind.equals("page") && !kind.equals("integration")) {
                continue;
            }
            boolean isCovered = testsByScreen.containsKey(url);
            screensByKind.merge(kind, 1, Integer::sum);
            Map<String, Integer> module = byModule.computeIfAbsent(index.page(url).module(), k -> new TreeMap<>(Map.of("screens", 0, "covered", 0)));
            module.merge("screens", 1, Integer::sum);
            if (isCovered) {
                coveredByKind.merge(kind, 1, Integer::sum);
                module.merge("covered", 1, Integer::sum);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("kind", kind);
                entry.put("tests", new ArrayList<>(testsByScreen.get(url)));
                covered.put(url, entry);
            } else if (kind.equals("page")) {
                uncoveredPages.add(url);
            }
        }

        int operations = 0;
        int stateChanging = 0;
        int onCovered = 0;
        int stateChangingOnCovered = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operationList = (List<Map<String, Object>>) new OperationsExtractor(tree.root()).build().get("operations");
        for (Map<String, Object> operation : operationList) {
            boolean change = (boolean) operation.get("stateChanging");
            @SuppressWarnings("unchecked")
            boolean reached = ((List<String>) operation.get("screens")).stream().anyMatch(testsByScreen::containsKey);
            operations++;
            stateChanging += change ? 1 : 0;
            onCovered += reached ? 1 : 0;
            stateChangingOnCovered += change && reached ? 1 : 0;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("seleniumClasses", classes.values().stream().filter(c -> c.methods().values().stream().anyMatch(Method::test)).count());
        summary.put("seleniumTestMethods", tests.size());
        summary.put("testMethodsTouchingScreens", methodsWithScreens);
        summary.put("literalResolution", resolution);
        summary.put("screensByKind", screensByKind);
        summary.put("coveredScreensByKind", coveredByKind);
        summary.put("coverageByModule", byModule);
        Map<String, Object> operationSummary = new LinkedHashMap<>();
        operationSummary.put("operations", operations);
        operationSummary.put("onCoveredScreens", onCovered);
        operationSummary.put("stateChanging", stateChanging);
        operationSummary.put("stateChangingOnCoveredScreens", stateChangingOnCovered);
        summary.put("operationsFromOperationsInventory", operationSummary);
        summary.put("quicklookChecks", quicklook.size());

        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", "glassfish");
        source.put("commit", ModulesExtractor.Git.head(tree.root()));
        document.put("source", source);
        document.put("summary", summary);
        document.put("seleniumTests", tests);
        document.put("quicklookChecks", quicklook);
        document.put("coveredScreens", covered);
        document.put("uncoveredPages", uncoveredPages);
        document.put("unresolvedIdLiterals", unresolved);

        Files.createDirectories(output.getParent());
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/inventory/extractor (test-coverage). Do not edit by hand.\n"
                        + "Textual estimate: tests are not run; a screen is covered when a test names it or an element id resolves to it.\n"
                        + "Operations on covered screens are reachable from a tested screen, not necessarily exercised.\n"
                        + "Interpretation and notes belong in docs/as-is/tests.md; suite status is in inventory/tests.yaml."),
                StandardCharsets.UTF_8);
    }

    private record Result(String kind, Set<String> screens) {
    }

    /**
     * Ids declared by each screen's files (including the content of integration points it includes), tree node
     * targets, and screen kinds.
     */
    private void indexScreens() {
        Set<String> integrationContent = new TreeSet<>();
        Map<String, Set<String>> contentByType = new TreeMap<>();
        for (Map<String, Object> point : index.integrationPoints()) {
            Object raw = point.get("contentUrl");
            if (raw != null) {
                Set<String> targets = index.resolveAlias(raw.toString());
                integrationContent.addAll(targets);
                contentByType.computeIfAbsent(point.get("type").toString(), k -> new TreeSet<>()).addAll(targets);
            }
        }
        for (PageIndex.Page page : index.pages()) {
            Set<String> ids = new TreeSet<>();
            Matcher id = ID_ATTRIBUTE.matcher(page.text());
            while (id.find()) {
                ids.add(id.group(1));
            }
            fileIds.put(page.url(), ids);
            declaredIds.addAll(ids);
        }
        for (PageIndex.Page page : index.pages()) {
            Matcher tag = TAG.matcher(page.text());
            while (tag.find()) {
                String nodeId = null;
                String url = null;
                String childUrl = null;
                Matcher attribute = NODE_ATTRIBUTE.matcher(tag.group());
                while (attribute.find()) {
                    switch (attribute.group(1)) {
                        case "id" -> nodeId = attribute.group(2);
                        case "url" -> url = attribute.group(2);
                        default -> childUrl = attribute.group(2);
                    }
                }
                if (nodeId != null) {
                    addTarget(nodeUrls, nodeId, url, page.url());
                    addTarget(childUrls, nodeId, childUrl, page.url());
                }
            }
            if (page.url().endsWith(".jsf")) {
                String kind = ScreensExtractor.kind(index, page.url(), integrationContent.contains(page.url()));
                kinds.put(page.url(), kind);
                if (SCREEN_KINDS.contains(kind)) {
                    Set<String> ids = new TreeSet<>();
                    for (String file : index.composition(page.url())) {
                        ids.addAll(fileIds.get(file));
                        Matcher included = INCLUDED_INTEGRATION.matcher(index.page(file).text());
                        while (included.find()) {
                            for (String content : contentByType.getOrDefault(included.group(1), Set.of())) {
                                index.composition(content).forEach(c -> ids.addAll(fileIds.get(c)));
                            }
                        }
                    }
                    screenIds.put(page.url(), ids);
                }
            }
        }
    }

    private void addTarget(Map<String, Map<String, Set<String>>> targets, String nodeId, String rawUrl, String pageUrl) {
        if (rawUrl == null) {
            return;
        }
        String url = rawUrl.replaceFirst("^#\\{[A-Za-z.]*[cC]ontextPath\\}", "");
        int query = url.indexOf('?');
        url = query < 0 ? url : url.substring(0, query);
        if (url.contains("#{") || url.contains("${") || !url.endsWith(".jsf")) {
            return;
        }
        Set<String> resolved = index.resolveAlias(PageIndex.resolve(url, PageIndex.directory(pageUrl)));
        if (!resolved.isEmpty()) {
            targets.computeIfAbsent(nodeId, k -> new TreeMap<>()).computeIfAbsent(pageUrl, k -> new TreeSet<>()).addAll(resolved);
        }
    }

    /** Targets of a tree node; when several files define it, those whose file also declares the parent node. */
    private Set<String> nodeTargets(Map<String, Map<String, Set<String>>> targets, String nodeId, String parentId) {
        Set<String> all = new TreeSet<>();
        Set<String> underParent = new TreeSet<>();
        targets.get(nodeId).forEach((file, urls) -> {
            all.addAll(urls);
            if (parentId != null && fileIds.get(file).contains(parentId)) {
                underParent.addAll(urls);
            }
        });
        return all.size() > 1 && !underParent.isEmpty() ? underParent : all;
    }

    private void readClasses(Path sources) throws IOException {
        for (Path java : javaFiles(sources)) {
            String text = AdminguiTree.read(java);
            Matcher className = CLASS_NAME.matcher(text);
            if (!className.find()) {
                continue;
            }
            Map<String, String> constants = new TreeMap<>();
            Matcher constant = CONSTANT.matcher(text);
            while (constant.find()) {
                constants.put(constant.group(1), constant.group(2));
                globalConstants.computeIfAbsent(constant.group(1), k -> new TreeSet<>()).add(constant.group(2));
            }
            Map<String, Method> methods = new TreeMap<>();
            Matcher declaration = METHOD_DECLARATION.matcher(text);
            int previousEnd = 0;
            while (declaration.find()) {
                int open = text.indexOf('{', declaration.end());
                int semicolon = text.indexOf(';', declaration.end());
                if (open < 0 || (semicolon >= 0 && semicolon < open) || declaration.start() < previousEnd) {
                    continue;
                }
                String body = JavaSource.blockAfter(text, declaration.end());
                if (body == null) {
                    continue;
                }
                boolean test = TEST_ANNOTATION.matcher(text.substring(previousEnd, declaration.start())).find();
                methods.putIfAbsent(declaration.group(1), new Method(declaration.group(1), test, lineOf(text, declaration.start()), body));
                previousEnd = open + body.length();
            }
            classes.put(className.group(1), new TestClass(className.group(1), tree.relative(java), methods, constants));
        }
    }

    /** String literals and constant values of a method in order, expanding helper calls. */
    private List<String> literals(TestClass owner, Method method, int depth, Set<String> visited, Set<String> usedClasses) {
        List<String> result = new ArrayList<>();
        if (!visited.add(owner.name() + "." + method.name())) {
            return result;
        }
        String body = method.body();
        Map<String, String> instances = new TreeMap<>();
        Matcher instance = INSTANCE.matcher(body);
        while (instance.find()) {
            if (classes.containsKey(instance.group(3))) {
                instances.put(instance.group(2), instance.group(3));
            }
        }
        Matcher token = TOKEN.matcher(body);
        while (token.find()) {
            if (inLineComment(body, token.start())) {
                continue;
            }
            if (token.group(1) != null) {
                result.add(token.group(1));
            } else if (token.group(2) != null) {
                String value = constant(owner, token.group(2));
                if (value != null) {
                    result.add(value);
                }
            } else if (token.group(3) != null) {
                String calledClass = instances.get(token.group(3));
                if (calledClass == null && classes.containsKey(token.group(3))) {
                    calledClass = token.group(3);
                }
                if (calledClass != null && depth > 0) {
                    usedClasses.add(calledClass);
                    TestClass target = classes.get(calledClass);
                    Method helper = findMethod(target, token.group(4));
                    if (helper != null) {
                        result.addAll(literals(target, helper, depth - 1, visited, usedClasses));
                    }
                }
            } else if (depth > 0) {
                Method helper = findMethod(owner, token.group(5));
                if (helper != null && !helper.test()) {
                    TestClass helperOwner = owner.methods().containsKey(helper.name()) ? owner : classes.get(BASE_CLASS);
                    result.addAll(literals(helperOwner, helper, depth - 1, visited, usedClasses));
                }
            }
        }
        return result;
    }

    private Method findMethod(TestClass owner, String name) {
        Method method = owner.methods().get(name);
        if (method == null && classes.containsKey(BASE_CLASS)) {
            method = classes.get(BASE_CLASS).methods().get(name);
        }
        return method;
    }

    private String constant(TestClass owner, String name) {
        if (owner.constants().containsKey(name)) {
            return owner.constants().get(name);
        }
        Set<String> values = globalConstants.get(name);
        return values != null && values.size() == 1 ? values.iterator().next() : null;
    }

    /** The screens a literal names, or null when the literal is neither a page nor a component id. */
    private Result resolve(String literal, Set<String> current) {
        Result page = pageLiteral(literal);
        if (page != null) {
            return page;
        }
        if (!ID_LITERAL.matcher(literal).matches()) {
            return null;
        }
        String[] segments = literal.split(":");
        if (literal.startsWith("_") || literal.startsWith(":") || literal.equals("treeForm:tree")
                || Stream.of(segments).filter(s -> !s.isEmpty()).count() < 2) {
            // A fragment of a concatenated id
            return null;
        }
        if (literal.startsWith("treeForm:tree:")) {
            if (segments.length <= 2) {
                return null;
            }
            boolean dynamicChild = false;
            for (int i = segments.length - 1; i >= 2; i--) {
                String segment = nodeSegment(segments[i]);
                if (segment.isEmpty() || segment.equals("link")) {
                    continue;
                }
                String parent = i > 2 ? nodeSegment(segments[i - 1]) : null;
                if (dynamicChild && childUrls.containsKey(segment)) {
                    return new Result("tree", nodeTargets(childUrls, segment, parent));
                }
                if (nodeUrls.containsKey(segment)) {
                    Set<String> targets = nodeTargets(nodeUrls, segment, parent);
                    return targets.size() <= AMBIGUITY_LIMIT ? new Result("tree", targets) : new Result("ambiguous", Set.of());
                }
                dynamicChild = true;
            }
            return new Result("unresolved", Set.of());
        }
        List<String> declared = Stream.of(segments).filter(declaredIds::contains).toList();
        if (declared.isEmpty()) {
            return new Result("unresolved", Set.of());
        }
        Set<String> candidates = new TreeSet<>();
        screenIds.forEach((url, ids) -> {
            if (ids.containsAll(declared)) {
                candidates.add(url);
            }
        });
        Set<String> inContext = new TreeSet<>(candidates);
        inContext.retainAll(current);
        if (!inContext.isEmpty()) {
            return new Result("context", inContext);
        }
        if (candidates.isEmpty()) {
            return new Result("unresolved", Set.of());
        }
        // After a navigation the new screen is usually in the module of the previous one
        Set<String> currentModules = new TreeSet<>();
        current.forEach(url -> currentModules.add(index.page(url).module()));
        Set<String> sameModule = new TreeSet<>();
        candidates.stream().filter(url -> currentModules.contains(index.page(url).module())).forEach(sameModule::add);
        if (candidates.size() > 1 && !sameModule.isEmpty() && sameModule.size() <= AMBIGUITY_LIMIT) {
            return new Result("module", sameModule);
        }
        return candidates.size() <= AMBIGUITY_LIMIT ? new Result("ids", candidates) : new Result("ambiguous", Set.of());
    }

    private static String nodeSegment(String segment) {
        return segment.replaceFirst("_(link|turner|image)$", "");
    }

    private Result pageLiteral(String literal) {
        Matcher page = PAGE_LITERAL.matcher(literal);
        if (!page.matches()) {
            return null;
        }
        Set<String> screens = index.resolveAlias(PageIndex.normalize("/" + page.group(1)));
        return screens.isEmpty() ? new Result("unresolved", Set.of()) : new Result("page", screens);
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static boolean inLineComment(String text, int position) {
        String before = text.substring(text.lastIndexOf('\n', position - 1) + 1, position);
        return before.contains("//") || before.stripLeading().startsWith("*");
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
