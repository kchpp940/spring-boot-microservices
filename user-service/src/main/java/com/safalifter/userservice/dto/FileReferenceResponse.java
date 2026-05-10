package com.safalifter.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileReferenceResponse {
    private String fileId;
    private String entityType;
    private String entityId;
    private Integer referenceCount;
    private boolean bound;
    private boolean unbound;
}
