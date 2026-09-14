package runtime;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;

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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visits screens of the admin console in a headless Chromium and records what each one shows: HTTP status, final
 * URL, title, error text, console and page errors, failed requests, and the size of the DOM and accessibility tree.
 * The DOM, the accessibility snapshot and, on request, a full-page screenshot are written per screen for later
 * comparison; the summary is written as runtime-observations.yaml.
 */
final class Crawler {

    private static final double NAVIGATION_TIMEOUT = 90_000;
    private static final double IDLE_TIMEOUT = 20_000;
    private static final int LOGIN_ATTEMPTS = 40;
    /** The console answers 202 with a loading page while it is being (re)deployed. */
    private static final int LOADING_RETRIES = 40;
    private static final int LOADING_STATUS = 202;
    /** Consecutive screens without any response after which the server is taken to be down. */
    private static final int ABORT_AFTER_FAILURES = 5;
    /** Discovered links (with a query) opened again for a screen that failed without parameters. */
    private static final int VARIANTS_PER_SCREEN = 2;
    private static final int MAX_FILE_NAME = 150;
    private static final int KEPT_MESSAGES = 3;
    private static final String USERNAME_FIELD = "[id='Login.username']";
    private static final Pattern ERROR_TEXT = Pattern.compile(
            "An error has occurred|HTTP Status 5\\d\\d|(?:java|jakarta|com\\.sun|org\\.glassfish)\\.[\\w.]+(?:Exception|Error)\\b");

    private final String baseUrl;
    private final boolean screenshots;
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> pageErrors = new ArrayList<>();
    private final List<String> failedRequests = new ArrayList<>();
    private String abortedAt;
    /** Screen path to links with a query that point to it, collected from the visited pages. */
    private final Map<String, Set<String>> discovered = new TreeMap<>();

