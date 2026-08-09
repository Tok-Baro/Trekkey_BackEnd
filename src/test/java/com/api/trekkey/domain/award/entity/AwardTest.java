package com.api.trekkey.domain.award.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AwardTest {

    @Test
    @DisplayName("awardType 컬럼이 없는 기존 수상은 상격 문자열로 유형을 복원한다")
    void getAwardType_infersLegacyAwardTypeFromPrize() {
        Award legacyAward = Award.builder()
                .prize("총장상")
                .build();

        assertThat(legacyAward.getAwardType())
                .isEqualTo(AwardType.PRESIDENT_AWARD);
    }
}
