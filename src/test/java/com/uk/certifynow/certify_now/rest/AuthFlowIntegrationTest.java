package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.uk.certifynow.certify_now.BaseIntegrationTest;
import io.restassured.http.ContentType;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the complete authentication flow.
 *
 * <p>Boots the full Spring context with Testcontainers PostgreSQL and exercises register →
 * verify-email → login → logout end-to-end via the REST API.
 */
class AuthFlowIntegrationTest extends BaseIntegrationTest {

  @Test
  void fullAuthFlow_registerVerifyLoginLogout() {
    // ── 1. Register ──
    final AtomicReference<String> codeRef = new AtomicReference<>();
    doAnswer(
            inv -> {
              codeRef.set(inv.getArgument(2));
              return null;
            })
        .when(emailService)
        .sendVerificationEmail(eq("flow@example.com"), any(), any());

    final String accessToken =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
            {
              "email": "flow@example.com",
              "password": "Test1234!",
              "fullName": "Flow Tester",
              "role": "CUSTOMER"
            }
            """)
            .post("/api/v1/auth/register")
            .then()
            .statusCode(201)
            .body("data.accessToken", notNullValue())
            .body("data.user.email", equalTo("flow@example.com"))
            .body("data.user.emailVerified", equalTo(false))
            .extract()
            .path("data.accessToken");

    assertThat(codeRef.get()).isNotBlank();

    // ── 2. Verify email ──
    given()
        .contentType(ContentType.JSON)
        .body("{\"code\": \"" + codeRef.get() + "\"}")
        .post("/api/v1/auth/verify-email")
        .then()
        .statusCode(200)
        .body("data.user.emailVerified", equalTo(true));

    // ── 3. Login ──
    final var loginResponse =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
            {
              "email": "flow@example.com",
              "password": "Test1234!"
            }
            """)
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("data.accessToken", notNullValue())
            .body("data.refreshToken", notNullValue())
            .body("data.user.emailVerified", equalTo(true))
            .extract();

    final String loginAccessToken = loginResponse.path("data.accessToken");
    final String refreshToken = loginResponse.path("data.refreshToken");

