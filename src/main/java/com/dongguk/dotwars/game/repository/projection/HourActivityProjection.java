package com.dongguk.dotwars.game.repository.projection;

/** 시간대별 활동량 — bucket(예: "05-26 16시"), cnt. */
public interface HourActivityProjection {
    String getBucket();
    long getCnt();
}
