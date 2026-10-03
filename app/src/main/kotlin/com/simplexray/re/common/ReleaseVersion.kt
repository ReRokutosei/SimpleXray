package com.simplexray.re.common

/**
 * Minimal SemVer-style comparator for GitHub release tags.
 *
 * It intentionally accepts tags with a leading "v" and tolerates missing
 * minor/patch components so that both app versions and repository tags can be
 * compared without a full-featured SemVer dependency.
 */
object ReleaseVersion {

    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)

        val core = compareNumeric(a.numbers, b.numbers)
        if (core != 0) return core

        val aPre = a.prerelease
        val bPre = b.prerelease
        if (aPre == null && bPre == null) return 0
        if (aPre == null) return 1 // release > prerelease
        if (bPre == null) return -1

        val aParts = aPre.split('.')
        val bParts = bPre.split('.')
        val max = maxOf(aParts.size, bParts.size)
        for (i in 0 until max) {
            if (i >= aParts.size) return -1 // shorter prerelease has lower precedence
            if (i >= bParts.size) return 1

            val x = aParts[i]
            val y = bParts[i]
            val xNumber = x.toLongOrNull()
            val yNumber = y.toLongOrNull()
            val cmp = when {
                xNumber != null && yNumber != null -> xNumber.compareTo(yNumber)
                xNumber != null -> -1 // numeric identifiers have lower precedence
                yNumber != null -> 1
                else -> x.compareTo(y)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    private data class Parsed(val numbers: List<Long>, val prerelease: String?)

    private fun parse(raw: String): Parsed {
        val normalized = raw.trim().removePrefix("v").removePrefix("V")
        val withoutBuild = normalized.substringBefore('+')
        val hyphen = withoutBuild.indexOf('-')
        val core = if (hyphen >= 0) withoutBuild.substring(0, hyphen) else withoutBuild
        val prerelease = if (hyphen >= 0) withoutBuild.substring(hyphen + 1) else null
        val numbers = core.split('.').map { it.toLongOrNull() ?: 0L }
        return Parsed(numbers, prerelease?.ifBlank { null })
    }

    private fun compareNumeric(a: List<Long>, b: List<Long>): Int {
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val x = a.getOrElse(i) { 0L }
            val y = b.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
