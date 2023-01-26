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

import org.truffle.io.nodes.IONode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.UndefinedNameException;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOCall;
import org.truffle.io.runtime.objects.IOCoroutine;
import org.truffle.io.runtime.objects.IOFunction;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IOLocals;
import org.truffle.io.runtime.objects.IOMessage;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

@NodeInfo(shortName = "()")
public final class InvokeNode extends IONode {

    @Child
    protected IONode receiverNode;
    @Child
    protected IONode valueNode;
    private final TruffleString name;
    @Children
    private final IONode[] argumentNodes;

    public InvokeNode(final IONode receiverNode, final IONode valueNode, final TruffleString name,
            final IONode[] argumentNodes) {
        this.receiverNode = receiverNode;
        this.valueNode = valueNode;
        this.name = name;
        this.argumentNodes = argumentNodes;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object receiver = receiverNode.executeGeneric(frame);
        Object value = null;
        if (valueNode == null) {
            IOObject prototype = null;
            if (receiver instanceof IOObject) {
                prototype = (IOObject) receiver;
            } else {
                prototype = IOState.get(this).getPrototype(receiver);
            }
            value = IOObjectUtil.getSlotOrDefault(prototype, name);
        } else {
            value = valueNode.executeGeneric(frame);
        }

        if (value == null) {
            executeNull(frame, receiver, value);
        }
        if (value instanceof IOFunction) {
            return executeFunction(frame, receiver, (IOFunction) value);
        }
        if (value instanceof IOBlock) {
            return executeBlock(frame, receiver, (IOBlock) value);
        }
        if (value instanceof IOMethod) {
            return executeMethod(frame, receiver, (IOMethod) value);
        }
        return executeOther(frame, receiver, value);
    }

    protected final Object executeNull(VirtualFrame frame, final Object receiver, Object unknown) {
        throw UndefinedNameException.undefinedField(this, name);
    }

    protected final Object executeFunction(VirtualFrame frame, final Object receiver, IOFunction function) {
        final int argumentsCount = argumentNodes.length + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, receiver, function, argumentsCount);
    }

    protected final Object executeBlock(VirtualFrame frame, final Object receiver, IOBlock block) {
        IOLocals sender = block.getSender();
        IOMessage message = IOState.get(this).createMessage(name, argumentNodes);
        IOCoroutine currentCoroutine = IOState.get(this).getCurrentCoroutine();
        IOCall call = IOState.get(this).createCall(sender, sender, message, null, block, currentCoroutine);
        int argumentsCount = block.getNumArgs() + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, call, block, argumentsCount);
    }

    protected final Object executeMethod(VirtualFrame frame, final Object receiver, IOMethod method) {
        final IOObject prototype;
        if (receiver instanceof IOObject) {
            prototype = (IOObject) receiver;
        } else {
            prototype = IOState.get(this).getPrototype(receiver);
        }
        IOLocals sender = IOState.get(this).createLocals(prototype, frame.materialize());
        IOMessage message = IOState.get(this).createMessage(name, argumentNodes);
        IOCoroutine currentCoroutine = IOState.get(this).getCurrentCoroutine();
        IOCall call = IOState.get(this).createCall(sender, receiver, message, null, method, currentCoroutine);
        int argumentsCount = method.getNumArgs() + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, call, method, argumentsCount);
    }

    protected final Object executeOther(VirtualFrame frame, final Object receiver, Object value) {
        return value;
    }

    @ExplodeLoop
    protected final Object execute(VirtualFrame frame, final Object receiver, IOInvokable invokable, final int argumentsCount) {
        CompilerAsserts.compilationConstant(argumentsCount);
        Object[] argumentValues = new Object[argumentsCount];
        argumentValues[IOLocals.TARGET_ARGUMENT_INDEX] = receiver;
        for (int i = 0; i < argumentNodes.length; i++) {
            argumentValues[i + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX] = argumentNodes[i].executeGeneric(frame);
        }
        Object result = DirectCallNode.create(invokable.getCallTarget()).call(argumentValues);
        return result;
    }

}
