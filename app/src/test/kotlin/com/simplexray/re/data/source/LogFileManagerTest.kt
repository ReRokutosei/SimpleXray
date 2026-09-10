package com.simplexray.re.data.source

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LogFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createFakeContext(): ContextWrapper {
        val dir = tempFolder.newFolder()
        return object : ContextWrapper(null) {
            override fun getFilesDir(): File = dir
        }
    }

    @Test
    fun testReadNonExistentFileReturnsEmpty() {
        val context = createFakeContext()
        val manager = LogFileManager(context)
        assertEquals("", manager.readLogs())
    }

    @Test
    fun testAppendAndReadLogs() {
        val context = createFakeContext()
        val manager = LogFileManager(context)

        manager.appendLog("line 1")
        manager.appendLog("line 2")
        manager.appendLogs(listOf("line 3", "line 4"))

        val content = manager.readLogs()
        assertNotNull(content)
        assertTrue(content!!.contains("line 1"))
        assertTrue(content.contains("line 2"))
        assertTrue(content.contains("line 3"))
        assertTrue(content.contains("line 4"))
    }

    @Test
    fun testClearLogs() {
        val context = createFakeContext()
        val manager = LogFileManager(context)

        manager.appendLog("some logs")
        manager.clearLogs()

        val content = manager.readLogs()
        assertEquals("", content)
    }

    @Test
    fun testMultiInstanceConcurrency() {
        val context = createFakeContext()
        val manager1 = LogFileManager(context)
        val manager2 = LogFileManager(context)

        val threadCount = 10
        val writesPerThread = 50
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            val isManager1 = t % 2 == 0
            val manager = if (isManager1) manager1 else manager2
            Thread {
                try {
                    for (i in 0 until writesPerThread) {
                        manager.appendLog("Thread $t log $i")
                        if (i % 10 == 0) {
                            val logs = manager.readLogs()
                            if (logs == null) {
                                errorCount.incrementAndGet()
                            }
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        assertTrue("All concurrent operations should complete within timeout", completed)
        assertEquals("There should be no concurrent read/write errors", 0, errorCount.get())

        val finalLogs = manager2.readLogs()
        assertNotNull(finalLogs)
        assertTrue(finalLogs!!.isNotEmpty())
    }
}
