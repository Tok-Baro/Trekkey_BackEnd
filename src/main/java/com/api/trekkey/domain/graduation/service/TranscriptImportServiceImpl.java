package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;

import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.StudentCourseRecord;
import com.api.trekkey.domain.graduation.exception.GraduationErrorResponseCode;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.StudentCourseRecordRepository;
import com.api.trekkey.domain.graduation.web.dto.TranscriptImportRes;
import com.api.trekkey.global.exception.CustomException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class TranscriptImportServiceImpl implements TranscriptImportService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Pattern PDF_COURSE = Pattern.compile(
            "(?i)^(20\\d{2})\\s*[-년./]?\\s*([12])(?:\\s*학기)?\\s+([A-Z0-9_-]{3,20})\\s+(.+?)\\s+([0-9]+(?:\\.[05])?)\\s+([A-F](?:\\+|0|-)?|P|NP|S|U)$");
    private static final Pattern PDF_COURSE_NO_CODE = Pattern.compile(
            "(?i)^(20\\d{2})\\s*[-년./]?\\s*([12])(?:\\s*학기)?\\s+(.+?)\\s+([0-9]+(?:\\.[05])?)\\s+([A-F](?:\\+|0|-)?|P|NP|S|U)$");
    private static final Pattern TOTAL = Pattern.compile("(?:총\\s*(?:취득|이수)?\\s*학점|취득학점)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9])?)");
    private static final Pattern GPA = Pattern.compile("(?:평점평균|누적평점|총평점평균)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)");

    private final StudentAcademicProfileRepository profileRepository;
    private final StudentCourseRecordRepository courseRepository;
    private final HansungTranscriptPdfParser hansungTranscriptPdfParser;

    @Override
    public TranscriptImportRes importTranscript(Long userId, MultipartFile file, boolean apply) {
        StudentAcademicProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(GraduationErrorResponseCode.GRADUATION_PROFILE_NOT_CONFIGURED));
        byte[] bytes = read(file);
        Parsed parsed = isPdf(bytes, file.getOriginalFilename()) ? parsePdf(bytes) : parseCsv(bytes);
        if (parsed.courses.isEmpty()) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
        }
        if (apply) {
            courseRepository.deleteAllByProfileId(profile.getId());
            courseRepository.flush();
            for (ParsedCourse course : parsed.courses) {
                courseRepository.save(StudentCourseRecord.builder()
                        .profile(profile)
                        .term(course.term)
                        .courseCode(course.courseCode)
                        .courseName(course.courseName)
                        .credits(course.credits)
                        .grade(course.grade)
                        .completionStatus(course.completionStatus)
                        .category(course.category)
                        .mappingStatus(CourseMappingStatus.SELF_REPORTED)
                        .retakeGroupKey(course.courseCode)
                        .sourceType(parsed.sourceType)
                        .build());
            }
            BigDecimal total = parsed.totalCredits == null
                    ? parsed.courses.stream().filter(course -> course.completionStatus == CourseCompletionStatus.COMPLETED)
                        .map(course -> course.credits).reduce(BigDecimal.ZERO, BigDecimal::add)
                    : parsed.totalCredits;
            boolean hasFail = parsed.courses.stream().anyMatch(course -> course.completionStatus == CourseCompletionStatus.FAILED);
            profile.applyImportedTranscript(total, parsed.gpa, parsed.latestTerm(), hasFail);
            profileRepository.saveAndFlush(profile);
        }
        return new TranscriptImportRes(
                apply,
                parsed.sourceType.name(),
                parsed.courses.size(),
                parsed.totalCredits,
                parsed.gpa,
                parsed.latestTerm(),
                List.copyOf(parsed.warnings),
                parsed.courses.stream().map(course -> new TranscriptImportRes.Course(
                        course.term, course.courseCode, course.courseName, course.credits,
                        course.grade, course.category.name(), course.completionStatus.name())).toList());
    }

    private Parsed parseCsv(byte[] bytes) {
        String text = decodeText(bytes).replace("\\r\\n", "\\n").replace('\r', '\n');
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() < 2) throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
        List<String> headers = csvRow(lines.getFirst()).stream().map(this::normalizeHeader).toList();
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) columns.put(headers.get(i), i);
        requireColumn(columns, "term");
        requireColumn(columns, "courseName");
        requireColumn(columns, "credits");
        Parsed parsed = new Parsed(RecordSourceType.CSV);
        Set<String> identities = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = csvRow(lines.get(i));
            String term = normalizeTerm(value(cells, columns, "term"));
            String name = value(cells, columns, "courseName").trim();
            BigDecimal credits = decimal(value(cells, columns, "credits"));
            String grade = nullable(value(cells, columns, "grade"));
            String code = nullable(value(cells, columns, "courseCode"));
            if (term == null || name.isBlank() || credits == null) {
                parsed.warnings.add((i + 1) + "행을 읽지 못해 제외했습니다.");
                continue;
            }
            if (code == null) code = "IMP-" + shortHash(term + "|" + name + "|" + i);
            if (!identities.add(term + "|" + code)) {
                parsed.warnings.add((i + 1) + "행의 중복 과목을 제외했습니다.");
                continue;
            }
            CourseCategory category = category(value(cells, columns, "category"));
            CourseCompletionStatus status = completion(value(cells, columns, "completionStatus"), grade);
            parsed.courses.add(new ParsedCourse(term, code, name, credits, grade, category, status));
        }
        parsed.totalCredits = sumCompleted(parsed.courses);
        return parsed;
    }

    private Parsed parsePdf(byte[] bytes) {
        try {
            var hansung = hansungTranscriptPdfParser.parse(bytes);
            if (hansung.isPresent()) {
                Parsed parsed = new Parsed(RecordSourceType.PDF);
                parsed.totalCredits = hansung.get().totalCredits();
                parsed.gpa = hansung.get().gpa();
                parsed.warnings.addAll(hansung.get().warnings());
                int sequence = 0;
                for (var course : hansung.get().courses()) {
                    String code = "HS-" + shortHash(course.term() + "|" + course.rawCategory()
                            + "|" + course.courseName() + "|" + sequence++);
                    parsed.courses.add(new ParsedCourse(
                            course.term(), code, course.courseName(), course.credits(), course.grade(),
                            category(course.rawCategory()), completion("", course.grade())));
                }
                return parsed;
            }
        } catch (IOException ignored) {
            // Continue with the generic PDF parser below.
        }
        Parsed parsed = new Parsed(RecordSourceType.PDF);
        StringBuilder text = new StringBuilder();
        try (PdfReader reader = new PdfReader(bytes)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
        } catch (IOException | RuntimeException exception) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
        }
        String contents = text.toString();
        parsed.totalCredits = matchDecimal(TOTAL, contents);
        parsed.gpa = matchDecimal(GPA, contents);
        int sequence = 0;
        for (String raw : contents.lines().toList()) {
            String line = raw.replaceAll("\\s+", " ").trim();
            Matcher matcher = PDF_COURSE.matcher(line);
            if (matcher.matches()) {
                parsed.courses.add(pdfCourse(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4),
                        matcher.group(5), matcher.group(6)));
                continue;
            }
            matcher = PDF_COURSE_NO_CODE.matcher(line);
            if (matcher.matches()) {
                String term = matcher.group(1) + "-" + matcher.group(2);
                String name = matcher.group(3).trim();
                parsed.courses.add(pdfCourse(matcher.group(1), matcher.group(2),
                        "PDF-" + shortHash(term + "|" + name + "|" + sequence++), name,
                        matcher.group(4), matcher.group(5)));
            }
        }
        if (parsed.totalCredits == null && !parsed.courses.isEmpty()) parsed.totalCredits = sumCompleted(parsed.courses);
        if (parsed.gpa == null) parsed.warnings.add("누적 평점을 자동으로 찾지 못했습니다. 미리보기에서 직접 확인해주세요.");
        parsed.warnings.add("PDF 가져오기 결과는 원본 표와 대조한 뒤 저장해주세요.");
        return parsed;
    }

    private ParsedCourse pdfCourse(String year, String semester, String code, String name, String credits, String grade) {
        return new ParsedCourse(year + "-" + semester, code.toUpperCase(Locale.ROOT), name.trim(),
                new BigDecimal(credits), grade.toUpperCase(Locale.ROOT), CourseCategory.FREE_ELECTIVE,
                completion("", grade));
    }

    private CourseCategory category(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "교필", "교양필수", "GENERAL_REQUIRED" -> CourseCategory.GENERAL_REQUIRED;
            case "핵심", "핵교", "선필교", "배분", "교양배분", "GENERAL_DISTRIBUTION" -> CourseCategory.GENERAL_DISTRIBUTION;
            case "일교", "교선", "교양선택", "GENERAL_ELECTIVE" -> CourseCategory.GENERAL_ELECTIVE;
            case "전기", "전공기초", "MAJOR_FOUNDATION" -> CourseCategory.MAJOR_FOUNDATION;
            case "전필", "전지", "전공지정", "전공필수", "부전필", "복전필", "연전필", "MAJOR_REQUIRED" -> CourseCategory.MAJOR_REQUIRED;
            case "전선", "부전선", "복전선", "연전선", "전공선택", "MAJOR_ELECTIVE" -> CourseCategory.MAJOR_ELECTIVE;
            case "대학원", "GRADUATE_COURSE" -> CourseCategory.GRADUATE_COURSE;
            default -> CourseCategory.FREE_ELECTIVE;
        };
    }

    private CourseCompletionStatus completion(String value, String grade) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.isBlank()) {
            try { return CourseCompletionStatus.valueOf(normalized); }
            catch (IllegalArgumentException ignored) { }
        }
        if (grade == null || grade.isBlank()) return CourseCompletionStatus.IN_PROGRESS;
        return grade.trim().equalsIgnoreCase("F") || grade.trim().equalsIgnoreCase("NP")
                ? CourseCompletionStatus.FAILED : CourseCompletionStatus.COMPLETED;
    }

    private String normalizeHeader(String value) {
        String key = value.replace("\\ufeff", "").replaceAll("[ _-]", "").toLowerCase(Locale.ROOT);
        return switch (key) {
            case "학기", "이수학기", "term" -> "term";
            case "과목코드", "학수번호", "coursecode" -> "courseCode";
            case "과목명", "교과목명", "coursename" -> "courseName";
            case "학점", "credits" -> "credits";
            case "성적", "등급", "grade" -> "grade";
            case "이수구분", "구분", "category" -> "category";
            case "이수상태", "completionstatus" -> "completionStatus";
            default -> key;
        };
    }

    private List<String> csvRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString().trim()); cell.setLength(0);
            } else cell.append(ch);
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    private void requireColumn(Map<String, Integer> columns, String name) {
        if (!columns.containsKey(name)) throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
    }
    private String value(List<String> cells, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        return index == null || index >= cells.size() ? "" : cells.get(index);
    }
    private BigDecimal decimal(String value) {
        try { return value == null || value.isBlank() ? null : new BigDecimal(value.trim()); }
        catch (NumberFormatException exception) { return null; }
    }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeTerm(String value) {
        if (value == null) return null;
        Matcher matcher = Pattern.compile("(20\\d{2})\\D*([12])").matcher(value.trim());
        return matcher.find() ? matcher.group(1) + "-" + matcher.group(2) : null;
    }
    private BigDecimal matchDecimal(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value.replaceAll("\\s+", " "));
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }
    private BigDecimal sumCompleted(List<ParsedCourse> courses) {
        return courses.stream().filter(course -> course.completionStatus == CourseCompletionStatus.COMPLETED)
                .map(course -> course.credits).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE)
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
        try { return file.getBytes(); }
        catch (IOException exception) { throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID); }
    }
    private boolean isPdf(byte[] bytes, String name) {
        return bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                || name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
    private String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return Charset.forName("MS949").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
    private String shortHash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8))).substring(0, 12).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static final class Parsed {
        private final RecordSourceType sourceType;
        private final List<ParsedCourse> courses = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private BigDecimal totalCredits;
        private BigDecimal gpa;
        private Parsed(RecordSourceType sourceType) { this.sourceType = sourceType; }
        private String latestTerm() { return courses.stream().map(course -> course.term).max(String::compareTo).orElse(null); }
    }
    private record ParsedCourse(String term, String courseCode, String courseName, BigDecimal credits,
                                String grade, CourseCategory category, CourseCompletionStatus completionStatus) { }
}
