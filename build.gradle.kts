// :tisam — the NeoForge mod.
//
// Hellow is a separate mod jar, so it is a compile-time-only dependency here:
// players install hellow themselves (declared as a required mod in
// neoforge.mods.toml) and TISAM never bundles it.

plugins {
    id("net.neoforged.moddev").version("2.0.141")
}

neoForge {
    version = "21.1.241"

    parchment {
        mappingsVersion.set("2024.11.17")
        minecraftVersion.set("1.21.1")
    }

    mods {
        create("tisam") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Mod metadata (must match the values in META-INF/neoforge.mods.toml).
object ModConfig {
    const val MINECRAFT_VERSION = "1.21.1"
    const val MINECRAFT_VERSION_RANGE = "[1.21.1]"
    const val NEO_VERSION = "21.1.241"
    const val NEO_VERSION_RANGE = "[21.1.233,)"
    const val LOADER_VERSION_RANGE = "[1,)"
    const val MOD_ID = "tisam"
    const val MOD_NAME = "There Is Still A Mod"
    const val MOD_LICENSE = "Apache-2.0"
    const val MOD_VERSION = "1.0.0"
    const val MOD_DISPLAY_URL = "https://modrinth.com/mod/tisam"
    const val MOD_AUTHORS = "filowxy"
    const val MOD_DESCRIPTION = "TISAM — a compliance-friendly optional mod recommender for modpack authors. Never bundles any third-party mod files; all downloads are user-initiated through official channels."
}

version = ModConfig.MOD_VERSION
group = "com.tisam"

dependencies {
    // Hellow is a separate mod: compile against its jar, resolve it at runtime
    // through the NeoForge mod dependency declared in neoforge.mods.toml.
    compileOnly(files("libs/hellow-1.0.0.jar"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Gson is a runtime dep of Minecraft; the unit-test classpath doesn't inherit
    // it, and ModuleEntry's config tests exercise the lenient deserializer directly.
    testImplementation("com.google.code.gson:gson:2.11.0")
    // Gradle's jvm-test-suite plugin requires the launcher on the runtime
    // classpath, not just the test classpath. Without this you get a
    // "Failed to load JUnit Platform" error at test-execution time.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // SLF4J is provided by the Minecraft launcher at runtime but is
    // missing from the unit-test runtime classpath.
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.9")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
