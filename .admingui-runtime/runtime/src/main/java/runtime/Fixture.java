package runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

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

        String node = "localhost-" + domain;
        Path war = sampleWar();
        runAll(portBase, List.of(
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
                List.of("deploy", "--name", "baselineApp", "--contextroot", "baselineApp", war.toString())));
        extend(domain, portBase);
        System.out.println("fixture ready: http://localhost:" + (portBase + 48) + "/");
    }

    /**
     * Adds what the screens that need more than the basic configuration show: a file realm user, a connector security
     * map, a resource adapter configuration, an external JNDI resource, a lifecycle module, and one application of each
     * archive type (EJB jar, EAR, application client, resource adapter, and a web application that runs a batch job).
     * The archives are compiled here from minimal sources written for the test domain.
     */
    void extend(String domain, int portBase) throws IOException, InterruptedException {
        Path work = Files.createTempDirectory("fixture-" + domain);
        Path passwords = work.resolve("passwords.txt");
        Files.writeString(passwords, "AS_ADMIN_PASSWORD=\nAS_ADMIN_USERPASSWORD=baseline1\nAS_ADMIN_MAPPEDPASSWORD=baseline1\n",
                StandardCharsets.UTF_8);
        Archives archives = new Archives(work, glassfishHome.resolve("modules"));
        runAll(portBase, List.of(
                List.of("--passwordfile", passwords.toString(), "create-file-user", "--authrealmname", "file", "baselineUser"),
                List.of("--passwordfile", passwords.toString(), "create-connector-security-map",
                        "--poolname", "jms/baselineFactory-Connection-Pool", "--principals", "baselineUser",
                        "--mappedusername", "baselineMapped", "baselineMap"),
                List.of("create-resource-adapter-config", "jmsra"),
                List.of("create-jndi-resource", "--restype", "java.lang.String",
                        "--factoryclass", "baseline.jndi.BaselineFactory", "--jndilookupname", "baseline", "jndi/baseline"),
                List.of("create-lifecycle-module", "--classname", "baseline.lifecycle.BaselineLifecycle", "--enabled=false",
                        "baselineLifecycle"),
                List.of("deploy", "--name", "baselineEjb", archives.ejbJar().toString()),
                List.of("deploy", "--name", "baselineEar", archives.ear().toString()),
                List.of("deploy", "--name", "baselineClient", archives.appClient().toString()),
                List.of("deploy", "--name", "baselineRar", archives.rar().toString()),
                List.of("deploy", "--name", "baselineBatch", "--contextroot", "baselineBatch", archives.batchWar().toString())));
    }

    void delete(String domain) throws IOException, InterruptedException {
        asadmin(List.of("stop-domain", domain));
        asadmin(List.of("delete-domain", domain));
    }

    private void runAll(int portBase, List<List<String>> commands) throws IOException, InterruptedException {
        String port = String.valueOf(portBase + 48);
        for (List<String> command : commands) {
            List<String> withPort = new ArrayList<>(List.of("--user", "admin", "--port", port));
            withPort.addAll(command);
            asadmin(withPort);
        }
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
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("index.html", "<!DOCTYPE html><html><head><title>baseline</title></head><body>baseline</body></html>"
                .getBytes(StandardCharsets.UTF_8));
        zip(war, entries);
        war.toFile().deleteOnExit();
        return war;
    }

    private static void zip(Path file, Map<String, byte[]> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    /** Minimal archives of each type, compiled against the API jars of the GlassFish installation. */
    private static final class Archives {

        private final Path work;
        private final Path modules;

        Archives(Path work, Path modules) {
            this.work = work;
            this.modules = modules;
        }

        Path ejbJar() throws IOException {
            Path jar = work.resolve("baselineEjb.jar");
            zip(jar, compile("ejb", Map.of("baseline/ejb/BaselineBean.java", """
                    package baseline.ejb;

                    import jakarta.ejb.Stateless;

                    @Stateless
                    public class BaselineBean {
                        public String name() {
                            return "baseline";
                        }
                    }
                    """), "jakarta.ejb-api.jar"));
            return jar;
        }

        Path ear() throws IOException {
            Path module = work.resolve("baselineEarEjb.jar");
            zip(module, compile("ear-ejb", Map.of("baseline/ear/BaselineEarBean.java", """
                    package baseline.ear;

                    import jakarta.ejb.Stateless;

                    @Stateless
                    public class BaselineEarBean {
                        public String name() {
                            return "baseline";
                        }
                    }
                    """), "jakarta.ejb-api.jar"));
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("META-INF/application.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <application xmlns="https://jakarta.ee/xml/ns/jakartaee" version="10">
                        <display-name>baselineEar</display-name>
                        <module><ejb>baselineEarEjb.jar</ejb></module>
                    </application>
                    """.getBytes(StandardCharsets.UTF_8));
            entries.put("baselineEarEjb.jar", Files.readAllBytes(module));
            Path ear = work.resolve("baselineEar.ear");
            zip(ear, entries);
            return ear;
        }

        Path appClient() throws IOException {
            Map<String, byte[]> entries = compile("client", Map.of("baseline/client/BaselineClient.java", """
                    package baseline.client;

                    public class BaselineClient {
                        public static void main(String[] arguments) {
                            System.out.println("baseline");
                        }
                    }
                    """));
            entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: baseline.client.BaselineClient\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            entries.put("META-INF/application-client.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <application-client xmlns="https://jakarta.ee/xml/ns/jakartaee" version="10">
                        <display-name>baselineClient</display-name>
                    </application-client>
                    """.getBytes(StandardCharsets.UTF_8));
            Path jar = work.resolve("baselineClient.jar");
            zip(jar, entries);
            return jar;
        }

        Path rar() throws IOException {
            Path classes = work.resolve("baselineRa.jar");
            zip(classes, compile("ra", Map.of("baseline/ra/BaselineResourceAdapter.java", """
                    package baseline.ra;

                    import jakarta.resource.spi.ActivationSpec;
                    import jakarta.resource.spi.BootstrapContext;
                    import jakarta.resource.spi.Connector;
                    import jakarta.resource.spi.ResourceAdapter;
                    import jakarta.resource.spi.endpoint.MessageEndpointFactory;

                    import javax.transaction.xa.XAResource;

                    @Connector(displayName = "baselineRar", vendorName = "baseline", eisType = "baseline")
                    public class BaselineResourceAdapter implements ResourceAdapter {

                        @Override
                        public void start(BootstrapContext context) {
                        }

                        @Override
                        public void stop() {
                        }

                        @Override
                        public void endpointActivation(MessageEndpointFactory factory, ActivationSpec spec) {
                        }

                        @Override
                        public void endpointDeactivation(MessageEndpointFactory factory, ActivationSpec spec) {
                        }

                        @Override
                        public XAResource[] getXAResources(ActivationSpec[] specs) {
                            return new XAResource[0];
                        }

                        @Override
                        public boolean equals(Object other) {
                            return other instanceof BaselineResourceAdapter;
                        }

                        @Override
                        public int hashCode() {
                            return BaselineResourceAdapter.class.hashCode();
                        }
                    }
                    """), "jakarta.resource-api.jar", "jakarta.transaction-api.jar"));
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("baselineRa.jar", Files.readAllBytes(classes));
            Path rar = work.resolve("baselineRar.rar");
            zip(rar, entries);
            return rar;
        }

        Path batchWar() throws IOException {
            Map<String, byte[]> classes = compile("batch", Map.of(
                    "baseline/batch/BaselineBatchlet.java", """
                    package baseline.batch;

                    import jakarta.batch.api.Batchlet;

                    public class BaselineBatchlet implements Batchlet {
                        @Override
                        public String process() {
                            return "COMPLETED";
                        }

                        @Override
                        public void stop() {
                        }
                    }
                    """,
                    "baseline/batch/BaselineJobStarter.java", """
                    package baseline.batch;

                    import jakarta.batch.runtime.BatchRuntime;
                    import jakarta.servlet.ServletContextEvent;
                    import jakarta.servlet.ServletContextListener;
                    import jakarta.servlet.annotation.WebListener;

                    import java.util.Properties;

                    /** Starts the job once when the application starts, so that the batch screens have an execution. */
                    @WebListener
                    public class BaselineJobStarter implements ServletContextListener {
                        @Override
                        public void contextInitialized(ServletContextEvent event) {
                            BatchRuntime.getJobOperator().start("baselineJob", new Properties());
                        }
                    }
                    """), "jakarta.batch-api.jar", "jakarta.servlet-api.jar");
            Map<String, byte[]> entries = new LinkedHashMap<>();
            classes.forEach((name, bytes) -> entries.put("WEB-INF/classes/" + name, bytes));
            entries.put("WEB-INF/classes/META-INF/batch-jobs/baselineJob.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <job id="baselineJob" xmlns="https://jakarta.ee/xml/ns/jakartaee" version="2.0">
                        <step id="baselineStep">
                            <batchlet ref="baseline.batch.BaselineBatchlet"/>
                        </step>
                    </job>
                    """.getBytes(StandardCharsets.UTF_8));
            Path war = work.resolve("baselineBatch.war");
            zip(war, entries);
            return war;
        }

        /** Compiles the sources and returns the class files by their path in an archive. */
        private Map<String, byte[]> compile(String name, Map<String, String> sources, String... apiJars) throws IOException {
            Path sourceDirectory = work.resolve(name + "-src");
            Path classDirectory = work.resolve(name + "-classes");
            Files.createDirectories(classDirectory);
            List<String> arguments = new ArrayList<>(List.of("--release", "21", "-d", classDirectory.toString()));
            if (apiJars.length > 0) {
                arguments.add("-cp");
                arguments.add(String.join(java.io.File.pathSeparator,
                        Stream.of(apiJars).map(jar -> modules.resolve(jar).toString()).toList()));
            }
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path file = sourceDirectory.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
                arguments.add(file.toString());
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler.run(null, null, null, arguments.toArray(String[]::new)) != 0) {
                throw new IOException("Could not compile the " + name + " archive");
            }
            Map<String, byte[]> classes = new LinkedHashMap<>();
            try (Stream<Path> files = Files.walk(classDirectory)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    classes.put(classDirectory.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                }
            }
            return classes;
        }
    }
}
