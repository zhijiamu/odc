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
package com.oceanbase.odc.metadb.datasecurity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import com.oceanbase.odc.service.datasecurity.model.MaskingSegmentType;

import lombok.Data;

/**
 * @author gaoda.xy
 * @date 2023/5/18 12:30
 */
@Data
@Entity
@Table(name = "data_security_masking_algorithm_segment")
public class MaskingSegmentEntity {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "masking_algorithm_id", nullable = false)
    private Long maskingAlgorithmId;

    @Column(name = "is_mask", nullable = false)
    private Boolean mask;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "type", nullable = false)
    private MaskingSegmentType type;

    @Column(name = "replaced_characters")
    private String replacedCharacters;

    @Column(name = "delimiter")
    private String delimiter;

    @Column(name = "digit_number")
    private Integer digitNumber;

    @Column(name = "digit_percentage")
    private Integer digitPercentage;

    @Column(name = "ordinal")
    private Integer ordinal;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Generated(value = GenerationTime.ALWAYS)
    @Column(name = "create_time", nullable = false, insertable = false, updatable = false)
    private Date createTime;

    @Generated(value = GenerationTime.ALWAYS)
    @Column(name = "update_time", nullable = false, insertable = false, updatable = false)
    private Date updateTime;

}
