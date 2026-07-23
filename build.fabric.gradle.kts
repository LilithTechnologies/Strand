import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("net.fabricmc.fabric-loom")
    id("dev.yumi.gradle.licenser")
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenLocal()
    maven("https://maven.cloverclient.com/releases")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    mavenCentral()
}

base {
    archivesName.set(project.property("archives_base_name") as String)
}

sourceSets.main {
    kotlin.srcDir(rootProject.file("src/fabric/kotlin"))
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

    implementation(include("gg.sona:eos:1.1.0")!!)

    implementation(project(":common"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", sc.current.version)
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to sc.current.version,
            "loader_version" to project.property("loader_version") as String,
            "kotlin_loader_version" to project.property("kotlin_loader_version") as String
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

license {
    rule(rootProject.file("codeformat/HEADER"))

    include("**/*.java")
    include("**/*.kt")
    exclude("**/*.properties")
}

loom {
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
        jvmArguments.add("-Ddevauth.enabled=true")
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
