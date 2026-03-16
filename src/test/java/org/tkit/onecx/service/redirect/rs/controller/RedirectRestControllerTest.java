package org.tkit.onecx.service.redirect.rs.controller;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tkit.onecx.service.redirect.rs.RedirectConfig;

import io.quarkus.test.InjectMock;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.SmallRyeConfig;

@QuarkusTest
class RedirectRestControllerTest {

    @InjectMock
    RedirectConfig redirectConfig;

    public static class ConfigProducer {

        @Inject
        Config config;

        @Produces
        @ApplicationScoped
        @Mock
        RedirectConfig config() {
            return config.unwrap(SmallRyeConfig.class).getConfigMapping(RedirectConfig.class);
        }
    }

    private static RedirectConfig.ClientRule clientRule(String pattern, String replacePattern) {
        return new RedirectConfig.ClientRule() {
            @Override
            public String pattern() {
                return pattern;
            }

            @Override
            public String replacePattern() {
                return replacePattern;
            }
        };
    }

    @Test
    void usesFallbackWhenNoRuleMatches() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/unknown/path");
    }

    @Test
    void usesCustomFallbackWhenNoRuleMatches() throws IOException {
        Path tmp = Files.createTempFile("tpl", ".html");
        Files.writeString(tmp, "custom {reqPath}", StandardCharsets.UTF_8);

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.of(tmp.toString()));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("custom").contains("some/unknown/path");
    }

    @Test
    void usesFallbackWhenNoRuleMatchesAndCustomTemplateFailed() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.of("not/existing/path.html"));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/unknown/path").doesNotContain("custom");
    }

    @Test
    void appliesBestMatchingRule() {
        var ruleMap = new HashMap<String, Map<String, RedirectConfig.ClientRule>>();

        ruleMap.put(".*test-ui.*", Map.of(
                "0", clientRule(".*test-ui.*", "/new/path")));

        ruleMap.put(".*test-ui/subTest.*", Map.of(
                "0", clientRule(".*test-ui/subTest.*", "/new/path/subTest")));

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(ruleMap);
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/subTest")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*test-ui/subTest.*").contains("/new/path/subTest");
    }

    @Test
    void appliesDefaultTemplateWhenRuleMatches() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*test-ui.*", Map.of(
                        "0", clientRule(".*test-ui.*", "/new/path"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/old")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*test-ui.*");
        assertThat(body).contains("/new/path");
    }

    @Test
    void usesCustomTemplateWhenConfigured() throws IOException {
        Path tmp = Files.createTempFile("tpl", ".html");
        Files.writeString(tmp, "custom {rules}", StandardCharsets.UTF_8);

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*custom-test.*", Map.of(
                        "0", clientRule(".*custom-test.*", "/custom/replaced"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.of(tmp.toString()));

        var body = given()
                .accept(TEXT_HTML)
                .get("/custom-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("custom");
        assertThat(body).contains(".*custom-test.*");
        assertThat(body).contains("/custom/replaced");
    }

    @Test
    void continuesWithDefaultTemplateWhenCustomTemplateFails() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*fallback-test.*", Map.of(
                        "0", clientRule(".*fallback-test.*", "/fallback/replaced"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.of("/non/existing/path.html"));

        var body = given()
                .accept(TEXT_HTML)
                .get("/fallback-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*fallback-test.*");
        assertThat(body).contains("/fallback/replaced");
    }

    @Test
    void appliesMultipleClientRulesInOrder() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*multi-rule.*", Map.of(
                        "0", clientRule(".*multi-rule.*#/task/(?<woId>.+)", "/workorder/($woId)"),
                        "1", clientRule(".*multi-rule.*#/testOrder/(?<orderId>.+)", "/testorder/($orderId)"),
                        "2", clientRule(".*multi-rule.*", "/overview"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/multi-rule/page")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/workorder/($woId)").contains("/testorder/($orderId)").contains("/overview");
    }

    @Test
    void appliesSimpleRuleWithoutNamedGroups() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*test-ui.*", Map.of(
                        "0", clientRule("old", "new"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/old")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("\"pattern\":\"old\"").contains("\"replacePattern\":\"new\"");
    }

    @Test
    void appliesRuleWithNonNumericKeyWithoutThrowing() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*non-numeric.*", Map.of(
                        "not-a-number", clientRule(".*non-numeric.*", "/some/path"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/non-numeric/page")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/path");
    }

}
