package org.tkit.onecx.service.redirect.rs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RedirectUtilsTest {

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
    void rulesToJson_sortsNumericKeysInOrder() {
        var rules = Map.of(
                "2", clientRule("pattern-c", "/replace-c"),
                "0", clientRule("pattern-a", "/replace-a"),
                "1", clientRule("pattern-b", "/replace-b"));

        String json = RedirectUtils.rulesToJson(rules);

        int posA = json.indexOf("pattern-a");
        int posB = json.indexOf("pattern-b");
        int posC = json.indexOf("pattern-c");
        assertThat(posA).isLessThan(posB);
        assertThat(posB).isLessThan(posC);
    }

    @Test
    void rulesToJson_nonNumericKeyFallsToEnd() {
        var rules = Map.of(
                "0", clientRule("pattern-first", "/replace-first"),
                "not-a-number", clientRule("pattern-last", "/replace-last"));

        String json = RedirectUtils.rulesToJson(rules);

        // non-numeric key gets Integer.MAX_VALUE priority → sorted to the end
        int posFirst = json.indexOf("pattern-first");
        int posLast = json.indexOf("pattern-last");
        assertThat(posFirst).isLessThan(posLast);
    }

    @Test
    void rulesToJson_allNonNumericKeys_doesNotThrow() {
        var rules = Map.of(
                "foo", clientRule("pattern-foo", "/replace-foo"),
                "bar", clientRule("pattern-bar", "/replace-bar"));

        String json = RedirectUtils.rulesToJson(rules);

        assertThat(json).startsWith("[").endsWith("]").contains("pattern-foo").contains("pattern-bar");
    }

    @Test
    void rulesToJson_nonNumericKey_numberFormatExceptionIsCaught() {
        var rules = Map.of("not-a-number", clientRule("pattern-x", "/replace-x"));

        assertThatCode(() -> RedirectUtils.rulesToJson(rules)).doesNotThrowAnyException();

        String json = RedirectUtils.rulesToJson(rules);
        assertThat(json).contains("pattern-x").contains("/replace-x");
    }

    @Test
    void jsonString_escapesDoubleQuotes() {
        String result = RedirectUtils.jsonString("say \"hello\"");
        assertThat(result).isEqualTo("\"say \\\"hello\\\"\"");
    }

    @Test
    void jsonString_doesNotEscapeBackslashes() {
        String result = RedirectUtils.jsonString("a\\?b");
        assertThat(result).isEqualTo("\"a\\?b\"");
    }
}
