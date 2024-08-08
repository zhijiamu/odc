/*
 * Copyright (c) 2023 OceanBase.
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
package com.oceanbase.odc.service.worksheet;

import static com.oceanbase.odc.service.worksheet.constants.WorksheetConstant.PROJECT_FILES_NAME_LIKE_SEARCH_LIMIT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.oceanbase.odc.core.shared.exception.InternalServerError;
import com.oceanbase.odc.service.common.util.OdcFileUtil;
import com.oceanbase.odc.service.worksheet.constants.WorksheetConstant;
import com.oceanbase.odc.service.worksheet.converter.WorksheetConverter;
import com.oceanbase.odc.service.worksheet.domain.BatchCreateWorksheets;
import com.oceanbase.odc.service.worksheet.domain.BatchOperateWorksheetsResult;
import com.oceanbase.odc.service.worksheet.domain.DivideBatchOperateWorksheets;
import com.oceanbase.odc.service.worksheet.domain.Path;
import com.oceanbase.odc.service.worksheet.domain.WorkSheetsSearch;
import com.oceanbase.odc.service.worksheet.domain.Worksheet;
import com.oceanbase.odc.service.worksheet.domain.WorksheetOssGateway;
import com.oceanbase.odc.service.worksheet.domain.WorksheetProjectRepository;
import com.oceanbase.odc.service.worksheet.factory.WorksheetServiceFactory;
import com.oceanbase.odc.service.worksheet.model.BatchOperateWorksheetsResp;
import com.oceanbase.odc.service.worksheet.model.BatchUploadWorksheetsReq;
import com.oceanbase.odc.service.worksheet.model.GenerateWorksheetUploadUrlResp;
import com.oceanbase.odc.service.worksheet.model.GenerateWorksheetUploadUrlReq;
import com.oceanbase.odc.service.worksheet.model.UpdateWorksheetReq;
import com.oceanbase.odc.service.worksheet.model.WorksheetLocation;
import com.oceanbase.odc.service.worksheet.model.WorksheetMetaResp;
import com.oceanbase.odc.service.worksheet.model.WorksheetResp;
import com.oceanbase.odc.service.worksheet.service.WorksheetService;
import com.oceanbase.odc.service.worksheet.utils.WorksheetPathUtil;

/**
 *
 * @author keyang
 * @date 2024/7/31
 * @since 4.3.2
 */
@Service
public class WorksheetServiceFacadeImpl implements WorksheetServiceFacade {
    @Resource
    private WorksheetProjectRepository worksheetProjectRepository;
    @Resource
    private WorksheetOssGateway projectFileOssGateway;

    @Resource
    private WorksheetServiceFactory worksheetServiceFactory;

    @Override
    public GenerateWorksheetUploadUrlResp generateUploadUrl(Long projectId, GenerateWorksheetUploadUrlReq req) {
        return null;
    }


    @Override
    public WorksheetMetaResp createWorksheet(Long projectId, String pathStr, String objectKey) {
        Path createPath = new Path(pathStr);
        WorksheetService projectFileService = worksheetServiceFactory.getProjectFileService(
                createPath.getLocation());
        Worksheet file = projectFileService.createWorksheet(projectId, createPath, objectKey);
        return WorksheetConverter.convertToMetaResp(file);
    }


    @Override
    public WorksheetResp getWorksheetDetail(Long projectId, String pathStr) {
        Path path = new Path(pathStr);
        WorksheetService projectFileService = worksheetServiceFactory.getProjectFileService(
                path.getLocation());
        Worksheet file = projectFileService.getWorksheetDetails(projectId, path);
        return WorksheetConverter.convertToResp(file);
    }

