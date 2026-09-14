package runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assigns the management REST requests the console sends from the server to the screen that caused them (課題 V-05,
 * V-09).
 *
 * <p>The management REST requests do not appear in the access log of the admin listener. With the logger
 * {@code org.glassfish.admingui} at FINEST, {@code RestUtil.restRequest} writes each request to server.log
 * ("restRequest: endpoint=... attrs=... method=..."). The crawler records when it started and finished each screen
 * ({@code startedAt}, {@code finishedAt}), and each logged request is assigned to the screen whose interval contains
 * it. Requests the console sends without {@code RestUtil.restRequest} are not logged and do not appear here.
 */
final class RestCalls {

    /** Start of a server.log record: the timestamp, the level, and the logger. */
    private static final Pattern RECORD = Pattern.compile("^\\[([0-9T:.+\\-Z]+)\\] \\[[^\\]]*\\] \\[([A-Z]+)\\] \\[[^\\]]*\\] \\[([^\\]]*)\\]");
    private static final Pattern REQUEST = Pattern.compile("restRequest: endpoint=(\\S+)");
    /** A request line of the Jersey LoggingFeature that the management REST service registers: "1 > GET http://...". */
    private static final Pattern SERVER_REQUEST = Pattern.compile("^\\d+ > (GET|POST|PUT|DELETE|OPTIONS|HEAD) (\\S+)");
    private static final Pattern METHOD = Pattern.compile("^method=(\\w+)");
    private static final Pattern OBSERVATION_URL = Pattern.compile("^  - url: \"(.*)\"$");
    private static final Pattern STARTED = Pattern.compile("^    startedAt: (\\d+)$");
    private static final Pattern FINISHED = Pattern.compile("^    finishedAt: (\\d+)$");
    private static final Pattern BASE = Pattern.compile("^https?://[^/]+/(management|monitoring)/domain");

    private record Call(long time, String method, String endpoint) {
    }

    private record Screen(String url, long startedAt, long finishedAt, List<Call> calls) {
    }

    private RestCalls() {
    }

