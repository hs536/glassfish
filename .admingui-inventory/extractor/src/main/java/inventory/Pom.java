package inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** The parts of a Maven POM that the inventories need. Only {@code ${project.groupId}} is interpolated. */
final class Pom {

    record Dependency(String groupId, String artifactId, String scope) {
    }

    private final Element project;

    private Pom(Element project) {
        this.project = project;
    }

    static Pom read(Path file) throws Exception {
        return new Pom(Xml.parse(file).getDocumentElement());
    }

    String groupId() {
        String own = childText(project, "groupId");
        if (own != null) {
            return own;
        }
        Element parent = child(project, "parent");
        return parent == null ? null : childText(parent, "groupId");
    }

    String artifactId() {
        return childText(project, "artifactId");
    }

    String packaging() {
        String packaging = childText(project, "packaging");
        return packaging == null ? "jar" : packaging;
    }

    /** Direct dependencies of the project (not dependency management, not plugin dependencies). */
    List<Dependency> dependencies() {
        List<Dependency> result = new ArrayList<>();
        Element dependencies = child(project, "dependencies");
        if (dependencies == null) {
            return result;
        }
        for (Element dependency : children(dependencies, "dependency")) {
            String groupId = childText(dependency, "groupId");
            if ("${project.groupId}".equals(groupId)) {
                groupId = groupId();
            }
            result.add(new Dependency(groupId, childText(dependency, "artifactId"), childText(dependency, "scope")));
        }
        return result;
    }

    private static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String childText(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? null : element.getTextContent().trim();
    }

    /** XML parsing without network access or external entity resolution. */
    static final class Xml {

        private Xml() {
        }

        static org.w3c.dom.Document parse(Path file) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(file.toFile());
        }
    }
}
