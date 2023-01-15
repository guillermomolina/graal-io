/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the counter set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following counter:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.truffle.io.nodes.variables;

import org.truffle.io.nodes.arithmetic.AddNodeGen;
import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.literals.LongLiteralNode;
import org.truffle.io.nodes.logic.LessOrEqualNodeGen;
import org.truffle.io.nodes.logic.LessThanNodeGen;
import org.truffle.io.nodes.logic.LogicalNotNodeGen;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@NodeInfo(shortName = "for", description = "The node implementing a for loop")
public final class ForLocalVariableNode extends ExpressionNode {

    private int slotFrameIndex;
    @Child
    private ExpressionNode slotNameNode;
    @Child
    private ExpressionNode startValueNode;
    @Child
    private ExpressionNode endValueNode;
    @Child
    private ExpressionNode stepValueNode;
    @Child
    private ExpressionNode readControlNode;
    @Child
    private ExpressionNode bodyNode;
    @Child
    private ExpressionNode isDescendingNode;

    public ForLocalVariableNode(int slotFrameIndex, ExpressionNode slotNameNode,
            ExpressionNode startValueNode, ExpressionNode endValueNode, ExpressionNode stepValueNode,
            ExpressionNode bodyNode) {
        this.slotFrameIndex = slotFrameIndex;
        this.slotNameNode = slotNameNode;
        this.startValueNode = startValueNode;
        this.endValueNode = endValueNode;
        this.stepValueNode = stepValueNode;
        this.bodyNode = bodyNode;
        this.readControlNode = ReadLocalVariableNodeGen.create(slotFrameIndex);
        this.isDescendingNode = LessThanNodeGen.create(endValueNode, startValueNode);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        ExpressionNode initialAssignmentNode = WriteLocalVariableNodeGen.create(startValueNode, slotFrameIndex,
                slotNameNode);
        final boolean isDescending = evaluateIsDescendingNode(frame);
        final ExpressionNode hasEndedNode;
        if (isDescending) {
            hasEndedNode = LogicalNotNodeGen.create(LessThanNodeGen.create(readControlNode, endValueNode));
        } else {
            hasEndedNode = LessOrEqualNodeGen.create(readControlNode, endValueNode);
        }
        if (stepValueNode == null) {
            if (isDescending) {
                stepValueNode = new LongLiteralNode(-1);
            } else {
                stepValueNode = new LongLiteralNode(1);
            }
        }
        ExpressionNode addNode = AddNodeGen.create(readControlNode, stepValueNode);
        ExpressionNode stepVariableNode = WriteLocalVariableNodeGen.create(addNode, slotFrameIndex, slotNameNode);
        ForRepeatingNode forRepeatingNode = new ForRepeatingNode(hasEndedNode, bodyNode, stepVariableNode);
        LoopNode loopNode = Truffle.getRuntime().createLoopNode(forRepeatingNode);

        initialAssignmentNode.executeGeneric(frame);
        return loopNode.execute(frame);
    }

    private boolean evaluateIsDescendingNode(VirtualFrame frame) {
        try {
            return isDescendingNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            throw new UnsupportedSpecializationException(this, new Node[] { isDescendingNode }, ex.getResult());
        }
    }

}
