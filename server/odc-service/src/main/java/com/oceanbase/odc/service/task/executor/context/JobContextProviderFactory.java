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

package com.oceanbase.odc.service.task.executor.context;

import com.oceanbase.odc.service.task.enums.TaskRunMode;

import lombok.NonNull;

/**
 * @author gaoda.xy
 * @date 2023/11/23 13:55
 */
public class JobContextProviderFactory {

    public static JobContextProvider create(@NonNull String taskRunMode) {
        if (taskRunMode.equalsIgnoreCase(TaskRunMode.PROCESS.name())) {
            return new ProcessJobContextProvider();
        } else if (taskRunMode.equalsIgnoreCase(TaskRunMode.K8S.name())) {
            return new K8sJobContextProvider();
        } else {
            throw new RuntimeException("Unsupported task run mode: " + taskRunMode);
        }
    }
}
