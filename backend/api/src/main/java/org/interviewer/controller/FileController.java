package org.interviewer.controller;

import org.interviewer.base.MinIOConfig;
import org.interviewer.base.MinIOUtils;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * FileController
 **/
@RestController
@RequestMapping("file")
public class FileController {

    @Resource
    private MinIOConfig minIOConfig;

    @PostMapping("uploadInterviewerImage")
    public GraceJSONResult uploadInterviewerImage(@RequestParam("file") MultipartFile file) throws Exception {

        // Get original file name
        String filename = file.getOriginalFilename();
        if (StringUtils.isBlank(filename)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_NULL_ERROR);
        }

        // Store object directly under bucket root (no extra folder prefix)
        filename = dealWithoutFilename(filename);
        String imageUrl = MinIOUtils.uploadFile(minIOConfig.getBucketName(),
                filename,
                file.getInputStream(),
                true);

        // Bucket is public-read now, return permanent direct URL
        return GraceJSONResult.ok(imageUrl);
    }

    @PostMapping("uploadInterviewVideo")
    public GraceJSONResult uploadInterviewVideo(@RequestParam("file") MultipartFile file) throws Exception {

        // Get original file name
        String filename = file.getOriginalFilename();
        if (StringUtils.isBlank(filename)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_NULL_ERROR);
        }

        // Keep videos under bucket root as well (no subfolder)
        filename = dealWithoutFilename(filename);
        String videoUrl = MinIOUtils.uploadFile(minIOConfig.getBucketName(),
                filename,
                file.getInputStream(),
                true);

        return GraceJSONResult.ok(videoUrl);
    }

    private String dealWithoutFilename(String filename) {
        String suffixName = filename.substring(filename.lastIndexOf("."));
        String uuid = UUID.randomUUID().toString();
        return uuid + suffixName;
    }

}