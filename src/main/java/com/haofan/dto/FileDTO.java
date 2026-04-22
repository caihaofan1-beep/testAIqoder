package com.haofan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDTO {
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String base64Content;
    private boolean isImage;
}
