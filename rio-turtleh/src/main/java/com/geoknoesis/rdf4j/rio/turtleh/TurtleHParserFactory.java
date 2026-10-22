package com.geoknoesis.rdf4j.rio.turtleh;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;

/** Registered through {@code META-INF/services}, so {@code Rio.createParser(TURTLE_H)} finds it. */
public class TurtleHParserFactory implements RDFParserFactory {

    /** Creates the factory; Rio instantiates it through the service loader. */
    public TurtleHParserFactory() {
    }

    @Override
    public RDFFormat getRDFFormat() {
        return TurtleHRDFFormat.TURTLE_H;
    }

    @Override
    public RDFParser getParser() {
        return new TurtleHParser();
    }
}
