package com.safalifter.filestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileReferenceRequest {
    private String fileId;
    private String entityType;
    private String entityId;
}
