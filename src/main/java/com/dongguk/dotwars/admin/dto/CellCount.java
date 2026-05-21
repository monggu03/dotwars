package com.dongguk.dotwars.admin.dto;

/** 한 칸(x,y)이 지금까지 칠해진 누적 횟수. 격전지 랭킹 + 히트맵에 사용. */
public record CellCount(int x, int y, long count) {}
