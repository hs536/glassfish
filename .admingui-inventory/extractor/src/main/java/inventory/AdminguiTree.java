package inventory;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Locations in the GlassFish working tree that the inventories read. */
final class AdminguiTree {

    static final List<String> PAGE_EXTENSIONS = List.of(".jsf", ".inc", ".layout");

    private final Path root;
    private final Path admingui;

    AdminguiTree(Path root) {
        this.root = root;
        this.admingui = root.resolve("appserver").resolve("admingui");
    }

    Path root() {
        return root;
    }

    Path admingui() {
        return admingui;
    }

    /** Module directories under appserver/admingui (those with a pom.xml), sorted by name. */
    List<Path> modules() throws IOException {
        try (Stream<Path> directories = Files.list(admingui)) {
            return directories.filter(d -> Files.isRegularFile(d.resolve("pom.xml"))).sorted().toList();
        }
    }

    /** JSFTemplating page files of a module (outside target/), sorted by path. */
    List<Path> pages(Path module) throws IOException {
        return files(module, PAGE_EXTENSIONS);
    }

    /** Files under a module's src/ whose names end with one of the extensions, sorted by path. */
    List<Path> files(Path module, List<String> extensions) throws IOException {
        Path src = module.resolve("src");
        if (!Files.isDirectory(src)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(src)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> extensions.stream().anyMatch(e -> p.getFileName().toString().endsWith(e)))
                    .sorted((a, b) -> relative(a).compareTo(relative(b)))
                    .toList();
        }
    }

    /** Java sources of a module's main code, sorted by path. */
    List<Path> mainJavaSources(Path module) throws IOException {
        return files(module, List.of(".java")).stream()
                .filter(p -> module.resolve("src/main/java").relativize(p).toString().indexOf("..") < 0)
                .filter(p -> p.startsWith(module.resolve("src/main/java")))
                .toList();
    }

    /** The JSFTemplating jar of the built distribution, which carries the built-in handler definitions. */
    Path distributedJsftemplatingJar() {
        return root.resolve("appserver/distributions/glassfish/target/stage/glassfish8/glassfish/modules/jsftemplating.jar");
    }

    String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    static String read(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        }
    }
}
