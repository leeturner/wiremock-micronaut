package com.leeturner.wiremock.micronaut.testsupport;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/** Lets two fixture classes prove they ran at the same time: each waits for the other. */
public final class Rendezvous {
  private static final CyclicBarrier BARRIER = new CyclicBarrier(2);

  private Rendezvous() {}

  /** Blocks until another caller arrives; throws if none does within 10 seconds. */
  public static void meet() {
    try {
      BARRIER.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      throw new IllegalStateException("The parallel fixture classes never overlapped", e);
    }
  }
}
