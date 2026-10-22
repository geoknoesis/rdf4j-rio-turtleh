plugins {
    `java-library`
    `maven-publish`
}

group = "com.geoknoesis"
version = "0.1.0"

repositories {
    mavenCentral()
}

val rdf4jVersion = "6.1.0"

dependencies {
    // RDF4J 6.x provides the RDF 1.2 Turtle parser (triple terms, reifiers, annotations, directional literals).
    api("org.eclipse.rdf4j:rdf4j-rio-api:$rdf4jVersion")
    api("org.eclipse.rdf4j:rdf4j-model:$rdf4jVersion")
    // api, not implementation: TurtleHParser extends TurtleParser, so callers compile against it.
    api("org.eclipse.rdf4j:rdf4j-rio-turtle:$rdf4jVersion")

    testImplementation(libs.junit)
}

// RDF4J 6.x is compiled for Java 25.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName = "rdf4j-rio-turtleh"
}

tasks.test {
    // Writer round trips over the RDF-H suite fixtures run when -PrdfHolonDir points at a checkout.
    providers.gradleProperty("rdfHolonDir").orNull?.let { systemProperty("rdfHolonDir", it) }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "rdf4j-rio-turtleh"
            from(components["java"])
            pom {
                name = "RDF4J Rio Turtle-H"
                description = "RDF4J Rio parser and writer for Turtle-H 0.1.0, the @holon block syntax of RDF-H 0.8."
                url = "https://geoknoesis.github.io/rdf-holon/turtle-h/"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name = "Stephane Fellah"
                        organization = "Geoknoesis LLC"
                        organizationUrl = "https://geoknoesis.com"
                    }
                }
            }
        }
    }
}
