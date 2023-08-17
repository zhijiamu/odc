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
package com.oceanbase.odc.service.iam.model;

import java.io.Serializable;
import java.util.List;

import com.oceanbase.odc.core.shared.constant.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author gaoda.xy
 * @date 2022/11/25 11:18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionConfig implements Serializable {
    private Long resourceId;
    private ResourceType resourceType;
    private List<String> actions;
}
