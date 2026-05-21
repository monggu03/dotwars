package com.dongguk.dotwars.admin.dto;

/** 시간대별 활동량 — label(예: "05-26 16시") + 그 시간의 페인트 수. */
public record HourBucket(String label, long count) {}
