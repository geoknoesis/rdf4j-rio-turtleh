package com.geoknoesis.rdf4j.rio.turtleh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter;

/**
 * Turtle-H serializer implementing the folding rules of the Turtle-H specification.
 * <p>
 * A reifier is folded into an {@code @holon} block only when it is a blank node whose only statements are
 * exactly one {@code rdf:reifies} and one {@code h:inHolon}, the reified triple is asserted, the holon is
 * registered in M, and nothing else mentions the reifier. All other reifiers are written expanded at top level.
 * Repeated foldable occurrences are written as repeated statements, so occurrence multiplicity is preserved.
 * <p>
 * Supply M with {@link #setMetadata(Model)}. Without it, M is derived from the statements: registrations plus
 * management statements that are not filed content; this is reported through {@link #getReports()}.
 * <p>
 * Statements are buffered until {@link #endRDF()}, because folding needs the whole graph. The writer indexes them
 * in a single pass with plain hash structures rather than an indexed {@link Model}, so serialization time and
 * memory are linear in the number of statements.
 */
public class TurtleHWriter extends AbstractRDFWriter {

    private static final Pattern PREFIX_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*|");
    private static final Pattern LOCAL_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    private final Writer out;
    private final Set<Statement> data = new LinkedHashSet<>();
    private final Map<BNode, String> labels = new HashMap<>();
    private final Map<IRI, String> iriText = new HashMap<>();
    private final List<String> reports = new ArrayList<>();
    private Model metadata;

    /**
     * Creates a writer; output is produced at {@link #endRDF()}, after all statements are known.
     *
     * @param out the destination; it is buffered unless it already buffers or is a {@link StringWriter}
     */
    public TurtleHWriter(Writer out) {
        this.out = out instanceof BufferedWriter || out instanceof StringWriter ? out : new BufferedWriter(out, 1 << 16);
    }

    /**
     * Sets the metadata context M written with the data.
     *
     * @param metadata M; when not set, M is derived from the statements and the derivation is reported
     */
    public void setMetadata(Model metadata) {
        this.metadata = metadata;
    }

    /**
     * Notes about information that a re-parse will not reproduce exactly, as the specification requires.
     *
     * @return the notes from the last {@link #endRDF()}
     */
    public List<String> getReports() {
        return Collections.unmodifiableList(reports);
    }

    @Override
    public RDFFormat getRDFFormat() {
        return TurtleHRDFFormat.TURTLE_H;
    }

    @Override
    protected void consumeStatement(Statement st) {
        data.add(withoutContext(st));
    }

    @Override
    public void handleComment(String comment) {
        // Comments are not preserved; statements are reordered into top-level and block sections.
    }

    @Override
    public void endRDF() throws RDFHandlerException {
        checkWritingStarted();
        try {
            write();
            out.flush();
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }
    }

    /** Per-reifier facts gathered in one pass: how many statements it is the subject of, and the two that matter. */
    private static final class Reifier {
        int statements;
        int reifiesCount;
        int inHolonCount;
        Statement reifies;
        Statement inHolon;
    }

