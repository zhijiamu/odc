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
package com.oceanbase.odc.service.onlineschemachange.oms.enums;


/**
 * @author yaobin
 * @date 2023-06-01
 * @since 4.2.0
 */
public enum OmsStepStatus {

    /**
     * 初始化
     */
    INIT,
    /**
     * 运行中
     */
    RUNNING,
    /**
     * 失败
     */
    FAILED,
    /**
     * 已完成
     */
    FINISHED,
    /**
     * 暂停中
     */
    SUSPEND,
    /**
     * 持续监控状态，用于增量同步和增量校验的持续监控态
     */
    MONITORING,

    UNKNOWN
}
