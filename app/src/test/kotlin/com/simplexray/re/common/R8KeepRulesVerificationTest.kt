package com.simplexray.re.common

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Validates R8 release outputs (seeds.txt / mapping.txt) to guarantee that
 * critical reflection, JNI bindings, Android components, and gRPC protobuf
 * models were not mistakenly stripped or pruned during minification.
 *
 * If release mapping outputs have not yet been produced, this test is skipped.
 */
class R8KeepRulesVerificationTest {

    private fun findSeedsFile(): File? {
        val candidates = listOf(
            File("build/outputs/mapping/release/seeds.txt"),
            File("app/build/outputs/mapping/release/seeds.txt"),
            File("../app/build/outputs/mapping/release/seeds.txt")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    @Test
    fun verifyCriticalSeedsArePreserved() {
        val seedsFile = findSeedsFile()
        assumeTrue("Skipping R8 keep-rule verification: seeds.txt not found (run assembleRelease first)", seedsFile != null)

        val content = seedsFile!!.readText()

        // 1. Android services & workers
        val requiredClasses = listOf(
            "com.simplexray.re.service.TProxyService",
            "com.simplexray.re.service.BenchmarkService",
            "com.simplexray.re.service.GeoUpdateWorker"
        )
        for (cls in requiredClasses) {
            assertTrue("Expected class '$cls' to be retained in seeds.txt", content.contains(cls))
        }

        // 2. Critical JNI native methods
        val requiredNativeSignatures = listOf(
            "TProxyStartService",
            "TProxyStopService",
            "nativeSpawnXray",
            "nativeReapChild",
            "nativeStart",
            "nativeStop"
        )
        for (sig in requiredNativeSignatures) {
            assertTrue("Expected native symbol '$sig' to be retained in seeds.txt", content.contains(sig))
        }

        // 3. gRPC Protobuf command models used by CoreStatsClient
        val requiredProtoSymbols = listOf(
            "com.xray.app.stats.command.StatsServiceGrpc",
            "com.xray.app.stats.command.QueryStatsRequest",
            "com.xray.app.stats.command.SysStatsResponse",
            "com.xray.app.stats.command.Stat"
        )
        for (proto in requiredProtoSymbols) {
            assertTrue("Expected protobuf symbol '$proto' to be retained in seeds.txt", content.contains(proto))
        }

        // 4. SnakeYAML reflection classes
        assertTrue(
            "Expected org.yaml.snakeyaml to be retained in seeds.txt",
            content.contains("org.yaml.snakeyaml")
        )
    }
}
