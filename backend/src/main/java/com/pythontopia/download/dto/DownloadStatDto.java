package com.pythontopia.download.dto;

/** 客户端安装包下载统计（key=android_apk/windows_exe…，count=累计下载/点击次数）。 */
public record DownloadStatDto(String key, long count) {}
