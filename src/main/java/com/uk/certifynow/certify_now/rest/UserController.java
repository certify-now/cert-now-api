package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(final UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public ApiResponse<UserMeDTO> getMe(
      final Authentication authentication, final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final UserMeDTO user = userService.getMe(userId);
    return ApiResponse.of(user, requestId(request));
  }

  @PutMapping("/me")
  public ApiResponse<Void> updateMe(
      @Valid @RequestBody final UpdateMeRequest updateMeRequest,
      final Authentication authentication,
      final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    userService.updateMe(userId, updateMeRequest);
    return ApiResponse.of(null, requestId(request));
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}