    private void write() throws IOException {
        Map<BNode, Reifier> reifiers = new HashMap<>();
        Set<Resource> hasMembership = new HashSet<>();
        List<Statement> reifiesStatements = new ArrayList<>();
        Set<BNode> mentioned = new HashSet<>();
        for (Statement st : data) {
            Resource subject = st.getSubject();
            IRI predicate = st.getPredicate();
            if (RDF.REIFIES.equals(predicate)) {
                reifiesStatements.add(st);
            } else if (RDFH.IN_HOLON.equals(predicate)) {
                hasMembership.add(subject);
            }
            if (subject instanceof BNode b) {
                Reifier r = reifiers.computeIfAbsent(b, k -> new Reifier());
                r.statements++;
                if (RDF.REIFIES.equals(predicate)) {
                    r.reifiesCount++;
                    r.reifies = st;
                } else if (RDFH.IN_HOLON.equals(predicate)) {
                    r.inHolonCount++;
                    r.inHolon = st;
                }
            }
            collectBNodes(st.getObject(), mentioned);
        }

        Model m = metadata != null ? metadata : deriveMetadata(reifiesStatements, hasMembership);
        for (Statement st : m) {
            collectBNodes(st.getSubject(), mentioned);
            collectBNodes(st.getObject(), mentioned);
        }
        Set<Resource> registry = registry(m);
        Map<Resource, List<Statement>> blocks = new LinkedHashMap<>();
        registry.forEach(h -> blocks.put(h, new ArrayList<>()));

        Set<Statement> consumed = new HashSet<>();
        Set<Statement> foldedBase = new HashSet<>();
        int foldedCount = 0;
        for (Statement reifies : reifiesStatements) {
            Statement base = foldable(reifies, reifiers, registry, mentioned);
            if (base == null) {
                continue;
            }
            Statement membership = reifiers.get((BNode) reifies.getSubject()).inHolon;
            blocks.get(membership.getObject()).add(base);
            foldedBase.add(base);
            consumed.add(reifies);
            consumed.add(membership);
            foldedCount++;
        }

        // A top-level management assertion becomes authoritative on re-parse. If it is absent
        // from M, it must be emitted solely through an existing foldable membership. Inventing
        // a block membership would change the graph, so otherwise reject before writing output.
        for (Statement st : data) {
            if (RDFH.isManagement(st) && !m.contains(st) && !foldedBase.contains(st)
                    && !consumed.contains(st)) {
                throw new TurtleHSerializationException(
                        "unrepresentable-context: top-level output would promote content into M: " + st);
            }
        }

        for (Map.Entry<String, String> ns : namespaceTable.entrySet()) {
            if (PREFIX_NAME.matcher(ns.getKey()).matches()) {
                out.write("@prefix " + ns.getKey() + ": <" + escapeIri(ns.getValue()) + "> .\n");
            }
        }
        if (!namespaceTable.isEmpty()) {
            out.write("\n");
        }

        // Management statements of M first (re-parsing copies them back into M), then other unfolded data.
        Set<Statement> written = new HashSet<>();
        for (Statement st : m) {
            Statement plain = withoutContext(st);
            if (written.add(plain)) {
                writeStatement(plain, "");
            }
        }
        for (Statement st : data) {
            if (!consumed.contains(st) && !foldedBase.contains(st) && !written.contains(st)) {
                writeStatement(st, "");
            }
        }

        for (Map.Entry<Resource, List<Statement>> block : blocks.entrySet()) {
            out.write("\n@holon ");
            out.write(term(block.getKey()));
            if (block.getValue().isEmpty()) {
                out.write(" { }\n");
                continue;
            }
            out.write(" {\n");
            for (Statement st : block.getValue()) {
                writeStatement(st, "    ");
            }
            out.write("}\n");
        }

        if (foldedCount > 0) {
            reports.add("Folded " + foldedCount + " membership reifier(s); a re-parse assigns them fresh blank-node labels.");
        }
        boolean reduced = false;
        for (Statement st : m) {
            reduced |= !RDFH.isManagement(st);
        }
        if (reduced) {
            reports.add("M contains non-management statements; a re-parse places them in G only, reducing M to its management statements.");
        }
        for (Resource h : registry) {
            if (!m.contains(h, RDF.TYPE, RDFH.HOLON)) {
                reports.add("Holon " + h + " gains an h:Holon registration from its @holon block.");
            }
        }
    }

    private static void collectBNodes(Value v, Set<BNode> into) {
        if (v instanceof BNode b) {
            into.add(b);
        } else if (v instanceof TripleTerm tt) {
            collectBNodes(tt.getSubject(), into);
            collectBNodes(tt.getObject(), into);
        }
    }

    /** Returns the asserted base statement when the reifier of {@code reifies} can be folded, else null. */
    private Statement foldable(Statement reifies, Map<BNode, Reifier> reifiers, Set<Resource> registry,
            Set<BNode> mentioned) {
        if (!(reifies.getSubject() instanceof BNode b) || !(reifies.getObject() instanceof TripleTerm tt)) {
            return null;
        }
        Reifier r = reifiers.get(b);
        if (r.statements != 2 || r.reifiesCount != 1 || r.inHolonCount != 1 || mentioned.contains(b)
                || !registry.contains(r.inHolon.getObject())) {
            return null;
        }
        Statement base = VF.createStatement(tt.getSubject(), tt.getPredicate(), tt.getObject());
        return data.contains(base) ? base : null;
    }