    @Override
    public List<WorksheetMetaResp> listWorksheets(Long projectId, String pathStr) {
        Path path = new Path(pathStr);
        WorksheetService projectFileService = worksheetServiceFactory.getProjectFileService(
                path.getLocation());
        List<Worksheet> worksheets = projectFileService.listWorksheets(projectId, path);
        return worksheets.stream()
                .map(WorksheetConverter::convertToMetaResp)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorksheetMetaResp> searchWorksheets(Long projectId, String nameLike) {
        if (StringUtils.isBlank(nameLike)) {
            return new ArrayList<>();
        }
        WorksheetService repoProjectFileService =
                worksheetServiceFactory.getProjectFileService(WorksheetLocation.REPOS);
        WorksheetService normalProjectFileService =
                worksheetServiceFactory.getProjectFileService(WorksheetLocation.WORKSHEETS);
        WorkSheetsSearch workSheetsSearch = new WorkSheetsSearch(nameLike,
                normalProjectFileService.searchWorksheets(projectId, nameLike, PROJECT_FILES_NAME_LIKE_SEARCH_LIMIT),
                repoProjectFileService.searchWorksheets(projectId, nameLike, PROJECT_FILES_NAME_LIKE_SEARCH_LIMIT));
        List<Worksheet> worksheets = workSheetsSearch.searchByNameLike(nameLike);
        return worksheets.stream()
                .map(WorksheetConverter::convertToMetaResp)
                .limit(PROJECT_FILES_NAME_LIKE_SEARCH_LIMIT)
                .collect(Collectors.toList());
    }

    @Override
    public BatchOperateWorksheetsResp batchUploadWorksheets(Long projectId, BatchUploadWorksheetsReq req) {
        BatchCreateWorksheets batchCreateWorksheets = new BatchCreateWorksheets(req);
        Path parentPath = batchCreateWorksheets.getParentPath();
        WorksheetService projectFileService = worksheetServiceFactory.getProjectFileService(
                parentPath.getLocation());
        BatchOperateWorksheetsResult batchOperateWorksheetsResult = projectFileService.batchUploadWorksheets(projectId,
                batchCreateWorksheets);
        return WorksheetConverter.convertToBatchOperateResp(batchOperateWorksheetsResult);
    }

    @Override
    public BatchOperateWorksheetsResp batchDeleteWorksheets(Long projectId, List<String> paths) {
        DivideBatchOperateWorksheets divideBatchOperateWorksheets = new DivideBatchOperateWorksheets(paths);

        BatchOperateWorksheetsResult batchOperateWorksheetsResult = new BatchOperateWorksheetsResult();
        if (CollectionUtils.isNotEmpty(divideBatchOperateWorksheets.getNormalPaths())) {

            batchOperateWorksheetsResult.addResult(
                    worksheetServiceFactory.getProjectFileService(WorksheetLocation.WORKSHEETS)
                            .batchDeleteWorksheets(projectId, divideBatchOperateWorksheets.getNormalPaths()));
        }
        if (CollectionUtils.isNotEmpty(divideBatchOperateWorksheets.getReposPaths())) {
            batchOperateWorksheetsResult.addResult(
                    worksheetServiceFactory.getProjectFileService(WorksheetLocation.REPOS)
                            .batchDeleteWorksheets(projectId, divideBatchOperateWorksheets.getReposPaths()));
        }
        return WorksheetConverter.convertToBatchOperateResp(batchOperateWorksheetsResult);
    }

    @Override
    public List<WorksheetMetaResp> renameWorksheet(Long projectId, String pathStr, String destination) {
        Path path = new Path(pathStr);
        Path destPath = new Path(destination);
        WorksheetService projectFileService = worksheetServiceFactory.getProjectFileService(
                path.getLocation());
        List<Worksheet> worksheets = projectFileService.renameWorksheet(projectId, path, destPath);
        return worksheets.stream()
                .map(WorksheetConverter::convertToMetaResp)
                .collect(Collectors.toList());
    }


    @Override
    public List<WorksheetMetaResp> editWorksheet(Long projectId, String pathStr, UpdateWorksheetReq req) {
        Path path = new Path(pathStr);
        Path destPath = new Path(req.getDestination());
        WorksheetService projectFileService = worksheetServiceFactory.getProjectFileService(
                path.getLocation());
        List<Worksheet> worksheets =
                projectFileService.editWorksheet(projectId, path, destPath, req.getObjectKey(), req.getVersion());
        return worksheets.stream()
                .map(WorksheetConverter::convertToMetaResp)
                .collect(Collectors.toList());
    }


    @Override
    public String batchDownloadWorksheets(Long projectId, Set<String> paths) {
        DivideBatchOperateWorksheets divideBatchOperateWorksheets = new DivideBatchOperateWorksheets(paths);
        if (divideBatchOperateWorksheets.size() == 1) {
            Path downloadPath = divideBatchOperateWorksheets.findFirst().get();
            return worksheetServiceFactory.getProjectFileService(downloadPath.getLocation())
                    .getDownloadUrl(projectId, downloadPath);
        }
        Optional<Path> commonParentPathOptional =
                WorksheetPathUtil.findCommonParentPath(divideBatchOperateWorksheets.all());
        String rootDirectoryName = getRootDirectoryName(projectId, commonParentPathOptional);
        String parentOfDownloadDirectory = WorksheetPathUtil.getWorksheetDownloadDirectory();
        String downloadDirectoryStr = parentOfDownloadDirectory + rootDirectoryName;
        String zipFileStr = WorksheetPathUtil.getWorksheetDownloadZipFile(parentOfDownloadDirectory, rootDirectoryName);
        java.nio.file.Path downloadDirectoryPath =
                WorksheetPathUtil.createFileWithParent(downloadDirectoryStr, true);
        File downloadDirectory = downloadDirectoryPath.toFile();
        try {
            if (CollectionUtils.isNotEmpty(divideBatchOperateWorksheets.getNormalPaths())) {
                worksheetServiceFactory.getProjectFileService(WorksheetLocation.WORKSHEETS)
                        .downloadPathsToDirectory(projectId, divideBatchOperateWorksheets.getNormalPaths(),
                                commonParentPathOptional, downloadDirectory);
            }
            if (CollectionUtils.isNotEmpty(divideBatchOperateWorksheets.getReposPaths())) {
                worksheetServiceFactory.getProjectFileService(WorksheetLocation.REPOS)
                        .downloadPathsToDirectory(projectId, divideBatchOperateWorksheets.getReposPaths(),
                                commonParentPathOptional, downloadDirectory);
            }

            try {
                WorksheetPathUtil.createFileWithParent(zipFileStr, false);
                OdcFileUtil.zip(downloadDirectoryStr, zipFileStr);
            } catch (IOException e) {
                throw new InternalServerError("create file error,"
                        + "downloadDirectoryStr: " + downloadDirectoryStr + ",zipFileStr: " + zipFileStr, e);
            }
            String zipOssObjectKey = projectFileOssGateway.uploadFile(new File(zipFileStr),
                    WorksheetConstant.DOWNLOAD_ZIP_DURATION_SECONDS);
            return projectFileOssGateway.generateDownloadUrl(zipOssObjectKey);
        } finally {
            OdcFileUtil.deleteFiles(new File(parentOfDownloadDirectory));
        }
    }

    private String getRootDirectoryName(Long projectId, Optional<Path> commonParentPathOptional) {
        if (!commonParentPathOptional.isPresent() || commonParentPathOptional.get().isRoot()) {
            return worksheetProjectRepository.getProjectName(projectId);
        }
        return commonParentPathOptional.get().getName();
    }
}