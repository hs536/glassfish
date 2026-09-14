package runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches the REST endpoint templates extracted from the pages (inventory/operations.yaml) against the endpoints
 * observed while the console was crawled (rest-calls output), to see which extracted operations actually happen, what
 * the templates with an unknown base URL resolve to, and which observed endpoints the extraction does not know
 * (課題 V-05, V-06, V-09).
 *
 * <p>A template placeholder inside the path ({name}) matches one path segment; a placeholder at the start other than
 * {REST} (for example {parentUrl}) matches any prefix. Queries and a trailing .json are ignored on both sides.
 * Templates that are placeholders only (for example {REST}/{endpoint}) would match everything; they are counted as
 * too generic and are not used to explain observed endpoints.
 */
final class OperationsMatch {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+\\}");
    private static final Pattern OBSERVED = Pattern.compile("^  (GET|POST|PUT|DELETE|OPTIONS|HEAD) (.+): (\\d+)$");
    private static final int EXAMPLES = 5;
    private static final int MIN_LITERAL = 3;

    private record Operation(List<String> methods, String endpoint, boolean stateChanging) {
    }

    private OperationsMatch() {
    }

    static void write(Path operationsYaml, Path restCallsYaml, Path output) throws IOException {
        List<Operation> operations = readOperations(operationsYaml);
        Map<String, Integer> observed = readObserved(restCallsYaml);

        List<Map<String, Object>> operationEntries = new ArrayList<>();
        List<Map<String, Object>> unknownBase = new ArrayList<>();
        Map<String, Boolean> explained = new TreeMap<>();
        observed.keySet().forEach(k -> explained.put(k, false));
        int generic = 0;
        int seenGet = 0;
        int totalGet = 0;
        int seenChanging = 0;
        int totalChanging = 0;
        for (Operation operation : operations) {
            String template = normalize(operation.endpoint());
            boolean tooGeneric = PLACEHOLDER.matcher(template.replace("{REST}", "")).replaceAll("").replace("/", "").length() < MIN_LITERAL;
            Pattern regex = regex(template);
            List<String> matches = new ArrayList<>();
            int count = 0;
            int changingCount = 0;
            for (Map.Entry<String, Integer> entry : observed.entrySet()) {
                String method = entry.getKey().substring(0, entry.getKey().indexOf(' '));
                String path = normalize(entry.getKey().substring(method.length() + 1));
                if (operation.methods().contains(method) && regex.matcher(path).matches()) {
                    count += entry.getValue();
                    if (!method.equals("GET") && !method.equals("OPTIONS") && !method.equals("HEAD")) {
                        changingCount += entry.getValue();
                    }
                    if (matches.size() < EXAMPLES) {
                        matches.add(entry.getKey());
                    }
                    if (!tooGeneric) {
                        explained.put(entry.getKey(), true);
                    }
                }
            }
            if (tooGeneric) {
                // A template that is placeholders only matches every observed endpoint, so it proves nothing
                generic++;
            } else if (operation.stateChanging()) {
                // Observed only when a changing method itself was sent; the same template may also be read with GET
                totalChanging++;
                seenChanging += changingCount > 0 ? 1 : 0;
            } else if (operation.methods().contains("GET")) {
                totalGet++;
                seenGet += count > 0 ? 1 : 0;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("httpMethods", operation.methods());
            entry.put("endpoint", operation.endpoint());
            entry.put("stateChanging", operation.stateChanging());
            entry.put("tooGeneric", tooGeneric);
            entry.put("observedCalls", count);
            entry.put("observedChangingCalls", changingCount);
            entry.put("observedExamples", matches);
            operationEntries.add(entry);
            if (!operation.endpoint().startsWith("{REST}") && !matches.isEmpty() && !tooGeneric) {
                Map<String, Object> resolved = new LinkedHashMap<>();
                resolved.put("endpoint", operation.endpoint());
                resolved.put("observedExamples", matches);
                unknownBase.add(resolved);
            }
        }
        List<String> unexplained = explained.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("extractedOperations", operations.size());
        summary.put("tooGenericTemplates", generic);
        summary.put("readOnlyOperationsWithGet", totalGet);
        summary.put("readOnlyOperationsObserved", seenGet);
        summary.put("stateChangingOperations", totalChanging);
        summary.put("stateChangingOperationsObserved", seenChanging);
        summary.put("unknownBaseTemplatesResolved", unknownBase.size());
        summary.put("observedEndpoints", observed.size());
        summary.put("observedEndpointsNotExplainedByTemplates", unexplained.size());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("source", Map.of("operations", operationsYaml.getFileName().toString(),
                "restCalls", restCallsYaml.getFileName().toString()));
        document.put("summary", summary);
        document.put("unknownBaseTemplates", unknownBase);
        document.put("observedEndpointsNotExplainedByTemplates", unexplained);
        document.put("operations", operationEntries);
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/runtime (match-operations). Do not edit by hand.\n"
                        + "Extracted REST endpoint templates matched against the endpoints observed while crawling (screens opened, no buttons pressed).\n"
                        + "Interpretation and notes belong in docs/as-is/rest-bridge.md."),
                StandardCharsets.UTF_8);
    }

    private static List<Operation> readOperations(Path operationsYaml) throws IOException {
        List<Operation> operations = new ArrayList<>();
        boolean inOperations = false;
        boolean inMethods = false;
        List<String> methods = null;
        String endpoint = null;
        for (String line : Files.readAllLines(operationsYaml, StandardCharsets.UTF_8)) {
            if (!line.startsWith(" ")) {
                inOperations = line.equals("operations:");
                continue;
            }
            if (!inOperations) {
                continue;
            }
            if (line.equals("  - httpMethods:")) {
                methods = new ArrayList<>();
                endpoint = null;
                inMethods = true;
            } else if (inMethods && line.startsWith("      - \"")) {
                methods.add(line.substring(9, line.length() - 1));
            } else if (line.startsWith("    endpoint: \"")) {
                inMethods = false;
                endpoint = line.substring(15, line.length() - 1);
            } else if (line.startsWith("    stateChanging: ") && methods != null && endpoint != null) {
                operations.add(new Operation(methods, endpoint, line.endsWith("true")));
                methods = null;
            } else if (!line.startsWith("      ")) {
                inMethods = false;
            }
        }
        return operations;
    }

    private static Map<String, Integer> readObserved(Path restCallsYaml) throws IOException {
        Map<String, Integer> observed = new TreeMap<>();
        boolean inEndpoints = false;
        for (String line : Files.readAllLines(restCallsYaml, StandardCharsets.UTF_8)) {
            if (!line.startsWith(" ")) {
                inEndpoints = line.equals("endpoints:");
                continue;
            }
            Matcher match = OBSERVED.matcher(line);
            if (inEndpoints && match.matches()) {
                observed.merge(match.group(1) + " " + match.group(2), Integer.parseInt(match.group(3)), Integer::sum);
            }
        }
        return observed;
    }

    private static String normalize(String endpoint) {
        String path = endpoint;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return path;
    }

    private static Pattern regex(String template) {
        StringBuilder regex = new StringBuilder();
        Matcher placeholder = PLACEHOLDER.matcher(template);
        int last = 0;
        while (placeholder.find()) {
            regex.append(Pattern.quote(template.substring(last, placeholder.start())));
            String name = placeholder.group();
            if (name.equals("{REST}")) {
                regex.append(Pattern.quote(name));
            } else if (placeholder.start() == 0) {
                regex.append(".*");
            } else {
                regex.append("[^/]*");
            }
            last = placeholder.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        return Pattern.compile(regex.toString());
    }
}
