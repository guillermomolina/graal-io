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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import org.truffle.io.NotImplementedException;
import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOCall;
import org.truffle.io.runtime.objects.IOFunction;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IONil;

public final class InvokeNode extends ExpressionNode {

    @Child
    private DirectCallNode callNode;

    @Children
    private ExpressionNode[] argumentNodes;

    private IOInvokable invokable;
    private Object receiver;

    public InvokeNode(final IOInvokable invokable, final Object receiver, final ExpressionNode[] argumentNodes) {
        this.invokable = invokable;
        this.receiver = receiver;
        this.argumentNodes = argumentNodes;
        this.callNode = invokable != null ? DirectCallNode.create(invokable.getCallTarget()) : null;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (callNode == null) {
            /* The source code did not have a "main" function, so nothing to execute. */
            return IONil.SINGLETON;
        } else {
            if (invokable instanceof IOBlock) {
                return executeMethod((IOBlock) invokable, frame);
            } else if (invokable instanceof IOFunction) {
                return executeFunction((IOFunction) invokable, frame);
            }
            throw new ShouldNotBeHereException();
        }
    }

    @ExplodeLoop
    protected final Object executeFunction(IOFunction function, VirtualFrame frame) {
        CompilerAsserts.compilationConstant(argumentNodes.length + 1);
        Object[] argumentValues = new Object[argumentNodes.length + 1];
        argumentValues[0] = receiver;
        for (int i = 0; i < argumentNodes.length; i++) {
            argumentValues[i + 1] = argumentNodes[i].executeGeneric(frame);
        }
        Object result = DirectCallNode.create(invokable.getCallTarget()).call(argumentValues);
        return result;
    }

    @ExplodeLoop
    protected final Object executeMethod(IOBlock method, VirtualFrame frame) {
        int argumentCount = Integer.max(argumentNodes.length, method.getNumArgs());
        CompilerAsserts.compilationConstant(argumentCount);
        Object[] argumentValues = new Object[argumentCount + 2];
        IOCall call = IOState.get(this).createCall(null, null, receiver, null, method, null);
        argumentValues[0] = receiver;
        argumentValues[1] = call;
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
                while (argumentNodesIndex < argumentNodes.length) {
                    argumentValues[2 + argumentValuesIndex++] = IOState.get(this)
                            .createMessage(argumentNodes[argumentNodesIndex++]);
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
