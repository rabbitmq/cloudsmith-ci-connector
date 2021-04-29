package com.rabbitmq.concourse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.rabbitmq.concourse.CloudsmithResource.CloudsmithPackageAccess;
import com.rabbitmq.concourse.CloudsmithResource.Input;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpTest {

  WireMockServer wireMockServer;

  @BeforeEach
  public void startMockServer() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor(wireMockServer.port());
  }

  @AfterEach
  public void stopMockServer() {
    wireMockServer.stop();
  }

  @Test
  void findForDelete() throws Exception {
    String in =
        "{\n"
            + "  \"source\": {\n"
            + "    \"username\": \"foo\",\n"
            + "    \"organization\": \"bar\",\n"
            + "    \"repository\": \"weird#repo\",\n"
            + "    \"api_key\": \"the-api-key\",\n"
            + "    \"name\": \"^erlang*\",\n"
            + "    \"distribution\": \"debian/stretch\"\n"
            + "  },\n"
            + "  \"params\": {\n"
            + "    \"delete\": true,\n"
            + "    \"version_filter\": \"1:23*\",\n"
            + "    \"keep_last_n\": 10\n"
            + "  }\n"
            + "}";
    Input input = CloudsmithResource.GSON.fromJson(in, Input.class);
    stubFor(get(urlPathMatching("/packages/.*")).willReturn(aResponse().withBody("[]")));
    CloudsmithPackageAccess access = access(input);
    access.find();
    verify(
        exactly(1),
        getRequestedFor(
            urlEqualTo(
                "/packages/bar/weird%23repo/?query=filename%3A%5Eerlang*+AND+version%3A1%3A23*+AND+distribution%3Adebian+AND+distribution%3Astretch")));
  }

  @Test
  void pagination() throws Exception {
    String in =
        "{\n"
            + "  \"source\": {\n"
            + "    \"username\": \"team-rabbitmq\",\n"
            + "    \"organization\": \"rabbitmq\",\n"
            + "    \"repository\": \"rabbitmq-erlang\",\n"
            + "    \"api_key\": \"the api key\",\n"
            + "    \"name\": \"^erlang*\",\n"
            + "    \"distribution\": \"debian/stretch\"\n"
            + "  },\n"
            + "  \"params\": {\n"
            + "    \"delete\": true,\n"
            + "    \"version_filter\": \"1:23*\",\n"
            + "    \"keep_last_n\": 2\n"
            + "  }\n"
            + "}";
    String response = "[{\"filename\": \"some-package.deb\"}]";
    IntFunction<String> nextHeader =
        page ->
            "<" + baseUrl() + "/packages/rabbitmq/rabbitmq-erlang/page-" + page + ">; rel=\"next\"";
    Input input = CloudsmithResource.GSON.fromJson(in, Input.class);
    stubFor(
        get(urlPathMatching("/packages/rabbitmq/rabbitmq-erlang/.*"))
            .willReturn(aResponse().withBody(response).withHeader("Link", nextHeader.apply(2))));
    stubFor(
        get(urlPathMatching("/packages/rabbitmq/rabbitmq-erlang/page-2"))
            .willReturn(aResponse().withBody(response).withHeader("Link", nextHeader.apply(3))));
    stubFor(
        get(urlPathMatching("/packages/rabbitmq/rabbitmq-erlang/page-3"))
            .willReturn(aResponse().withBody(response)));
    CloudsmithPackageAccess access = access(input);
    List<Package> packages = access.find();
    assertThat(packages).hasSize(3);
  }

  CloudsmithPackageAccess access(Input input) {
    return new CloudsmithPackageAccess(input, baseUrl(), baseUrl(), baseUrl());
  }

  String baseUrl() {
    return "http://localhost:" + wireMockServer.port();
  }
}
