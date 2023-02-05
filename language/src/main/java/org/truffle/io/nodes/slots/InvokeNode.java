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
package org.truffle.io.nodes.slots;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.IoNode;
import org.truffle.io.runtime.IoState;
import org.truffle.io.runtime.exceptions.UndefinedNameException;
import org.truffle.io.runtime.objects.IoBlock;
import org.truffle.io.runtime.objects.IoCall;
import org.truffle.io.runtime.objects.IoCoroutine;
import org.truffle.io.runtime.objects.IoFunction;
import org.truffle.io.runtime.objects.IoInvokable;
import org.truffle.io.runtime.objects.IoLocals;
import org.truffle.io.runtime.objects.IoMessage;
import org.truffle.io.runtime.objects.IoMethod;
import org.truffle.io.runtime.objects.IoObject;

@NodeInfo(shortName = "()")
@NodeChild(value = "receiverNode", type = IoNode.class)
@NodeField(name = "name", type = TruffleString.class)
@NodeField(name = "argumentNodes", type = IoNode[].class)
public abstract class InvokeNode extends IoNode {

    public abstract TruffleString getName();

    public abstract IoNode[] getArgumentNodes();

    protected final Object invokeOrGet(VirtualFrame frame, final Object value, final Object receiver,
            IoObject prototype) {
        if (value == null) {
            throw UndefinedNameException.undefinedField(this, getName());
        }
        if (value instanceof IoFunction) {
            return invokeFunction(frame, (IoFunction) value, receiver);
        }
        if (value instanceof IoBlock) {
            return invokeBlock(frame, (IoBlock) value, receiver, prototype);
        }
        if (value instanceof IoMethod) {
            return invokeMethod(frame, (IoMethod) value, receiver, prototype);
        }
        return value;
    }

    protected final Object invokeFunction(VirtualFrame frame, IoFunction function, final Object receiver) {
        final int argumentsCount = getArgumentNodes().length + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return doInvoke(frame, function, receiver, argumentsCount);
    }

    protected final Object invokeBlock(VirtualFrame frame, IoBlock block, final Object receiver, IoObject prototype) {
        final Object target;
        if (block.getCallSlotIsUsed()) {
            IoLocals sender = block.getSender();
            IoMessage message = IoState.get(this).createMessage(getName(), getArgumentNodes());
            IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
            IoCall call = IoState.get(this).createCall(sender, sender, message, prototype, block, currentCoroutine);
            target = call;
        } else {
            target = receiver;
        }
        int argumentsCount = block.getNumArgs() + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return doInvoke(frame, block, target, argumentsCount);
    }

    protected final Object invokeMethod(VirtualFrame frame, IoMethod method, final Object receiver,
            IoObject prototype) {
        final Object target;
        if (method.getCallSlotIsUsed()) {
            IoLocals sender = IoState.get(this).createLocals(receiver, frame.materialize());
            IoMessage message = IoState.get(this).createMessage(getName(), getArgumentNodes());
            IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
            IoCall call = IoState.get(this).createCall(sender, receiver, message, prototype, method, currentCoroutine);
            target = call;
        } else {
            target = receiver;
        }
        int argumentsCount = method.getNumArgs() + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return doInvoke(frame, method, target, argumentsCount);
    }

    @ExplodeLoop
    protected final Object doInvoke(VirtualFrame frame, IoInvokable invokable, final Object receiver,
            final int argumentsCount) {
        CompilerAsserts.compilationConstant(argumentsCount);
        Object[] argumentValues = new Object[argumentsCount];
        argumentValues[IoLocals.TARGET_ARGUMENT_INDEX] = receiver;
        for (int i = 0; i < getArgumentNodes().length; i++) {
            argumentValues[i + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX] = getArgumentNodes()[i].executeGeneric(frame);
        }
        Object result = DirectCallNode.create(invokable.getCallTarget()).call(argumentValues);
        return result;
    }

}
