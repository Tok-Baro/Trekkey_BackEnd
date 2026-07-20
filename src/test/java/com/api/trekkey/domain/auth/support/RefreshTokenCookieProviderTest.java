package com.api.trekkey.domain.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.api.trekkey.global.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCookieProviderTest {

    @Mock
    private HttpServletRequest request;

    private RefreshTokenCookieProvider cookieProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshCookieName("refreshToken");
        properties.setRefreshCookieSecure(false);
        properties.setRefreshCookieSameSite("Lax");
        properties.setRefreshExpiration(1_209_600L);
        cookieProvider = new RefreshTokenCookieProvider(properties);
    }

    @Test
    @DisplayName("로컬 refresh cookie는 HttpOnly, Secure=false, SameSite=Lax 설정을 사용한다")
    void createCookie_usesLocalSecurityProperties() {
        ResponseCookie cookie = cookieProvider.createCookie("refresh-token");

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("refresh-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1_209_600));
    }

    @Test
    @DisplayName("refresh cookie 삭제 응답은 생성 cookie와 같은 이름과 path, 0초 max-age를 사용한다")
    void deleteCookie_usesSameNameAndPathWithZeroMaxAge() {
        ResponseCookie created = cookieProvider.createCookie("refresh-token");
        ResponseCookie deleted = cookieProvider.deleteCookie();

        assertThat(deleted.getName()).isEqualTo(created.getName());
        assertThat(deleted.getPath()).isEqualTo(created.getPath());
        assertThat(deleted.getValue()).isEmpty();
        assertThat(deleted.getMaxAge()).isZero();
        assertThat(deleted.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("요청 cookie에서 refresh token 값을 추출한다")
    void extract_returnsRefreshTokenWhenPresent() {
        given(request.getCookies()).willReturn(new Cookie[]{
                new Cookie("other", "value"),
                new Cookie("refreshToken", "refresh-token")
        });

        assertThat(cookieProvider.extract(request)).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("요청에 cookie가 없으면 null을 반환한다")
    void extract_returnsNullWhenCookiesAreMissing() {
        given(request.getCookies()).willReturn(null);

        assertThat(cookieProvider.extract(request)).isNull();
    }

    @Test
    @DisplayName("refresh token과 무관한 cookie만 있으면 null을 반환한다")
    void extract_returnsNullWhenRefreshCookieIsMissing() {
        given(request.getCookies()).willReturn(new Cookie[]{new Cookie("other", "value")});

        assertThat(cookieProvider.extract(request)).isNull();
    }
}
