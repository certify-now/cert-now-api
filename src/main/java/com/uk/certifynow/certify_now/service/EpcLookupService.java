package com.uk.certifynow.certify_now.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
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
 * <p>API: {@code GET /api/domestic/search?uprn=<uprn>} Auth: Bearer token in Authorization header.
 *
 * <p>Actual response envelope (camelCase JSON):
 *
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
 * <p>Returns the most recent {@link EpcRecord} for the UPRN, or {@code null} if no EPC is on
 * record.
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
   *
   * <ul>
   *   <li>{@code INFO} — EPC found or genuinely not on record (expected empty result)
   *   <li>{@code ERROR} — null response body or null {@code data} field (mapping drift / auth
   *       failure / network issue) — both are actionable failures requiring attention
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
        log.error(
            "EPC API response had a null 'data' field for UPRN={} — possible API contract change",
            uprn);
        return null;
      }

      // The API returns {"data": {"error": "..."}} (a Map, not a List) when no EPC exists for
      // the UPRN. This is a known quirk — check the type before trying to iterate.
      if (!(response.data() instanceof List<?> dataList)) {
        log.info(
            "No EPC on record in government registry for UPRN={} (API returned non-array data: {})",
            uprn,
            response.data());
        return null;
      }

      if (dataList.isEmpty()) {
        log.info("No EPC on record in government registry for UPRN={}", uprn);
        return null;
      }

      // data[] is ordered most-recent first; each element is a Map of field→value
      @SuppressWarnings("unchecked")
      final Map<String, Object> row = (Map<String, Object>) dataList.get(0);
      final String registrationDateStr = (String) row.get("registrationDate");
      final String energyBand = (String) row.get("currentEnergyEfficiencyBand");
      final String certificateNumber = (String) row.get("certificateNumber");

      log.info(
          "EPC found for UPRN={}: band={} registrationDate={} certificateNumber={}",
          uprn,
          energyBand,
          registrationDateStr,
          certificateNumber);

      try {
        final LocalDate registrationDate = LocalDate.parse(registrationDateStr);
        return new EpcRecord(registrationDate, energyBand, certificateNumber);
      } catch (DateTimeParseException ex) {
        log.error(
            "EPC API returned unparseable registrationDate '{}' for UPRN={} — skipping",
            registrationDateStr,
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
   * @param energyBand letter band rating, e.g. "D"
   * @param certificateNumber the unique certificate number, e.g. "2711-1106-6149-2191-5120"
   */
  public record EpcRecord(
      LocalDate registrationDate, String energyBand, String certificateNumber) {}

  // ── Internal JSON mapping records ─────────────────────────────────────────

  /**
   * The government API returns two different shapes for the {@code data} field:
   *
   * <ul>
   *   <li>A JSON array (deserialised as {@code List<Map>}) when EPCs exist for the UPRN.
   *   <li>A JSON object {@code {"error": "..."}} (deserialised as {@code Map}) when no EPC is on
   *       record for the UPRN.
   * </ul>
   *
   * Using {@code Object} lets Jackson deserialise either shape without error. We check {@code
   * instanceof List} at runtime to distinguish the two cases. {@code @JsonIgnoreProperties} is
   * added so that unrecognised envelope fields (e.g. pagination) don't break deserialization.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EpcSearchResponse(Object data) {}
}
