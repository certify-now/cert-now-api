package com.uk.certifynow.certify_now.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for the UK Government EPC (Energy Performance Certificate) API.
 *
 * <p>API: {@code GET /api/domestic/search?uprn=<uprn>}
 * Auth: Bearer token in Authorization header.
 *
 * <p>Actual response envelope (camelCase JSON):
 * <pre>
 * {
 *   "data": [
 *     {
 *       "registrationDate": "2021-03-24",
 *       "currentEnergyEfficiencyBand": "D",
 *       ...
 *     }
 *   ],
 *   "pagination": { ... }
 * }
 * </pre>
 *
 * <p>Returns the most recent {@link EpcRecord} for the UPRN, or {@code null} if no EPC is on record.
 */
@Service
public class EpcLookupService {

  private static final Logger log = LoggerFactory.getLogger(EpcLookupService.class);

  private final RestClient restClient;

  public EpcLookupService(
      @Value("${app.epc.base-url}") final String baseUrl,
      @Value("${app.epc.api-key}") final String apiKey) {
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
  }

  /**
   * Looks up the most recent EPC record for the given UPRN.
   *
   * <p>Logging conventions:
   * <ul>
   *   <li>{@code INFO}  — EPC found or genuinely not on record (expected empty result)</li>
   *   <li>{@code ERROR} — null response body or null {@code data} field (mapping drift / auth
   *       failure / network issue) — both are actionable failures requiring attention</li>
   * </ul>
   *
   * @param uprn the Unique Property Reference Number
   * @return the most recent {@link EpcRecord}, or {@code null} if no EPC was found or the UPRN is
   *     blank
   */
  public EpcRecord lookup(final String uprn) {
    if (uprn == null || uprn.isBlank()) {
      return null;
    }

    try {
      final EpcSearchResponse response =
          restClient
              .get()
              .uri("/api/domestic/search?uprn={uprn}", uprn)
              .retrieve()
              .body(EpcSearchResponse.class);

      if (response == null) {
        log.error(
            "EPC API returned a null body for UPRN={} — possible auth failure or network issue",
            uprn);
        return null;
      }

      if (response.data() == null) {
        // Null data (not empty list) means our field mapping doesn't match the API envelope.
        // This is a mapping-drift bug, not a valid "no result" case.
        log.error(
            "EPC API response had a null 'data' field for UPRN={} — possible API contract change."
                + " Raw response: {}",
            uprn,
            response);
        return null;
      }

      if (response.data().isEmpty()) {
        log.info("No EPC on record in government registry for UPRN={}", uprn);
        return null;
      }

      // data[] is ordered most-recent first
      final EpcRow row = response.data().get(0);
      log.info(
          "EPC found for UPRN={}: band={} registrationDate={}",
          uprn,
          row.currentEnergyEfficiencyBand(),
          row.registrationDate());

      try {
        final LocalDate registrationDate = LocalDate.parse(row.registrationDate());
        return new EpcRecord(registrationDate, row.currentEnergyEfficiencyBand());
      } catch (DateTimeParseException ex) {
        log.error(
            "EPC API returned unparseable registrationDate '{}' for UPRN={} — skipping",
            row.registrationDate(),
            uprn);
        return null;
      }

    } catch (HttpClientErrorException.NotFound ex) {
      // 404 is the API's way of saying "no EPC on record" — expected for many properties
      log.info("No EPC on record in government registry for UPRN={} (404)", uprn);
      return null;
    } catch (RestClientException ex) {
      log.error("EPC API call failed for UPRN={}: {}", uprn, ex.getMessage(), ex);
      return null;
    }
  }

  // ── Public result record ──────────────────────────────────────────────────

  /**
   * Distilled EPC data needed to create the {@code Certificate} entity.
   *
   * @param registrationDate the date the EPC was lodged at the government registry
   * @param energyBand       letter band rating, e.g. "D"
   */
  public record EpcRecord(LocalDate registrationDate, String energyBand) {}

  // ── Internal JSON mapping records ─────────────────────────────────────────

  /**
   * No {@code @JsonIgnoreProperties} here intentionally — if the API renames its envelope field
   * (e.g. {@code data} → {@code results}), Jackson will throw an
   * {@code UnrecognizedPropertyException} which is caught and logged as ERROR above, rather than
   * silently deserialising to {@code null}.
   */
  private record EpcSearchResponse(List<EpcRow> data) {}

  /** Row-level fields are allowed to grow freely — new fields won't break existing mapping. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EpcRow(String registrationDate, String currentEnergyEfficiencyBand) {}
}
