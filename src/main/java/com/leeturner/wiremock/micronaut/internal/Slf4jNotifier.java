package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.common.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes WireMock's own output through SLF4J, one logger per server. */
final class Slf4jNotifier implements Notifier {
  private final Logger logger;

  Slf4jNotifier(String serverName) {
    this.logger = LoggerFactory.getLogger("WireMock." + serverName);
  }

  @Override
  public void info(String message) {
    logger.info(message);
  }

  @Override
  public void error(String message) {
    logger.error(message);
  }

  @Override
  public void error(String message, Throwable t) {
    logger.error(message, t);
  }
}
