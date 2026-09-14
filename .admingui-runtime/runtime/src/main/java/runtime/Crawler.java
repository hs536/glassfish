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
    /** The browser locale, which the console uses to choose the display language. */
    private final String locale;
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> pageErrors = new ArrayList<>();
    private final List<String> failedRequests = new ArrayList<>();
    private String abortedAt;
    /** Screen path to links with a query that point to it, collected from the visited pages. */
    private final Map<String, Set<String>> discovered = new TreeMap<>();

    Crawler(String baseUrl, boolean screenshots) {
        this(baseUrl, screenshots, "en-US");
    }

    Crawler(String baseUrl, boolean screenshots, String locale) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.screenshots = screenshots;
        this.locale = locale;
    }

    void run(Path screensYaml, Path outputDirectory, Set<String> kinds, Path skipFile) throws IOException {
        Map<String, String> skipped = readSkipFile(skipFile);
        List<ScreenList.Screen> targets = ScreenList.read(screensYaml).stream()
                .filter(s -> kinds.contains(s.kind()) && s.url().replaceFirst("\\?.*", "").matches(".*\\.(jsf|xhtml)")
                        && !skipped.containsKey(s.url()))
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
                    .setViewportSize(1280, 1024).setLocale(locale).setTimezoneId("UTC"));
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

    /**
     * Opens each URL listed in a text file (one per line; blank lines and lines starting with # are ignored, and text
     * after " #" is a comment) and records the same observation as {@link #run}. Used for screens that need query
     * parameters built from the names of the test domain (docs/as-is/runtime-observations.md, 課題 V-12).
     */
    void runUrls(Path urlsFile, Path outputDirectory) throws IOException {
        runUrls(urlsFile, outputDirectory, false);
    }

    /**
     * Same as {@link #runUrls(Path, Path)}; with {@code ajax}, each URL is loaded into the content area of
     * /common/index.jsf with admingui.ajax.loadPage, the way the tree, tabs and buttons of the console open screens,
     * instead of being opened directly. Problems that only show when a screen is opened directly (B-19, B-21) do not
     * appear in that mode.
     */
    void runUrls(Path urlsFile, Path outputDirectory, boolean ajax) throws IOException {
        List<String> urls = new ArrayList<>();
        for (String line : Files.readAllLines(urlsFile, StandardCharsets.UTF_8)) {
            int comment = line.indexOf(" #");
            String url = (comment < 0 ? line : line.substring(0, comment)).strip();
            if (!url.isEmpty() && !url.startsWith("#")) {
                urls.add(url);
            }
        }
        Files.createDirectories(outputDirectory.resolve("dom"));
        Files.createDirectories(outputDirectory.resolve("aria"));
        if (screenshots) {
            Files.createDirectories(outputDirectory.resolve("screenshots"));
        }
        List<Map<String, Object>> observations = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        String browserVersion;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            browserVersion = browser.version();
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1280, 1024).setLocale(locale).setTimezoneId("UTC"));
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
            for (String url : urls) {
                ScreenList.Screen target = new ScreenList.Screen(url, null, "page", true);
                Map<String, Object> observation = ajax ? observeLoaded(page, url, outputDirectory) : observe(page, target, outputDirectory);
                if (Boolean.TRUE.equals(observation.get("redirectedToLogin"))) {
                    login(page);
                    observation = ajax ? observeLoaded(page, url, outputDirectory) : observe(page, target, outputDirectory);
                }
                observation.put("variantOf", url.contains("?") ? url.substring(0, url.indexOf('?')) : url);
                count(counts, observation);
                observations.add(observation);
                System.out.println(observation.get("status") + " " + (observation.get("errorText") == null ? "ok " : "ng ") + url);
            }
            browser.close();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("urls", urls.size());
        summary.putAll(counts);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("baseUrl", baseUrl);
        source.put("browser", "chromium " + browserVersion);
        source.put("urlsFile", urlsFile.getFileName().toString());
        source.put("mode", ajax ? "loaded into /common/index.jsf with admingui.ajax.loadPage" : "opened directly");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("source", source);
        document.put("summary", summary);
        document.put("urls", observations);
        Files.writeString(outputDirectory.resolve("url-observations.yaml"), Yaml.write(document,
                "Generated by tools/runtime (crawl-urls). Do not edit by hand.\n"
                        + "Interpretation and notes belong in docs/as-is/runtime-observations.md."),
                StandardCharsets.UTF_8);
    }

    /**
     * Opens the navigation tree on /common/index.jsf and clicks each tree link that is present, recording where the
     * click leads and what the resulting page shows. Unlike {@link #run}, the pages are reached as a user reaches
     * them, so script errors and alerts that depend on the navigation path show up here.
     */
    void runTree(Path outputDirectory, Path skipFile, String onlyPath) throws IOException {
        Map<String, String> skipped = readSkipFile(skipFile);
        Files.createDirectories(outputDirectory);
        List<Map<String, Object>> observations = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1280, 1024).setLocale(locale).setTimezoneId("UTC"));
            Page page = context.newPage();
            page.onPageError(error -> pageErrors.add(firstLine(error)));
            page.onConsoleMessage(message -> {
                if ("error".equals(message.type())) {
                    consoleErrors.add(message.text());
                }
            });
            login(page);

            Object found = page.locator("a[id^='treeForm:tree'][id$='_link']")
                    .evaluateAll("elements => elements.map(e => e.id + ' ' + e.getAttribute('href'))");
            Map<String, String> links = new TreeMap<>();
            if (found instanceof List<?> list) {
                for (Object item : list) {
                    String[] parts = String.valueOf(item).split(" ", 2);
                    links.putIfAbsent(parts[0], parts.length > 1 ? parts[1] : null);
                }
            }
            for (Map.Entry<String, String> link : links.entrySet()) {
                String href = link.getValue() == null ? "" : relative(link.getValue());
                String path = href.contains("?") ? href.substring(0, href.indexOf('?')) : href;
                if (skipped.containsKey(path) || (onlyPath != null && !path.contains(onlyPath))) {
                    continue;
                }
                consoleErrors.clear();
                pageErrors.clear();
                Map<String, Object> observation = new LinkedHashMap<>();
                observation.put("treeLinkId", link.getKey());
                observation.put("href", href);
                String clickError = null;
                Integer ajaxStatus = null;
                try {
                    page.navigate(baseUrl + "/common/index.jsf", new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT));
                    waitForIdle(page);
                    // Clearing the errors of the index page keeps only those of the page the click leads to
                    consoleErrors.clear();
                    pageErrors.clear();
                    // Nodes inside collapsed branches are not visible; dispatch the click to the link itself. The
                    // console loads the target into #content with Ajax (admingui.ajax.loadPage), so the URL does
                    // not change: wait for that response, then let the inserted content run its scripts
                    Runnable click = () -> page.locator("[id='" + link.getKey() + "']").dispatchEvent("click");
                    if (path.isEmpty()) {
                        click.run();
                    } else {
                        Response response = page.waitForResponse(r -> r.url().contains(path),
                                new Page.WaitForResponseOptions().setTimeout(20_000), click);
                        ajaxStatus = response.status();
                    }
                    waitForIdle(page);
                    page.waitForTimeout(1_500);
                } catch (PlaywrightException e) {
                    clickError = summarize(e.getMessage());
                }
                observation.put("ajaxStatus", ajaxStatus);
                String bodyText = safe(() -> page.locator("body").innerText(), "");
                Matcher error = ERROR_TEXT.matcher(bodyText);
                String alertText = safe(() -> {
                    var image = page.locator("img[id$='pageAlert:alertImage']");
                    return image.count() > 0 ? image.first().getAttribute("alt") : null;
                }, null);
                observation.put("clickError", clickError);
                observation.put("finalUrl", relative(page.url()));
                observation.put("title", safe(page::title, null));
                observation.put("contentHeading", safe(() -> {
                    var heading = page.locator("#content h1");
                    return heading.count() > 0 ? heading.first().innerText().strip() : null;
                }, null));
                observation.put("errorText", error.find() ? error.group() : null);
                observation.put("alertText", alertText);
                observation.put("pageErrors", new ArrayList<>(pageErrors.subList(0, Math.min(KEPT_MESSAGES, pageErrors.size()))));
                observation.put("consoleErrors", consoleErrors.size());
                increment(counts, "withClickError", clickError != null);
                increment(counts, "withErrorText", observation.get("errorText") != null);
                increment(counts, "withAlert", alertText != null);
                increment(counts, "withPageErrors", !pageErrors.isEmpty());
                observations.add(observation);
                System.out.println((clickError == null ? "ok " : "ng ") + link.getKey() + " -> " + observation.get("finalUrl"));
            }
            browser.close();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("treeLinks", observations.size());
        summary.putAll(counts);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("summary", summary);
        document.put("treeLinks", observations);
        Files.writeString(outputDirectory.resolve("tree-observations.yaml"), Yaml.write(document,
                "Generated by tools/runtime (crawl-tree). Do not edit by hand.\n"
                        + "Each tree link present on /common/index.jsf is clicked from the index page."),
                StandardCharsets.UTF_8);
    }

    void login(Page page) {
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
        // Epoch milliseconds, so that server-side log entries can be assigned to the screen (RestCalls)
        observation.put("startedAt", System.currentTimeMillis());
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
        observation.put("finishedAt", System.currentTimeMillis());
        return observation;
    }

    /** Loads the URL into the content area of the index page with the console's own Ajax navigation. */
    private Map<String, Object> observeLoaded(Page page, String url, Path outputDirectory) throws IOException {
        consoleErrors.clear();
        pageErrors.clear();
        failedRequests.clear();
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("url", url);
        observation.put("startedAt", System.currentTimeMillis());
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        Integer status = null;
        String navigationError = null;
        try {
            page.navigate(baseUrl + "/common/index.jsf", new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT));
            waitForIdle(page);
            consoleErrors.clear();
            pageErrors.clear();
            failedRequests.clear();
            Response response = page.waitForResponse(r -> r.url().contains(path),
                    new Page.WaitForResponseOptions().setTimeout(30_000),
                    () -> page.evaluate("url => admingui.ajax.loadPage({url: url})", baseUrl + url));
            status = response.status();
            waitForIdle(page);
            page.waitForTimeout(1_500);
        } catch (PlaywrightException e) {
            navigationError = summarize(e.getMessage());
        }
        String bodyText = safe(() -> page.locator("#content").innerText(), "");
        Matcher error = ERROR_TEXT.matcher(bodyText);
        String alertText = safe(() -> {
            var image = page.locator("#content img[id$='pageAlert:alertImage']");
            return image.count() > 0 ? image.first().getAttribute("alt") : null;
        }, null);
        String html = safe(() -> page.locator("#content").innerHTML(), "");
        String aria = safe(() -> page.locator("#content").ariaSnapshot(), "");
        String name = "ajax_" + fileName(url);
        Files.writeString(outputDirectory.resolve("dom").resolve(name + ".html"), html, StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("aria").resolve(name + ".yml"), aria, StandardCharsets.UTF_8);
        if (screenshots) {
            safe(() -> page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputDirectory.resolve("screenshots").resolve(name + ".png")).setFullPage(true)), null);
        }
        observation.put("status", status);
        observation.put("finalUrl", relative(page.url()));
        observation.put("title", safe(page::title, null));
        observation.put("contentHeading", safe(() -> {
            var heading = page.locator("#content h1");
            return heading.count() > 0 ? heading.first().innerText().strip() : null;
        }, null));
        observation.put("redirectedToLogin", page.locator(USERNAME_FIELD).count() > 0);
        observation.put("navigationError", navigationError);
        observation.put("loadingRetries", 0);
        observation.put("networkIdleTimeout", false);
        observation.put("errorText", error.find() ? error.group() : null);
        observation.put("alertText", alertText);
        observation.put("consoleErrors", consoleErrors.size());
        observation.put("consoleErrorSamples", new ArrayList<>(consoleErrors.subList(0, Math.min(KEPT_MESSAGES, consoleErrors.size()))));
        observation.put("pageErrors", new ArrayList<>(pageErrors.subList(0, Math.min(KEPT_MESSAGES, pageErrors.size()))));
        observation.put("failedRequests", new ArrayList<>(failedRequests.subList(0, Math.min(KEPT_MESSAGES, failedRequests.size()))));
        observation.put("domBytes", html.getBytes(StandardCharsets.UTF_8).length);
        observation.put("ariaLines", aria.lines().count());
        observation.put("finishedAt", System.currentTimeMillis());
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
