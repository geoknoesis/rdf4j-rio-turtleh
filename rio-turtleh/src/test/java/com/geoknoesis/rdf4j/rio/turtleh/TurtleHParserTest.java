package com.geoknoesis.rdf4j.rio.turtleh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.TripleTerm;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Test;

public class TurtleHParserTest {

    private static final String PREFIXES = "@prefix ex: <https://example.org/> . @prefix h: <https://w3id.org/rdf-h#> .\n";
    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    private static IRI ex(String local) {
        return VF.createIRI("https://example.org/", local);
    }

    private static TurtleHDocument parse(String body) throws IOException {
        return TurtleHDocument.parse(PREFIXES + body, "https://example.org/");
    }

    /** The holons in which the triple is filed, one entry per membership reifier. */
    private static java.util.List<Resource> filedIn(Model g, Resource s, IRI p, org.eclipse.rdf4j.model.Value o) {
        TripleTerm tt = VF.createTripleTerm(s, p, o);
        return g.filter(null, RDF.REIFIES, tt).subjects().stream()
                .flatMap(r -> g.filter(r, RDFH.IN_HOLON, null).objects().stream())
                .map(Resource.class::cast)
                .collect(Collectors.toList());
    }

    @Test
    public void emptyBlockRegistersHolonInGAndM() throws IOException {
        TurtleHDocument doc = parse("@holon ex:H { }");
        assertTrue(doc.getData().contains(ex("H"), RDF.TYPE, RDFH.HOLON));
        assertTrue(doc.getMetadata().contains(ex("H"), RDF.TYPE, RDFH.HOLON));
        assertEquals(1, doc.getData().size());
    }

    @Test
    public void blockStatementIsAssertedAndFiledOnce() throws IOException {
        Model g = parse("@holon ex:H { ex:s ex:p ex:o . }").getData();
        assertTrue(g.contains(ex("s"), ex("p"), ex("o")));
        assertEquals(java.util.List.of(ex("H")), filedIn(g, ex("s"), ex("p"), ex("o")));
        // The generated registration and membership triples are not themselves filed.
        assertTrue(filedIn(g, ex("H"), RDF.TYPE, RDFH.HOLON).isEmpty());
        assertEquals(4, g.size());
    }

    @Test
    public void nestingCreatesNoParentAndFilesInnerContentOnlyInInnerHolon() throws IOException {
        TurtleHDocument doc = parse("@holon ex:Outer { ex:a ex:p ex:b . @holon ex:Inner { ex:c ex:p ex:d . } }");
        assertEquals(java.util.List.of(ex("Inner")), filedIn(doc.getData(), ex("c"), ex("p"), ex("d")));
        assertEquals(java.util.List.of(ex("Outer")), filedIn(doc.getData(), ex("a"), ex("p"), ex("b")));
        assertFalse(doc.getData().contains(null, RDFH.PARENT_HOLON, null));
        assertFalse(doc.getMetadata().contains(null, RDFH.PARENT_HOLON, null));
    }

    @Test
    public void managementStatementsInsideBlocksAreContentNotMetadata() throws IOException {
        TurtleHDocument doc = parse("ex:A h:parentHolon ex:B .\n@holon ex:H { ex:H h:parentHolon ex:Other . }");
        assertTrue(doc.getMetadata().contains(ex("A"), RDFH.PARENT_HOLON, ex("B")));
        assertFalse(doc.getMetadata().contains(ex("H"), RDFH.PARENT_HOLON, ex("Other")));
        assertTrue(doc.getData().contains(ex("H"), RDFH.PARENT_HOLON, ex("Other")));
    }

    @Test
    public void ordinaryTopLevelStatementsStayOutOfM() throws IOException {
        TurtleHDocument doc = parse("ex:s ex:p ex:o . ex:X a ex:Thing . ex:Y a h:CompleteHolon .");
        assertEquals(Set.of(VF.createStatement(ex("Y"), RDF.TYPE, RDFH.COMPLETE_HOLON)),
                Set.copyOf(doc.getMetadata()));
    }

    @Test
    public void repeatedStatementsGetDistinctOccurrences() throws IOException {
        Model g = parse("@holon ex:H { ex:s ex:p ex:o . ex:s ex:p ex:o . }").getData();
        assertEquals(2, filedIn(g, ex("s"), ex("p"), ex("o")).size());
    }

    @Test
    public void everyGeneratedTripleOfACollectionIsFiled() throws IOException {
        Model g = parse("@holon ex:H { ex:s ex:items (ex:a ex:b) . }").getData();
        long reifiers = g.filter(null, RDFH.IN_HOLON, ex("H")).size();
        assertEquals(5, reifiers); // items link plus two first/rest pairs
    }

