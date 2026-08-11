package com.api.trekkey.domain.organization.repository;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /*
        findAllByStatusAndNameContainingIgnoreCase() : 키워드로 학교를 검색해서 활성상태라면 학교를 가져오는 쿼리메서드
        existsByName() : 학교를 이름으로 검색 시 학교가 존재하는지 확인하는 쿼리메서드
     */
    List<Organization> findAllByStatusAndNameContainingIgnoreCase(OrganizationStatus status, String keyword);

    boolean existsByName(String name);

    Optional<Organization> findByIdAndStatus(Long id, OrganizationStatus status);

    Optional<Organization> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") Long id);
}
