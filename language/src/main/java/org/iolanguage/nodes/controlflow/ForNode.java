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
package org.iolanguage.nodes.controlflow;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.literals.LongLiteralNode;
import org.iolanguage.nodes.logic.LessOrEqualNodeGen;
import org.iolanguage.nodes.logic.LessThanNodeGen;
import org.iolanguage.nodes.logic.LogicalNotNodeGen;
import org.iolanguage.runtime.objects.IoNil;

@NodeInfo(shortName = "for", description = "The node implementing a for loop")
public final class ForNode extends IoNode {

    @Child
    private IoNode initializeNode;
    @Child
    private IoNode stepNode;
    @Child
    private IoNode readNode;
    @Child
    private IoNode startValueNode;
    @Child
    private IoNode endValueNode;
    @Child
    private IoNode stepValueNode;
    @Child
    private IoNode bodyNode;
    @Child
    private IoNode isDescendingNode;
    @Child
    private IoNode isPositiveStepValueNode;

    public ForNode(IoNode initializeNode, IoNode stepNode, IoNode readNode, IoNode startValueNode,
            IoNode endValueNode, IoNode stepValueNode, IoNode bodyNode) {
        this.initializeNode = initializeNode;
        this.stepNode = stepNode;
        this.readNode = readNode;
        this.startValueNode = startValueNode;
        this.endValueNode = endValueNode;
        this.stepValueNode = stepValueNode;
        this.bodyNode = bodyNode;
        this.isDescendingNode = LessThanNodeGen.create(endValueNode, startValueNode);
        this.isPositiveStepValueNode = LessThanNodeGen.create(new LongLiteralNode(0), stepValueNode);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        final boolean isDescending = evaluateIsDescendingNode(frame);
        final IoNode conditionNode;
        if (isDescending) {
            if(evaluateIsPositiveStepValueNode(frame)) {
                return IoNil.SINGLETON;
            }
            conditionNode = LogicalNotNodeGen.create(LessThanNodeGen.create(readNode, endValueNode));
        } else {
            conditionNode = LessOrEqualNodeGen.create(readNode, endValueNode);
        }
        ForRepeatingNode forRepeatingNode = new ForRepeatingNode(conditionNode, bodyNode, stepNode);
        LoopNode loopNode = Truffle.getRuntime().createLoopNode(forRepeatingNode);

        initializeNode.executeGeneric(frame);
        loopNode.execute(frame);
        return forRepeatingNode.getLastResult();
    }

    protected boolean evaluateIsDescendingNode(VirtualFrame frame) {
        try {
            return isDescendingNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            throw new UnsupportedSpecializationException(this, new IoNode[] { isDescendingNode }, ex.getResult());
        }
    }

    protected boolean evaluateIsPositiveStepValueNode(VirtualFrame frame) {
        try {
            return isPositiveStepValueNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            throw new UnsupportedSpecializationException(this, new IoNode[] { isDescendingNode }, ex.getResult());
        }
    }
}
