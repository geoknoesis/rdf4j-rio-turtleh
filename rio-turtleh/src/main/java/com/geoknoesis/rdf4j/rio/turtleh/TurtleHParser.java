package com.geoknoesis.rdf4j.rio.turtleh;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.turtle.TurtleParser;

/**
 * Turtle-H parser built on the RDF 1.2 {@link TurtleParser} of RDF4J.
 * <p>
 * The asserted graph G is reported to the {@link org.eclipse.rdf4j.rio.RDFHandler}. The metadata context M is
 * available from {@link #getMetadata()} after parsing; M and G share blank-node identity.
 * <p>
 * Expansion follows the Turtle-H expansion algorithm: every asserted triple event produced while parsing a
 * statement inside a block (including collection, blank-node property list, reifier, and annotation triples)
 * is reported as a base assertion plus a fresh blank-node reifier with {@code rdf:reifies} and
 * {@code h:inHolon}. Generated membership and registration triples are not themselves filed. Every block
 * registers its holon in G and M. Top-level management statements are copied to M; management statements
 * inside blocks are content only. Nesting creates no parent relation.
 */
public class TurtleHParser extends TurtleParser {

    private static final String KEYWORD = "@holon";

    /** Default bound on block nesting; the Turtle-H grammar itself has no limit. */
    public static final int DEFAULT_MAX_NESTING_DEPTH = 256;

    private final Deque<Resource> holons = new ArrayDeque<>();
    private Model metadata = new DynamicModelFactory().createEmptyModel();
    private int maxNestingDepth = DEFAULT_MAX_NESTING_DEPTH;

    /** Creates a parser using the default value factory. */
    public TurtleHParser() {
        super();
    }

    /**
     * Creates a parser that builds values with the given factory.
     *
     * @param valueFactory the value factory
     */
    public TurtleHParser(ValueFactory valueFactory) {
        super(valueFactory);
    }

    @Override
    public RDFFormat getRDFFormat() {
        return TurtleHRDFFormat.TURTLE_H;
    }

    /**
     * The metadata context M produced by the most recent parse.
     *
     * @return M, sharing blank-node identity with the statements reported to the handler
     */
    public Model getMetadata() {
        return metadata;
    }

