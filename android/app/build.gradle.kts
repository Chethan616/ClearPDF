import java.util.Properties
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Rust half of the app: the cargo workspace in engine/ beside this Gradle project, whose
// `artefact-mobile` crate (engine/mobile) is the library the app loads.
val repoDir: File = rootDir.parentFile
val cargoWorkspaceDir: File = File(repoDir, "engine")
val rustAbis: List<String> =
    providers.gradleProperty("artefact.abis").getOrElse("arm64-v8a,x86_64").split(',').map { it.trim() }.filter { it.isNotEmpty() }
val minApi = 29

// The app's version: gradle.properties' artefact.version, or the version tag's when the release
// workflow builds a release. The version code follows from it (0.0.1-beta → 101, 0.0.1-beta.2 →
// 102, 0.0.1 → 199, 0.1.0-beta → 10001), so a pre-release comes before its release and every
// release before the next version's pre-releases; times ten plus the ABI of a one-ABI build
// (armeabi-v7a 1, arm64-v8a 2, x86_64 3; 0 for several), so each APK of a release has its own code
// (F-Droid needs that). ClearPDF 2.x shipped codes up to 95, so 3.x codes always sort above them.
val appVersion: String = providers.gradleProperty("artefact.version").get()
val appVersionCode: Int = run {
    val m = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z-]+?(\.(\d+))?)?""").matchEntire(appVersion)) {
        "artefact.version $appVersion is not a version like 1.2.3, 1.2.3-beta or 1.2.3-beta.4"
    }
    val (major, minor, patch) = m.groupValues.subList(1, 4).map { it.toInt() }
    val pre = if (m.groupValues[4].isEmpty()) null else m.groupValues[6].toIntOrNull() ?: 1
    require(minor < 100 && patch < 100 && (pre ?: 1) in 1..98) {
        "artefact.version $appVersion: minor and patch must be under 100, and a pre-release's number 1 to 98"
    }
    val base = ((major * 100 + minor) * 100 + patch) * 100 + (pre ?: 99)
    providers.gradleProperty("artefact.versionCode").orNull?.let { listed ->
        require(listed.trim().toIntOrNull() == base) {
            "gradle.properties: artefact.versionCode=$listed, but version $appVersion's is $base (set both before tagging)"
        }
    }
    val abi = when (rustAbis.singleOrNull()) {
        "armeabi-v7a" -> 1
        "arm64-v8a" -> 2
        "x86_64" -> 3
        else -> 0
    }
    base * 10 + abi
}

// The release key: the CLEARPDF_UPLOAD_* variables (CI, from the repository's secrets), else
// key.properties at the repository root (a local Play build). Each channel keeps the key its
// installs were signed with, or they can't update. Without either a release build is signed with
// the debug key; with -Partefact.unsigned=true (F-Droid's build) it isn't signed at all.
val keyProperties = Properties().apply {
    val f = File(repoDir, "key.properties")
    if (f.isFile) f.inputStream().use(::load)
}
fun signingValue(property: String, env: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() } ?: keyProperties.getProperty(property)?.takeIf { it.isNotBlank() }
val releaseKeystore: File? = signingValue("storeFile", "CLEARPDF_UPLOAD_STORE_FILE")
    ?.let { File(it).takeIf(File::isAbsolute) ?: File(repoDir, it) }
val unsignedRelease: Boolean = providers.gradleProperty("artefact.unsigned").orNull == "true"

/** rustup's `cargo` (Android Studio may not see rustup's PATH), else `cargo` from PATH; `CARGO` overrides. */
val cargoExecutable: String = run {
    val exe = if (System.getProperty("os.name").lowercase().startsWith("windows")) "cargo.exe" else "cargo"
    val rustup = File(System.getProperty("user.home"), ".cargo/bin/$exe")
    providers.environmentVariable("CARGO").orNull ?: if (rustup.isFile) rustup.absolutePath else exe
}

/** Builds `libartefact.so` (release profile) for each ABI with cargo-ndk. */
abstract class CargoNdkBuild : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val workspaceDir: Property<String>
    @get:Input abstract val ndkDir: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:Input abstract val apiLevel: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val targets = abis.get().flatMap { listOf("-t", it) }
        val out = outputDir.get().asFile.absolutePath
        // Reproducible: the library mustn't hold the paths it was built in (panic locations name
        // source files), so the same commit builds byte-identical here, on CI and on F-Droid's
        // build server. Encoded flags (0x1f-separated) survive spaces in the paths.
        val cargoHome = System.getenv("CARGO_HOME")?.takeIf { it.isNotBlank() } ?: File(System.getProperty("user.home"), ".cargo").absolutePath
        val remap = listOf(
            "--remap-path-prefix=${File(workspaceDir.get()).absolutePath}=/artefact",
            "--remap-path-prefix=$cargoHome=/cargo",
        )
        execOperations.exec {
            workingDir(workspaceDir.get())
            environment("ANDROID_NDK_HOME", ndkDir.get())
            environment("CARGO_ENCODED_RUSTFLAGS", remap.joinToString("\u001f"))
            commandLine(
                listOf(cargo.get(), "ndk") + targets +
                    listOf("--platform", apiLevel.get().toString(), "-o", out, "build", "--release", "--locked", "-p", "artefact-mobile", "--lib"),
            )
        }
        // cargo-ndk copies every shared library cargo left behind (dylib builds of a few
        // dependencies); the app's library links them statically, so only it ships
        File(out).walk().filter { it.isFile && it.name != "libartefact.so" }.forEach { it.delete() }
    }
}

/** Generates the Kotlin bindings from the library cargo built (UniFFI library mode). */
abstract class UniffiBindgen : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val workspaceDir: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val library: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        execOperations.exec {
            workingDir(workspaceDir.get())
            commandLine(
                cargo.get(), "run", "--quiet", "--locked", "-p", "artefact-mobile-bindgen", "--",
                "generate", library.get().asFile.absolutePath, "--language", "kotlin", "--out-dir", out.absolutePath, "--no-format",
            )
        }
    }
}

/**
 * Files from the engine's `assets/` (and `vendor/`) the app ships, copied at build time rather than
 * duplicated: the licences of what the library bundles (fonts, icons). [targets] maps each file name to its path in the generated resource (or asset)
 * folder.
 */
abstract class EngineAssets : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val files: ConfigurableFileCollection
    @get:Input abstract val targets: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        for (file in files) {
            val target = targets.get()[file.name] ?: continue
            file.copyTo(File(out, target), overwrite = true)
        }
    }
}

/**
 * The UI icons (Lucide, ISC: engine/assets/icons, each listed in ATTRIBUTION.toml) as Compose
 * path data, generated at build time rather than copied: `LucideIcons.ArrowLeft` and so on, one
 * per SVG. Lucide draws with strokes only, from paths, circles, ellipses, rects, lines and
 * polylines, which this turns into SVG path strings.
 */
abstract class LucideIconsTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val iconsDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val pkg = File(out, "com/chethan616/clearpdf/ui").apply { mkdirs() }
        val attr = Regex("""([\w-]+)="([^"]*)"""")
        val element = Regex("""<(path|circle|ellipse|rect|line|polyline|polygon)\b([^>]*)/?>""")
        fun n(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
        val sb = StringBuilder()
        sb.appendLine("// Generated by app/build.gradle.kts (LucideIconsTask) from assets/icons: do not edit.")
        sb.appendLine("package com.chethan616.clearpdf.ui\n")
        sb.appendLine("import androidx.compose.ui.graphics.vector.ImageVector\n")
        sb.appendLine("/** Lucide icons (ISC, Lucide Contributors; assets/icons/LICENSE-lucide.txt). */")
        sb.appendLine("object LucideIcons {")
        val files = iconsDir.get().asFile.listFiles { f -> f.extension == "svg" }.orEmpty().sortedBy { it.name }
        for (f in files) {
            val paths = mutableListOf<String>()
            for (m in element.findAll(f.readText())) {
                val a = attr.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                fun d(k: String) = a[k]?.toDoubleOrNull() ?: 0.0
                when (m.groupValues[1]) {
                    "path" -> a["d"]?.let(paths::add)
                    "circle", "ellipse" -> {
                        val rx = if (m.groupValues[1] == "circle") d("r") else d("rx")
                        val ry = if (m.groupValues[1] == "circle") d("r") else d("ry")
                        val cx = d("cx"); val cy = d("cy")
                        paths += "M${n(cx - rx)} ${n(cy)}a${n(rx)} ${n(ry)} 0 1 0 ${n(2 * rx)} 0a${n(rx)} ${n(ry)} 0 1 0 ${n(-2 * rx)} 0"
                    }
                    "rect" -> {
                        val x = d("x"); val y = d("y"); val w = d("width"); val h = d("height")
                        val rx = minOf(a["rx"]?.toDoubleOrNull() ?: a["ry"]?.toDoubleOrNull() ?: 0.0, w / 2)
                        val ry = minOf(a["ry"]?.toDoubleOrNull() ?: rx, h / 2)
                        paths += if (rx <= 0.0) "M${n(x)} ${n(y)}h${n(w)}v${n(h)}h${n(-w)}Z" else
                            "M${n(x + rx)} ${n(y)}H${n(x + w - rx)}A${n(rx)} ${n(ry)} 0 0 1 ${n(x + w)} ${n(y + ry)}V${n(y + h - ry)}" +
                                "A${n(rx)} ${n(ry)} 0 0 1 ${n(x + w - rx)} ${n(y + h)}H${n(x + rx)}A${n(rx)} ${n(ry)} 0 0 1 ${n(x)} ${n(y + h - ry)}" +
                                "V${n(y + ry)}A${n(rx)} ${n(ry)} 0 0 1 ${n(x + rx)} ${n(y)}Z"
                    }
                    "line" -> paths += "M${n(d("x1"))} ${n(d("y1"))}L${n(d("x2"))} ${n(d("y2"))}"
                    "polyline", "polygon" -> {
                        val pts = (a["points"] ?: "").trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                        if (pts.size >= 4) {
                            val s = StringBuilder("M${pts[0]} ${pts[1]}")
                            for (i in 2 until pts.size - 1 step 2) s.append("L${pts[i]} ${pts[i + 1]}")
                            if (m.groupValues[1] == "polygon") s.append("Z")
                            paths += s.toString()
                        }
                    }
                }
            }
            if (paths.isEmpty()) continue
            val id = f.nameWithoutExtension.split('-').joinToString("") { p -> p.replaceFirstChar { it.uppercase() } }
            val list = paths.joinToString(", ") { "\"" + it.replace("\"", "") + "\"" }
            sb.appendLine("    val $id: ImageVector by lazy { lucide(\"${f.nameWithoutExtension}\", $list) }")
        }
        sb.appendLine("}")
        File(pkg, "LucideIcons.kt").writeText(sb.toString())
    }
}

/**
 * The text-recognition models (`cargo xtask models` fetches them into assets/models, listed in
 * ATTRIBUTION.toml), copied into the app's assets when they are there. Without them the app still
 * builds; Recognize text then says it isn't installed.
 */
abstract class OcrModels : DefaultTask() {
    @get:Internal abstract val modelsDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = File(outputDir.get().asFile, "models")
        out.deleteRecursively()
        val src = modelsDir.get().asFile
        val files = src.listFiles { f -> f.isFile }.orEmpty()
        if (files.none { it.extension == "rten" }) return
        out.mkdirs()
        for (f in files) f.copyTo(File(out, f.name), overwrite = true)
    }
}

android {
    namespace = "com.chethan616.clearpdf"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.chethan616.clearpdf"
        minSdk = minApi
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        ndk { abiFilters.addAll(rustAbis) }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("storePassword", "CLEARPDF_UPLOAD_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CLEARPDF_UPLOAD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CLEARPDF_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // the pseudo-languages (English with accents, mirrored text) show any text not yet in strings.xml
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (unsignedRelease) null else signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // reproducible builds: no record of the checkout in the APK
            vcsInfo { include = false }
        }
    }
    // reproducible builds: no dependency metadata signing block (F-Droid's check rejects it)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // the languages in res/values-* go into the manifest, so Android 13+ offers them under
    // App languages in the system settings (res/resources.properties names the default)
    androidResources {
        generateLocaleConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val cargoBuild = tasks.register<CargoNdkBuild>("cargoBuild$name") {
            group = "rust"
            description = "Builds libartefact.so for $rustAbis"
            cargo.set(cargoExecutable)
            workspaceDir.set(cargoWorkspaceDir.absolutePath)
            ndkDir.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
            abis.set(rustAbis)
            apiLevel.set(minApi)
            // cargo knows what changed
            outputs.upToDateWhen { false }
        }
        val bindgen = tasks.register<UniffiBindgen>("uniffiBindgen$name") {
            group = "rust"
            description = "Generates the Kotlin bindings of libartefact.so"
            cargo.set(cargoExecutable)
            workspaceDir.set(cargoWorkspaceDir.absolutePath)
            library.set(cargoBuild.flatMap { it.outputDir.file("${rustAbis.first()}/libartefact.so") })
        }
        val assetsDir = File(cargoWorkspaceDir, "assets")
        val licences = tasks.register<EngineAssets>("engineLicences$name") {
            // the Licences screen shows each (the Adobe CMaps' notice, from a crates.io crate, is a
            // plain asset: app/src/main/assets/licences)
            val map = mapOf(
                File(assetsDir, "icons/LICENSE-lucide.txt") to "licences/LICENSE-lucide.txt",
                File(assetsDir, "fonts/OFL-DancingScript.txt") to "licences/OFL-DancingScript.txt",
                File(cargoWorkspaceDir, "vendor/hayro-interpret/assets/LICENSE_FOXIT") to "licences/LICENSE-foxit.txt",
            )
            files.from(map.keys)
            targets.set(map.mapKeys { (k, _) -> k.name })
        }
        val icons = tasks.register<LucideIconsTask>("lucideIcons$name") {
            iconsDir.set(File(assetsDir, "icons"))
        }
        variant.sources.java?.addGeneratedSourceDirectory(icons, LucideIconsTask::outputDir)
        val models = tasks.register<OcrModels>("ocrModels$name") {
            modelsDir.set(File(assetsDir, "models"))
            outputs.upToDateWhen { false }
        }
        variant.sources.assets?.addGeneratedSourceDirectory(models, OcrModels::outputDir)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoBuild, CargoNdkBuild::outputDir)
        variant.sources.java?.addGeneratedSourceDirectory(bindgen, UniffiBindgen::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(licences, EngineAssets::outputDir)
    }
}

// reproducible builds: no baseline profile in the APK (its bytes vary from one build to the next)
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(project(":backdrop"))
    implementation(libs.kyant.shapes)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.jna) { artifact { type = "aar" } }
    testImplementation(libs.junit)
}
