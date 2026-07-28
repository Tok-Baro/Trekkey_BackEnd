package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredentialSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncCredentialSourceRepository extends JpaRepository<AncCredentialSource, Long> {

    Optional<AncCredentialSource> findBySourceFingerprint(byte[] sourceFingerprint);

    // 대회 발급 현황 — 다형성 source(TEAM/SUBMISSION/AWARD)를 팀으로 역해석해 대회 소속 credential을 모은다
    @Query(value = """
            select c.public_id       as credentialPublicId,
                   c.credential_no   as credentialNo,
                   c.credential_type as credentialType,
                   c.status          as status,
                   s.source_type     as sourceType,
                   tm.public_id      as teamPublicId,
                   tm.name           as teamName,
                   c.issued_at       as issuedAt
            from anc_credential_source s
            join anc_credential c on c.id = s.credential_id
            left join submission sub on sub.id = s.submission_id
            left join award a on a.id = s.award_id
            join team tm on tm.id = coalesce(s.team_id, sub.team_id, a.team_id)
            where tm.contest_id = :contestId
            order by c.issued_at desc
            """, nativeQuery = true)
    List<CredentialSummaryRow> findSummaryRowsByContestId(@Param("contestId") Long contestId);

    // 팀 발급 현황 — 참여·작품·수상 credential을 한 번에
    @Query(value = """
            select c.public_id       as credentialPublicId,
                   c.credential_no   as credentialNo,
                   c.credential_type as credentialType,
                   c.status          as status,
                   s.source_type     as sourceType,
                   tm.public_id      as teamPublicId,
                   tm.name           as teamName,
                   c.issued_at       as issuedAt
            from anc_credential_source s
            join anc_credential c on c.id = s.credential_id
            left join submission sub on sub.id = s.submission_id
            left join award a on a.id = s.award_id
            join team tm on tm.id = coalesce(s.team_id, sub.team_id, a.team_id)
            where tm.id = :teamId
            order by c.issued_at desc
            """, nativeQuery = true)
    List<CredentialSummaryRow> findSummaryRowsByTeamId(@Param("teamId") Long teamId);
}
