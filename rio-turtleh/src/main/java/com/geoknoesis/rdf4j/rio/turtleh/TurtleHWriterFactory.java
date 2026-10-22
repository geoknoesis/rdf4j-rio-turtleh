package com.geoknoesis.rdf4j.rio.turtleh;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterFactory;

/** Registered through {@code META-INF/services}, so {@code Rio.createWriter(TURTLE_H, out)} finds it. */
public class TurtleHWriterFactory implements RDFWriterFactory {

    /** Creates the factory; Rio instantiates it through the service loader. */
    public TurtleHWriterFactory() {
    }

    @Override
    public RDFFormat getRDFFormat() {
        return TurtleHRDFFormat.TURTLE_H;
    }

    @Override
    public RDFWriter getWriter(OutputStream out) {
        return new TurtleHWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    @Override
    public RDFWriter getWriter(OutputStream out, String baseURI) {
        return getWriter(out);
    }

    @Override
    public RDFWriter getWriter(Writer writer) {
        return new TurtleHWriter(writer);
    }

    @Override
    public RDFWriter getWriter(Writer writer, String baseURI) {
        return getWriter(writer);
    }
}
