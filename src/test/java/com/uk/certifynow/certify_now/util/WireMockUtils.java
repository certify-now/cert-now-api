package com.uk.certifynow.certify_now.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WireMockUtils {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("\"raw_token\"\\s*:\\s*\"([^\"]+)\"");

  private final WireMockServer wireMockServer;

  public WireMockUtils(final WireMockServer wireMockServer) {
    this.wireMockServer = wireMockServer;
  }

  public void stubEmailSuccess() {
    wireMockServer.stubFor(
        post(urlEqualTo("/email/send"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
  }

  public void stubEmailFailure() {
    wireMockServer.stubFor(
        post(urlEqualTo("/email/send"))
            .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));
  }

  public void resetStubs() {
    wireMockServer.resetAll();
  }

  public boolean verificationEmailWasSentTo(final String email) {
    return !wireMockServer.findAll(
            postRequestedFor(urlEqualTo("/email/send"))
                .withRequestBody(containing("\"type\":\"verification\""))
                .withRequestBody(containing("\"to_email\":\"" + email + "\"")))
        .isEmpty();
  }

  public boolean securityNotificationWasSentTo(final String email) {
    final String expectedLower = email.toLowerCase();
    final List<LoggedRequest> requests =
        wireMockServer.findAll(
            postRequestedFor(urlEqualTo("/email/send"))
                .withRequestBody(containing("\"type\":\"security_notification\"")));
    return requests.stream()
        .map(LoggedRequest::getBodyAsString)
        .map(String::toLowerCase)
        .anyMatch(body -> body.contains("\"to_email\":\"" + expectedLower + "\""));
  }

  public Optional<String> extractVerificationToken(final String email) {
    final List<LoggedRequest> requests =
        wireMockServer.findAll(
            postRequestedFor(urlEqualTo("/email/send"))
                .withRequestBody(containing("\"type\":\"verification\""))
                .withRequestBody(containing("\"to_email\":\"" + email + "\"")));
    if (requests.isEmpty()) {
      return Optional.empty();
    }
    final String body = requests.get(requests.size() - 1).getBodyAsString();
    final Matcher matcher = TOKEN_PATTERN.matcher(body);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  public int countSecurityNotifications() {
    return wireMockServer
        .findAll(
            postRequestedFor(urlEqualTo("/email/send"))
                .withRequestBody(containing("\"type\":\"security_notification\"")))
        .size();
  }

  public Optional<String> extractVerificationLink(final String email) {
    final Pattern pattern =
        Pattern.compile("\"verification_link\"\\s*:\\s*\"([^\"]+)\"", Pattern.MULTILINE);
    final List<LoggedRequest> requests =
        wireMockServer.findAll(
            postRequestedFor(urlEqualTo("/email/send"))
                .withRequestBody(containing("\"type\":\"verification\""))
                .withRequestBody(containing("\"to_email\":\"" + email + "\"")));
    if (requests.isEmpty()) {
      return Optional.empty();
    }
    final String body = requests.get(requests.size() - 1).getBodyAsString();
    final Matcher matcher = pattern.matcher(body);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
