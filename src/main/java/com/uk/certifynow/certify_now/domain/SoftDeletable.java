package com.uk.certifynow.certify_now.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Interface for entities that support soft deletion. Soft-deleted entities are not physically
 * removed from the database; instead, they are marked with a deletion timestamp and the ID of the
 * user who performed the deletion.
 *
 * <p>Entities implementing this interface should also use {@code @SQLRestriction("deleted_at IS
 * NULL")} to automatically filter soft-deleted records from standard JPA queries.
 */
public interface SoftDeletable {

  OffsetDateTime getDeletedAt();

  void setDeletedAt(OffsetDateTime deletedAt);

  UUID getDeletedBy();

  void setDeletedBy(UUID deletedBy);

  /** Returns {@code true} if this entity has been soft-deleted. */
  default boolean isDeleted() {
    return getDeletedAt() != null;
  }
}