    private Model deriveMetadata(List<Statement> reifiesStatements, Set<Resource> hasMembership) {
        Set<Statement> filed = new HashSet<>();
        for (Statement st : reifiesStatements) {
            if (st.getObject() instanceof TripleTerm tt && hasMembership.contains(st.getSubject())) {
                filed.add(VF.createStatement(tt.getSubject(), tt.getPredicate(), tt.getObject()));
            }
        }
        Model derived = new LinkedHashModel();
        for (Statement st : data) {
            if (RDFH.isManagement(st) && !filed.contains(st)) {
                derived.add(st);
            }
        }
        reports.add("No metadata context supplied; M was derived from unfiled management statements.");
        return derived;
    }

    private static Set<Resource> registry(Model m) {
        Set<Resource> registry = new LinkedHashSet<>();
        for (Statement st : m) {
            if ((RDF.TYPE.equals(st.getPredicate())
                    && (RDFH.HOLON.equals(st.getObject()) || RDFH.COMPLETE_HOLON.equals(st.getObject())))
                    || RDFH.CONTENT_GRAPH.equals(st.getPredicate())) {
                registry.add(st.getSubject());
            }
        }
        return registry;
    }

    private static Statement withoutContext(Statement st) {
        return st.getContext() == null ? st : VF.createStatement(st.getSubject(), st.getPredicate(), st.getObject());
    }

    private void writeStatement(Statement st, String indent) throws IOException {
        out.write(indent);
        out.write(term(st.getSubject()));
        out.write(' ');
        out.write(term(st.getPredicate()));
        out.write(' ');
        out.write(term(st.getObject()));
        out.write(" .\n");
    }

    private String term(Value v) {
        if (v instanceof IRI iri) {
            return iriText.computeIfAbsent(iri, this::formatIri);
        }
        if (v instanceof BNode b) {
            return "_:" + labels.computeIfAbsent(b, k -> "b" + labels.size());
        }
        if (v instanceof TripleTerm tt) {
            return "<<( " + term(tt.getSubject()) + " " + iriTerm(tt.getPredicate()) + " " + term(tt.getObject()) + " )>>";
        }
        Literal lit = (Literal) v;
        StringBuilder sb = new StringBuilder(lit.getLabel().length() + 8).append('"');
        escapeString(lit.getLabel(), sb);
        sb.append('"');
        if (lit.getLanguage().isPresent()) {
            sb.append('@').append(lit.getLanguage().get());
            Literal.BaseDirection dir = lit.getBaseDirection();
            if (dir == Literal.BaseDirection.LTR) {
                sb.append("--ltr");
            } else if (dir == Literal.BaseDirection.RTL) {
                sb.append("--rtl");
            }
        } else if (!XSD.STRING.equals(lit.getDatatype())) {
            sb.append("^^").append(iriTerm(lit.getDatatype()));
        }
        return sb.toString();
    }

    /** Formats an IRI once; results are memoized because predicates and classes repeat throughout a graph. */
    private String formatIri(IRI iri) {
        if (RDF.TYPE.equals(iri)) {
            return "a";
        }
        String name = iri.stringValue();
        for (Map.Entry<String, String> ns : namespaceTable.entrySet()) {
            if (name.startsWith(ns.getValue()) && PREFIX_NAME.matcher(ns.getKey()).matches()) {
                String local = name.substring(ns.getValue().length());
                if (LOCAL_NAME.matcher(local).matches()) {
                    return ns.getKey() + ":" + local;
                }
            }
        }
        return "<" + escapeIri(name) + ">";
    }

    /** IRIs in predicate position inside triple terms and datatypes never abbreviate to {@code a}. */
    private String iriTerm(IRI iri) {
        String t = term(iri);
        return "a".equals(t) ? "<" + escapeIri(iri.stringValue()) + ">" : t;
    }

    private static String escapeIri(String iri) {
        StringBuilder sb = new StringBuilder(iri.length());
        iri.codePoints().forEach(c -> {
            if (c <= 0x20 || "<>\"{}|^`\\".indexOf(c) >= 0) {
                sb.append(String.format(c > 0xFFFF ? "\\U%08X" : "\\u%04X", c));
            } else {
                sb.appendCodePoint(c);
            }
        });
        return sb.toString();
    }

    private static void escapeString(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
    }
}
