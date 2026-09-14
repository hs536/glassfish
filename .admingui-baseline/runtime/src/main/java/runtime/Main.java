package runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Runtime observation of the admin console (調査計画.md S11).
 *
 * <pre>
 * fixture-create &lt;glassfish install dir&gt; &lt;domain&gt; &lt;port base&gt;
 * fixture-delete &lt;glassfish install dir&gt; &lt;domain&gt;
 * crawl &lt;console base url&gt; &lt;inventory/screens.yaml&gt; &lt;output dir&gt; [--screenshots] [--kinds page,integration] [--skip-file tools/runtime/skip-screens.txt]
 * </pre>
 *
 * The glassfish install dir is the directory containing {@code bin/asadmin} (for example
 * {@code .../stage/glassfish8/glassfish}).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        }
        switch (args[0]) {
            case "fixture-create" -> {
                require(args, 4);
                new Fixture(Path.of(args[1])).create(args[2], Integer.parseInt(args[3]));
            }
            case "fixture-delete" -> {
                require(args, 3);
                new Fixture(Path.of(args[1])).delete(args[2]);
            }
            case "crawl" -> {
                require(args, 4);
                List<String> options = List.of(args).subList(4, args.length);
                boolean screenshots = options.contains("--screenshots");
                Set<String> kinds = Set.of("page");
                int kindsIndex = options.indexOf("--kinds");
                if (kindsIndex >= 0 && kindsIndex + 1 < options.size()) {
                    kinds = Set.of(options.get(kindsIndex + 1).split(","));
                }
                Path skipFile = null;
                int skipIndex = options.indexOf("--skip-file");
                if (skipIndex >= 0 && skipIndex + 1 < options.size()) {
                    skipFile = Path.of(options.get(skipIndex + 1));
                }
                new Crawler(args[1], screenshots).run(Path.of(args[2]), Path.of(args[3]), kinds, skipFile);
            }
            default -> usage();
        }
    }

    private static void require(String[] args, int count) {
        if (args.length < count) {
            usage();
        }
    }

    private static void usage() {
        System.err.println("usage: fixture-create <glassfish dir> <domain> <port base>"
                + " | fixture-delete <glassfish dir> <domain>"
                + " | crawl <base url> <screens.yaml> <output dir> [--screenshots] [--kinds page,integration] [--skip-file <file>]");
        System.exit(2);
    }
}