    @Test
    public void annotationTriplesAreFiledAsEvents() throws IOException {
        Model g = parse("@holon ex:H { ex:s ex:p ex:o ~ ex:ann {| ex:source ex:Doc |} . }").getData();
        assertEquals(java.util.List.of(ex("H")), filedIn(g, ex("ann"), ex("source"), ex("Doc")));
        assertEquals(java.util.List.of(ex("H")),
                filedIn(g, ex("ann"), RDF.REIFIES, VF.createTripleTerm(ex("s"), ex("p"), ex("o"))));
    }

    @Test
    public void blankNodeHolonSharesIdentityBetweenGAndM() throws IOException {
        TurtleHDocument doc = parse("@holon _:h { ex:s ex:p ex:o . }");
        Resource inG = (Resource) doc.getData().filter(null, RDFH.IN_HOLON, null).objects().iterator().next();
        assertTrue(inG instanceof BNode);
        assertTrue(doc.getMetadata().contains(inG, RDF.TYPE, RDFH.HOLON));
    }

    @Test
    public void directionalLiteralsKeepLanguageAndDirection() throws IOException {
        Model g = parse("@holon ex:H { ex:s ex:p \"hello\"@en--ltr . }").getData();
        Literal lit = (Literal) g.filter(ex("s"), ex("p"), null).objects().iterator().next();
        assertEquals("en", lit.getLanguage().orElseThrow());
        assertEquals(Literal.BaseDirection.LTR, lit.getBaseDirection());
    }

    @Test
    public void keywordInsideStringsAndCommentsIsNotABlock() throws IOException {
        TurtleHDocument doc = parse("# @holon ex:Fake {\n@holon ex:H { ex:s ex:p \"@holon ex:Fake { }\" . }");
        assertFalse(doc.getMetadata().contains(ex("Fake"), null, null));
    }

    @Test
    public void baseResolutionAppliesToHolonIdentifiers() throws IOException {
        TurtleHDocument doc = TurtleHDocument.parse("@base <https://example.org/base/> .\n@holon <H> { }", "");
        assertTrue(doc.getMetadata().contains(VF.createIRI("https://example.org/base/H"), RDF.TYPE, RDFH.HOLON));
    }

    @Test
    public void boundsBlockNestingDepth() throws IOException {
        StringBuilder deep = new StringBuilder(PREFIXES);
        for (int i = 0; i < 300; i++) {
            deep.append("@holon ex:H").append(i).append(" { ");
        }
        deep.append("}".repeat(300));
        assertThrows(RDFParseException.class, () -> TurtleHDocument.parse(deep.toString(), ""));
        TurtleHParser lenient = new TurtleHParser();
        lenient.setMaxNestingDepth(400);
        lenient.setRDFHandler(new org.eclipse.rdf4j.rio.helpers.StatementCollector());
        lenient.parse(new StringReader(deep.toString()), "");
        assertEquals(300, lenient.getMetadata().size());
        assertThrows(IllegalArgumentException.class, () -> new TurtleHParser().setMaxNestingDepth(0));
    }

    @Test
    public void rejectsAnonymousHolonIdentifier() {
        assertThrows(RDFParseException.class, () -> parse("@holon [] { ex:s ex:p ex:o . }"));
    }

    @Test
    public void rejectsDirectivesInsideBlocks() {
        assertThrows(RDFParseException.class, () -> parse("@holon ex:H { @prefix x: <https://x/> . }"));
        assertThrows(RDFParseException.class, () -> parse("@holon ex:H { PREFIX x: <https://x/> }"));
    }

    @Test
    public void rejectsUppercaseKeywordAndBareTriG() {
        assertThrows(RDFParseException.class, () -> parse("@HOLON ex:H { }"));
        assertThrows(RDFParseException.class, () -> parse("ex:H { ex:s ex:p ex:o . }"));
    }

    @Test
    public void rejectsMissingDotInsideBlock() {
        assertThrows(RDFParseException.class, () -> parse("@holon ex:H { ex:s ex:p ex:o }"));
    }

    @Test
    public void rioDiscoversTheFormatAndReturnsG() throws IOException {
        Model g = Rio.parse(new StringReader(PREFIXES + "@holon ex:H { ex:s ex:p ex:o . }"), "", TurtleHRDFFormat.TURTLE_H);
        assertTrue(g.contains(ex("s"), ex("p"), ex("o")));
        assertEquals(TurtleHRDFFormat.TURTLE_H, Rio.getParserFormatForFileName("doc.ttlh").orElseThrow());
        assertTrue(Rio.getWriterFormatForFileName("doc.ttlh").isPresent());
    }

    @Test
    public void plainTurtleIsUnchanged() throws IOException {
        TurtleHDocument doc = parse("ex:s ex:p ex:o .");
        assertEquals(Set.of(VF.createStatement(ex("s"), ex("p"), ex("o"))), Set.copyOf(doc.getData()));
        assertTrue(doc.getMetadata().isEmpty());
    }
}
