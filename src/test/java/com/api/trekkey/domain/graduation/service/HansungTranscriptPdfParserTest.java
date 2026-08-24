package com.api.trekkey.domain.graduation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HansungTranscriptPdfParserTest {
    private final HansungTranscriptPdfParser parser = new HansungTranscriptPdfParser();

    @Test
    void parsesBothColumnsAndCarriesTheLastTermsOntoTheNextPrintedPage() {
        String summary = """
                신청학점 취득학점 평점총계 평균평점 백분위
                132 132 507.5 3.9 93.14
                """;
        List<HansungTranscriptPdfParser.PageColumns> pages = List.of(
                new HansungTranscriptPdfParser.PageColumns("""
                        2024 학년도 2 학기
                        구분 교과명 학점 성적 현재트랙(변경시)
                        일교 음식과 세계문화 3 A+
                        2021 학년도 2 학기
                        교필 사고와 표현(발표와 토론) 2 A+
                        """, """
                        2024 학년도 1 학기
                        구분 교과명 학점 성적 현재트랙(변경시)
                        전필 캡스톤디자인 3 A+ 현재 : 제1트랙
                        2021 학년도 1 학기
                        교필 디자인 Thinking 2 B0
                        """),
                new HansungTranscriptPdfParser.PageColumns("""
                        구분 교과명 학점 성적 현재트랙(변경시)
                        전선 프로그래밍랩 3 B+ 현재 : 제1트랙
                        """, """
                        구분 교과명 학점 성적 현재트랙(변경시)
                        전기 컴퓨터프로그래밍 3 C0 현재 : 제1트랙
                        """));

        var result = parser.parseExtracted(summary, pages);

        assertThat(result.totalCredits()).isEqualByComparingTo("132");
        assertThat(result.gpa()).isEqualByComparingTo("3.9");
        assertThat(result.courses()).hasSize(6);
        assertThat(result.courses()).extracting(HansungTranscriptPdfParser.Course::term)
                .containsExactly("2024-2", "2021-2", "2024-1", "2021-1", "2021-2", "2021-1");
        assertThat(result.courses()).extracting(HansungTranscriptPdfParser.Course::grade)
                .contains("A+", "B0", "C0");
    }
}
