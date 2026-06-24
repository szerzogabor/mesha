package com.mesha.api.model;

/**
 * Distribution target for a downloadable Mesha client release.
 * Currently only Android is distributed as an APK; the enum exists so the
 * release-management system can host additional native clients (e.g. iOS) later
 * without a schema change.
 */
public enum AppPlatform {
    ANDROID
}
