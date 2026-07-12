package io.github.nhwalker.wayland.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Parses Wayland protocol XML into the {@link Protocol} model. */
public final class ProtocolParser {

  private ProtocolParser() {}

  public static Protocol parse(InputStream xml) throws IOException {
    Document document;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setExpandEntityReferences(false);
      document = factory.newDocumentBuilder().parse(xml);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("invalid protocol XML: " + e.getMessage(), e);
    }
    Element root = document.getDocumentElement();
    if (!root.getTagName().equals("protocol")) {
      throw new IOException("root element must be <protocol>, got <" + root.getTagName() + ">");
    }
    List<Protocol.Interface> interfaces = new ArrayList<>();
    for (Element element : children(root, "interface")) {
      interfaces.add(parseInterface(element));
    }
    return new Protocol(attr(root, "name"), interfaces);
  }

  private static Protocol.Interface parseInterface(Element element) {
    List<Protocol.Message> requests = new ArrayList<>();
    for (Element request : children(element, "request")) {
      requests.add(parseMessage(request));
    }
    List<Protocol.Message> events = new ArrayList<>();
    for (Element event : children(element, "event")) {
      events.add(parseMessage(event));
    }
    List<Protocol.EnumDef> enums = new ArrayList<>();
    for (Element enumElement : children(element, "enum")) {
      enums.add(parseEnum(enumElement));
    }
    return new Protocol.Interface(
        attr(element, "name"),
        intAttr(element, "version", 1),
        description(element),
        requests, events, enums);
  }

  private static Protocol.Message parseMessage(Element element) {
    List<Protocol.Arg> args = new ArrayList<>();
    for (Element arg : children(element, "arg")) {
      args.add(new Protocol.Arg(
          attr(arg, "name"),
          attr(arg, "type"),
          optionalAttr(arg, "interface"),
          "true".equals(optionalAttr(arg, "allow-null")),
          optionalAttr(arg, "enum")));
    }
    return new Protocol.Message(
        attr(element, "name"),
        intAttr(element, "since", 1),
        "destructor".equals(optionalAttr(element, "type")),
        description(element),
        args);
  }

  private static Protocol.EnumDef parseEnum(Element element) {
    List<Protocol.Entry> entries = new ArrayList<>();
    for (Element entry : children(element, "entry")) {
      String literal = attr(entry, "value");
      entries.add(new Protocol.Entry(attr(entry, "name"), parseValue(literal), literal));
    }
    return new Protocol.EnumDef(
        attr(element, "name"),
        "true".equals(optionalAttr(element, "bitfield")),
        description(element),
        entries);
  }

  private static int parseValue(String value) {
    return value.startsWith("0x") || value.startsWith("0X")
        ? Integer.parseUnsignedInt(value.substring(2), 16)
        : Integer.parseUnsignedInt(value);
  }

  /**
   * The {@code <description>} body as trimmed lines with leading/trailing blanks dropped;
   * falls back to the {@code summary} attribute if the body is empty.
   */
  private static List<String> description(Element parent) {
    List<Element> descriptions = children(parent, "description");
    if (descriptions.isEmpty()) {
      return List.of();
    }
    Element description = descriptions.get(0);
    List<String> lines = new ArrayList<>();
    for (String line : description.getTextContent().split("\n", -1)) {
      lines.add(line.strip());
    }
    while (!lines.isEmpty() && lines.get(0).isEmpty()) {
      lines.remove(0);
    }
    while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    if (lines.isEmpty()) {
      String summary = optionalAttr(description, "summary");
      return summary == null ? List.of() : List.of(summary);
    }
    return List.copyOf(lines);
  }

  private static List<Element> children(Element parent, String tag) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element element && element.getTagName().equals(tag)) {
        result.add(element);
      }
    }
    return result;
  }

  private static String attr(Element element, String name) {
    String value = element.getAttribute(name);
    if (value.isEmpty()) {
      throw new IllegalArgumentException(
          "<" + element.getTagName() + "> is missing attribute '" + name + "'");
    }
    return value;
  }

  private static String optionalAttr(Element element, String name) {
    String value = element.getAttribute(name);
    return value.isEmpty() ? null : value;
  }

  private static int intAttr(Element element, String name, int defaultValue) {
    String value = element.getAttribute(name);
    return value.isEmpty() ? defaultValue : Integer.parseInt(value);
  }
}
