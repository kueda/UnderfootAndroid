package rocks.underfoot.underfootandroid.maptuils

import android.location.Location

object MapHelpers {
    private const val TWO_MINUTES = 1000 * 60 * 2;

    // Adapted from https://stackoverflow.com/questions/6181704/good-way-of-getting-the-users-location-in-android
    fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        // A new location is always better than no location
        currentBestLocation ?: return true

        // Check whether the new location fix is newer or older
        val timeDelta = location.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > TWO_MINUTES
        val isSignificantlyOlder = timeDelta < -TWO_MINUTES
        val isNewer = timeDelta > 0

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) return true
        // If the new location is more than two minutes older, it must be worse
        if (isSignificantlyOlder) return false

        // Check whether the new location fix is more or less accurate
        val accuracyDelta = (location.accuracy - currentBestLocation.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 200

        // Check if the old and new location are from the same provider
        val isFromSameProvider = isSameProvider(
            location.provider,
            currentBestLocation.provider
        )

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
//            Log.d(TAG, "location more accurate");
            return true
        } else if (isNewer && !isLessAccurate) {
//            Log.d(TAG, "location newer and not less accurate");
            return true
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
//            Log.d(TAG, "location newer and isn't crazy less accurate and from the same provider");
            return true
        }
        //        Log.d(TAG, "location not better");
        return false
    }

    /** Checks whether two providers are the same  */
    private fun isSameProvider(provider1: String?, provider2: String?): Boolean {
        return if (provider1 == null) {
            provider2 == null
        } else provider1 == provider2
    }
}