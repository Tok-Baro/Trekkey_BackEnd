package com.api.trekkey.domain.contest.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContestHtmlSanitizerTest {

    private final ContestHtmlSanitizer contestHtmlSanitizer = new ContestHtmlSanitizer();

    @Test
    @DisplayName("허용된 서식 태그는 그대로 보존한다")
    void sanitize_preservesAllowedTags() {
        String html = "<h2>Title</h2><h3>Subtitle</h3><p>Body <strong>bold</strong> <em>italic</em> <b>b</b></p>"
                + "<ul><li>item1</li></ul><ol><li>item2</li></ol>";

        String result = contestHtmlSanitizer.sanitize(html);

        assertThat(result).isEqualTo(html);
    }

    @Test
    @DisplayName("script 태그는 내용까지 통째로 제거한다")
    void sanitize_removesScriptTag() {
        String result = contestHtmlSanitizer.sanitize("<p>hello</p><script>alert(1)</script>");

        assertThat(result).isEqualTo("<p>hello</p>");
    }

    @Test
    @DisplayName("허용 태그의 속성(onclick 등)은 모두 제거한다")
    void sanitize_removesAttributes() {
        String result = contestHtmlSanitizer.sanitize(
                "<p onclick=\"alert(1)\" style=\"color:red\" class=\"x\">hi</p>");

        assertThat(result).isEqualTo("<p>hi</p>");
    }

    @Test
    @DisplayName("허용되지 않은 태그는 제거하되 텍스트는 보존한다")
    void sanitize_dropsDisallowedTagButKeepsText() {
        String result = contestHtmlSanitizer.sanitize(
                "<div><p>text</p><a href=\"javascript:alert(1)\">link</a><img src=\"x\" onerror=\"alert(1)\"></div>");

        assertThat(result).isEqualTo("<p>text</p>link");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("null 또는 공백 입력은 null을 반환한다")
    void sanitize_returnsNullForNullOrBlank(String html) {
        assertThat(contestHtmlSanitizer.sanitize(html)).isNull();
    }
}
