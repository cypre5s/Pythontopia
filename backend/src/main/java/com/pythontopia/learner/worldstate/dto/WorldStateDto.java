package com.pythontopia.learner.worldstate.dto;

/**
 * 对外快照：小镇氛围保持固定；最近连击是客观练习记录，不代表学生情绪。
 *
 * @param mood 心情天气：{@code sunny} / {@code cloudy} / {@code rainy}
 * @param streak 最近连续成败势头：正=连胜，负=连挫，0=无数据/中性
 * @param emotionScore 兼容旧客户端的中性占位值（固定 0.5，不是画像信号）
 */
public record WorldStateDto(String mood, int streak, double emotionScore) {}
