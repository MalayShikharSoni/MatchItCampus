package com.matchitcampus.app.location

import com.matchitcampus.app.ui.TestItems.item
import com.matchitcampus.app.ui.TestItems.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemPositionsTest {

    private val realZone = zone("block_34").copy(latitude = 31.2560, longitude = 75.7060, isPlaceholder = false)
    private val placeholderZone = zone("block_35") // TestItems zones are placeholders

    @Test
    fun exactPin_winsOverTheZone() {
        val pinned = item("a", zone = realZone).copy(latitude = 31.2500, longitude = 75.7000)
        assertEquals(LatLng(31.2500, 75.7000), pinned.mapPosition())
    }

    @Test
    fun noPin_usesTheCentreOfARealZone() {
        assertEquals(LatLng(31.2560, 75.7060), item("a", zone = realZone).mapPosition())
    }

    @Test
    fun noPin_placeholderZone_hasNoPosition() {
        assertNull(item("a", zone = placeholderZone).mapPosition())
        assertNull(item("a", zone = null).mapPosition())
    }

    @Test
    fun distanceFrom_isNullWithoutAPosition() {
        assertNull(item("a").distanceFrom(LatLng(31.255, 75.705)))
        assertEquals(0.0, item("a", zone = realZone).distanceFrom(LatLng(31.2560, 75.7060))!!, 1e-6)
    }
}
