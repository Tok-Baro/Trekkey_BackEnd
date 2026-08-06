package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRecordRes;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRecordAdminServiceImpl
        implements ReviewRecordAdminService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewScoreItemRepository reviewScoreItemRepository;

    @Override
    public List<ReviewRecordRes> getReviews(
            Long adminUserId,
            String contestPublicId,
            Long roundId) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ReviewRound round = reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
        if (!round.getContest().getId().equals(contest.getId())) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);
        }

        List<Review> reviews =
                reviewRepository.findAllWithDetailsByReviewRoundId(roundId);
        if (reviews.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ReviewScoreItem>> scoreItemsByReviewId =
                reviewScoreItemRepository
                        .findAllWithCriterionByReviewIdIn(
                                reviews.stream().map(Review::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                item -> item.getReview().getId()));

        return reviews.stream()
                .map(review -> ReviewRecordRes.from(
                        review,
                        scoreItemsByReviewId.getOrDefault(
                                review.getId(),
                                List.of())))
                .toList();
    }

    private User findActiveAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(
                    UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }

    private Contest findContest(String publicId, User admin) {
        Contest contest = contestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.CONTEST_NOT_FOUND));
        if (!contest.getOrganization().getId()
                .equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
        return contest;
    }
}
