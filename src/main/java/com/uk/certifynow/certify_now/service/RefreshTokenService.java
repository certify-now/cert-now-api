package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.RefreshTokenDTO;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;

  public RefreshTokenService(
      final RefreshTokenRepository refreshTokenRepository, final UserRepository userRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
  }

  public List<RefreshTokenDTO> findAll() {
    final List<RefreshToken> refreshTokens = refreshTokenRepository.findAll(Sort.by("id"));
    return refreshTokens.stream()
        .map(refreshToken -> mapToDTO(refreshToken, new RefreshTokenDTO()))
        .toList();
  }

  public RefreshTokenDTO get(final UUID id) {
    return refreshTokenRepository
        .findById(id)
        .map(refreshToken -> mapToDTO(refreshToken, new RefreshTokenDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final RefreshTokenDTO refreshTokenDTO) {
    final RefreshToken refreshToken = new RefreshToken();
    mapToEntity(refreshTokenDTO, refreshToken);
    return refreshTokenRepository.save(refreshToken).getId();
  }

  public void update(final UUID id, final RefreshTokenDTO refreshTokenDTO) {
    final RefreshToken refreshToken =
        refreshTokenRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(refreshTokenDTO, refreshToken);
    refreshTokenRepository.save(refreshToken);
  }

  public void delete(final UUID id) {
    final RefreshToken refreshToken =
        refreshTokenRepository.findById(id).orElseThrow(NotFoundException::new);
    refreshTokenRepository.delete(refreshToken);
  }

  private RefreshTokenDTO mapToDTO(
      final RefreshToken refreshToken, final RefreshTokenDTO refreshTokenDTO) {
    refreshTokenDTO.setId(refreshToken.getId());
    refreshTokenDTO.setRevoked(refreshToken.getRevoked());
    refreshTokenDTO.setCreatedAt(refreshToken.getCreatedAt());
    refreshTokenDTO.setExpiresAt(refreshToken.getExpiresAt());
    refreshTokenDTO.setRevokedAt(refreshToken.getRevokedAt());
    refreshTokenDTO.setDeviceInfo(refreshToken.getDeviceInfo());
    refreshTokenDTO.setIpAddress(refreshToken.getIpAddress());
    refreshTokenDTO.setTokenHash(refreshToken.getTokenHash());
    refreshTokenDTO.setUser(refreshToken.getUser() == null ? null : refreshToken.getUser().getId());
    return refreshTokenDTO;
  }

  private RefreshToken mapToEntity(
      final RefreshTokenDTO refreshTokenDTO, final RefreshToken refreshToken) {
    refreshToken.setRevoked(refreshTokenDTO.getRevoked());
    refreshToken.setCreatedAt(refreshTokenDTO.getCreatedAt());
    refreshToken.setExpiresAt(refreshTokenDTO.getExpiresAt());
    refreshToken.setRevokedAt(refreshTokenDTO.getRevokedAt());
    refreshToken.setDeviceInfo(refreshTokenDTO.getDeviceInfo());
    refreshToken.setIpAddress(refreshTokenDTO.getIpAddress());
    refreshToken.setTokenHash(refreshTokenDTO.getTokenHash());
    final User user =
        refreshTokenDTO.getUser() == null
            ? null
            : userRepository
                .findById(refreshTokenDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    refreshToken.setUser(user);
    return refreshToken;
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final RefreshToken userRefreshToken = refreshTokenRepository.findFirstByUserId(event.getId());
    if (userRefreshToken != null) {
      referencedException.setKey("user.refreshToken.user.referenced");
      referencedException.addParam(userRefreshToken.getId());
      throw referencedException;
    }
  }
}
