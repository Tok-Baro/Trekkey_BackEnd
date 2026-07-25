package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.publicapi.web.dto.JudgePortalRes;
import com.api.trekkey.domain.review.publicapi.web.dto.ReviewSubmitReq;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.review.support.ReviewTokenSupport;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JudgeReviewServiceImpl implements JudgeReviewService {

    private final ContestJudgeRepository contestJudgeRepository;
    private final ReviewAssignmentRepository assignmentRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewScoreItemRepository scoreItemRepository;
    private final ReviewCriterionRepository criterionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final FileStoragePort fileStoragePort;
    private final ReviewTokenSupport reviewTokenSupport;

    @Override
    public JudgePortalRes getPortal(String rawToken) {
        ContestJudge judge = findJudgeByToken(rawToken);

        List<ReviewAssignment> assignments =
                assignmentRepository.findAllByContestJudgeIdOrderByAssignedAtAsc(judge.getId());

        //이미 제출한 배정 표시
        Set<Long> reviewedAssignmentIds = assignments.isEmpty() ? Set.of()
                : reviewRepository.findAllByAssignmentIdIn(
                                assignments.stream().map(ReviewAssignment::getId).toList()).stream()
                        .map(review -> review.getAssignment().getId())
                        .collect(Collectors.toSet());

        //라운드별 평가 기준 일괄 조회
        List<Long> stageIds = assignments.stream()
                .map(assignment -> assignment.getContestStageEntry().getContestStage().getId())
                .distinct()
                .toList();
        Map<Long, List<JudgePortalRes.CriterionSummary>> criteriaByStageId = stageIds.isEmpty()
                ? Map.of()
                : criterionRepository.findAllByContestStageIdInOrderBySortOrderAsc(stageIds).stream()
                        .filter(ReviewCriterion::isActive)
                        .collect(Collectors.groupingBy(
                                criterion -> criterion.getContestStage().getId(),
                                Collectors.mapping(criterion -> new JudgePortalRes.CriterionSummary(
                                                criterion.getId(),
                                                criterion.getLabel(),
                                                BigDecimal.valueOf(criterion.getMaxScore())),
                                        Collectors.toList())));

        //제출물 파일 일괄 조회
        List<Long> submissionIds = assignments.stream()
                .map(assignment -> assignment.getContestStageEntry().getSubmission().getId())
                .distinct()
                .toList();
        Map<Long, List<JudgePortalRes.FileSummary>> filesBySubmissionId = submissionIds.isEmpty()
                ? Map.of()
                : submissionFileRepository.findAllBySubmissionIdIn(submissionIds).stream()
                        .collect(Collectors.groupingBy(
                                file -> file.getSubmission().getId(),
                                Collectors.mapping(file -> new JudgePortalRes.FileSummary(
                                                file.getId(), file.getOriginalName(), file.getSizeBytes()),
                                        Collectors.toList())));

        List<JudgePortalRes.AssignmentRes> assignmentResList = assignments.stream()
                .map(assignment -> new JudgePortalRes.AssignmentRes(
                        assignment.getId(),
                        assignment.getContestStageEntry().getContestStage().getName(),
                        reviewedAssignmentIds.contains(assignment.getId()),
                        new JudgePortalRes.SubmissionSummary(
                                assignment.getContestStageEntry().getSubmission().getTitle(),
                                assignment.getContestStageEntry().getSubmission().getTeam().getName(),
                                filesBySubmissionId.getOrDefault(
                                        assignment.getContestStageEntry().getSubmission().getId(), List.of())),
                        criteriaByStageId.getOrDefault(
                                assignment.getContestStageEntry().getContestStage().getId(), List.of())))
                .toList();

        return new JudgePortalRes(
                judge.getName(),
                judge.getRoleLabel(),
                judge.getContest().getTitle(),
                assignmentResList);
    }

    @Override
    public FileDownload downloadFile(String rawToken, Long fileId) {
        ContestJudge judge = findJudgeByToken(rawToken);

        SubmissionFile file = submissionFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ReviewErrorResponseCode.ASSIGNMENT_NOT_FOUND));

        //배정된 제출물의 파일만 열람할 수 있다
        boolean assigned = assignmentRepository
                .findAllByContestJudgeIdOrderByAssignedAtAsc(judge.getId()).stream()
                .anyMatch(assignment -> assignment.getContestStageEntry().getSubmission().getId()
                        .equals(file.getSubmission().getId()));
        if (!assigned) {
            throw new CustomException(ReviewErrorResponseCode.ASSIGNMENT_NOT_FOUND);
        }

        return new FileDownload(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    @Override
    @Transactional
    public void submitReview(String rawToken, Long assignmentId, ReviewSubmitReq request) {
        ContestJudge judge = findJudgeByToken(rawToken);

        ReviewAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new CustomException(ReviewErrorResponseCode.ASSIGNMENT_NOT_FOUND));
        if (!assignment.getContestJudge().getId().equals(judge.getId())) {
            throw new CustomException(ReviewErrorResponseCode.ASSIGNMENT_NOT_FOUND);
        }
        //확정(마감)된 라운드에는 제출할 수 없다
        if (assignment.getContestStageEntry().getContestStage().getStatus() != StageStatus.OPEN) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_NOT_OPEN);
        }
        //배정당 한 건 — 제출 후 수정 불가 (erd-mvp §5)
        if (reviewRepository.existsByAssignmentId(assignment.getId())) {
            throw new CustomException(ReviewErrorResponseCode.REVIEW_ALREADY_SUBMITTED);
        }

        //라운드의 활성 평가 기준 전체와 요청 점수가 정확히 일치해야 한다
        Long stageId = assignment.getContestStageEntry().getContestStage().getId();
        Map<Long, ReviewCriterion> criteriaById = criterionRepository
                .findAllByContestStageIdInOrderBySortOrderAsc(List.of(stageId)).stream()
                .filter(ReviewCriterion::isActive)
                .collect(Collectors.toMap(ReviewCriterion::getId, criterion -> criterion));

        Map<Long, BigDecimal> scoresByCriterionId = new HashMap<>();
        for (ReviewSubmitReq.ScoreReq scoreReq : request.scores()) {
            scoresByCriterionId.put(scoreReq.criterionId(), scoreReq.score());
        }
        if (!scoresByCriterionId.keySet().equals(criteriaById.keySet())) {
            throw new CustomException(ReviewErrorResponseCode.SCORE_CRITERION_MISMATCH);
        }
        //0 <= score <= criterion.maxScore (erd-mvp 제약)
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> scoreEntry : scoresByCriterionId.entrySet()) {
            BigDecimal max = BigDecimal.valueOf(criteriaById.get(scoreEntry.getKey()).getMaxScore());
            if (scoreEntry.getValue().compareTo(BigDecimal.ZERO) < 0
                    || scoreEntry.getValue().compareTo(max) > 0) {
                throw new CustomException(ReviewErrorResponseCode.SCORE_OUT_OF_RANGE);
            }
            totalScore = totalScore.add(scoreEntry.getValue());
        }

        LocalDateTime now = LocalDateTime.now();
        Review review = reviewRepository.save(Review.builder()
                .assignment(assignment)
                .totalScore(totalScore)
                .comment(request.comment() == null ? null : request.comment().trim())
                .submittedAt(now)
                .build());

        List<ReviewScoreItem> items = new ArrayList<>();
        scoresByCriterionId.forEach((criterionId, score) -> items.add(ReviewScoreItem.builder()
                .review(review)
                .criterion(criteriaById.get(criterionId))
                .score(score)
                .build()));
        scoreItemRepository.saveAll(items);

        assignment.complete(now);
    }

    //======= 헬퍼 메서드 ==========

    private ContestJudge findJudgeByToken(String rawToken) {
        ContestJudge judge = contestJudgeRepository.findByReviewTokenHash(reviewTokenSupport.hash(rawToken))
                .orElseThrow(() -> new CustomException(ReviewErrorResponseCode.REVIEW_TOKEN_INVALID));
        if (judge.isTokenExpired(LocalDateTime.now())) {
            throw new CustomException(ReviewErrorResponseCode.REVIEW_TOKEN_EXPIRED);
        }
        return judge;
    }
}
