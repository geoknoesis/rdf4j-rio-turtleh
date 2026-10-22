package com.geoknoesis.rdf4j.rio.turtleh;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.rdf4j.rio.RDFFormat;

/**
 * The Turtle-H format: Turtle 1.2 plus {@code @holon} blocks, as defined by the Turtle-H 0.1.0 companion
 * specification of RDF-H. The media type {@code text/turtle-h} is proposed and not yet registered.
 */
public final class TurtleHRDFFormat {

    /** Turtle-H: media type {@code text/turtle-h}, extension {@code .ttlh}, UTF-8, RDF 1.2 terms. */
    public static final RDFFormat TURTLE_H = new RDFFormat(
            "Turtle-H",
            List.of("text/turtle-h"),
            StandardCharsets.UTF_8,
            List.of("ttlh"),
            true, // namespaces
            false, // contexts: Turtle-H is a single-graph syntax
            true); // RDF 1.2 triple terms

    private TurtleHRDFFormat() {
    }
}
