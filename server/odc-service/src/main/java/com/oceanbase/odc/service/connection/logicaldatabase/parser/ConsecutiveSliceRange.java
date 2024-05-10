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
package com.oceanbase.odc.service.connection.logicaldatabase.parser;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import lombok.Getter;

/**
 * @Author: Lebie
 * @Date: 2024/4/22 13:29
 * @Description: []
 */
@Getter
public class ConsecutiveSliceRange extends BaseRangeExpression {
    private String rangeStart;

    private String rangeEnd;

    ConsecutiveSliceRange(ParserRuleContext ruleNode, String rangeStart, String rangeEnd) {
        super(ruleNode);
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }


    @Override
    public List<String> listRanges() throws BadExpressionException {
        return LogicalTableExpressionParseUtils.listSteppedRanges(rangeStart, rangeEnd, "1", this.getText());
    }
}