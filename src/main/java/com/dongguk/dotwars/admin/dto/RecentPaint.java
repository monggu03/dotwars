package com.dongguk.dotwars.admin.dto;

import java.time.LocalDateTime;

/** 실시간 페인트 로그 스트림의 한 줄. */
public record RecentPaint(int x, int y, long factionId, LocalDateTime paintedAt) {}
