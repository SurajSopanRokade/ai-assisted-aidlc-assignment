package com.copilot.urlshortener.dto.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineeringArtifacts {
    private List<String> folder_structure;
    private String database_schema;
    private List<String> api_contracts;
    private List<ArtifactFile> key_files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtifactFile {
        private String path;
        private String description;
    }
}
