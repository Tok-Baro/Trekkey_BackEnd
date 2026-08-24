package com.api.trekkey.domain.graduation.service;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.stereotype.Component;

/** Parses the browser print layout of 한성대학교 종합정보시스템 성적조회(누적). */
@Component
public class HansungTranscriptPdfParser {
    private static final Pattern TERM = Pattern.compile("^(20\\d{2})\\s*학년도\\s*([12])\\s*학기$");
    private static final Pattern COURSE = Pattern.compile(
            "^(교필|선필교|일교|핵심|전기|전필|전지|전선|일선|부전필|부전선|복전필|복전선|연전필|연전선|교직|교직선)"
                    + "\\s+(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+([A-F](?:\\+|0|-)?|P|NP|S|U)(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY = Pattern.compile(
            "신청학점\\s+취득학점\\s+평점총계\\s+평균평점[^\\r\\n]*\\R\\s*"
                    + "([0-9]+(?:\\.[0-9]+)?)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+"
                    + "([0-9]+(?:\\.[0-9]+)?)\\s+([0-9]+(?:\\.[0-9]+)?)");

    public Optional<Transcript> parse(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper fullTextStripper = new PDFTextStripper();
            fullTextStripper.setSortByPosition(true);
            String fullText = fullTextStripper.getText(document);
            if (!isHansungCumulativeReport(fullText)) return Optional.empty();

            List<PageColumns> pages = new ArrayList<>();
            for (PDPage page : document.getPages()) pages.add(extractColumns(page));
            Transcript transcript = parseExtracted(fullText, pages);
            return transcript.courses().isEmpty() ? Optional.empty() : Optional.of(transcript);
        }
    }

    Transcript parseExtracted(String fullText, List<PageColumns> pages) {
        List<Course> courses = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        String[] activeTerms = new String[2];
        for (PageColumns page : pages) {
            parseColumn(page.left(), 0, activeTerms, courses, identities);
            parseColumn(page.right(), 1, activeTerms, courses, identities);
        }

        BigDecimal totalCredits = null;
        BigDecimal gpa = null;
        Matcher summary = SUMMARY.matcher(fullText);
        if (summary.find()) {
            totalCredits = new BigDecimal(summary.group(2));
            gpa = new BigDecimal(summary.group(4));
        }
        if (totalCredits == null && !courses.isEmpty()) {
            totalCredits = courses.stream()
                    .filter(course -> !course.grade().equalsIgnoreCase("F") && !course.grade().equalsIgnoreCase("NP"))
                    .map(Course::credits)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return new Transcript(totalCredits, gpa, List.copyOf(courses), List.of(
                "한성대학교 성적조회(누적) 인쇄 화면을 인식했습니다.",
                "가져온 과목과 누계학점은 종합정보시스템 원본과 대조해주세요."));
    }

    private PageColumns extractColumns(PDPage page) throws IOException {
        float width = page.getCropBox().getWidth();
        float height = page.getCropBox().getHeight();
        float middle = width / 2f;
        PDFTextStripperByArea extractor = new PDFTextStripperByArea();
        extractor.setSortByPosition(true);
        extractor.addRegion("left", new Rectangle2D.Float(0, 0, middle, height));
        extractor.addRegion("right", new Rectangle2D.Float(middle, 0, width - middle, height));
        extractor.extractRegions(page);
        return new PageColumns(extractor.getTextForRegion("left"), extractor.getTextForRegion("right"));
    }

    private void parseColumn(
            String text,
            int column,
            String[] activeTerms,
            List<Course> courses,
            Set<String> identities) {
        for (String raw : text.lines().toList()) {
            String line = raw.replaceAll("\\s+", " ").trim();
            Matcher term = TERM.matcher(line);
            if (term.matches()) {
                activeTerms[column] = term.group(1) + "-" + term.group(2);
                continue;
            }
            if (activeTerms[column] == null) continue;
            Matcher course = COURSE.matcher(line);
            if (!course.matches()) continue;
            String category = course.group(1).toUpperCase();
            String name = course.group(2).trim();
            BigDecimal credits = new BigDecimal(course.group(3));
            String grade = course.group(4).toUpperCase();
            String identity = activeTerms[column] + "|" + category + "|" + name + "|" + credits + "|" + grade;
            if (identities.add(identity)) {
                courses.add(new Course(activeTerms[column], category, name, credits, grade));
            }
        }
    }

    private boolean isHansungCumulativeReport(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        return compact.contains("한성대학교")
                && compact.contains("성적조회(누적)")
                && compact.contains("종합정보시스템");
    }

    record PageColumns(String left, String right) { }

    public record Transcript(
            BigDecimal totalCredits,
            BigDecimal gpa,
            List<Course> courses,
            List<String> warnings) { }

    public record Course(
            String term,
            String rawCategory,
            String courseName,
            BigDecimal credits,
            String grade) { }
}
