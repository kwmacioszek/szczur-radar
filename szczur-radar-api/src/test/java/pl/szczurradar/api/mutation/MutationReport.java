package pl.szczurradar.api.mutation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads PIT's {@code mutations.xml} with the JDK's XML parser. The parser refuses a document type declaration
 * and resolves no external entity, DTD or schema: the report is plain elements.
 */
final class MutationReport {

	private MutationReport() {
	}

	/** Every mutant of the report at {@code file}, in the report's order. */
	static List<Mutant> read(Path file) {
		Element root;
		try (InputStream input = Files.newInputStream(file)) {
			root = parser().parse(input).getDocumentElement();
		}
		catch (IOException | SAXException exception) {
			throw new IllegalStateException("Cannot read the PIT report " + file + ": " + exception.getMessage(),
					exception);
		}
		if (!root.getTagName().equals("mutations")) {
			throw new IllegalStateException("Not a PIT mutations report: " + file + " has the root <"
					+ root.getTagName() + ">");
		}
		NodeList elements = root.getElementsByTagName("mutation");
		List<Mutant> mutants = new ArrayList<>(elements.getLength());
		for (int index = 0; index < elements.getLength(); index++) {
			mutants.add(mutant((Element) elements.item(index), file, index + 1));
		}
		return mutants;
	}

	private static Mutant mutant(Element element, Path file, int position) {
		String where = file + ", mutation " + position;
		MutantKey key = new MutantKey(text(element, "mutatedClass", where), text(element, "mutatedMethod", where),
				text(element, "methodDescription", where), text(element, "mutator", where),
				text(element, "description", where));
		int lineNumber;
		try {
			lineNumber = Integer.parseInt(text(element, "lineNumber", where).strip());
		}
		catch (NumberFormatException exception) {
			throw new IllegalStateException(where + ": lineNumber is not a number", exception);
		}
		return new Mutant(key, lineNumber, status(element.getAttribute("status"), where));
	}

	private static MutantStatus status(String status, String where) {
		try {
			return MutantStatus.valueOf(status);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException(where + ": unknown status '" + status + "'", exception);
		}
	}

	private static String text(Element element, String name, String where) {
		NodeList children = element.getElementsByTagName(name);
		if (children.getLength() != 1) {
			throw new IllegalStateException(where + ": expected one <" + name + ">, found " + children.getLength());
		}
		return children.item(0).getTextContent();
	}

	private static DocumentBuilder parser() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
		try {
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder parser = factory.newDocumentBuilder();
			// Fatal errors throw, as by default, but nothing is printed to stderr.
			parser.setErrorHandler(new DefaultHandler());
			return parser;
		}
		catch (ParserConfigurationException exception) {
			throw new IllegalStateException("The JDK's XML parser refuses a hardening feature", exception);
		}
	}

}
