/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
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
 * This license is subject to the following condition:
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
package org.truffle.io.nodes.expression;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import org.truffle.io.NotImplementedException;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IONil;

public final class IOInvokeMethodNode extends IOExpressionNode {

    @Child
    private DirectCallNode callNode;

    @Children
    private IOExpressionNode[] argumentNodes;

    private IOMethod method;
    private Object receiver;

    public IOInvokeMethodNode(final IOMethod method, final Object receiver,
            final IOExpressionNode[] argumentNodes) {
        this.method = method;
        this.receiver = receiver;
        this.argumentNodes = argumentNodes;
        this.callNode = method != null ? DirectCallNode.create(method.getCallTarget()) : null;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (callNode == null) {
            /* The source code did not have a "main" function, so nothing to execute. */
            return IONil.SINGLETON;
        } else {
            return executeMethod(frame);
        }
    }

    @ExplodeLoop
    protected final Object executeMethod(VirtualFrame frame) {
        int argumentCount = Integer.max(argumentNodes.length, method.getNumArgs());
        CompilerAsserts.compilationConstant(argumentCount);
        Object[] argumentValues = new Object[argumentCount + 2];
        argumentValues[0] = receiver;
        argumentValues[1] = method;
        if (argumentCount > 0) {
            int argumentValuesIndex = 0;
            int argumentNodesIndex = 0;
            while (argumentValuesIndex < Integer.min(argumentNodes.length, method.getNumArgs())) {
                argumentValues[2 + argumentValuesIndex++] = argumentNodes[argumentNodesIndex++].executeGeneric(frame);
            }
            while (argumentValuesIndex < method.getNumArgs()) {
                argumentValues[2 + argumentValuesIndex++] = IONil.SINGLETON;
            }
            if (argumentNodesIndex < argumentNodes.length) {
                final MaterializedFrame materializedFrame = frame.materialize();
                while (argumentNodesIndex < argumentNodes.length) {
                    argumentValues[2 + argumentValuesIndex++] = IOState.get(this)
                            .createBlock(argumentNodes[argumentNodesIndex++], materializedFrame);
                }
                throw new NotImplementedException();
            }
            assert argumentNodesIndex == argumentNodes.length;
            assert argumentValuesIndex == argumentCount;
        }
        Object result = DirectCallNode.create(method.getCallTarget()).call(argumentValues);
        return result;
    }

}
