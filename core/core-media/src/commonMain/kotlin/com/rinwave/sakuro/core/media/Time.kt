package com.rinwave.sakuro.core.media

/** Current wall-clock time in epoch seconds; a small expect/actual to avoid a dependency on kotlinx-datetime. */
expect fun nowEpochSeconds(): Long
