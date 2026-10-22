package com.geoknoesis.rdf4j.rio.turtleh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Test;

public class TurtleHWriterTest {

    private static final String PREFIXES = "@prefix ex: <https://example.org/> . @prefix h: <https://w3id.org/rdf-h#> .\n";
    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();
    private static final IRI M_GRAPH = VF.createIRI("urn:test:M");

    private static IRI ex(String local) {
        return VF.createIRI("https://example.org/", local);
    }

    /** G and M in one model, M in a named graph, so blank-node sharing between them is compared jointly. */
    private static Model joint(TurtleHDocument doc) {
        Model m = new LinkedHashModel();
        doc.getData().forEach(st -> m.add(st.getSubject(), st.getPredicate(), st.getObject()));
        doc.getMetadata().forEach(st -> m.add(st.getSubject(), st.getPredicate(), st.getObject(), M_GRAPH));
        return m;
    }

    private static void assertRoundTrip(String source) throws IOException {
        TurtleHDocument first = TurtleHDocument.parse(source, "https://example.org/");
        String written = first.write();
        TurtleHDocument second = TurtleHDocument.parse(written, "https://example.org/");
        assertTrue("Round trip changed G or M. Serialized:\n" + written,
                Models.isomorphic(joint(first), joint(second)));
    }

    @Test
    public void roundTripsBlocksNestingAndManagement() throws IOException {
        assertRoundTrip(PREFIXES + "ex:A h:parentHolon ex:B . ex:B a h:Holon .\n"
                + "@holon ex:A { ex:s ex:p ex:o ; ex:q \"x\"@en--rtl . @holon ex:B { ex:t ex:p (1 2) . } }\n"
                + "@holon _:h { _:x ex:p ex:o . }\n@holon ex:Empty { }");
    }

    @Test
    public void preservesOccurrenceMultiplicity() throws IOException {
        TurtleHDocument doc = TurtleHDocument.parse(PREFIXES + "@holon ex:H { ex:s ex:p ex:o . ex:s ex:p ex:o . }", "");
        String written = doc.write();
        assertEquals(2, written.split("ex:s ex:p ex:o \\.", -1).length - 1);
        assertRoundTrip(PREFIXES + "@holon ex:H { ex:s ex:p ex:o . ex:s ex:p ex:o . }");
    }

    @Test
    public void reifiersWithOccurrenceMetadataOrIriIdentityStayExpanded() throws IOException {
        String source = PREFIXES
                + "ex:H a h:Holon .\nex:s ex:p ex:o .\n"
                + "_:r rdf:reifies <<( ex:s ex:p ex:o )>> ; h:inHolon ex:H ; ex:source ex:Doc .\n"
                + "ex:occ rdf:reifies <<( ex:s ex:p ex:o )>> ; h:inHolon ex:H .\n";
        source = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" + source;
        TurtleHDocument doc = TurtleHDocument.parse(source, "");
        String written = doc.write();
        assertTrue(written, written.contains("ex:occ h:inHolon ex:H"));
        assertTrue(written, written.contains("ex:source ex:Doc"));
        assertTrue(written, written.contains("@holon ex:H { }"));
        assertRoundTrip(source);
    }

    @Test
    public void membershipInUnregisteredHolonIsNotFolded() throws IOException {
        String source = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" + PREFIXES
                + "ex:s ex:p ex:o .\n_:r rdf:reifies <<( ex:s ex:p ex:o )>> ; h:inHolon ex:Unregistered .\n";
        String written = TurtleHDocument.parse(source, "").write();
        assertFalse(written, written.contains("@holon"));
        assertRoundTrip(source);
    }

    @Test
    public void reportsRegistrationGainedFromBlock() throws IOException {
        TurtleHDocument doc = TurtleHDocument.parse(PREFIXES + "ex:H h:contentGraph ex:G .", "");
        List<String> reports = doc.write(new StringWriter());
        assertTrue(reports.toString(), reports.stream().anyMatch(r -> r.contains("gains an h:Holon registration")));
    }

