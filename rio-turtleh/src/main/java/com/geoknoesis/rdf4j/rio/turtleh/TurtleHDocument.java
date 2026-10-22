package com.geoknoesis.rdf4j.rio.turtleh;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

/**
 * A parsed Turtle-H document: the asserted graph G and the metadata context M, sharing blank-node identity.
 * <p>
 * Rio's {@code Rio.parse} returns only G. Use this class when RDF-H processing needs M as well.
 */
public final class TurtleHDocument {

    private final Model data;
    private final Model metadata;

    /**
     * Pairs an asserted graph with its metadata context; the two should share blank-node identity.
     *
     * @param data     the asserted graph G
     * @param metadata the metadata context M
     */
    public TurtleHDocument(Model data, Model metadata) {
        this.data = data;
        this.metadata = metadata;
    }

    /**
     * The asserted graph G, including base assertions, membership records, and block registrations.
     *
     * @return G
     */
    public Model getData() {
        return data;
    }

    /**
     * The metadata context M: block registrations and top-level management statements.
     *
     * @return M
     */
    public Model getMetadata() {
        return metadata;
    }

    /**
     * Parses a Turtle-H document into G and M.
     *
     * @param reader  the Turtle-H source
     * @param baseURI the base IRI for relative references, or {@code null}
     * @return the parsed document
     * @throws IOException if reading fails
     * @throws org.eclipse.rdf4j.rio.RDFParseException if the source is not valid Turtle-H
     */
    public static TurtleHDocument parse(Reader reader, String baseURI) throws IOException {
        // DynamicModel stores statements in insertion order and builds indexes only when filter() is first used.
        Model data = new DynamicModelFactory().createEmptyModel();
        TurtleHParser parser = new TurtleHParser();
        StatementCollector collector = new StatementCollector(data);
        parser.setRDFHandler(collector);
        parser.parse(reader, baseURI == null ? "" : baseURI);
        collector.getNamespaces().forEach(data::setNamespace);
        return new TurtleHDocument(data, parser.getMetadata());
    }

    /**
     * Parses a Turtle-H string into G and M.
     *
     * @param text    the Turtle-H source
     * @param baseURI the base IRI for relative references, or {@code null}
     * @return the parsed document
     * @throws IOException if reading fails
     * @throws org.eclipse.rdf4j.rio.RDFParseException if the source is not valid Turtle-H
     */
    public static TurtleHDocument parse(String text, String baseURI) throws IOException {
        return parse(new StringReader(text), baseURI);
    }

    /**
     * Serializes G with M using the Turtle-H folding rules.
     *
     * @param out the destination
     * @return the notes the serializer reports about information a re-parse will not reproduce exactly
     */
    public List<String> write(Writer out) {
        TurtleHWriter writer = new TurtleHWriter(out);
        writer.setMetadata(metadata);
        writer.startRDF();
        data.getNamespaces().forEach(ns -> writer.handleNamespace(ns.getPrefix(), ns.getName()));
        data.forEach(writer::handleStatement);
        writer.endRDF();
        return writer.getReports();
    }

    /**
     * Serializes G with M using the Turtle-H folding rules.
     *
     * @return the Turtle-H text
     */
    public String write() {
        StringWriter out = new StringWriter();
        write(out);
        return out.toString();
    }
}
