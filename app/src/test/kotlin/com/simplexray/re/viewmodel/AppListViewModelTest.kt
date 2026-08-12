package com.simplexray.re.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListViewModelTest {

    private fun pkg(
        label: String,
        isSystemApp: Boolean = false,
        hasInternetPermission: Boolean = true
    ) = Package(
        selected = false,
        label = label,
        packageName = "pkg.$label",
        isSystemApp = isSystemApp,
        hasInternetPermission = hasInternetPermission
    )

    private val all = listOf(
        pkg("Chrome", isSystemApp = false, hasInternetPermission = true),
        pkg("Settings", isSystemApp = true, hasInternetPermission = true),
        pkg("Offline App", isSystemApp = false, hasInternetPermission = false)
    )

    @Test
    fun `filter returns everything when no filters apply`() {
        val result = filterPackages(all, query = "", showSystemApps = true, showNoInternetApps = true)
        assertEquals(all, result)
    }

    @Test
    fun `filter hides system apps when showSystemApps is false`() {
        val result = filterPackages(all, query = "", showSystemApps = false, showNoInternetApps = true)
        assertEquals(listOf(pkg("Chrome"), pkg("Offline App", hasInternetPermission = false)), result)
    }

    @Test
    fun `filter hides no-internet apps when showNoInternetApps is false`() {
        val result = filterPackages(all, query = "", showSystemApps = true, showNoInternetApps = false)
        assertEquals(listOf(pkg("Chrome"), pkg("Settings", isSystemApp = true)), result)
    }

    @Test
    fun `filter matches query case-insensitively`() {
        val result = filterPackages(all, query = "chrome", showSystemApps = true, showNoInternetApps = true)
        assertEquals(listOf(pkg("Chrome")), result)
    }

    @Test
    fun `filter combines all criteria`() {
        val result = filterPackages(
            all,
            query = "APP",
            showSystemApps = false,
            showNoInternetApps = false
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter keeps original order`() {
        val result = filterPackages(
            listOf(pkg("B"), pkg("A"), pkg("C")),
            query = "",
            showSystemApps = true,
            showNoInternetApps = true
        )
        assertEquals(listOf("B", "A", "C"), result.map { it.label })
    }
}
