package com.api.trekkey.domain.user.entity;

public enum UserRole {
    /*
        ROOT_ADMIN : 학교 대표 관리자 — 관리자 초대·승인 전담, 운영팀이 생성
        ADMIN : 교직원(관리자)
        PARTICIPANT : 학생(참여자)
     */
    ROOT_ADMIN,
    ADMIN,
    PARTICIPANT
}
