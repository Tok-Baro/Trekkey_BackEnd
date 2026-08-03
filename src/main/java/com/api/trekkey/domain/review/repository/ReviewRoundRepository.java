package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewRound;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRoundRepository extends JpaRepository<ReviewRound, Long> {

    List<ReviewRound> findAllByContestIdOrderByRoundNoAsc(Long contestId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<ReviewRound>
            findFirstByContestIdAndRoundNoLessThanOrderByRoundNoDesc(
                    Long contestId,
                    int roundNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reviewRound
            from ReviewRound reviewRound
            where reviewRound.contest.id = :contestId
            order by reviewRound.roundNo
            """)
    List<ReviewRound> findAllForUpdateByContestIdOrderByRoundNoAsc(
            @Param("contestId") Long contestId);

    @Query("""
            select reviewRound.contest.organization.id
            from ReviewRound reviewRound
            where reviewRound.id = :id
            """)
    Optional<Long> findOrganizationIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reviewRound
            from ReviewRound reviewRound
            where reviewRound.id = :id
            """)
    Optional<ReviewRound> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select reviewRound
            from ReviewRound reviewRound
            where reviewRound.id = :id
            """)
    Optional<ReviewRound> findByIdForShare(@Param("id") Long id);
}
