package de.fayard

import de.fayard.VersionsOnlyMode.*
import java.io.File

fun BuildSrcVersionsExtension.useBuildSrc() = versionsOnlyMode != null

enum class VersionsOnlyMode {
    KOTLIN_VAL,
    KOTLIN_OBJECT,
    GROOVY_DEF,
    GROOVY_EXT,
    GRADLE_PROPERTIES;

    val quote: String get() = when(this) {
        KOTLIN_VAL,
        KOTLIN_OBJECT -> doubleQuote
        GROOVY_DEF,
        GROOVY_EXT -> singleQuote
        GRADLE_PROPERTIES -> ""
    }

    fun suggestedFilename(): String = when(this) {
        KOTLIN_VAL -> "build.gradle.kts"
        KOTLIN_OBJECT -> "Versions.kt"
        GROOVY_DEF -> "build.gradle"
        GROOVY_EXT -> "build.gradle"
        GRADLE_PROPERTIES -> "gradle.properties"
    }

    val comment: String get() = when(this) {
        GRADLE_PROPERTIES -> "#"
        else -> "//"
    }
}


fun parseBuildFile(versionsOnlyFile: File?, versionsOnlyMode: VersionsOnlyMode): SingleModeResult? {
    return when {
        versionsOnlyFile == null -> null
        versionsOnlyFile.canRead().not() -> null
        else -> {
            val lines = versionsOnlyFile.readLines()
            val startOfBlock = lines.indexOfFirst {
                it.trim().endsWith(PluginConfig.VERSIONS_ONLY_START)
            }
            val endOfBlock = lines.indexOfFirst {
                it.trim().endsWith(PluginConfig.VERSIONS_ONLY_END)
            }
            if (startOfBlock == -1 || endOfBlock == -1) {
                null
            } else {
                var indent = lines[endOfBlock].substringBefore(versionsOnlyMode.comment, missingDelimiterValue = "    ")
                if (versionsOnlyMode == GRADLE_PROPERTIES) indent = ""
                SingleModeResult(startOfBlock, endOfBlock, indent)

            }
        }
    }
}

fun regenerateBuildFile(versionsOnlyFile: File?, extension: BuildSrcVersionsExtension, dependencies: List<Dependency>) {
    val versionsOnlyMode = extension.versionsOnlyMode ?: return
    val parseResult = parseBuildFile(versionsOnlyFile, versionsOnlyMode) ?: SingleModeResult.DEFAULT
    val (startOfBlock, endOfBlock, indent) = parseResult

    val newBlock = regenerateBlock(versionsOnlyMode, dependencies, indent)

    if (versionsOnlyFile != null && parseResult != SingleModeResult.DEFAULT) {
        val lines = versionsOnlyFile.readLines()
        val newLines = lines.subList(0, startOfBlock) + newBlock + lines.subList(endOfBlock + 1, lines.size)
        versionsOnlyFile.writeText(newLines.joinToString(separator = "\n", postfix = "\n"))
    } else {
        println("""
            
== 📋 copy-paste needed! 📋 ==

Copy-paste the snippet below:

${newBlock.joinToString(separator = "\n")}

in the file you configure with something like:
 
// build.gradle(.kts) 
buildSrcVersions {
    versionsOnlyMode = VersionsOnlyMode.${versionsOnlyMode}
    versionsOnlyFile = "${versionsOnlyMode.suggestedFilename()}"            
}

See ${PluginConfig.issue54VersionOnlyMode}
        """
        )
        println()
    }
}

fun regenerateBlock(mode: VersionsOnlyMode, dependencies: List<Dependency>, indent: String): List<String> {
    val comment = mode.comment
    val result = mutableListOf<String>()
    result += PluginConfig.VERSIONS_ONLY_INTRO.map { "$indent$comment $it" }
    if (mode == GROOVY_EXT) result += "${indent}ext {"
    if (mode == GRADLE_PROPERTIES) result += "\n"
    result += dependencies.map { versionOnly(it, mode, indent) }
    if (mode == GROOVY_EXT) result += "${indent}}"
    result += "$indent$comment ${PluginConfig.VERSIONS_ONLY_END}"
    return result
}

fun versionOnly(d: Dependency, versionsOnlyMode: VersionsOnlyMode, indent: String): String {
    val available = d.versionInformation()
        .replace(doubleQuote, versionsOnlyMode.quote)
        .replace(slashslash, versionsOnlyMode.comment)

    return when(versionsOnlyMode) {
        KOTLIN_OBJECT -> throw IllegalStateException("KOTLIN_OBJECT should not be handled here")
        KOTLIN_VAL -> """${indent}val ${d.versionName} = "${d.version}"$available"""
        GROOVY_DEF -> """${indent}def ${d.versionName} = '${d.version}'$available"""
        GROOVY_EXT -> """${indent}${indent}${d.versionName} = '${d.version}'$available"""
        GRADLE_PROPERTIES -> "${available.trim()}\n${d.versionName}=${d.version}"
    }
}

private val singleQuote = "'"
private val doubleQuote = "\""
private val slashslash = "//"
