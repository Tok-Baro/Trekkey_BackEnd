package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredentialSubject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncCredentialSubjectRepository extends JpaRepository<AncCredentialSubject, Long> {

    List<AncCredentialSubject> findByCredentialIdOrderBySubjectOrderAsc(Long credentialId);

    List<AncCredentialSubject> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 개인 이력 한 방 조회 — payload 전체 대신 대회명만 SQL로 추출.
    // 순수 LEFT JOIN이라 credentialId null = 끊어진 참조(경고 대상)이고,
    // 조직 필터는 서비스에서 issuerOrganizationId로 구분한다 (타 조직 발급분을 오탐하지 않기 위함)
    @Query(value = """
            select c.id                      as credentialId,
                   s.credential_id           as subjectCredentialId,
                   c.issuer_organization_id  as issuerOrganizationId,
                   c.public_id               as credentialPublicId,
                   c.credential_no           as credentialNo,
                   c.credential_type         as credentialType,
                   c.status                  as status,
                   s.role_code               as roleCode,
                   s.display_name_snapshot   as displayName,
                   json_unquote(json_extract(c.payload_json, '$.source.snapshot.contestTitle')) as contestTitle,
                   c.issued_at               as issuedAt
            from anc_credential_subject s
            left join anc_credential c on c.id = s.credential_id
            where s.user_id = :userId
            order by c.issued_at desc
            """, nativeQuery = true)
    List<CredentialHistoryRow> findHistoryRowsByUserId(@Param("userId") Long userId);

    // Public profiles only expose explicitly public subjects backed by an on-chain state.
    @Query(value = """
            select c.id                      as credentialId,
                   s.credential_id           as subjectCredentialId,
                   c.issuer_organization_id  as issuerOrganizationId,
                   c.public_id               as credentialPublicId,
                   c.credential_no           as credentialNo,
                   c.credential_type         as credentialType,
                   c.status                  as status,
                   s.role_code               as roleCode,
                   s.display_name_snapshot   as displayName,
                   json_unquote(json_extract(c.payload_json, '$.source.snapshot.contestTitle')) as contestTitle,
                   c.issued_at               as issuedAt
            from anc_credential_subject s
            join anc_credential c on c.id = s.credential_id
            where s.user_id = :userId
              and s.disclosure_class = 'PUBLIC'
              and c.status in ('ANCHORED', 'REVOKED', 'SUPERSEDED')
            order by c.issued_at desc
            """, nativeQuery = true)
    List<CredentialHistoryRow> findPublicOnChainHistoryRowsByUserId(@Param("userId") Long userId);
}
