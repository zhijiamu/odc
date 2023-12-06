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

package com.oceanbase.odc.service.task.schedule;

import java.util.Collections;

import com.oceanbase.odc.metadb.task.TaskEntity;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.task.TaskService;
import com.oceanbase.odc.service.task.caller.DefaultJobContext;
import com.oceanbase.odc.service.task.caller.JobContext;
import com.oceanbase.odc.service.task.config.JobConfiguration;

/**
 * @author yaobin
 * @date 2023-11-30
 * @since 4.2.4
 */
public class TaskTaskJobContextBuilder extends DefaultJobContextBuilder {

    @Override
    protected JobContext doBuild(JobConfiguration configuration, DefaultJobContext jobContext) {
        TaskService taskService = configuration.getTaskService();
        TaskEntity taskEntity = taskService.detail(jobContext.getJobIdentity().getId());
        if (taskEntity.getConnectionId() != null) {
            ConnectionConfig config = configuration.getConnectionService()
                    .getForConnectionSkipPermissionCheck(taskEntity.getConnectionId());
            config.setDefaultSchema(taskEntity.getDatabaseName());
            jobContext.setConnectionConfigs(Collections.singletonList(config));
        }
        return jobContext;
    }
}