import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("net.neoforged.moddev")
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenLocal()
    maven("https://maven.cloverclient.com/releases")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://repo.nyon.dev/releases")
    mavenCentral()
}

base {
    archivesName.set(project.property("archives_base_name") as String)
}

sourceSets.main {
    kotlin.srcDir(rootProject.file("src/neoforge/kotlin"))
    resources {
        srcDir("src/main/resources-neoforge")
    }
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

neoForge {
    version = project.property("neo_version") as String

    runs {
        create("client") {
            gameDirectory = file("../../run/")
            client()
            jvmArgument("-Ddevauth.enabled=true")
        }
    }

    mods {
        create("strand") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("gg.sona:eos:1.1.1")
    jarJar("gg.sona:eos:1.1.1")
    implementation(project(":common"))
    jarJar(project(":common"))

    runtimeOnly("me.djtheredstoner:DevAuth-neoforge:1.2.2")

    implementation("dev.nyon:KotlinLangForge:2.12.2-k2.4.10-3.1+neoforge")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", sc.current.version)
    inputs.property("neo_version", project.property("neo_version"))
    filteringCharset = "UTF-8"

    exclude("fabric.mod.json")

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            "version" to project.version,
            "minecraft_version" to sc.current.version,
            "neo_version" to project.property("neo_version") as String
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from(rootProject.file("LICENSE.md")) {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
    from(rootProject.file("NOTICE.md")) {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
    from(rootProject.file("PRIVACY_POLICY.md")) {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}
