package com.miranda.luna.store.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFolderRequest(
        @NotBlank(message = "Folder path is required")
        String folderPath
) {
}
