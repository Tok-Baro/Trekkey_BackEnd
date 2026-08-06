package com.api.trekkey.domain.contest.support;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * 대회 상세 HTML(detailHtml) 저장 전 XSS 방어용 sanitizer.
 * 프론트가 detailHtml을 dangerouslySetInnerHTML로 그대로 렌더링하므로
 * 허용된 서식 태그(h2,h3,p,ul,ol,li,strong,em,b,br) 외에는 모두 제거한다. (속성 불허)
 */
@Component
public class ContestHtmlSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("h2", "h3", "p", "ul", "ol", "li", "strong", "em", "b", "br")
            .toFactory();

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return POLICY.sanitize(html);
    }
}
