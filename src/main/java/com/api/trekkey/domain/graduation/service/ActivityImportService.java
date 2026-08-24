package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.web.dto.ActivityImportRes;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.exception.GraduationErrorResponseCode;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.global.exception.CustomException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ActivityImportService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Pattern TOTAL = Pattern.compile("(?i)(?:총|누적|합계)[^0-9]{0,30}([0-9]{1,5})\\s*(?:point|p|점)");
    private static final Pattern ROW = Pattern.compile("^(.+?)[,\\t]\\s*([0-9]{1,5})(?:\\s*(?:point|p|점))?(?:[,\\t]\\s*(.*))?$", Pattern.CASE_INSENSITIVE);
    private final StudentAcademicProfileRepository profileRepository;

    @Transactional
    public ActivityImportRes importActivities(Long userId, MultipartFile file, boolean apply) {
        StudentAcademicProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(GraduationErrorResponseCode.GRADUATION_PROFILE_NOT_CONFIGURED));
        byte[] bytes = read(file);
        boolean pdf = isPdf(bytes, file.getOriginalFilename());
        String text = pdf ? pdfText(bytes) : new String(bytes, StandardCharsets.UTF_8);
        List<ActivityImportRes.Activity> activities = new ArrayList<>();
        for (String line : text.lines().map(value -> value.replaceAll("\\s+", " ").trim()).toList()) {
            Matcher row = ROW.matcher(line);
            if (row.matches() && !row.group(1).matches("(?i).*(총|누적|합계).*$")) {
                activities.add(new ActivityImportRes.Activity(row.group(1).trim(), Integer.parseInt(row.group(2)),
                        row.group(3) == null ? null : row.group(3).trim()));
            }
        }
        int points = activities.stream().mapToInt(ActivityImportRes.Activity::points).sum();
        Matcher total = TOTAL.matcher(text.replaceAll("\\s+", " "));
        if (total.find()) points = Integer.parseInt(total.group(1));
        if (points == 0) throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
        List<String> warnings = new ArrayList<>();
        if (activities.isEmpty()) warnings.add("개별 활동은 찾지 못했지만 누적 포인트는 확인했습니다.");
        warnings.add("스마트자기관리시스템 원본과 포인트를 대조해주세요.");
        if (apply) {
            profile.applyImportedActivityPoints(points);
            profileRepository.saveAndFlush(profile);
        }
        return new ActivityImportRes(apply, pdf ? "PDF" : "CSV", points, activities.size(), warnings, activities);
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
    private String pdfText(byte[] bytes) {
        try (PdfReader reader = new PdfReader(bytes)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder result = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) result.append(extractor.getTextFromPage(page)).append('\n');
            return result.toString();
        } catch (IOException | RuntimeException exception) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_IMPORT_INVALID);
        }
    }
}
