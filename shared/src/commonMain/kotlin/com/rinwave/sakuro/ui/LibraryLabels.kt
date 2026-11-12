package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import com.rinwave.sakuro.core.media.DateBucket
import com.rinwave.sakuro.core.media.LibrarySort
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.section_earlier
import sakuro.composeapp.generated.resources.section_this_month
import sakuro.composeapp.generated.resources.section_this_week
import sakuro.composeapp.generated.resources.section_today
import sakuro.composeapp.generated.resources.section_yesterday
import sakuro.composeapp.generated.resources.sort_field_date_added
import sakuro.composeapp.generated.resources.sort_field_duration
import sakuro.composeapp.generated.resources.sort_field_name
import sakuro.composeapp.generated.resources.sort_field_resolution
import sakuro.composeapp.generated.resources.sort_field_size

/** Localized labels for library (core-media) enums; moves with the library feature. */
@Composable
fun LibrarySort.label(): String = stringResource(
    when (this) {
        LibrarySort.DateAdded -> Res.string.sort_field_date_added
        LibrarySort.Name -> Res.string.sort_field_name
        LibrarySort.Size -> Res.string.sort_field_size
        LibrarySort.Duration -> Res.string.sort_field_duration
        LibrarySort.Resolution -> Res.string.sort_field_resolution
    },
)

@Composable
fun DateBucket.label(): String = stringResource(
    when (this) {
        DateBucket.TODAY -> Res.string.section_today
        DateBucket.YESTERDAY -> Res.string.section_yesterday
        DateBucket.THIS_WEEK -> Res.string.section_this_week
        DateBucket.THIS_MONTH -> Res.string.section_this_month
        DateBucket.EARLIER -> Res.string.section_earlier
    },
)
