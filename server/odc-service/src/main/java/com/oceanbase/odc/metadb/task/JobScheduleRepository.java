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
package com.oceanbase.odc.metadb.task;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.oceanbase.odc.core.shared.constant.FlowStatus;
import com.oceanbase.odc.core.shared.constant.TaskStatus;
import com.oceanbase.odc.core.shared.constant.TaskType;
import com.oceanbase.odc.metadb.flow.FlowInstanceEntity;

/**
 * @author yaobin
 * @date 2023-12-06
 * @since 4.2.4
 */
@Repository
public interface JobScheduleRepository extends JpaRepository<JobEntity, Long>,
        JpaSpecificationExecutor<JobEntity> {

    @Transactional
    @Query("update JobEntity set "
            + "executor=:#{#param.executor},status=:#{#param.status},"
            + "progressPercentage=:#{#param.progressPercentage},resultJson=:#{#param.resultJson}"
            + " where id=:#{#param.id}")
    @Modifying
    int update(@Param("param") JobEntity entity);

    @Transactional
    @Query("update JobEntity set "
            + "jobName=:#{#param.jobName},status=:#{#param.status},scheduleTimes=:#{#param.scheduleTimes},"
            + "executionTimes=:#{#param.executionTimes}"
            + " where id=:#{#param.id}")
    @Modifying
    void updateJobNameAndStatus(@Param("param") JobEntity entity);


    @Transactional
    @Query("update JobEntity set "
            + "scheduleTimes=:scheduleTimes"
            + " where id=:id")
    @Modifying
    void updateScheduleTimes(@Param("id") Long id, @Param("scheduleTimes") Integer scheduleTimes);

    @Transactional
    @Query("update JobEntity set "
            + "status=:status"
            + " where id=:id")
    @Modifying
    void updateStatus(@Param("id") Long id, @Param("status") TaskStatus status);

    @Query(value = "select e.id from JobEntity e where e.flowInstanceId=:flowInstanceId and e.jobType=:jobType")
    List<JobEntity> findJobByFlowInstanceIdAndJobType(@Param("flowInstanceId") Long flowInstanceId,
        @Param("jobType") String jobType);


}
