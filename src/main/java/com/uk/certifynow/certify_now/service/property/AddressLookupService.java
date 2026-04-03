package com.uk.certifynow.certify_now.service.property;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uk.certifynow.certify_now.rest.dto.address.AddressSuggestionResponse;
import com.uk.certifynow.certify_now.rest.dto.address.ResolvedAddressResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin proxy around the Ideal Postcodes address API.
 *
 * <p>Keeping the third-party API key server-side prevents it from being extracted from the mobile
 * bundle.
 *
 * <p>Endpoints used:
 *
 * <ul>
 *   <li>Autocomplete: {@code GET /v1/autocomplete/addresses?q=&lt;query&gt;&api_key=&lt;key&gt;}
 *   <li>Resolve: {@code GET /v1/autocomplete/addresses/&lt;id&gt;/gbr?api_key=&lt;key&gt;}
 *   <li>Postcode centroid: {@code GET /v1/postcodes/&lt;postcode&gt;?api_key=&lt;key&gt;}
 * </ul>
 *
 * <p>The postcode centroid endpoint is used as a fallback when the user enters an address manually
 * (i.e. no autocomplete selection), ensuring every property always has coordinates for PostGIS
 * engineer-radius matching.
 */
@Service
public class AddressLookupService {

  private static final Logger log = LoggerFactory.getLogger(AddressLookupService.class);

  private final RestClient restClient;
  private final String apiKey;

  public AddressLookupService(
      @Value("${app.ideal-postcodes.base-url}") final String baseUrl,
      @Value("${app.ideal-postcodes.api-key}") final String apiKey) {
    this.apiKey = apiKey;
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  /** Returns up to 10 address suggestions for the given free-text query. */
  @Cacheable(value = "address-autocomplete", key = "T(String).valueOf(#query).trim()")
  public List<AddressSuggestionResponse> autocomplete(final String query) {
    final AutocompleteEnvelope envelope =
        restClient
            .get()
            .uri("/autocomplete/addresses?q={q}&api_key={k}", query, apiKey)
            .retrieve()
            .body(AutocompleteEnvelope.class);

    if (envelope == null || envelope.result() == null) {
      log.warn("Ideal Postcodes autocomplete returned empty response for query: {}", query);
      return List.of();
    }

    return envelope.result().hits().stream()
        .map(h -> new AddressSuggestionResponse(h.id(), h.suggestion()))
        .toList();
  }

  /**
   * Resolves a suggestion {@code id} (e.g. {@code paf_10093397}) to a full address with UPRN and
   * coordinates.
   */
  @Cacheable(value = "address-resolve", key = "#id")
  public ResolvedAddressResponse resolve(final String id) {
    final ResolveEnvelope envelope =
        restClient
            .get()
            .uri("/autocomplete/addresses/{id}/gbr?api_key={k}", id, apiKey)
            .retrieve()
            .body(ResolveEnvelope.class);

    if (envelope == null || envelope.result() == null) {
      throw new IllegalStateException(
          "Ideal Postcodes resolve returned empty response for id: " + id);
    }

    final ResolveResult r = envelope.result();
    return new ResolvedAddressResponse(
        r.line1(),
        r.line2() != null ? r.line2() : "",
        r.postTown(),
        r.county() != null ? r.county() : "",
        r.postcode(),
        r.countryIso2() != null ? r.countryIso2() : "GB",
        r.uprn() != null ? r.uprn() : "",
        r.latitude(),
        r.longitude());
  }

  /**
   * Returns the centroid coordinates for a UK postcode.
   *
   * <p>Used as a fallback when the user enters an address manually (without autocomplete), so every
   * property always receives coordinates for radius-based engineer matching. Postcode centroids are
   * accurate to ~50 m for UK postcodes.
   *
   * @param postcode raw UK postcode (spaces allowed, case-insensitive)
   * @return lat/lng of the postcode centroid, or {@code null} if the lookup fails
   */
  @Cacheable(
      value = "postcode-centroid",
      key = "T(String).valueOf(#postcode).replaceAll(' ', '')",
      unless = "#result == null")
  public double[] lookupPostcodeCentroid(final String postcode) {
    try {
      final PostcodeEnvelope envelope =
          restClient
              .get()
              .uri("/postcodes/{postcode}?api_key={k}", postcode.replace(" ", ""), apiKey)
              .retrieve()
              .body(PostcodeEnvelope.class);

      if (envelope == null || envelope.result() == null) {
        log.warn("Ideal Postcodes centroid returned empty response for postcode: {}", postcode);
        return null;
      }

      final PostcodeResult r = envelope.result();
      if (r.latitude() == null || r.longitude() == null) {
        log.warn("Ideal Postcodes centroid missing lat/lng for postcode: {}", postcode);
        return null;
      }

      return new double[] {r.latitude(), r.longitude()};
    } catch (Exception ex) {
      log.error("Postcode centroid lookup failed for {}: {}", postcode, ex.getMessage());
      return null;
    }
  }

  // ── Internal JSON mapping records ────────────────────────────────────────────

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AutocompleteEnvelope(AutocompleteResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AutocompleteResult(List<Hit> hits) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Hit(String id, String suggestion) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ResolveEnvelope(ResolveResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ResolveResult(
      @JsonProperty("line_1") String line1,
      @JsonProperty("line_2") String line2,
      @JsonProperty("post_town") String postTown,
      @JsonProperty("county") String county,
      @JsonProperty("postcode") String postcode,
      @JsonProperty("country_iso_2") String countryIso2,
      @JsonProperty("uprn") String uprn,
      @JsonProperty("latitude") Double latitude,
      @JsonProperty("longitude") Double longitude) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PostcodeEnvelope(PostcodeResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PostcodeResult(
      @JsonProperty("latitude") Double latitude, @JsonProperty("longitude") Double longitude) {}
}
