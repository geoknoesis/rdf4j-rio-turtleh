package com.geoknoesis.rdf4j.rio.turtleh.conformance;

import java.io.IOException;

import org.eclipse.rdf4j.model.Statement;

import com.geoknoesis.rdf4j.rio.turtleh.TurtleHDocument;

/**
 * Parses a small Turtle-H document, prints G and M, and serializes it back to Turtle-H.
 */
public final class TurtleHExample {

    private TurtleHExample() {
    }

    public static void main(String[] args) throws IOException {
        String source = """
                @prefix ex: <https://example.org/> .
                @prefix h: <https://w3id.org/rdf-h#> .

                ex:Engine h:parentHolon ex:Car .

                @holon ex:Car {
                    ex:Car ex:name "Car 123" .
                    @holon ex:Engine {
                        ex:Engine ex:power "150"^^<http://www.w3.org/2001/XMLSchema#integer> .
                    }
                }
                """;

        TurtleHDocument document = TurtleHDocument.parse(source, "https://example.org/");

        System.out.println("# Asserted graph G");
        for (Statement st : document.getData()) {
            System.out.println(st);
        }
        System.out.println("\n# Metadata context M");
        for (Statement st : document.getMetadata()) {
            System.out.println(st);
        }
        System.out.println("\n# Serialized Turtle-H");
        System.out.print(document.write());
    }
}
