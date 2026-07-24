package com.api.trekkey.domain.organization.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; //학교이름

    @Enumerated(EnumType.STRING)
    private OrganizationStatus status;
}