    /**
     * Sets the maximum depth of nested {@code @holon} blocks; deeper input is rejected as a syntax error, as the
     * Turtle-H specification recommends for untrusted input, since each level holds parser state.
     *
     * @param depth the maximum number of enclosing blocks, at least 1
     */
    public void setMaxNestingDepth(int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException("Nesting depth must be at least 1");
        }
        this.maxNestingDepth = depth;
    }

    @Override
    public synchronized void parse(InputStream in, String baseURI)
            throws IOException, RDFParseException, RDFHandlerException {
        reset();
        super.parse(in, baseURI);
    }

    @Override
    public synchronized void parse(Reader reader, String baseURI)
            throws IOException, RDFParseException, RDFHandlerException {
        reset();
        super.parse(reader, baseURI);
    }

    private void reset() {
        holons.clear();
        metadata = new DynamicModelFactory().createEmptyModel();
    }

    @Override
    protected void parseStatement() throws IOException, RDFParseException, RDFHandlerException {
        // A one-character check keeps the keyword lookahead off the path of ordinary statements.
        if (peekCodePoint() == '@' && peekHolonKeyword()) {
            parseHolonBlock();
        } else {
            super.parseStatement();
        }
    }

    /**
     * Parses {@code holonBlock ::= '@holon' holonId '{' holonContent* '}'}, where
     * {@code holonContent ::= triples '.' | holonBlock}.
     *
     * @throws IOException if reading fails
     * @throws RDFParseException if the block is malformed
     * @throws RDFHandlerException if the handler rejects a statement
     */
    protected void parseHolonBlock() throws IOException, RDFParseException, RDFHandlerException {
        for (int i = 0; i < KEYWORD.length(); i++) {
            readCodePoint();
        }
        skipWSC();
        Resource holon = parseHolonId();
        skipWSC();
        int open = readCodePoint();
        if (open != '{') {
            reportFatalError("Expected '{' after the @holon identifier, found: " + describe(open));
        }

        if (holons.size() >= maxNestingDepth) {
            reportFatalError("@holon blocks nested deeper than " + maxNestingDepth + " levels");
        }
        register(holon);
        holons.push(holon);
        try {
            while (true) {
                int c = skipWSC();
                if (c == -1) {
                    throwEOFException();
                } else if (c == '}') {
                    readCodePoint();
                    return;
                } else if (c == '@' && peekHolonKeyword()) {
                    parseHolonBlock();
                } else if (c == '@' || (isDirectiveStart(c) && peekSparqlDirective())) {
                    reportFatalError("Directives are permitted only at top level, not inside an @holon block");
                } else {
                    parseTriples();
                    skipWSC();
                    verifyCharacterOrFail(readCodePoint(), ".");
                }
            }
        } finally {
            holons.pop();
        }
    }

    /**
     * Parses {@code holonId ::= iri | BLANK_NODE_LABEL}.
     *
     * @return the holon identifier
     * @throws IOException if reading fails
     * @throws RDFParseException if the identifier is a literal, variable, anonymous blank node, or missing
     * @throws RDFHandlerException if the handler rejects a statement
     */
    protected Resource parseHolonId() throws IOException, RDFParseException, RDFHandlerException {
        int c = peekCodePoint();
        if (c == '<') {
            return parseURI();
        }
        if (c == '_') {
            return parseNodeID();
        }
        if (c == '[') {
            reportFatalError("A blank-node holon identifier must be labeled; '[]' is not permitted");
        }
        if (c == '{') {
            reportFatalError("Missing holon identifier after @holon");
        }
        if (c == '"' || c == '\'' || c == '?' || c == '$' || c == '(') {
            reportFatalError("A holon identifier must be an IRI or a labeled blank node, found: " + describe(c));
        }
        Value id = parseQNameOrBoolean();
        if (!(id instanceof IRI)) {
            reportFatalError("A holon identifier must be an IRI or a labeled blank node, found: " + id);
        }
        return (IRI) id;
    }

    @Override
    protected void reportStatement(Resource subj, IRI pred, Value obj)
            throws RDFParseException, RDFHandlerException {
        super.reportStatement(subj, pred, obj);
        Resource holon = holons.peek();
        if (holon == null) {
            if (RDFH.isManagement(pred, obj)) {
                metadata.add(subj, pred, obj);
            }
            return;
        }
        // Membership records are emitted directly, never through reportStatement, so they are not filed again.
        Resource reifier = createNode();
        emit(reifier, RDF.REIFIES, valueFactory.createTripleTerm(subj, pred, obj));
        emit(reifier, RDFH.IN_HOLON, holon);
    }

    private void register(Resource holon) throws RDFParseException, RDFHandlerException {
        emit(holon, RDF.TYPE, RDFH.HOLON);
        metadata.add(holon, RDF.TYPE, RDFH.HOLON);
    }

    private void emit(Resource subj, IRI pred, Value obj) throws RDFParseException, RDFHandlerException {
        if (rdfHandler != null) {
            Statement st = createStatement(subj, pred, obj);
            rdfHandler.handleStatement(st);
        }
    }

    /** True when the next characters are exactly the case-sensitive keyword {@code @holon}. */
    private boolean peekHolonKeyword() throws IOException {
        String ahead = peekChars(KEYWORD.length() + 1);
        if (!ahead.startsWith(KEYWORD)) {
            return false;
        }
        return ahead.length() == KEYWORD.length() || !isNameChar(ahead.codePointAt(KEYWORD.length()));
    }

    private boolean peekSparqlDirective() throws IOException {
        String ahead = peekChars(8).toUpperCase(Locale.ROOT);
        for (String keyword : new String[] { "PREFIX", "BASE", "VERSION" }) {
            if (ahead.startsWith(keyword) && (ahead.length() == keyword.length()
                    || Character.isWhitespace(ahead.charAt(keyword.length())))) {
                return true;
            }
        }
        return false;
    }

    private String peekChars(int count) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int c = readCodePoint();
            if (c == -1) {
                break;
            }
            sb.appendCodePoint(c);
        }
        if (sb.length() > 0) {
            unread(sb.toString());
        }
        return sb.toString();
    }

    private static boolean isDirectiveStart(int c) {
        int lower = c | 0x20;
        return lower == 'p' || lower == 'b' || lower == 'v';
    }

    private static boolean isNameChar(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static String describe(int c) {
        return c == -1 ? "end of input" : "'" + new String(Character.toChars(c)) + "'";
    }
}
