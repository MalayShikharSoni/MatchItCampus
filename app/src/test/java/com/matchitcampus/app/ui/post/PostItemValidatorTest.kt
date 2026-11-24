package com.matchitcampus.app.ui.post

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostItemValidatorTest {

    private val today = 1_795_392_000_000L
    private val day = 24L * 60 * 60 * 1000

    private val validLost = PostItemForm(
        type = ItemType.LOST,
        title = "Black earphone case",
        description = "JBL case with a star sticker",
        category = Category.ELECTRONICS,
        eventDate = today,
        zoneId = "block_34",
    )

    private fun errors(form: PostItemForm) = PostItemValidator.validate(form, today)

    @Test
    fun completeLostForm_isValid() {
        assertTrue(errors(validLost).isEmpty())
    }

    @Test
    fun emptyForm_listsEveryRequiredField() {
        val result = errors(PostItemForm(eventDate = today))
        assertEquals(
            mapOf(
                PostField.TITLE to FieldError.REQUIRED,
                PostField.DESCRIPTION to FieldError.REQUIRED,
                PostField.CATEGORY to FieldError.REQUIRED,
                PostField.ZONE to FieldError.REQUIRED,
            ),
            result,
        )
    }

    @Test
    fun title_lengthLimits_ignoreSurroundingSpaces() {
        assertEquals(FieldError.TOO_SHORT, errors(validLost.copy(title = "  ab  "))[PostField.TITLE])
        assertEquals(FieldError.REQUIRED, errors(validLost.copy(title = "   "))[PostField.TITLE])
        assertEquals(FieldError.TOO_LONG, errors(validLost.copy(title = "x".repeat(61)))[PostField.TITLE])
        assertFalse(PostField.TITLE in errors(validLost.copy(title = "x".repeat(60))))
    }

    @Test
    fun description_tooLong() {
        assertEquals(FieldError.TOO_LONG, errors(validLost.copy(description = "x".repeat(501)))[PostField.DESCRIPTION])
    }

    @Test
    fun date_inTheFuture_isRejected_todayIsFine() {
        assertEquals(FieldError.IN_FUTURE, errors(validLost.copy(eventDate = today + day))[PostField.DATE])
        assertFalse(PostField.DATE in errors(validLost.copy(eventDate = today - day)))
    }

    @Test
    fun question_isRequiredOnlyForFoundItems() {
        assertFalse(PostField.QUESTION in errors(validLost))
        val found = validLost.copy(type = ItemType.FOUND)
        assertEquals(FieldError.REQUIRED, errors(found)[PostField.QUESTION])
        assertTrue(errors(found.copy(verificationQuestion = "What sticker is on it?")).isEmpty())
        assertEquals(FieldError.TOO_LONG, errors(found.copy(verificationQuestion = "x".repeat(121)))[PostField.QUESTION])
    }

    @Test
    fun photo_flags() {
        val editing = validLost.copy(existingPhotoPath = "/old.jpg", existingHasPhoto = true)
        assertEquals("/old.jpg", editing.shownPhotoPath)
        assertTrue(editing.willHavePhoto)

        val replaced = editing.copy(draftPhotoPath = "/new.jpg")
        assertEquals("/new.jpg", replaced.shownPhotoPath)

        val removed = editing.copy(removeExistingPhoto = true)
        assertEquals(null, removed.shownPhotoPath)
        assertFalse(removed.willHavePhoto)
    }
}
