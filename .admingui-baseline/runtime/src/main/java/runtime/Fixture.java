package runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates and deletes a disposable test domain with representative configuration, so that list and edit screens
 * have something to show. The console changes configuration when some screens are merely opened
 * (docs/as-is/rest-bridge.md, chapter 3), so observations must not run against a domain that matters.
 *
 * <p>Every asadmin command is run and logged; a failing command does not stop the others, because the purpose is
 * observation, and the log records what exists.
 */
final class Fixture {

    private final Path glassfishHome;

    Fixture(Path glassfishHome) {
        this.glassfishHome = glassfishHome;
    }

    void create(String domain, int portBase) throws IOException, InterruptedException {
        asadmin(List.of("--user", "admin", "create-domain", "--nopassword=true", "--portbase", String.valueOf(portBase), domain));
        asadmin(List.of("start-domain", domain));

        String port = String.valueOf(portBase + 48);
        String node = "localhost-" + domain;
        Path war = sampleWar();
        List<List<String>> commands = List.of(
                List.of("create-system-properties", "baseline.property=baseline"),
                List.of("create-jdbc-connection-pool", "--datasourceclassname", "org.apache.derby.jdbc.ClientDataSource",
                        "--restype", "javax.sql.DataSource",
                        "--property", "portNumber=1527:serverName=localhost:databaseName=baseline:user=APP:password=APP",
                        "baselinePool"),
                List.of("create-jdbc-resource", "--connectionpoolid", "baselinePool", "jdbc/baseline"),
                List.of("create-jms-resource", "--restype", "jakarta.jms.Queue", "--property", "Name=BaselineQueue", "jms/baselineQueue"),
                List.of("create-jms-resource", "--restype", "jakarta.jms.ConnectionFactory", "jms/baselineFactory"),
                List.of("create-mail-resource", "--mailhost", "localhost", "--mailuser", "baseline",
                        "--fromaddress", "baseline@localhost", "mail/baseline"),
                List.of("create-custom-resource", "--restype", "java.lang.String",
                        "--factoryclass", "org.glassfish.resources.custom.factory.PrimitivesAndStringFactory",
                        "--property", "value=baseline", "custom/baseline"),
                List.of("create-managed-executor-service", "concurrent/baselineExecutor"),
                List.of("create-cluster", "baselineCluster"),
                List.of("create-instance", "--node", node, "--cluster", "baselineCluster", "baselineClusterInstance"),
                List.of("create-instance", "--node", node, "baselineStandalone"),
                List.of("deploy", "--name", "baselineApp", "--contextroot", "baselineApp", war.toString()));
        for (List<String> command : commands) {
            List<String> withPort = new ArrayList<>(List.of("--user", "admin", "--port", port));
            withPort.addAll(command);
            asadmin(withPort);
        }
        System.out.println("fixture ready: http://localhost:" + port + "/");
    }

    void delete(String domain) throws IOException, InterruptedException {
        asadmin(List.of("stop-domain", domain));
        asadmin(List.of("delete-domain", domain));
    }

    private void asadmin(List<String> arguments) throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        Path executable = glassfishHome.resolve("bin").resolve(windows ? "asadmin.bat" : "asadmin");
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putIfAbsent("AS_JAVA", System.getProperty("java.home"));
        System.out.println("> asadmin " + String.join(" ", arguments));
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        output.lines().forEach(line -> System.out.println("  " + line));
        System.out.println("  exit " + exit);
    }

    /** A minimal web application with one static page, created on the fly. */
    private static Path sampleWar() throws IOException {
        Path war = Files.createTempFile("baselineApp", ".war");
        try (OutputStream out = Files.newOutputStream(war); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("index.html"));
            zip.write("<!DOCTYPE html><html><head><title>baseline</title></head><body>baseline</body></html>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        war.toFile().deleteOnExit();
        return war;
    }
}
