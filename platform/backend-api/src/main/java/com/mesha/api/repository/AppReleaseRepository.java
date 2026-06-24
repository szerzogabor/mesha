package com.mesha.api.repository;

import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {

    Optional<AppRelease> findFirstByPlatformAndPublishedTrueOrderByVersionCodeDesc(AppPlatform platform);

    List<AppRelease> findAllByPlatformOrderByVersionCodeDesc(AppPlatform platform);

    List<AppRelease> findAllByPlatformAndPublishedTrueOrderByVersionCodeDesc(AppPlatform platform);

    boolean existsByPlatformAndVersionCode(AppPlatform platform, int versionCode);
}
