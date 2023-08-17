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
package com.oceanbase.tools.sqlparser.statement.expression;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oceanbase.tools.sqlparser.statement.BaseStatement;
import com.oceanbase.tools.sqlparser.statement.Expression;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

/**
 * {@link ConstExpression}
 *
 * @author yh263208
 * @date 2022-11-28 17:27
 * @since ODC_release_4.1.0
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class ConstExpression extends BaseStatement implements Expression {

    private final String exprConst;

    public ConstExpression(TerminalNode terminalNode) {
        this(null, terminalNode);
    }

    public ConstExpression(ParserRuleContext ruleNode) {
        this(ruleNode, null);
    }

    public ConstExpression(@NonNull String exprConst) {
        this.exprConst = exprConst;
    }

    private ConstExpression(ParserRuleContext ruleNode, TerminalNode terminalNode) {
        super(ruleNode, terminalNode);
        if (ruleNode != null) {
            this.exprConst = ruleNode.getText();
        } else if (terminalNode != null) {
            this.exprConst = terminalNode.getText();
        } else {
            throw new IllegalArgumentException("Illegal arguments");
        }
    }

    @Override
    public String toString() {
        return this.exprConst;
    }

}
