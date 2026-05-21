package com.dongguk.dotwars.game.repository.projection;

/** 네이티브 쿼리 결과 매핑 — 컬럼 alias(x, y, cnt)가 getter 이름과 일치해야 함. */
public interface CellCountProjection {
    int getX();
    int getY();
    long getCnt();
}
