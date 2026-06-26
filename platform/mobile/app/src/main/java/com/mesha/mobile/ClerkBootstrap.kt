package com.mesha.mobile

/**
 * Tracks whether [com.clerk.api.Clerk] was actually initialized. A blank/invalid
 * publishable key (e.g. a release built without the CI secret set) makes
 * `Clerk.initialize` throw, and every other Clerk API — including the ones
 * [com.mesha.mobile.data.repository.AuthRepository] touches on construction — is
 * unsafe to call afterward. [com.mesha.mobile.ui.MeshaApp] checks this before
 * reaching for anything Clerk-backed so a misconfigured build degrades to an
 * error screen instead of crashing on launch.
 */
object ClerkBootstrap {
    @Volatile
    var isReady: Boolean = false
        internal set
}
