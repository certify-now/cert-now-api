package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, UUID> {

  EngineerProfile findFirstByUserId(UUID id);

  Optional<EngineerProfile> findByUserId(UUID userId);

  List<EngineerProfile> findByStatusAndIsOnline(EngineerApplicationStatus status, Boolean isOnline);

  List<EngineerProfile> findByStatus(EngineerApplicationStatus status);

  /**
   * Finds all APPROVED engineers whose stored location is within their own service radius of the
   * given coordinates. Uses PostGIS ST_DWithin for spatial filtering. No online/offline filter per
   * the broadcast model.
   */
  @Query(
      value =
          """
      SELECT ep.* FROM engineer_profile ep
      WHERE ep.status = 'APPROVED'
      AND ep.location IS NOT NULL
      AND ST_DWithin(
        ep.location::geography,
        ST_MakePoint(:lng, :lat)::geography,
        ep.service_radius_miles * 1609.34
      )
      """,
      nativeQuery = true)
  List<EngineerProfile> findNearbyApproved(@Param("lat") double lat, @Param("lng") double lng);
}
