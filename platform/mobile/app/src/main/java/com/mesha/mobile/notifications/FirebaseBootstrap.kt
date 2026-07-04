package com.mesha.mobile.notifications

/**
 * Tracks whether the default [com.google.firebase.FirebaseApp] was successfully
 * initialised (see [com.mesha.mobile.MeshaApplication]). Firebase is configured manually
 * from BuildConfig rather than a committed google-services.json, so on a build with no
 * Firebase config it stays `false` and all FCM code paths no-op instead of crashing.
 */
object FirebaseBootstrap {
    @Volatile
    var isReady: Boolean = false
}
