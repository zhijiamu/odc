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
package com.oceanbase.odc.service.schedule.job;

import org.quartz.JobExecutionContext;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.service.dlm.model.DataArchiveParameters;
import com.oceanbase.odc.service.dlm.model.DataArchiveTableConfig;
import com.oceanbase.odc.service.dlm.utils.DataArchiveConditionUtil;
import com.oceanbase.odc.service.quartz.util.ScheduleTaskUtils;
import com.oceanbase.tools.migrator.common.enums.JobType;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author：tinker
 * @Date: 2023/5/9 14:46
 * @Descripition:
 */
@Slf4j
public class DataArchiveJob extends AbstractDlmJob {
    @Override
    public void executeJob(JobExecutionContext context) {
        executeInTaskFramework(context);
    }

    private void executeInTaskFramework(JobExecutionContext context) {

        DataArchiveParameters dataArchiveParameters = ScheduleTaskUtils.getDataArchiveParameters(context);
        DLMJobReq parameters = new DLMJobReq();
        parameters.setJobName(ScheduleTaskUtils.getJobName(context));
        parameters.setScheduleTaskId(getScheduleTaskId());
        parameters.setJobType(JobType.MIGRATE);
        parameters.setTables(dataArchiveParameters.getTables());
        for (DataArchiveTableConfig tableConfig : parameters.getTables()) {
            tableConfig.setConditionExpression(StringUtils.isNotEmpty(tableConfig.getConditionExpression())
                    ? DataArchiveConditionUtil.parseCondition(tableConfig.getConditionExpression(),
                            dataArchiveParameters.getVariables(),
                            context.getFireTime())
                    : "");
        }
        parameters.setDeleteAfterMigration(dataArchiveParameters.isDeleteAfterMigration());
        parameters.setMigrationInsertAction(dataArchiveParameters.getMigrationInsertAction());
        parameters.setNeedPrintSqlTrace(dataArchiveParameters.isNeedPrintSqlTrace());
        parameters
                .setRateLimit(limiterService.getByOrderIdOrElseDefaultConfig(getScheduleId()));
        parameters.setWriteThreadCount(dataArchiveParameters.getWriteThreadCount());
        parameters.setReadThreadCount(dataArchiveParameters.getReadThreadCount());
        parameters.setShardingStrategy(dataArchiveParameters.getShardingStrategy());
        parameters.setScanBatchSize(dataArchiveParameters.getScanBatchSize());
        parameters.setSourceDs(getDataSourceInfo(dataArchiveParameters.getSourceDatabaseId()));
        parameters.setTargetDs(getDataSourceInfo(dataArchiveParameters.getTargetDataBaseId()));
        parameters.getSourceDs().setQueryTimeout(dataArchiveParameters.getQueryTimeout());
        parameters.getTargetDs().setQueryTimeout(dataArchiveParameters.getQueryTimeout());
        parameters.setSyncTableStructure(dataArchiveParameters.getSyncTableStructure());



        publishJob(parameters, dataArchiveParameters.getTimeoutMillis(),
                dataArchiveParameters.getSourceDatabaseId());
    }

}
