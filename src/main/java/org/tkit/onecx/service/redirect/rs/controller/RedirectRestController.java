package org.tkit.onecx.service.redirect.rs.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.tkit.onecx.service.redirect.rs.RedirectConfig;
import org.tkit.onecx.service.redirect.rs.RedirectUtils;

import io.quarkus.logging.Log;
import io.quarkus.qute.Engine;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;

@Path("/{path:.*}")
@ApplicationScoped
@SuppressWarnings("java:S3655")
public class RedirectRestController {

    @Inject
    Template redirectTemplate;

    @Inject
    Template fallbackTemplate;

    @Inject
    RedirectConfig redirectConfig;

    @Inject
    Engine engine;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response redirectIncomingRequest(@Context UriInfo uriInfo) {
        String fullPath = uriInfo.getRequestUri().toString();

        Map<String, RedirectConfig.ClientRule> clientRules = redirectConfig.urlRewriteRules().entrySet().stream()
                .filter(entry -> fullPath.matches(entry.getKey()))
                .max((e1, e2) -> {
                    // Prefer the more specific pattern (longer pattern = more specific)
                    int matchLength1 = e1.getKey().replace("\\.\\*", "").length();
                    int matchLength2 = e2.getKey().replace("\\.\\*", "").length();
                    return Integer.compare(matchLength1, matchLength2);
                })
                .map(Map.Entry::getValue)
                .orElse(null);

        Template tpl = redirectTemplate;

        // if no matching rule group is found, use fallback template
        if (clientRules == null) {
            tpl = fallbackTemplate;

            // use custom fallback template if provided
            if (redirectConfig.customFallbackTemplatePath().isPresent()) {
                try {
                    String content = Files.readString(Paths.get(redirectConfig.customFallbackTemplatePath().get()),
                            StandardCharsets.UTF_8);
                    tpl = engine.parse(content);
                } catch (IOException e) {
                    Log.error(
                            "Failed to load custom fallback template from path: " + redirectConfig.customFallbackTemplatePath(),
                            e);
                }
            }

            return Response.ok(tpl.data("reqPath", fullPath).render()).build();
        }

        // use custom redirect template if provided
        if (redirectConfig.customRedirectTemplatePath().isPresent()) {
            try {
                String content = Files.readString(Paths.get(redirectConfig.customRedirectTemplatePath().get()),
                        StandardCharsets.UTF_8);
                tpl = engine.parse(content);
            } catch (IOException e) {
                Log.error("Failed to load custom redirect template from path: " + redirectConfig.customRedirectTemplatePath(),
                        e);
            }
        }

        // Sort rules by their numeric index key and serialize to a JSON array for the template
        String rulesJson = RedirectUtils.rulesToJson(clientRules);

        return Response
                .ok(tpl.data("rules", new RawString(rulesJson)).render())
                .build();
    }
}
