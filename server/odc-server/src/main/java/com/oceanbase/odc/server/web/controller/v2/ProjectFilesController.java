/*
 * Copyright (c) 2024 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oceanbase.odc.server.web.controller.v2;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oceanbase.odc.service.common.response.ListResponse;
import com.oceanbase.odc.service.common.response.Responses;
import com.oceanbase.odc.service.common.response.SuccessResponse;
import com.oceanbase.odc.service.projectfiles.ProjectFilesServiceFacade;
import com.oceanbase.odc.service.projectfiles.model.BatchUploadProjectFileReq;
import com.oceanbase.odc.service.projectfiles.model.FileUploadTempCredentialResp;
import com.oceanbase.odc.service.projectfiles.model.GenerateProjectFileTempCredentialReq;
import com.oceanbase.odc.service.projectfiles.model.ProjectFileMetaResp;
import com.oceanbase.odc.service.projectfiles.model.ProjectFileResp;
import com.oceanbase.odc.service.projectfiles.model.UpdateProjectFileReq;

/**
 * 项目文件管理控制类
 *
 * @author keyangs
 * @date 2024/7/31
 * @since 4.3.2
 */
@RestController
@RequestMapping("/api/v2/project/{projectId}/files")
public class ProjectFilesController {

    @Resource
    private ProjectFilesServiceFacade projectFilesServiceFacade;

    @PostMapping("/generateTempCredential")
    public SuccessResponse<FileUploadTempCredentialResp> generateTempCredential(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody GenerateProjectFileTempCredentialReq req) {
        return Responses.success(projectFilesServiceFacade.generateTempCredential(projectId, req));
    }

    @PostMapping("/{path}")
    public SuccessResponse<ProjectFileMetaResp> createFile(
            @PathVariable("projectId") Long projectId,
            @PathVariable("path") String path,
            @RequestParam("objectKey") String objectKey) {
        return Responses.success(projectFilesServiceFacade.createFile(projectId, path, objectKey));
    }

    @GetMapping("/{path}")
    public SuccessResponse<ProjectFileResp> getFileDetails(
            @PathVariable("path") String path,
            @PathVariable("projectId") Long projectId) {
        return Responses.success(projectFilesServiceFacade.getFileDetails(projectId, path));
    }

    @GetMapping("/list")
    public ListResponse<ProjectFileMetaResp> listFiles(
            @PathVariable("projectId") Long projectId,
            @RequestParam("path") String path) {
        return Responses.list(projectFilesServiceFacade.listFiles(projectId, path));
    }

    @GetMapping("/search")
    public ListResponse<ProjectFileMetaResp> searchFiles(
            @PathVariable("projectId") Long projectId,
            @RequestParam("nameLike") String nameLike) {
        return Responses.list(projectFilesServiceFacade.searchFiles(projectId, nameLike));
    }

    @PostMapping("/batchUpload")
    public ListResponse<ProjectFileMetaResp> batchUploadFiles(
            @PathVariable("projectId") Long projectId,
            @RequestBody BatchUploadProjectFileReq req) {
        return Responses.list(projectFilesServiceFacade.batchUploadFiles(projectId, req));
    }

    @PostMapping("/batchDelete")
    public ListResponse<ProjectFileMetaResp> batchDeleteFiles(
            @PathVariable("projectId") Long projectId,
            @RequestBody List<String> paths) {
        return Responses.list(projectFilesServiceFacade.batchDeleteFiles(projectId, paths));
    }

    @PutMapping("/rename")
    public ListResponse<ProjectFileMetaResp> renameFile(
            @PathVariable("projectId") Long projectId,
            @RequestParam("path") String path,
            @RequestParam("destination") String destination) {
        return Responses.list(projectFilesServiceFacade.renameFile(projectId, path, destination));
    }

    @PutMapping("/{path}")
    public ListResponse<ProjectFileMetaResp> editFile(
            @PathVariable("projectId") Long projectId,
            @PathVariable("path") String path,
            @RequestBody UpdateProjectFileReq req) {
        return Responses.list(projectFilesServiceFacade.editFile(projectId, path, req));
    }

    @PostMapping("/batchDownload")
    public SuccessResponse<String> batchDownloadFiles(
            @PathVariable("projectId") Long projectId,
            @RequestBody Set<String> paths) {
        return Responses.success(projectFilesServiceFacade.batchDownloadFiles(projectId, paths));
    }
}
