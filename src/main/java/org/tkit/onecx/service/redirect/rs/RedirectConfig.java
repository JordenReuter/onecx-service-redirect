package org.tkit.onecx.service.redirect.rs;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocFilename;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Redirect & Replace configuration
 */
@ConfigDocFilename("onecx-service-redirect.adoc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "onecx.redirect")
public interface RedirectConfig {

    /**
     * Url Redirect Rules.
     * The outer map key is a server-side regex matched against the incoming request URL.
     * The inner map key is an integer index (e.g. "0", "1", "2") defining the priority order
     * in which the client-side patterns are tried. Lower index = higher priority.
     */
    @WithName("url-rewrite-rules")
    Map<String, Map<String, ClientRule>> urlRewriteRules();

    /**
     * File path to custom redirect template
     */
    @WithName("custom-redirect-template-path")
    Optional<String> customRedirectTemplatePath();

    /**
     * File path to custom fallback template
     */
    @WithName("custom-fallback-template-path")
    Optional<String> customFallbackTemplatePath();

    /**
     * A single client-side rewrite rule (pattern + replacement).
     * Multiple rules per URL group are tried in index order by the browser.
     */
    interface ClientRule {

        /**
         * Regex pattern tested against window.location.href (including fragment) in the browser.
         */
        @WithName("pattern")
        String pattern();

        /**
         * Replace-pattern to rewrite the URL. Use ($groupName) to reference named capture groups.
         */
        @WithName("replace-pattern")
        String replacePattern();
    }
}
