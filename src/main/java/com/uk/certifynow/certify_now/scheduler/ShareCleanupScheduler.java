package com.uk.certifynow.certify_now.scheduler;

import com.uk.certifynow.certify_now.repos.ShareTokenRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes expired share tokens daily. Runs at 3:00 AM UTC. */
@Component
public class ShareCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(ShareCleanupScheduler.class);

  private final ShareTokenRepository shareTokenRepository;
  private final Clock clock;

  public ShareCleanupScheduler(final ShareTokenRepository shareTokenRepository, final Clock clock) {
    this.shareTokenRepository = shareTokenRepository;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void deleteExpiredShareTokens() {
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final int deleted = shareTokenRepository.deleteExpiredTokens(now);
    if (deleted > 0) {
      log.info("Share cleanup: deleted {} expired share token(s)", deleted);
    } else {
      log.debug("Share cleanup: no expired share tokens to delete");
    }
  }
}
