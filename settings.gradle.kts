plugins {
    // Provisions the Java 25 toolchain that RDF4J 6.x requires.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "rdf4j-rio-turtleh"

// The published library: RDF4J dependencies only.
include("rio-turtleh")
// Conformance-suite adapter and example; not published.
include("conformance")
