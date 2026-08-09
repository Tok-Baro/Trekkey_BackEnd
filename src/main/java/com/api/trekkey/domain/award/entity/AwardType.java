package com.api.trekkey.domain.award.entity;

import java.util.Arrays;

public enum AwardType {
    GRAND_PRIZE("대상"),
    EXCELLENCE("최우수상"),
    MERIT("우수상"),
    ENCOURAGEMENT("장려상"),
    HONORABLE_MENTION("입선"),
    SPECIAL("특별상"),
    PRESIDENT_AWARD("총장상"),
    CUSTOM(null);

    private final String defaultPrize;

    AwardType(String defaultPrize) {
        this.defaultPrize = defaultPrize;
    }

    public String resolvePrize(String customPrize) {
        return this == CUSTOM ? customPrize.trim() : defaultPrize;
    }

    public static AwardType forRank(int rankNo) {
        return switch (rankNo) {
            case 1 -> GRAND_PRIZE;
            case 2 -> EXCELLENCE;
            case 3 -> MERIT;
            case 4 -> ENCOURAGEMENT;
            case 5 -> HONORABLE_MENTION;
            default -> CUSTOM;
        };
    }

    public static AwardType fromPrize(String prize) {
        return Arrays.stream(values())
                .filter(type -> type.defaultPrize != null)
                .filter(type -> type.defaultPrize.equals(prize))
                .findFirst()
                .orElse(CUSTOM);
    }
}
