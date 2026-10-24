package com.pythontopia.admin;

import java.util.List;

public record AdminStudentImportResultDto(
    int createdCount, int skippedCount, List<AdminStudentCredentialDto> credentials) {}
