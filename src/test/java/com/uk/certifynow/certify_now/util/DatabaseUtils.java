package com.uk.certifynow.certify_now.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseUtils {

  private final JdbcTemplate jdbcTemplate;

  public DatabaseUtils(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Map<String, Object>> findUserByEmail(final String email) {
    return queryOne("select * from \"user\" where lower(email) = lower(?)", email);
  }

  public Optional<Map<String, Object>> findUserById(final String userId) {
    return queryOne("select * from \"user\" where id::text = ?", userId);
  }

  public int countUsersByEmail(final String email) {
    return queryInt("select count(*) from \"user\" where lower(email) = lower(?)", email);
  }

  public int countUsersByPhone(final String phone) {
    return queryInt("select count(*) from \"user\" where phone = ?", phone);
  }

  public boolean userExistsWithEmail(final String email) {
    return countUsersByEmail(email) > 0;
  }

  public List<Map<String, Object>> findConsentsByUserId(final String userId) {
    return jdbcTemplate.queryForList("select * from user_consent where user_id::text = ?", userId);
  }

  public List<Map<String, Object>> findActiveRefreshTokensByUserId(final String userId) {
    return jdbcTemplate.queryForList(
        """
        select *
        from refresh_token
        where user_id::text = ?
          and revoked = false
        order by created_at desc
        """,
        userId);
  }

  public Optional<Map<String, Object>> findVerificationTokenByUserId(final String userId) {
    return queryOne(
        """
        select *
        from email_verification_tokens
        where user_id::text = ?
        order by created_at desc
        limit 1
        """,
        userId);
  }

  public String getPasswordHashForEmail(final String email) {
    return jdbcTemplate.queryForObject(
        "select password_hash from \"user\" where lower(email) = lower(?)", String.class, email);
  }

  public String getUserStatusByEmail(final String email) {
    return jdbcTemplate.queryForObject(
        "select status from \"user\" where lower(email) = lower(?)", String.class, email);
  }

  public boolean isEmailVerified(final String email) {
    final Boolean value =
        jdbcTemplate.queryForObject(
            "select email_verified from \"user\" where lower(email) = lower(?)",
            Boolean.class,
            email);
    return Boolean.TRUE.equals(value);
  }

  public void setUserStatus(final String userId, final String status) {
    jdbcTemplate.update("update \"user\" set status = ? where id::text = ?", status, userId);
  }

  public boolean customerProfileExistsForUserId(final String userId) {
    return queryInt("select count(*) from customer_profile where user_id::text = ?", userId) > 0;
  }

  public boolean engineerProfileExistsForUserId(final String userId) {
    return queryInt("select count(*) from engineer_profile where user_id::text = ?", userId) > 0;
  }

  public int countAllUsers() {
    return queryInt("select count(*) from \"user\"");
  }

  public boolean refreshTokenHashExists(final String tokenHash) {
    return queryInt("select count(*) from refresh_token where token_hash = ?", tokenHash) > 0;
  }

  public boolean rawRefreshTokenExists(final String rawToken) {
    return queryInt("select count(*) from refresh_token where token_hash = ?", rawToken) > 0;
  }

  public Optional<Map<String, Object>> findPropertyById(final UUID propertyId) {
    return queryOne("select * from property where id = ?", propertyId);
  }

  public int countPropertiesByOwnerId(final String userId) {
    return queryInt("select count(*) from property where owner_id::text = ?", userId);
  }

  public int countPropertiesByOwnerIdAndActive(final String userId, final boolean isActive) {
    return queryInt(
        "select count(*) from property where owner_id::text = ? and is_active = ?",
        userId,
        isActive);
  }

  public int getCustomerTotalProperties(final String userId) {
    final Integer value =
        jdbcTemplate.queryForObject(
            "select total_properties from customer_profile where user_id::text = ?",
            Integer.class,
            userId);
    return value == null ? 0 : value;
  }

  private Optional<Map<String, Object>> queryOne(final String sql, final Object... args) {
    final List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private int queryInt(final String sql, final Object... args) {
    final Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return value == null ? 0 : value;
  }
}
