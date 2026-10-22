package com.geoknoesis.rdf4j.rio.turtleh.conformance;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.function.Supplier;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;

import com.geoknoesis.rdf4j.rio.turtleh.TurtleHDocument;
import com.geoknoesis.rdf4j.rio.turtleh.TurtleHParser;

/**
 * Throughput benchmark for the Turtle-H codecs. Prints median times over several runs after warm-up.
 * <p>
 * Usage: {@code TurtleHBenchmark [statements] [runs]}. Compares against RDF4J's own Turtle parser and writer on
 * the expanded form, so the numbers show the cost Turtle-H adds rather than raw machine speed.
 */
public final class TurtleHBenchmark {

    private static final String PREFIXES = "@prefix ex: <https://example.org/> .\n@prefix h: <https://w3id.org/rdf-h#> .\n";

    private TurtleHBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 7;

        String oneHolon = block(n, 1);
        String manyHolons = block(n, n / 10);
        String plain = plainTurtle(n);
        String expanded = expandedTurtle(oneHolon);
        TurtleHDocument parsed = TurtleHDocument.parse(oneHolon, "");
        TurtleHDocument parsedMany = TurtleHDocument.parse(manyHolons, "");

        System.out.printf("statements=%,d  runs=%d (median, after warm-up)%n", n, runs);
        report("parse  Turtle-H, 1 holon, to handler", runs, () -> parseToHandler(oneHolon));
        report("parse  Turtle-H, n/10 holons, to handler", runs, () -> parseToHandler(manyHolons));
        report("parse  Turtle-H, no blocks, to handler", runs, () -> parseToHandler(plain));
        report("parse  RDF4J Turtle, same plain input", runs, () -> rdf4jTurtle(plain));
        report("parse  RDF4J Turtle, expanded 1-holon output", runs, () -> rdf4jTurtle(expanded));
        report("parse  TurtleHDocument (G+M models), 1 holon", runs, () -> document(oneHolon));
        report("write  Turtle-H, 1 holon", runs, () -> parsed.write().length());
        report("write  Turtle-H, n/10 holons", runs, () -> parsedMany.write().length());
        report("write  RDF4J Turtle, same G", runs, () -> rdf4jWrite(parsed.getData()));
    }

    private static String block(int n, int holons) {
        StringBuilder sb = new StringBuilder(PREFIXES);
        int perHolon = Math.max(1, n / holons);
        for (int h = 0, i = 0; h < holons; h++) {
            sb.append("@holon ex:H").append(h).append(" {\n");
            for (int k = 0; k < perHolon && i < n; k++, i++) {
                sb.append("ex:s").append(i).append(" ex:p \"value ").append(i).append("\"@en .\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private static String plainTurtle(int n) {
        StringBuilder sb = new StringBuilder(PREFIXES);
        for (int i = 0; i < n; i++) {
            sb.append("ex:s").append(i).append(" ex:p \"value ").append(i).append("\"@en .\n");
        }
        return sb.toString();
    }

    private static String expandedTurtle(String turtleH) throws IOException {
        StringWriter out = new StringWriter();
        Rio.write(TurtleHDocument.parse(turtleH, "").getData(), out, RDFFormat.TURTLE);
        return out.toString();
    }

    private static int parseToHandler(String text) {
        int[] count = new int[1];
        TurtleHParser parser = new TurtleHParser();
        parser.setRDFHandler(new AbstractRDFHandler() {
            @Override
            public void handleStatement(org.eclipse.rdf4j.model.Statement st) {
                count[0]++;
            }
        });
        try {
            parser.parse(new StringReader(text), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return count[0];
    }

    private static int rdf4jTurtle(String text) {
        int[] count = new int[1];
        var parser = Rio.createParser(RDFFormat.TURTLE);
        parser.setRDFHandler(new AbstractRDFHandler() {
            @Override
            public void handleStatement(org.eclipse.rdf4j.model.Statement st) {
                count[0]++;
            }
        });
        try {
            parser.parse(new StringReader(text), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return count[0];
    }

    private static int document(String text) {
        try {
            return TurtleHDocument.parse(text, "").getData().size();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int rdf4jWrite(Model g) {
        StringWriter out = new StringWriter();
        Rio.write(g, out, RDFFormat.TURTLE);
        return out.getBuffer().length();
    }

    private static void report(String label, int runs, Supplier<Integer> task) {
        for (int i = 0; i < 2; i++) {
            task.get();
        }
        long[] times = new long[runs];
        int result = 0;
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            result = task.get();
            times[i] = System.nanoTime() - t0;
        }
        Arrays.sort(times);
        System.out.printf("  %-48s %8.1f ms   (%,d)%n", label, times[runs / 2] / 1e6, result);
    }
}
