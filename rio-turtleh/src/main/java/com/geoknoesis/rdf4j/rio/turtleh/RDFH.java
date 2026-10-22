package com.geoknoesis.rdf4j.rio.turtleh;

import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;

/**
 * Terms of the RDF-H vocabulary ({@code https://w3id.org/rdf-h#}) used by Turtle-H.
 */
public final class RDFH {

    /** The RDF-H namespace IRI. */
    public static final String NAMESPACE = "https://w3id.org/rdf-h#";
    /** The conventional prefix for the RDF-H namespace. */
    public static final String PREFIX = "h";

    /** {@code h:Holon}: an identifiable unit with explicitly filed RDF content. */
    public static final IRI HOLON = iri("Holon");
    /** Deprecated legacy holon type; registers a holon and carries no completeness guarantee. */
    public static final IRI COMPLETE_HOLON = iri("CompleteHolon");
    /** {@code h:inHolon}: links a reifier to the holon in which its reified triple is filed. */
    public static final IRI IN_HOLON = iri("inHolon");
    /** {@code h:contentGraph}: selects the named graph holding a holon's content (dataset profile). */
    public static final IRI CONTENT_GRAPH = iri("contentGraph");
    /** {@code h:parentHolon}: the immediate administrative parent of a holon. */
    public static final IRI PARENT_HOLON = iri("parentHolon");
    /** {@code h:imports}: adds another holon's direct content to visible scope. */
    public static final IRI IMPORTS = iri("imports");
    /** {@code h:importsShapes}: selects a shapes graph for validating a holon. */
    public static final IRI IMPORTS_SHAPES = iri("importsShapes");

    private static final Set<IRI> MANAGEMENT_PREDICATES = Set.of(CONTENT_GRAPH, PARENT_HOLON, IMPORTS, IMPORTS_SHAPES);

    private RDFH() {
    }

    private static IRI iri(String localName) {
        return SimpleValueFactory.getInstance().createIRI(NAMESPACE, localName);
    }

    /**
     * True for the management statements that a Turtle-H parser copies from top level into M:
     * holon and legacy complete-holon typing, and graph-mapping, parent, import, and shape-import declarations.
     *
     * @param predicate the statement predicate
     * @param object    the statement object
     * @return whether a statement with this predicate and object is a management statement
     */
    public static boolean isManagement(IRI predicate, Value object) {
        if (RDF.TYPE.equals(predicate)) {
            return HOLON.equals(object) || COMPLETE_HOLON.equals(object);
        }
        return MANAGEMENT_PREDICATES.contains(predicate);
    }

    /**
     * Statement form of {@link #isManagement(IRI, Value)}.
     *
     * @param st the statement to test
     * @return whether the statement is a management statement
     */
    public static boolean isManagement(Statement st) {
        return isManagement(st.getPredicate(), st.getObject());
    }
}
