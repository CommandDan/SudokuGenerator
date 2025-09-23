import java.nio.file.Files

plugins {
    kotlin("jvm") version "2.2.20"
    application
    id("com.gradleup.shadow") version "9.1.0"
}

repositories {
    mavenCentral()
}

val openPDFVersion = "3.0.0"
val cliktVersion = "5.0.3"
dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))
    // OpenPDF
    implementation("com.github.librepdf:openpdf:$openPDFVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")

    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:$cliktVersion")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("$group.SudokuApp")
}

// Reproducible JAR'er (ingen fil-timestamps, stabil orden)
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Shadow JAR: én kørbar -all.jar med Main-Class sat
tasks.shadowJar {
    archiveBaseName.set("SudokuGenerator")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("all")

    manifest.attributes(
        mapOf("Main-Class" to application.mainClass.get())
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // mergeServiceFiles() // hvis du får service/metadata-konflikter
    // undlad minimize() for OpenPDF/TTF/kaml
}


// Eksterne filer
val fontsDir = layout.projectDirectory.dir("src/main/resources/fonts")
val readmeFile = layout.projectDirectory.file("README.md")

// Hjælpere til at kopiere konditionelt
fun CopySpec.maybeInclude(file: RegularFile) {
    if (file.asFile.exists()) from(file)
}
fun CopySpec.maybeIncludeDir(dir: Directory) {
    if (dir.asFile.exists() && dir.asFile.list()?.isNotEmpty() == true) from(dir) { into("fonts") }
}

tasks.register<Zip>("releaseZip") {
    description = "Pack a ZIP archive for release."
    group = "release"
    dependsOn(tasks.shadowJar)

    val baseName = "DanishCrosswordGenerator"
    val versionStr = project.version.toString()
    archiveFileName.set("$baseName-$versionStr.zip")
    destinationDirectory.set(layout.buildDirectory.dir("releases"))

    // ALT ind i roden af zip (ingen dybe mapper)
    from(tasks.shadowJar.get().archiveFile) {
        rename { "${baseName}-${versionStr}-all.jar" }
    }
    // valgfrit indhold
    maybeInclude(readmeFile)
    maybeIncludeDir(fontsDir)

    // læg en lille RUN.txt med kommandoeksempler
    val runTxt = layout.buildDirectory.file("tmp/RUN.txt")
    doFirst {
        val text = """
            Kørselseksempler:
              java -jar ${baseName}-${versionStr}-all.jar --mode single --out sudoku.pdf --solution-page --seed 123 --min-givens 30
              java -jar ${baseName}-${versionStr}-all.jar --mode single --out sudoku-6x6.pdf --n 6 --box-rows 2 --box-cols 3
        """.trimIndent()
        Files.createDirectories(runTxt.get().asFile.parentFile.toPath())
        runTxt.get().asFile.writeText(text, Charsets.UTF_8)
    }
    from(runTxt) { rename { "RUN.txt" } }

    // reproducible zip
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// --- Release version guard: ensure project.version matches tag "vX.Y.Z" ---
tasks.register("releaseVersion") {
    group = "release"
    description = "Validates that project.version matches the provided release tag (vX.Y.Z)."

    doLast {
        val projectVersion = project.version.toString()

        // Tag kan komme fra:
        //  - CI: GITHUB_REF=refs/tags/v1.2.3
        //  - CLI: -PreleaseTag=v1.2.3
        //  - (fallback) TAG env
        val fromGithubRef = System.getenv("GITHUB_REF") ?: ""
        val tagFromGithub = fromGithubRef.substringAfter("refs/tags/", missingDelimiterValue = "")
        val tagFromProp = (project.findProperty("releaseTag") as String?) ?: ""
        val tagFromEnv = System.getenv("TAG") ?: ""

        val rawTag = listOf(tagFromProp, tagFromGithub, tagFromEnv).firstOrNull { it.isNotBlank() } ?: ""

        if (rawTag.isBlank()) {
            throw GradleException(
                "No release tag provided. Supply -PreleaseTag=vX.Y.Z or set GITHUB_REF/ TAG environment variable."
            )
        }

        // Forventet format: vX.Y.Z (evt. med prærelease/build metadata, justér regex hvis ønsket)
        val tagRegex = Regex("""^v(\d+\.\d+\.\d+)(?:[-+].*)?$""")
        val match = tagRegex.matchEntire(rawTag)
            ?: throw GradleException("Invalid tag format '$rawTag'. Expected 'vX.Y.Z' (e.g., v1.2.3).")

        val tagVersion = match.groupValues[1] // uden 'v'

        if (projectVersion != tagVersion) {
            throw GradleException(
                "Project version ($projectVersion) does not match tag ($rawTag). " +
                        "Update version in gradle.properties to '$tagVersion' or retag."
            )
        }

        println("✔ releaseVersion: tag $rawTag matches project.version=$projectVersion")
    }
}