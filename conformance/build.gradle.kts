plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":rio-turtleh"))
    // JSON for the suite adapter protocol; kept out of the published library.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass.set("com.geoknoesis.rdf4j.rio.turtleh.conformance.TurtleHExample")
}

// Writes an adapter configuration for the RDF-H suite runner (tests/run_suite.py).
tasks.register("conformanceConfig") {
    val adapterMain = "com.geoknoesis.rdf4j.rio.turtleh.conformance.TurtleHConformanceAdapter"
    group = "verification"
    description = "Installs the adapter and writes build/conformance/adapter-config.json for the RDF-H suite runner."
    dependsOn("installDist")
    val installLib = layout.buildDirectory.dir("install/conformance/lib")
    val out = layout.buildDirectory.file("conformance/adapter-config.json")
    // The adapter must run on the Java 25 toolchain, not whatever java is on PATH.
    val java = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
        .map { it.executablePath.asFile.absolutePath.replace('\\', '/') }
    outputs.file(out)
    doLast {
        val classpath = installLib.get().asFile.absolutePath.replace('\\', '/') + "/*"
        val file = out.get().asFile
        file.parentFile.mkdirs()
        file.writeText("{\n  \"turtle-h\": [\"${java.get()}\", \"-cp\", \"$classpath\", \"$adapterMain\"]\n}\n")
    }
}