    Crawler(String baseUrl, boolean screenshots) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.screenshots = screenshots;
    }

    void run(Path screensYaml, Path outputDirectory, Set<String> kinds, Path skipFile) throws IOException {
        Map<String, String> skipped = readSkipFile(skipFile);
        List<ScreenList.Screen> targets = ScreenList.read(screensYaml).stream()
                .filter(s -> kinds.contains(s.kind()) && s.url().endsWith(".jsf") && !skipped.containsKey(s.url()))
                .sorted((a, b) -> a.url().compareTo(b.url()))
                .toList();
        Files.createDirectories(outputDirectory.resolve("dom"));
        Files.createDirectories(outputDirectory.resolve("aria"));
        if (screenshots) {
            Files.createDirectories(outputDirectory.resolve("screenshots"));
        }

        List<Map<String, Object>> observations = new ArrayList<>();
        List<Map<String, Object>> variants = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> variantCounts = new TreeMap<>();
        String browserVersion;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            browserVersion = browser.version();
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1280, 1024).setLocale("en-US").setTimezoneId("UTC"));
            Page page = context.newPage();
            page.onConsoleMessage(message -> {
                if ("error".equals(message.type())) {
                    consoleErrors.add(message.text());
                }
            });
            page.onPageError(error -> pageErrors.add(firstLine(error)));
            page.onResponse(response -> {
                if (response.status() >= 400) {
                    failedRequests.add(response.status() + " " + relative(response.url()));
                }
            });
            page.onRequestFailed(request -> failedRequests.add("failed " + relative(request.url()) + " " + request.failure()));

            login(page);
            int consecutiveFailures = 0;
            for (ScreenList.Screen target : targets) {
                Map<String, Object> observation = observe(page, target, outputDirectory);
                if (Boolean.TRUE.equals(observation.get("redirectedToLogin"))) {
                    login(page);
                    observation = observe(page, target, outputDirectory);
                }
                count(counts, observation);
                observations.add(observation);
                System.out.println(observation.get("status") + " " + target.url());
                consecutiveFailures = observation.get("status") == null ? consecutiveFailures + 1 : 0;
                if (consecutiveFailures >= ABORT_AFTER_FAILURES) {
                    abortedAt = target.url();
                    System.out.println("aborted: no response for " + ABORT_AFTER_FAILURES + " screens in a row");
                    break;
                }
            }

            // Second pass: screens that failed or showed an error are opened again through real links with parameters
            if (abortedAt == null) {
                for (Map<String, Object> first : observations) {
                    boolean problem = !Integer.valueOf(200).equals(first.get("status")) || first.get("errorText") != null;
                    String url = (String) first.get("url");
                    if (!problem) {
                        continue;
                    }
                    int opened = 0;
                    for (String link : discovered.getOrDefault(url, Set.of())) {
                        if (opened++ == VARIANTS_PER_SCREEN) {
                            break;
                        }
                        ScreenList.Screen variant = new ScreenList.Screen(link, (String) first.get("module"), (String) first.get("kind"), true);
                        Map<String, Object> observation = observe(page, variant, outputDirectory);
                        observation.put("variantOf", url);
                        count(variantCounts, observation);
                        variants.add(observation);
                        System.out.println(observation.get("status") + " " + link);
                    }
                }
            }
            browser.close();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("targets", targets.size());
        summary.put("visited", observations.size());
        summary.put("abortedAt", abortedAt);
        summary.putAll(counts);
        summary.put("skipped", skipped);
        Map<String, Object> variantSummary = new LinkedHashMap<>();
        variantSummary.put("visited", variants.size());
        variantSummary.putAll(variantCounts);
        summary.put("variants", variantSummary);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("baseUrl", baseUrl);
        source.put("browser", "chromium " + browserVersion);
        source.put("screenKinds", new ArrayList<>(new TreeSet<>(kinds)));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("source", source);
        document.put("summary", summary);
        document.put("screens", observations);
        document.put("variants", variants);
        Files.writeString(outputDirectory.resolve("runtime-observations.yaml"), Yaml.write(document,
                "Generated by tools/runtime (crawl). Do not edit by hand.\n"
                        + "Raw DOM, accessibility snapshots and screenshots are next to this file and are not committed.\n"
                        + "Interpretation and notes belong in docs/as-is/runtime-observations.md."),
                StandardCharsets.UTF_8);
    }

    private void login(Page page) {
        for (int attempt = 0; attempt < LOGIN_ATTEMPTS; attempt++) {
            try {
                page.navigate(baseUrl + "/", new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT));
                waitForIdle(page);
                if (page.locator(USERNAME_FIELD).count() > 0) {
                    page.locator(USERNAME_FIELD).fill("admin");
                    page.locator("[id='Login.password']").fill("");
                    page.locator("#loginButton").click();
                    waitForIdle(page);
                }
                // Logged in only when a console page answers 200 without showing the login form
                Response check = page.navigate(baseUrl + "/common/index.jsf", new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT));
                waitForIdle(page);
                if (check != null && check.status() == 200 && page.locator(USERNAME_FIELD).count() == 0) {
                    return;
                }
            } catch (PlaywrightException e) {
                System.out.println("login attempt " + attempt + ": " + summarize(e.getMessage()));
            }
            // The console is deployed lazily and answers with a loading page at first
            page.waitForTimeout(3_000);
        }
        throw new IllegalStateException("Could not log in to " + baseUrl);
    }

    private Map<String, Object> observe(Page page, ScreenList.Screen target, Path outputDirectory) throws IOException {
        consoleErrors.clear();
        pageErrors.clear();
        failedRequests.clear();

        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("url", target.url());
        observation.put("module", target.module());
        observation.put("kind", target.kind());
        Integer status = null;
        String navigationError = null;
        int loadingRetries = 0;
        for (; loadingRetries < LOADING_RETRIES; loadingRetries++) {
            try {
                Response response = page.navigate(baseUrl + target.url(), new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT));
                status = response == null ? null : response.status();
                navigationError = null;
            } catch (PlaywrightException e) {
                status = null;
                navigationError = summarize(e.getMessage());
            }
            if (status == null || status != LOADING_STATUS) {
                break;
            }
            page.waitForTimeout(3_000);
        }
        boolean idleTimeout = !waitForIdle(page);

        String bodyText = safe(() -> page.locator("body").innerText(), "");
        Matcher error = ERROR_TEXT.matcher(bodyText);
        // Woodstock alerts carry the summary and detail in the alt text of the alert image
        String alertText = safe(() -> {
            var image = page.locator("img[id$='pageAlert:alertImage']");
            return image.count() > 0 ? image.first().getAttribute("alt") : null;
        }, null);
        collectLinks(page);
        String html = safe(page::content, "");
        String aria = safe(() -> page.locator("body").ariaSnapshot(), "");
        String name = fileName(target.url());
        Files.writeString(outputDirectory.resolve("dom").resolve(name + ".html"), html, StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("aria").resolve(name + ".yml"), aria, StandardCharsets.UTF_8);
        if (screenshots) {
            safe(() -> page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputDirectory.resolve("screenshots").resolve(name + ".png")).setFullPage(true)), null);
        }

        observation.put("status", status);
        observation.put("finalUrl", relative(page.url()));
        observation.put("title", safe(page::title, null));
        observation.put("redirectedToLogin", page.locator(USERNAME_FIELD).count() > 0);
        observation.put("navigationError", navigationError);
        observation.put("loadingRetries", loadingRetries);
        observation.put("networkIdleTimeout", idleTimeout);
        observation.put("errorText", error.find() ? error.group() : null);
        observation.put("alertText", alertText);
        observation.put("consoleErrors", consoleErrors.size());
        observation.put("consoleErrorSamples", new ArrayList<>(consoleErrors.subList(0, Math.min(KEPT_MESSAGES, consoleErrors.size()))));
        observation.put("pageErrors", new ArrayList<>(pageErrors.subList(0, Math.min(KEPT_MESSAGES, pageErrors.size()))));
        observation.put("failedRequests", new ArrayList<>(failedRequests.subList(0, Math.min(KEPT_MESSAGES, failedRequests.size()))));
        observation.put("domBytes", html.getBytes(StandardCharsets.UTF_8).length);
        observation.put("ariaLines", aria.lines().count());
        return observation;
    }

    private static boolean waitForIdle(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(IDLE_TIMEOUT));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private static void count(Map<String, Integer> counts, Map<String, Object> observation) {
        Object status = observation.get("status");
        String statusClass = status == null ? "noResponse" : "status" + (((Integer) status) / 100) + "xx";
        counts.merge(statusClass, 1, Integer::sum);
        increment(counts, "redirectedToLogin", Boolean.TRUE.equals(observation.get("redirectedToLogin")));
        increment(counts, "withNavigationError", observation.get("navigationError") != null);
        increment(counts, "withNetworkIdleTimeout", Boolean.TRUE.equals(observation.get("networkIdleTimeout")));
        increment(counts, "withErrorText", observation.get("errorText") != null);
        increment(counts, "withConsoleErrors", ((Integer) observation.get("consoleErrors")) > 0);
        increment(counts, "withPageErrors", !((List<?>) observation.get("pageErrors")).isEmpty());
        increment(counts, "withFailedRequests", !((List<?>) observation.get("failedRequests")).isEmpty());
    }

    private static void increment(Map<String, Integer> counts, String key, boolean condition) {
        counts.merge(key, condition ? 1 : 0, Integer::sum);
    }

    private String relative(String url) {
        return url.startsWith(baseUrl) ? url.substring(baseUrl.length()) : url;
    }

    private static String fileName(String url) {
        String name = url.replaceAll("^/", "").replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() <= MAX_FILE_NAME ? name : name.substring(0, MAX_FILE_NAME - 10) + "_" + Integer.toHexString(url.hashCode());
    }

    /** Records same-origin links to .jsf screens that carry a query, keyed by the screen path. */
    private void collectLinks(Page page) {
        Object hrefs = safe(() -> page.locator("a[href]").evaluateAll("elements => elements.map(e => e.href)"), null);
        if (!(hrefs instanceof List<?> list)) {
            return;
        }
        for (Object href : list) {
            String link = relative(String.valueOf(href));
            int query = link.indexOf('?');
            int hash = link.indexOf('#');
            if (!link.startsWith("/") || query < 0 || (hash >= 0 && hash < query)) {
                continue;
            }
            String path = link.substring(0, query);
            if (path.endsWith(".jsf")) {
                discovered.computeIfAbsent(path, k -> new TreeSet<>()).add(hash < 0 ? link : link.substring(0, hash));
            }
        }
    }

    /** The informative part of a Playwright error: the line naming the network error or timeout, else the first line. */
    private static String summarize(String message) {
        if (message == null) {
            return null;
        }
        String chosen = message.lines().map(String::strip)
                .filter(line -> line.contains("net::") || line.contains("Timeout") || line.contains("message="))
                .findFirst().orElse(firstLine(message).strip());
        return chosen.length() > 200 ? chosen.substring(0, 200) : chosen;
    }

    private static Map<String, String> readSkipFile(Path skipFile) throws IOException {
        Map<String, String> skipped = new TreeMap<>();
        if (skipFile == null) {
            return skipped;
        }
        for (String line : Files.readAllLines(skipFile, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int hash = trimmed.indexOf('#');
            String url = (hash < 0 ? trimmed : trimmed.substring(0, hash)).strip();
            skipped.put(url, hash < 0 ? "" : trimmed.substring(hash + 1).strip());
        }
        return skipped;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return null;
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static <T> T safe(Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (PlaywrightException e) {
            return fallback;
        }
    }

}
