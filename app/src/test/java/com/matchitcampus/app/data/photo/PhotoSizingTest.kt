package com.matchitcampus.app.data.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizingTest {

    @Test
    fun scaledSize_fitsLongestSideTo800_keepingShape() {
        assertEquals(800 to 600, PhotoSizing.scaledSize(4000, 3000))
        assertEquals(600 to 800, PhotoSizing.scaledSize(3000, 4000))
        assertEquals(800 to 450, PhotoSizing.scaledSize(1920, 1080))
    }

    @Test
    fun scaledSize_neverEnlargesSmallPhotos() {
        assertEquals(640 to 480, PhotoSizing.scaledSize(640, 480))
        assertEquals(800 to 800, PhotoSizing.scaledSize(800, 800))
    }

    @Test
    fun scaledSize_keepsAtLeastOnePixel() {
        assertEquals(800 to 1, PhotoSizing.scaledSize(10_000, 1))
    }

    @Test
    fun sampleSize_isLargestPowerOfTwoKeepingAtLeast800() {
        assertEquals(4, PhotoSizing.sampleSize(4000, 3000)) // 4000 / 4 = 1000 ≥ 800, / 8 = 500 < 800
        assertEquals(2, PhotoSizing.sampleSize(1920, 1080)) // 1920 / 2 = 960
        assertEquals(1, PhotoSizing.sampleSize(1200, 900))  // 1200 / 2 = 600 < 800
        assertEquals(1, PhotoSizing.sampleSize(640, 480))
    }
}
