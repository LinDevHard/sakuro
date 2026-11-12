package com.rinwave.sakuro.feature.library

import androidx.compose.runtime.Composable
import com.rinwave.sakuro.core.media.DateBucket
import com.rinwave.sakuro.core.media.LibrarySort
import com.rinwave.sakuro.feature.library.resources.Res
import com.rinwave.sakuro.feature.library.resources.section_earlier
import com.rinwave.sakuro.feature.library.resources.section_this_month
import com.rinwave.sakuro.feature.library.resources.section_this_week
import com.rinwave.sakuro.feature.library.resources.section_today
import com.rinwave.sakuro.feature.library.resources.section_yesterday
import com.rinwave.sakuro.feature.library.resources.sort_field_date_added
import com.rinwave.sakuro.feature.library.resources.sort_field_duration
import com.rinwave.sakuro.feature.library.resources.sort_field_name
import com.rinwave.sakuro.feature.library.resources.sort_field_resolution
import com.rinwave.sakuro.feature.library.resources.sort_field_size
import org.jetbrains.compose.resources.stringResource

/** Localized labels for library (core-media) enums; moves with the library feature. */
@Composable
internal fun LibrarySort.label(): String = stringResource(
    when (this) {
        LibrarySort.DateAdded -> Res.string.sort_field_date_added
        LibrarySort.Name -> Res.string.sort_field_name
        LibrarySort.Size -> Res.string.sort_field_size
        LibrarySort.Duration -> Res.string.sort_field_duration
        LibrarySort.Resolution -> Res.string.sort_field_resolution
    },
)

@Composable
internal fun DateBucket.label(): String = stringResource(
    when (this) {
        DateBucket.TODAY -> Res.string.section_today
        DateBucket.YESTERDAY -> Res.string.section_yesterday
        DateBucket.THIS_WEEK -> Res.string.section_this_week
        DateBucket.THIS_MONTH -> Res.string.section_this_month
        DateBucket.EARLIER -> Res.string.section_earlier
    },
)
