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

package com.oceanbase.odc.service.connection.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * {@link CreateSessionReq}
 *
 * @author yh263208
 * @date 2023-11-15 21:06
 * @since ODC_release_4.2.3
 * @see java.io.Serializable
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CreateSessionReq implements Serializable {
    // 数据库ID
    private Long   dbId;
    // 数据源ID
    private Long   dsId;
    // 真实ID
    private String realId;
    // 来源
    private String from;

    /**
     * 根据连接配置创建会话请求
     *
     * @param connectionConfig 连接配置
     * @return 创建会话请求
     */
    public static CreateSessionReq from(@NonNull ConnectionConfig connectionConfig) {
        return new CreateSessionReq(connectionConfig.getId(), null, null);
    }

    /**
     * 构造函数
     *
     * @param dsId   数据源ID
     * @param dbId   数据库ID
     * @param realId 真实ID
     */
    public CreateSessionReq(Long dsId, Long dbId, String realId) {
        this.dbId = dbId;
        this.dsId = dsId;
        this.realId = realId;
    }

}