    @Test
    public void rejectsPromotionOfExpandedManagementContentBeforeWriting() throws IOException {
        String rdf = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n";
        TurtleHDocument parsed = TurtleHDocument.parse(rdf + PREFIXES
                + "ex:H a h:Holon . ex:A a h:Holon . ex:B a h:Holon .\n"
                + "ex:A h:imports ex:B .\n"
                + "ex:occ rdf:reifies <<( ex:A h:imports ex:B )>> ; h:inHolon ex:H .", "");
        Model metadata = new LinkedHashModel(parsed.getMetadata());
        metadata.remove(ex("A"), RDFH.IMPORTS, ex("B"));
        StringWriter out = new StringWriter();
        try {
            new TurtleHDocument(parsed.getData(), metadata).write(out);
            fail("Expected an unrepresentable-context rejection");
        } catch (TurtleHSerializationException expected) {
            assertTrue(expected.getMessage().contains("unrepresentable-context"));
            assertEquals("", out.toString());
        }
    }

    @Test
    public void preservesManagementContentWhenAnExistingOccurrenceCanFold() throws IOException {
        assertRoundTrip(PREFIXES + "@holon ex:H { ex:A h:imports ex:B . }");
        assertRoundTrip(PREFIXES + "@holon ex:H { ex:A h:parentHolon ex:B . }");
    }

    @Test
    public void rioWriterDerivesMetadataWithoutPromotingFiledContent() throws IOException {
        TurtleHDocument doc = TurtleHDocument.parse(PREFIXES + "@holon ex:H { ex:H h:parentHolon ex:Other . }", "");
        StringWriter out = new StringWriter();
        RDFWriter writer = Rio.createWriter(TurtleHRDFFormat.TURTLE_H, out);
        Rio.write(doc.getData(), writer);
        TurtleHDocument reparsed = TurtleHDocument.parse(out.toString(), "");
        assertFalse(out.toString(), reparsed.getMetadata().contains(ex("H"), RDFH.PARENT_HOLON, ex("Other")));
        assertTrue(reparsed.getMetadata().contains(ex("H"), RDF.TYPE, RDFH.HOLON));
    }

    /**
     * Regression guard for linear serialization. The former per-reifier graph scan took about 21 s for 8,000
     * filed statements; 30,000 must now finish well within the bound, which leaves room for slow machines.
     */
    @Test(timeout = 15_000)
    public void writesLargeHolonsInLinearTime() throws IOException {
        StringBuilder source = new StringBuilder(PREFIXES).append("@holon ex:H {\n");
        for (int i = 0; i < 30_000; i++) {
            source.append("ex:s").append(i).append(" ex:p ex:o").append(i).append(" .\n");
        }
        source.append("}\n");
        TurtleHDocument doc = TurtleHDocument.parse(source.toString(), "");
        String written = doc.write();
        assertEquals(30_000, written.split("\n    ex:s", -1).length - 1);
    }

    /**
     * Serializer conformance against the RDF-H suite: every positive Turtle-H fixture must survive
     * parse, write, parse with G and M jointly isomorphic. Runs when -PrdfHolonDir is set.
     */
    @Test
    public void suiteFixturesRoundTrip() throws IOException {
        String dir = System.getProperty("rdfHolonDir");
        assumeTrue("Set -PrdfHolonDir to run suite round trips", dir != null);
        Path cases = Path.of(dir, "tests", "turtle-h", "cases");
        int count = 0;
        try (Stream<Path> files = Files.list(cases)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".ttlh"))
                    .filter(f -> !f.getFileName().toString().startsWith("negative-")).sorted()::iterator) {
                String source = Files.readString(p, StandardCharsets.UTF_8);
                try {
                    assertRoundTrip(source);
                } catch (AssertionError e) {
                    throw new AssertionError(p.getFileName() + ": " + e.getMessage(), e);
                }
                count++;
            }
        }
        // Every expansion fixture (one .nt12 each) must have been round-tripped; serializer-only inputs have none.
        try (Stream<Path> files = Files.list(cases)) {
            assertEquals(files.filter(f -> f.toString().endsWith(".nt12")).count(), count);
        }
        assertTrue(count >= 18);
    }
}