    // ── 4. Logout ──
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + loginAccessToken)
        .body("{\"refreshToken\": \"" + refreshToken + "\"}")
        .post("/api/v1/auth/logout")
        .then()
        .statusCode(204);
  }

  @Test
  void login_wrongPassword_returns401() {
    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "email": "wrong-pw@example.com",
              "password": "Test1234!",
              "fullName": "Wrong Pw Tester",
              "role": "CUSTOMER"
            }
            """)
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "email": "wrong-pw@example.com",
              "password": "WrongPass1!"
            }
            """)
        .post("/api/v1/auth/login")
        .then()
        .statusCode(401)
        .body("error", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  void login_nonExistentUser_returns401() {
    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "email": "ghost@example.com",
              "password": "Test1234!"
            }
            """)
        .post("/api/v1/auth/login")
        .then()
        .statusCode(401)
        .body("error", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  void refreshToken_rotation_oldTokenInvalid_newTokenWorks() {
    final AtomicReference<String> codeRef = new AtomicReference<>();
    doAnswer(
            inv -> {
              codeRef.set(inv.getArgument(2));
              return null;
            })
        .when(emailService)
        .sendVerificationEmail(eq("rotate@example.com"), any(), any());

    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"email\":\"rotate@example.com\",\"password\":\"Test1234!\",\"fullName\":\"Rotate\",\"role\":\"CUSTOMER\"}")
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body("{\"code\":\"" + codeRef.get() + "\"}")
        .post("/api/v1/auth/verify-email")
        .then()
        .statusCode(200);

    final var loginResp =
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"rotate@example.com\",\"password\":\"Test1234!\"}")
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    final String oldRefreshToken = loginResp.path("data.refreshToken");

    final var rotateResp =
        given()
            .contentType(ContentType.JSON)
            .body("{\"refreshToken\":\"" + oldRefreshToken + "\"}")
            .post("/api/v1/auth/refresh")
            .then()
            .statusCode(200)
            .extract();

    final String newRefreshToken = rotateResp.path("data.refreshToken");
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

    given()
        .contentType(ContentType.JSON)
        .body("{\"refreshToken\":\"" + oldRefreshToken + "\"}")
        .post("/api/v1/auth/refresh")
        .then()
        .statusCode(403);

    given()
        .contentType(ContentType.JSON)
        .body("{\"refreshToken\":\"" + newRefreshToken + "\"}")
        .post("/api/v1/auth/refresh")
        .then()
        .statusCode(200);
  }

  @Test
  void refreshToken_reuse_revokesEntireFamily() {
    final AtomicReference<String> codeRef = new AtomicReference<>();
    doAnswer(
            inv -> {
              codeRef.set(inv.getArgument(2));
              return null;
            })
        .when(emailService)
        .sendVerificationEmail(eq("reuse@example.com"), any(), any());

    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"email\":\"reuse@example.com\",\"password\":\"Test1234!\",\"fullName\":\"Reuse\",\"role\":\"CUSTOMER\"}")
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body("{\"code\":\"" + codeRef.get() + "\"}")
        .post("/api/v1/auth/verify-email")
        .then()
        .statusCode(200);

    final var loginResp =
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"reuse@example.com\",\"password\":\"Test1234!\"}")
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    final String originalToken = loginResp.path("data.refreshToken");

    final var rotateResp =
        given()
            .contentType(ContentType.JSON)
            .body("{\"refreshToken\":\"" + originalToken + "\"}")
            .post("/api/v1/auth/refresh")
            .then()
            .statusCode(200)
            .extract();

    final String newToken = rotateResp.path("data.refreshToken");

    given()
        .contentType(ContentType.JSON)
        .body("{\"refreshToken\":\"" + originalToken + "\"}")
        .post("/api/v1/auth/refresh")
        .then()
        .statusCode(403)
        .body("error", equalTo("TOKEN_REUSE_DETECTED"));

    given()
        .contentType(ContentType.JSON)
        .body("{\"refreshToken\":\"" + newToken + "\"}")
        .post("/api/v1/auth/refresh")
        .then()
        .statusCode(403);
  }

  @Test
  void logout_accessTokenDenylisted_subsequentRequestReturns401() {
    final AtomicReference<String> codeRef = new AtomicReference<>();
    doAnswer(
            inv -> {
              codeRef.set(inv.getArgument(2));
              return null;
            })
        .when(emailService)
        .sendVerificationEmail(eq("denylist@example.com"), any(), any());

    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"email\":\"denylist@example.com\",\"password\":\"Test1234!\",\"fullName\":\"Denylist\",\"role\":\"CUSTOMER\"}")
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body("{\"code\":\"" + codeRef.get() + "\"}")
        .post("/api/v1/auth/verify-email")
        .then()
        .statusCode(200);

    final var loginResp =
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"denylist@example.com\",\"password\":\"Test1234!\"}")
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    final String accessToken = loginResp.path("data.accessToken");
    final String refreshToken = loginResp.path("data.refreshToken");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + accessToken)
        .body("{\"refreshToken\":\"" + refreshToken + "\"}")
        .post("/api/v1/auth/logout")
        .then()
        .statusCode(204);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .get("/api/v1/users/me")
        .then()
        .statusCode(401)
        .body("error", equalTo("TOKEN_REVOKED"));
  }

  @Test
  void register_duplicateEmail_returns201_silentlyHandled() {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"email\":\"dup@example.com\",\"password\":\"Test1234!\",\"fullName\":\"Dup\",\"role\":\"CUSTOMER\"}")
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"email\":\"dup@example.com\",\"password\":\"Different1!\",\"fullName\":\"Dup2\",\"role\":\"CUSTOMER\"}")
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);
  }

  @Test
  void unverifiedUser_cannotAccessProtectedEndpoints() {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"email\":\"unverified@example.com\",\"password\":\"Test1234!\",\"fullName\":\"Unverified\",\"role\":\"CUSTOMER\"}")
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    final var loginResp =
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"unverified@example.com\",\"password\":\"Test1234!\"}")
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    final String accessToken = loginResp.path("data.accessToken");

    given()
        .header("Authorization", "Bearer " + accessToken)
        .get("/api/v1/properties")
        .then()
        .statusCode(403)
        .body("error", equalTo("EMAIL_NOT_VERIFIED"));

    given()
        .header("Authorization", "Bearer " + accessToken)
        .get("/api/v1/users/me")
        .then()
        .statusCode(200);
  }
}
