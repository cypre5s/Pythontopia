package com.pythontopia.coursepack.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 一条任务点视频清单记录，对应 {@code data/course-pack-43/video_manifest.json} 数组里的一项。
 *
 * <p>清单进 git（即使为空数组），实际 mp4 字节落在 {@code videoDir} 且不进 git。字段说明：
 *
 * <pre>{@code
 * {
 *   "id": 1,                       // 稳定主键，前端 /api/course/videos/{id} 用它取流
 *   "chapter": 3,                  // 章节序号 1..7
 *   "title": "第三章 变量与表达式",  // 任务点标题
 *   "objectid": "<chaoxing objectid>", // 超星 ananas objectid，仅爬虫溯源用，不下发前端
 *   "file": "ch3_001.mp4",         // 相对 videoDir 的文件名（防穿越）
 *   "durationSec": 123,            // 时长秒，未知为 null
 *   "kcCodes": ["PY43-KC-017"]     // 关联 KC，可空
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseVideoManifestEntry(
    Long id,
    Integer chapter,
    String title,
    String objectid,
    String file,
    Integer durationSec,
    List<String> kcCodes) {}
