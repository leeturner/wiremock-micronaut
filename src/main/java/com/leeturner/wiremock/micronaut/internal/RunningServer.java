package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;

/** A started server together with the annotation that configured it. */
public record RunningServer(ConfigureWireMock options, WireMockServer server) {}
