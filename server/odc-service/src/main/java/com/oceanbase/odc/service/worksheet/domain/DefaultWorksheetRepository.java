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
package com.oceanbase.odc.service.worksheet.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * the repository of default worksheets which location in /Worksheets/
 *
 * @author keyangs
 * @date 2024/8/1
 * @since 4.3.2
 */
public interface DefaultWorksheetRepository {
    Optional<Worksheet> findByProjectAndPath(Long projectId, Path path, boolean isAddWriteLock,
            boolean createDefaultIfNotExist, boolean loadSubFiles,
            boolean loadSameLevelFiles);

    List<Worksheet> listByProjectIdAndPathNameLike(Long projectId, String pathNameLike, int limit);

    List<Worksheet> listByProjectIdAndPath(Long projectId, Path path, boolean loadSubFiles);

    void batchAdd(Set<Worksheet> files);

    void batchDelete(Set<Long> ids);

    void batchUpdateById(Set<Worksheet> files, boolean needAddVersion);
}