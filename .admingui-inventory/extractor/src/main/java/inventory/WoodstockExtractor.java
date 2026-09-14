package inventory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Writes inventory/woodstock-usage.yaml (調査計画.md S4): Woodstock tags and attributes used by admin console pages,
 * Woodstock Java APIs, Woodstock servlets/filters, and client-side references. Counts are textual (comments included).
 */
final class WoodstockExtractor {

    private static final List<String> MARKUP_EXTENSIONS = List.of(".jsf", ".inc", ".layout", ".xhtml");
    private static final List<String> REFERENCE_EXTENSIONS = List.of(".jsf", ".inc", ".layout", ".xhtml", ".js", ".css", ".properties", ".java");

    private static final Pattern TAG_START = Pattern.compile("<sun:([A-Za-z]\\w*)");
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z_][\\w:.-]*");
    private static final Pattern WOODSTOCK_IMPORT = Pattern.compile("(?m)^import\\s+(com\\.sun\\.webui\\.[\\w.]+)\\s*;");
    private static final Map<String, Pattern> CLIENT_REFERENCES = clientReferences();

    private final AdminguiTree tree;

    WoodstockExtractor(Path root) {
        this.tree = new AdminguiTree(root);
    }

    private static Map<String, Pattern> clientReferences() {
        Map<String, Pattern> references = new LinkedHashMap<>();
        references.put("webui.suntheme", Pattern.compile("webui\\.suntheme"));
        references.put("/theme/", Pattern.compile("/theme/"));
        references.put("dojo.", Pattern.compile("\\bdojo\\."));
        references.put("yui", Pattern.compile("\\byui/|\\bYAHOO\\."));
        return references;
    }

    void write(Path output) throws Exception {
        Map<String, TagUsage> tags = new TreeMap<>();
        Map<String, Map<String, Integer>> references = new LinkedHashMap<>();
        Map<String, List<String>> javaImports = new TreeMap<>();
        List<String> clientAssets = new ArrayList<>();
        for (String name : CLIENT_REFERENCES.keySet()) {
            references.put(name, new TreeMap<>());
        }

        for (Path module : tree.modules()) {
            String moduleName = module.getFileName().toString();
            for (Path file : tree.files(module, MARKUP_EXTENSIONS)) {
                String text = AdminguiTree.read(file);
                Matcher tag = TAG_START.matcher(text);
                while (tag.find()) {
                    TagUsage usage = tags.computeIfAbsent("sun:" + tag.group(1), TagUsage::new);
                    usage.record(moduleName, tree.relative(file), attributeNames(text, tag.end()));
                }
            }
            for (Path file : tree.files(module, REFERENCE_EXTENSIONS)) {
                String text = AdminguiTree.read(file);
                String extension = file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.'));
                for (Map.Entry<String, Pattern> reference : CLIENT_REFERENCES.entrySet()) {
                    Matcher matcher = reference.getValue().matcher(text);
                    while (matcher.find()) {
                        references.get(reference.getKey()).merge(extension, 1, Integer::sum);
                    }
                }
                if (extension.equals(".js") || extension.equals(".css")) {
                    clientAssets.add(tree.relative(file));
                }
                if (extension.equals(".java") && file.startsWith(module.resolve("src/main/java"))) {
                    Set<String> imports = new TreeSet<>();
                    Matcher matcher = WOODSTOCK_IMPORT.matcher(text);
                    while (matcher.find()) {
                        imports.add(matcher.group(1));
                    }
                    if (!imports.isEmpty()) {
                        javaImports.put(tree.relative(file), new ArrayList<>(imports));
                    }
                }
            }
        }

        List<TagUsage> byOccurrences = tags.values().stream()
                .sorted(Comparator.comparingInt((TagUsage t) -> t.occurrences).reversed().thenComparing(t -> t.tag))
                .toList();

        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", "glassfish");
        source.put("commit", ModulesExtractor.Git.head(tree.root()));
        document.put("source", source);
        document.put("summary", summary(byOccurrences));
        document.put("webXml", webXml(tree.admingui().resolve("war/src/main/webapp/WEB-INF/web.xml")));
        document.put("javaImports", javaImports);
        document.put("clientReferencesByFileType", references);
        document.put("clientAssets", clientAssets);
        List<Object> tagList = new ArrayList<>();
        byOccurrences.forEach(t -> tagList.add(t.toMap()));
        document.put("tags", tagList);

        Files.createDirectories(output.getParent());
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/inventory/extractor (woodstock). Do not edit by hand.\n"
                        + "Counts are textual matches in .jsf/.inc/.layout/.xhtml files (comments included).\n"
                        + "Interpretation and the per-component evaluation belong in docs/as-is/woodstock.md."),
                StandardCharsets.UTF_8);
    }

    /** Attribute names from just after the tag name to the end of the start tag, honoring quoted values. */
    static List<String> attributeNames(String text, int from) {
        List<String> names = new ArrayList<>();
        int length = text.length();
        int i = from;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '>' || (c == '/' && i + 1 < length && text.charAt(i + 1) == '>')) {
                break;
            }
            Matcher name = ATTRIBUTE_NAME.matcher(text).region(i, length);
            if (Character.isWhitespace(c) || !name.lookingAt()) {
                i++;
                continue;
            }
            names.add(name.group());
            i = skipWhitespace(text, name.end());
            if (i < length && text.charAt(i) == '=') {
                i = skipWhitespace(text, i + 1);
                if (i < length && (text.charAt(i) == '"' || text.charAt(i) == '\'')) {
                    int end = text.indexOf(text.charAt(i), i + 1);
                    if (end < 0) {
                        break;
                    }
                    i = end + 1;
                } else {
                    while (i < length && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '>') {
                        i++;
                    }
                }
            }
        }
        return names;
    }

    private static int skipWhitespace(String text, int i) {
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static Map<String, Object> summary(List<TagUsage> byOccurrences) {
        int total = byOccurrences.stream().mapToInt(t -> t.occurrences).sum();
        List<String> coverage = new ArrayList<>();
        int cumulative = 0;
        for (TagUsage tag : byOccurrences) {
            if (cumulative * 100 >= total * 95) {
                break;
            }
            coverage.add(tag.tag);
            cumulative += tag.occurrences;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tagKinds", byOccurrences.size());
        summary.put("occurrences", total);
        summary.put("tagsCovering95Percent", coverage);
        return summary;
    }

    private static Map<String, Object> webXml(Path webXml) throws Exception {
        Document document = Pom.Xml.parse(webXml);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filters", components(document, "filter", "filter-class", "filter-mapping"));
        result.put("servlets", components(document, "servlet", "servlet-class", "servlet-mapping"));
        return result;
    }

    /** Woodstock filters or servlets with their init parameters and mappings. */
    private static List<Object> components(Document document, String element, String classElement, String mappingElement) {
        List<Object> result = new ArrayList<>();
        NodeList declarations = document.getElementsByTagName(element);
        for (int i = 0; i < declarations.getLength(); i++) {
            Element declaration = (Element) declarations.item(i);
            String className = text(declaration, classElement);
            if (className == null || !className.startsWith("com.sun.webui.")) {
                continue;
            }
            String name = text(declaration, element + "-name");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("class", className);
            Map<String, String> initParams = new LinkedHashMap<>();
            NodeList params = declaration.getElementsByTagName("init-param");
            for (int p = 0; p < params.getLength(); p++) {
                Element param = (Element) params.item(p);
                initParams.put(text(param, "param-name"), text(param, "param-value"));
            }
            entry.put("initParams", initParams);
            List<String> mappings = new ArrayList<>();
            NodeList mappingNodes = document.getElementsByTagName(mappingElement);
            for (int m = 0; m < mappingNodes.getLength(); m++) {
                Element mapping = (Element) mappingNodes.item(m);
                if (name.equals(text(mapping, element + "-name"))) {
                    String urlPattern = text(mapping, "url-pattern");
                    String servletName = text(mapping, "servlet-name");
                    mappings.add(urlPattern != null ? "url-pattern " + urlPattern : "servlet " + servletName);
                }
            }
            entry.put("mappings", mappings);
            result.add(entry);
        }
        return result;
    }

    private static String text(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static final class TagUsage {
        final String tag;
        int occurrences;
        final Set<String> files = new TreeSet<>();
        final Map<String, Integer> modules = new TreeMap<>();
        final Map<String, Integer> attributes = new TreeMap<>();

        TagUsage(String tag) {
            this.tag = tag;
        }

        void record(String module, String file, List<String> attributeNames) {
            occurrences++;
            files.add(file);
            modules.merge(module, 1, Integer::sum);
            attributeNames.forEach(a -> attributes.merge(a, 1, Integer::sum));
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tag", tag);
            map.put("occurrences", occurrences);
            map.put("files", files.size());
            map.put("modules", modules);
            Map<String, Integer> sortedAttributes = new LinkedHashMap<>();
            attributes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .forEach(e -> sortedAttributes.put(e.getKey(), e.getValue()));
            map.put("attributes", sortedAttributes);
            return map;
        }
    }
}
