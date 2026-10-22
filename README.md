# RDF4J Rio Turtle-H codecs

An [RDF4J](https://rdf4j.org/) Rio parser and writer for **Turtle-H 0.1.0**, the `@holon` block syntax of
[RDF-H 0.8](https://geoknoesis.github.io/rdf-holon/). Turtle-H is Turtle 1.2 plus one construct:

```turtle
@prefix ex: <https://example.org/> .
@prefix h:  <https://w3id.org/rdf-h#> .

ex:Engine h:parentHolon ex:Car .   # top-level management statement: copied into M

@holon ex:Car {
    ex:Car ex:name "Car 123" .
    @holon ex:Engine {             # nesting files content; it creates no parent link
        ex:Engine ex:power 150 .
    }
}
```

A block expands to RDF 1.2: each triple is asserted, and a fresh reifier records its membership.

```turtle
ex:Car ex:name "Car 123" .
_:r1 rdf:reifies <<( ex:Car ex:name "Car 123" )>> ; h:inHolon ex:Car .
ex:Car a h:Holon .                 # every block registers its holon in G and M
```

## Conformance

The codecs pass all **81** cases of the RDF-H Turtle-H suite, run through the suite's own runner with an EARL
report: 22 positive syntax, 14 negative syntax, 22 expansion (joint G/M comparison), and 22 serialization tests.

What the parser implements, following the Turtle-H expansion algorithm:

- Built on RDF4J 6.1.0's RDF 1.2 `TurtleParser`: triple terms, reifiers, annotations, and directional
  literals (`"x"@en--ltr`) come from the base parser rather than text rewriting.
- Every asserted triple event inside a block is filed, including collection, blank-node property list,
  reifier, and annotation triples. Repeated statements get distinct reifiers.
- Generated membership and registration triples are never filed again.
- Every block registers `H a h:Holon` in both G and M, even when empty.
- Top-level management statements (`h:Holon`/`h:CompleteHolon` typing, `h:contentGraph`, `h:parentHolon`,
  `h:imports`, `h:importsShapes`) are copied to M; the same predicates inside blocks are content only.
- Block nesting is bounded (256 levels by default, `setMaxNestingDepth`), as the specification recommends
  for untrusted input.
- `@holon` is case-sensitive; identifiers are IRIs or labeled blank nodes (`[]` is rejected); directives
  (`@prefix`, `PREFIX`, …) are rejected inside blocks; text inside strings and comments is never a block.

The writer implements the specification's folding rules: only a blank-node reifier with exactly one
`rdf:reifies` and one `h:inHolon`, an asserted base triple, a holon registered in M, and no other mentions is
folded into a block. IRI reifiers, reifiers with occurrence metadata, and memberships in unregistered holons are
written expanded. Repeated occurrences are written as repeated statements, so multiplicity survives a round
trip. Differences a re-parse cannot reproduce (fresh reifier labels, registrations gained from blocks, M reduced
to management statements) are returned by `TurtleHWriter.getReports()`.

## Performance

Measured with `TurtleHBenchmark` at 100,000 statements filed in one holon (300,000 output statements), median of
7 runs after warm-up:

| Operation | Time |
|---|---|
| Parse to an `RDFHandler` | ~390 ms; within noise of RDF4J's own Turtle parser on the same statements without blocks |
| `TurtleHDocument.parse` (G and M as models) | ~490 ms |
| `TurtleHDocument.write` | ~220 ms; about 20× faster than RDF4J's Turtle writer on the same graph |

The parser adds 1–3% to RDF4J's Turtle parsing; the rest is the base parser. `TurtleHDocument` returns
`DynamicModel`s, which keep insertion order and build their indexes only when `filter()` is first called. The
writer indexes statements in one pass with plain hash structures, so its time and memory are linear; a unit test
guards against regressions. Run the benchmark with a Java 25 `java`:

```bash
./gradlew :conformance:installDist
java -cp "conformance/build/install/conformance/lib/*" \
    com.geoknoesis.rdf4j.rio.turtleh.conformance.TurtleHBenchmark 100000 7
```

## Usage

Maven coordinates: `com.geoknoesis:rdf4j-rio-turtleh:0.1.0`. Requires Java 25 (RDF4J 6.x is compiled for it);
Gradle provisions the toolchain. The library depends on RDF4J only.

```java
// G and M together (recommended for RDF-H processing)
TurtleHDocument doc = TurtleHDocument.parse(reader, baseIri);
Model g = doc.getData();
Model m = doc.getMetadata();
String turtleH = doc.write();

// Plain Rio: the format is registered through META-INF/services
Model g = Rio.parse(reader, baseIri, TurtleHRDFFormat.TURTLE_H);
Rio.write(g, writer, TurtleHRDFFormat.TURTLE_H);   // M derived from unfiled management statements
```

`Rio.parse` returns only G. Use `TurtleHDocument` or `TurtleHParser.getMetadata()` when you need M; G and M
share blank-node identity. The format uses the proposed media type `text/turtle-h` and extension `.ttlh`.

## Build and test

```bash
./gradlew build                                      # compile, unit tests, sources and javadoc jars
./gradlew test -PrdfHolonDir=/path/to/rdf-holon      # also round-trip every suite fixture through the writer
./gradlew publishToMavenLocal                        # install com.geoknoesis:rdf4j-rio-turtleh locally
./gradlew :conformance:run                           # example: parse, print G and M, write back
```

## Run the RDF-H conformance suite

```bash
./gradlew conformanceConfig        # installs the adapter, writes conformance/build/conformance/adapter-config.json
cd /path/to/rdf-holon
python tests/run_suite.py --manifest tests/turtle-h/manifest.ttl \
    --adapter-config /path/to/rdf4j-rio-turtleh/conformance/build/conformance/adapter-config.json \
    --require-all --implementation urn:geoknoesis:rdf4j-rio-turtleh \
    --report /path/to/rdf4j-rio-turtleh/conformance/build/conformance/earl.ttl
```

The adapter (`conformance.TurtleHConformanceAdapter`) reads one JSON request on stdin and answers per the suite
guide's adapter protocol: `parse`, `evaluate`, and `serialize`. It implements Turtle-H only; SPARQL-H requests
are answered `unsupported`.

## Layout

| Module | Contents |
|---|---|
| `rio-turtleh` | The published library (`rdf4j-rio-turtleh`): parser, writer, format, document API |
| `conformance` | Suite adapter, example, and `conformanceConfig` task; adds Jackson; not published |

| Class | Purpose |
|---|---|
| `TurtleHParser` | Rio parser; G to the handler, M from `getMetadata()` |
| `TurtleHWriter` | Rio writer with the specification's folding rules |
| `TurtleHDocument` | Parse to, and write from, G and M together |
| `TurtleHRDFFormat` | `text/turtle-h`, `.ttlh`, RDF 1.2 terms |
| `RDFH` | RDF-H vocabulary terms and the management-statement test |
| `conformance.TurtleHConformanceAdapter` | Adapter for the RDF-H suite runner |

## License

Apache License 2.0; see [LICENSE](LICENSE).

Serialization rejects an unrepresentable G/M pair with `TurtleHSerializationException`
before writing output if a top-level statement would activate management content absent from M.
The conformance adapter reports category and condition `unrepresentable-context`.
