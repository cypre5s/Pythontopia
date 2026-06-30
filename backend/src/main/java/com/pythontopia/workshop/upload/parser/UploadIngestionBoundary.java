package com.pythontopia.workshop.upload.parser;

import java.util.Map;
import java.util.Set;

public record UploadIngestionBoundary(
    String adapterName,
    Set<String> supportedExtensions,
    boolean externalOcrEnabled,
    boolean markerEnabled,
    Map<String, String> extractionModes) {}
