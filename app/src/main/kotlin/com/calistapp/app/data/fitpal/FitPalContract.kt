package com.calistapp.app.data.fitpal

import android.net.Uri

/**
 * Calistapp's copy of the FitPal bridge wire contract.
 *
 * FitPal exposes ONE ContentProvider (`content://com.fitpal.app.sync`); Calistapp is a pure client:
 *  - pushes finished workouts to the [PATH_EXERCISE] path (idempotent, keyed on the session id);
 *  - pulls the day's steps + FitPal's already-trimmed step-calories from the [PATH_STEPS] path.
 *
 * FitPal also wakes Calistapp end-of-day by broadcasting [ACTION_PULL_STEPS] to
 * `com.calistapp.app.sync.StepPullReceiver`.
 *
 * ⚠️ KEEP IN SYNC with FitPal's copy at `com.fitpal.app.sync.FitPalSyncContract` — these constant
 * VALUES must stay byte-identical. Only the package/class wrapper differs between the two apps.
 */
object FitPalContract {

    const val AUTHORITY = "com.fitpal.app.sync"

    const val PATH_EXERCISE = "exercise"
    const val PATH_STEPS = "steps"

    val EXERCISE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_EXERCISE")
    val STEPS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_STEPS")

    // --- Exercise columns (Calistapp writes these) ---
    const val COL_EXTERNAL_ID = "externalId"
    const val COL_DATE = "date"
    const val COL_NAME = "name"
    const val COL_MINUTES = "minutes"
    const val COL_MET = "met"
    const val COL_CALORIES = "calories"
    const val COL_SOURCE = "source"
    const val COL_DETAILS_JSON = "detailsJson"
    const val COL_START_MS = "startMs"

    // --- Steps columns (FitPal returns these; COL_CALORIES is POST-trim) ---
    const val COL_STEPS = "steps"
    const val COL_REDUCTION_PERCENT = "reductionPercent"

    const val QUERY_FROM = "from"
    const val QUERY_TO = "to"

    const val SOURCE_CALISTAPP = "calistapp"

    const val ACTION_PULL_STEPS = "com.calistapp.sync.action.PULL_STEPS"

    const val FITPAL_PKG = "com.fitpal.app"
    const val CALISTAPP_PKG = "com.calistapp"
}
