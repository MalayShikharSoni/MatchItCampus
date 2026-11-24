package com.matchitcampus.app.ui.post

import androidx.compose.runtime.Immutable
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemType

/** The form's fields that can be invalid, in the order they appear on screen. */
enum class PostField { TITLE, DESCRIPTION, CATEGORY, DATE, ZONE, QUESTION }

enum class FieldError { REQUIRED, TOO_SHORT, TOO_LONG, IN_FUTURE }

/**
 * Everything typed or picked on the Post screen: the draft.
 *
 * It's a plain data class with no Android types, so the validation rules can be tested on
 * the JVM, and each field is saved to `SavedStateHandle` (see `PostItemViewModel`).
 *
 * @property eventDate the day it was lost/found, as midnight UTC (see `Formatters`).
 * @property draftPhotoPath a newly picked photo, already compressed, not yet attached to the item.
 * @property existingPhotoPath when editing: the photo the item already has on this phone.
 * @property existingHasPhoto when editing: whether the item already has a photo (even if this
 *   phone has no copy of it).
 * @property removeExistingPhoto when editing: the user tapped "Remove photo".
 * @property latitude the exact spot, from "Use my location" or the pin picker (Phase 6); optional,
 *   because the zone alone is enough to post. Null together with [longitude].
 * @property addressLine a readable address for that spot, from reverse geocoding, if one was found.
 */
@Immutable
data class PostItemForm(
    val type: ItemType = ItemType.LOST,
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val eventDate: Long = 0L,
    val zoneId: String? = null,
    val verificationQuestion: String = "",
    val draftPhotoPath: String? = null,
    val existingPhotoPath: String? = null,
    val existingHasPhoto: Boolean = false,
    val removeExistingPhoto: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressLine: String? = null,
) {
    /** True once an exact spot has been chosen. */
    val hasPin: Boolean get() = latitude != null && longitude != null

    /** The photo to show in the form: the new one if any, otherwise the kept existing one. */
    val shownPhotoPath: String? get() = draftPhotoPath ?: existingPhotoPath.takeUnless { removeExistingPhoto }

    /** Whether the saved item will have a photo. */
    val willHavePhoto: Boolean get() = draftPhotoPath != null || (existingHasPhoto && !removeExistingPhoto)
}

/** The form rules, as one pure function (unit-tested in `PostItemValidatorTest`). */
object PostItemValidator {
    const val TITLE_MIN = 3
    const val TITLE_MAX = 60
    const val DESCRIPTION_MAX = 500
    const val QUESTION_MAX = 120

    /**
     * @param todayEventDate today as an `eventDate` value; later dates are rejected.
     * @return the problem with each invalid field; empty when the form can be posted.
     */
    fun validate(form: PostItemForm, todayEventDate: Long): Map<PostField, FieldError> = buildMap {
        val title = form.title.trim()
        when {
            title.isEmpty() -> put(PostField.TITLE, FieldError.REQUIRED)
            title.length < TITLE_MIN -> put(PostField.TITLE, FieldError.TOO_SHORT)
            title.length > TITLE_MAX -> put(PostField.TITLE, FieldError.TOO_LONG)
        }
        val description = form.description.trim()
        when {
            description.isEmpty() -> put(PostField.DESCRIPTION, FieldError.REQUIRED)
            description.length > DESCRIPTION_MAX -> put(PostField.DESCRIPTION, FieldError.TOO_LONG)
        }
        if (form.category == null) put(PostField.CATEGORY, FieldError.REQUIRED)
        if (form.eventDate > todayEventDate) put(PostField.DATE, FieldError.IN_FUTURE)
        if (form.zoneId == null) put(PostField.ZONE, FieldError.REQUIRED)
        // Only FOUND items have a question: it's how the finder checks a claimant is the owner.
        if (form.type == ItemType.FOUND) {
            val question = form.verificationQuestion.trim()
            when {
                question.isEmpty() -> put(PostField.QUESTION, FieldError.REQUIRED)
                question.length > QUESTION_MAX -> put(PostField.QUESTION, FieldError.TOO_LONG)
            }
        }
    }
}
