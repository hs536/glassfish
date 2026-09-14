package inventory;

import java.nio.file.Path;
import java.util.List;

/**
 * Regenerates inventories from a GlassFish working tree.
 *
 * <p>Usage: {@code java -jar target/inventory-extractor.jar <command> <glassfish repo root> <output yaml>}
 * where command is one of {@code modules}, {@code handlers}, {@code jsft-features}.
 * {@code handlers} needs the admingui modules and the distribution to have been built.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 4 && args[0].equals("worksheet")) {
            new WorksheetExtractor(Path.of(args[1]).toAbsolutePath().normalize())
                    .write(Path.of(args[2]).toAbsolutePath().normalize(), List.of(args).subList(3, args.length));
            return;
        }
        if (args.length != 3) {
            usage();
        }
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        switch (args[0]) {
            case "modules" -> new ModulesExtractor(root).write(output);
            case "handlers" -> new HandlersExtractor(root).write(output);
            case "jsft-features" -> new JsftFeaturesExtractor(root).write(output);
            case "woodstock" -> new WoodstockExtractor(root).write(output);
            case "help-i18n" -> new HelpI18nExtractor(root).write(output);
            case "screens" -> new ScreensExtractor(root).write(output);
            case "operations" -> new OperationsExtractor(root).write(output);
            case "integration-points" -> new IntegrationPointsExtractor(root).write(output);
            case "test-coverage" -> new TestCoverageExtractor(root).write(output);
            case "external-deps" -> new ExternalDepsExtractor(root).write(output);
            default -> usage();
        }
        System.out.println("wrote " + output);
    }

    private static void usage() {
        System.err.println("usage: <modules|handlers|jsft-features|woodstock|help-i18n|screens|operations|integration-points|test-coverage|external-deps> <glassfish repo root> <output yaml>");
        System.exit(2);
    }
}
