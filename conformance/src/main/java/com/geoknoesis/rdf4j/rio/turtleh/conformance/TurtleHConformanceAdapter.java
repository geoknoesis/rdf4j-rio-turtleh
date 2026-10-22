package com.geoknoesis.rdf4j.rio.turtleh.conformance;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.TripleTerm;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFParseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geoknoesis.rdf4j.rio.turtleh.TurtleHDocument;
import com.geoknoesis.rdf4j.rio.turtleh.TurtleHSerializationException;

/**
 * Implementation adapter for the RDF-H conformance suite runner ({@code tests/run_suite.py}).
 * <p>
 * Reads one JSON request on standard input and writes one JSON response on standard output, following the
 * adapter protocol in the suite guide: {@code parse} returns {@code accepted} or a {@code syntax} rejection;
 * {@code evaluate} returns {@code graph} (G) and {@code metadataGraph} (M) as arrays of term-JSON triples with
 * shared blank-node labels; {@code serialize} takes G and M in the same encoding and returns Turtle-H
 * {@code text}. Requests for other languages return {@code unsupported}, which the runner records as untested.
 */
public final class TurtleHConformanceAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    private TurtleHConformanceAdapter() {
    }

    public static void main(String[] args) throws IOException {
        JsonNode request;
        try (Reader in = new InputStreamReader(System.in, StandardCharsets.UTF_8)) {
            request = JSON.readTree(in);
        }
        ObjectNode response = respond(request);
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        out.println(JSON.writeValueAsString(response));
        out.flush();
    }

    public static ObjectNode respond(JsonNode request) {
        ObjectNode response = JSON.createObjectNode();
        if (!"turtle-h".equals(request.path("language").asText())) {
            return response.put("status", "unsupported").put("message", "This adapter implements Turtle-H only");
        }
        JsonNode input = request.path("input");
        if ("serialize".equals(request.path("operation").asText())) {
            Map<String, BNode> blanks = new HashMap<>();
            TurtleHDocument document = new TurtleHDocument(model(input.path("graph"), blanks),
                    model(input.path("metadataGraph"), blanks));
            try {
                response.put("status", "ok").putObject("result").put("text", document.write());
            } catch (TurtleHSerializationException e) {
                response.remove("result");
                response.put("status", "rejected").put("category", "unrepresentable-context")
                        .put("condition", "unrepresentable-context").put("message", e.getMessage());
            }
            return response;
        }
        String text = input.path("text").asText();
        String base = input.hasNonNull("baseURI") ? input.get("baseURI").asText() : "";
        TurtleHDocument document;
        try {
            document = TurtleHDocument.parse(text, base);
        } catch (RDFParseException e) {
            return response.put("status", "rejected").put("category", "syntax").put("message", e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String operation = request.path("operation").asText();
        if ("parse".equals(operation)) {
            return response.put("status", "accepted");
        }
        if (!"evaluate".equals(operation)) {
            return response.put("status", "unsupported").put("message", "Unknown operation " + operation);
        }
        ObjectNode result = response.put("status", "ok").putObject("result");
        result.set("graph", graph(document.getData()));
        result.set("metadataGraph", graph(document.getMetadata()));
        return response;
    }

    private static ArrayNode graph(Model model) {
        ArrayNode rows = JSON.createArrayNode();
        for (Statement st : model) {
            ArrayNode row = rows.addArray();
            row.add(term(st.getSubject()));
            row.add(term(st.getPredicate()));
            row.add(term(st.getObject()));
        }
        return rows;
    }

    /** Decodes term-JSON triples; blank-node labels map to one node across G and M. */
    private static Model model(JsonNode rows, Map<String, BNode> blanks) {
        Model model = new LinkedHashModel();
        for (JsonNode row : rows) {
            model.add((Resource) value(row.get(0), blanks), (IRI) value(row.get(1), blanks), value(row.get(2), blanks));
        }
        return model;
    }

    private static Value value(JsonNode t, Map<String, BNode> blanks) {
        String v = t.path("value").asText();
        switch (t.path("type").asText()) {
            case "uri":
                return VF.createIRI(v);
            case "bnode":
                return blanks.computeIfAbsent(v, k -> VF.createBNode());
            case "triple":
                JsonNode inner = t.get("value");
                return VF.createTripleTerm((Resource) value(inner.get("subject"), blanks),
                        (IRI) value(inner.get("predicate"), blanks), value(inner.get("object"), blanks));
            case "literal":
                if (t.has("xml:lang")) {
                    String dir = t.path("direction").asText("");
                    Literal.BaseDirection base = "ltr".equals(dir) ? Literal.BaseDirection.LTR
                            : "rtl".equals(dir) ? Literal.BaseDirection.RTL : Literal.BaseDirection.NONE;
                    return VF.createLiteral(v, t.get("xml:lang").asText(), base);
                }
                return t.has("datatype") ? VF.createLiteral(v, VF.createIRI(t.get("datatype").asText()))
                        : VF.createLiteral(v);
            default:
                throw new IllegalArgumentException("Unknown term type: " + t.path("type").asText());
        }
    }

    static ObjectNode term(Value v) {
        ObjectNode t = JSON.createObjectNode();
        if (v instanceof IRI) {
            return t.put("type", "uri").put("value", v.stringValue());
        }
        if (v instanceof BNode b) {
            return t.put("type", "bnode").put("value", b.getID());
        }
        if (v instanceof TripleTerm tt) {
            t.put("type", "triple");
            ObjectNode inner = t.putObject("value");
            inner.set("subject", term(tt.getSubject()));
            inner.set("predicate", term(tt.getPredicate()));
            inner.set("object", term(tt.getObject()));
            return t;
        }
        Literal lit = (Literal) v;
        t.put("type", "literal").put("value", lit.getLabel());
        if (lit.getLanguage().isPresent()) {
            t.put("xml:lang", lit.getLanguage().get());
            switch (lit.getBaseDirection()) {
                case LTR -> t.put("direction", "ltr");
                case RTL -> t.put("direction", "rtl");
                default -> {
                }
            }
        } else if (!XSD.STRING.equals(lit.getDatatype()) && !RDF.LANGSTRING.equals(lit.getDatatype())) {
            t.put("datatype", lit.getDatatype().stringValue());
        }
        return t;
    }
}
