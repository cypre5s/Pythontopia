package com.pythontopia.learner.worldstate.dto;

/**
 * 对外快照：小镇当前心情天气，由学生情绪与坚持度画像派生。
 *
 * @param mood 心情天气：{@code sunny} / {@code cloudy} / {@code rainy}
 * @param streak 最近连续成败势头：正=连胜，负=连挫，0=无数据/中性
 * @param emotionScore emotion_persistence 维度的 effective 得分（0..1，已读时 decay）
 */
public record WorldStateDto(String mood, int streak, double emotionScore) {}
