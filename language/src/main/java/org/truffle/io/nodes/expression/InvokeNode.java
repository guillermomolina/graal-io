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

import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.nodes.literals.MessageLiteralNode;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOFunction;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IOLocals;
import org.truffle.io.runtime.objects.IOMessage;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class InvokeNode extends ExpressionNode {

    @Child
    private DirectCallNode callNode;

    @Child
    private MessageLiteralNode messageNode;

    private IOInvokable invokable;
    private Object target;

    public InvokeNode(final IOInvokable invokable, final Object target, final MessageLiteralNode messageNode) {
        this.invokable = invokable;
        this.target = target;
        this.messageNode = messageNode;
        this.callNode = DirectCallNode.create(invokable.getCallTarget());
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (invokable instanceof IOBlock) {
            return executeMethod((IOBlock) invokable, frame);
        } else if (invokable instanceof IOFunction) {
            return executeFunction((IOFunction) invokable, frame);
        }
        throw new ShouldNotBeHereException();
    }

    @ExplodeLoop
    protected final Object executeFunction(IOFunction function, VirtualFrame frame) {
        ExpressionNode[] argumentNodes = messageNode.getArgumentNodes();
        CompilerAsserts.compilationConstant(argumentNodes.length + 1);
        Object[] argumentValues = new Object[argumentNodes.length + 1];
        argumentValues[0] = target;
        for (int i = 0; i < argumentNodes.length; i++) {
            argumentValues[i + 1] = argumentNodes[i].executeGeneric(frame);
        }
        Object result = callNode.call(argumentValues);
        return result;
    }

    @ExplodeLoop
    protected final Object executeMethod(IOBlock method, VirtualFrame frame) {
        IOMessage message = messageNode.executeGeneric(frame);
        ExpressionNode[] argumentNodes = messageNode.getArgumentNodes();
        int argumentsCount = method.getNumArgs() + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        CompilerAsserts.compilationConstant(argumentsCount);
        Object[] argumentValues = new Object[argumentsCount];
        argumentValues[IOLocals.TARGET_ARGUMENT_INDEX] = target;
        argumentValues[IOLocals.SENDER_ARGUMENT_INDEX] = IOState.get(this).createLocals(frame.materialize());
        argumentValues[IOLocals.MESSAGE_ARGUMENT_INDEX] = message;
        argumentValues[IOLocals.ACTIVATED_ARGUMENT_INDEX] = method;
        argumentValues[IOLocals.COROUTINE_ARGUMENT_INDEX] = IOState.get(this).getCurrentCoroutine();
        for (int i = 0; i < method.getNumArgs(); i++) {
            argumentValues[i + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX] = argumentNodes[i].executeGeneric(frame);
        }
        Object result = callNode.call(argumentValues);
        return result;
    }

}