    static void write(Path serverLog, Path output, List<Path> observationFiles) throws IOException {
        // A directory means the logs directory of the domain: the rotated server.log_<time> files, oldest first, then
        // server.log. Request logging at FINE fills the log quickly, so it rotates during a crawl.
        List<Path> logFiles = new ArrayList<>();
        if (Files.isDirectory(serverLog)) {
            try (var list = Files.list(serverLog)) {
                list.map(p -> p.getFileName().toString())
                        .filter(n -> n.startsWith("server.log_"))
                        .sorted()
                        .forEach(n -> logFiles.add(serverLog.resolve(n)));
            }
            logFiles.add(serverLog.resolve("server.log"));
        } else {
            logFiles.add(serverLog);
        }
        List<Call> clientCalls = new ArrayList<>();
        List<Call> serverCalls = new ArrayList<>();
        for (Path file : logFiles) {
            clientCalls.addAll(readCalls(file));
            serverCalls.addAll(readServerCalls(file));
        }
        // Requests received by the REST service cover every client path; the console's own log only RestUtil.restRequest
        List<Call> calls = serverCalls.isEmpty() ? clientCalls : serverCalls;
        List<Screen> screens = new ArrayList<>();
        for (Path file : observationFiles) {
            screens.addAll(readScreens(file));
        }
        screens.sort((a, b) -> Long.compare(a.startedAt(), b.startedAt()));

        int unassigned = 0;
        for (Call call : calls) {
            Screen owner = null;
            for (Screen screen : screens) {
                if (call.time() >= screen.startedAt() && call.time() <= screen.finishedAt()) {
                    owner = screen;
                    break;
                }
            }
            if (owner == null) {
                unassigned++;
            } else {
                owner.calls().add(call);
            }
        }

        Map<String, Integer> byMethod = new TreeMap<>();
        Map<String, Integer> endpoints = new TreeMap<>();
        TreeSet<String> changingScreens = new TreeSet<>();
        TreeSet<String> optionsScreens = new TreeSet<>();
        List<Map<String, Object>> screenEntries = new ArrayList<>();
        for (Screen screen : screens) {
            List<String> lines = new ArrayList<>();
            for (Call call : screen.calls()) {
                String template = template(call.endpoint());
                lines.add(call.method() + " " + template);
                byMethod.merge(call.method(), 1, Integer::sum);
                endpoints.merge(call.method() + " " + withoutQuery(template), 1, Integer::sum);
                if (call.method().equals("OPTIONS")) {
                    optionsScreens.add(screen.url());
                } else if (!call.method().equals("GET") && !call.method().equals("HEAD")) {
                    changingScreens.add(screen.url());
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("url", screen.url());
            entry.put("restCalls", lines);
            screenEntries.add(entry);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("screens", screens.size());
        summary.put("callSource", serverCalls.isEmpty() ? "RestUtil.restRequest (org.glassfish.admingui FINEST)"
                : "requests received by the REST service (org.glassfish.jersey.logging.LoggingFeature FINE)");
        summary.put("requestsReceivedByRestService", serverCalls.size());
        summary.put("requestsLoggedByRestUtilRestRequest", clientCalls.size());
        summary.put("loggedRestCalls", calls.size());
        summary.put("assigned", calls.size() - unassigned);
        summary.put("unassigned", unassigned);
        summary.put("byMethod", byMethod);
        summary.put("distinctEndpoints", endpoints.size());
        summary.put("screensWithRestCalls", screens.stream().filter(s -> !s.calls().isEmpty()).count());
        summary.put("screensSendingOptions", optionsScreens.size());
        summary.put("screensSendingPostPutDelete", new ArrayList<>(changingScreens));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("serverLogs", logFiles.stream().map(p -> p.getFileName().toString()).toList());
        source.put("observations", observationFiles.stream().map(p -> p.toString().replace('\\', '/')).toList());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("source", source);
        document.put("summary", summary);
        document.put("endpoints", endpoints);
        document.put("screens", screenEntries);
        Files.writeString(output, Yaml.write(document,
                "Generated by tools/runtime (rest-calls). Do not edit by hand.\n"
                        + "Management REST requests logged by RestUtil.restRequest (FINEST), assigned to the screen being opened.\n"
                        + "{REST} is .../management/domain, {MONITOR} is .../monitoring/domain.\n"
                        + "Interpretation and notes belong in docs/as-is/runtime-observations.md."),
                StandardCharsets.UTF_8);
    }

    private static List<Call> readCalls(Path serverLog) throws IOException {
        List<Call> calls = new ArrayList<>();
        long time = 0;
        boolean console = false;
        String endpoint = null;
        for (String line : Files.readAllLines(serverLog, StandardCharsets.UTF_8)) {
            Matcher record = RECORD.matcher(line);
            if (record.find()) {
                time = OffsetDateTime.parse(record.group(1)).toInstant().toEpochMilli();
                console = record.group(3).equals("org.glassfish.admingui");
                endpoint = null;
                continue;
            }
            if (!console) {
                continue;
            }
            Matcher request = REQUEST.matcher(line);
            if (request.find()) {
                endpoint = request.group(1);
                continue;
            }
            Matcher method = METHOD.matcher(line.strip());
            if (endpoint != null && method.find()) {
                calls.add(new Call(time, method.group(1).toUpperCase(), endpoint));
                endpoint = null;
            }
        }
        return calls;
    }

    private static List<Call> readServerCalls(Path serverLog) throws IOException {
        List<Call> calls = new ArrayList<>();
        long time = 0;
        for (String line : Files.readAllLines(serverLog, StandardCharsets.UTF_8)) {
            Matcher record = RECORD.matcher(line);
            if (record.find()) {
                time = OffsetDateTime.parse(record.group(1)).toInstant().toEpochMilli();
                continue;
            }
            Matcher request = SERVER_REQUEST.matcher(line.strip());
            if (request.find()) {
                calls.add(new Call(time, request.group(1), request.group(2)));
            }
        }
        return calls;
    }

    private static List<Screen> readScreens(Path observations) throws IOException {
        List<Screen> screens = new ArrayList<>();
        String url = null;
        long started = -1;
        for (String line : Files.readAllLines(observations, StandardCharsets.UTF_8)) {
            Matcher start = OBSERVATION_URL.matcher(line);
            if (start.matches()) {
                url = start.group(1);
                started = -1;
                continue;
            }
            Matcher begin = STARTED.matcher(line);
            if (begin.matches()) {
                started = Long.parseLong(begin.group(1));
                continue;
            }
            Matcher end = FINISHED.matcher(line);
            if (end.matches() && url != null && started >= 0) {
                screens.add(new Screen(url, started, Long.parseLong(end.group(1)), new ArrayList<>()));
                url = null;
            }
        }
        return screens;
    }

    private static String template(String endpoint) {
        Matcher base = BASE.matcher(endpoint);
        if (base.find()) {
            return (base.group(1).equals("management") ? "{REST}" : "{MONITOR}") + endpoint.substring(base.end());
        }
        return endpoint;
    }

    private static String withoutQuery(String path) {
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }
}
