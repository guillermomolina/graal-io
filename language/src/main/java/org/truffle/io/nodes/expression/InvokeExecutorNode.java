/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.IoNode;
import org.truffle.io.runtime.IoState;
import org.truffle.io.runtime.UndefinedNameException;
import org.truffle.io.runtime.objects.IoBlock;
import org.truffle.io.runtime.objects.IoCall;
import org.truffle.io.runtime.objects.IoCoroutine;
import org.truffle.io.runtime.objects.IoFunction;
import org.truffle.io.runtime.objects.IoInvokable;
import org.truffle.io.runtime.objects.IoLocals;
import org.truffle.io.runtime.objects.IoMessage;
import org.truffle.io.runtime.objects.IoMethod;
import org.truffle.io.runtime.objects.IoObject;

@NodeChild("invokableNode")
@NodeField(name = "receiver", type = Object.class)
@NodeField(name = "name", type = TruffleString.class)
@NodeField(name = "argumentNodes", type = IoNode[].class)
public abstract class InvokeExecutorNode extends IoNode {

    static final int LIBRARY_LIMIT = 3;

    public abstract Object getReceiver();

    public abstract TruffleString getName();

    public abstract IoNode[] getArgumentNodes();

    @Specialization(guards = "!values.isNull(invokable)", limit="LIBRARY_LIMIT")
    protected final Object executeNull(VirtualFrame frame, Object invokable,
            @CachedLibrary("invokable") InteropLibrary values) {
        throw UndefinedNameException.undefinedField(this, getName());
    }

    @Specialization
    protected final Object executeFunction(VirtualFrame frame, IoFunction function) {
        final int argumentsCount = getArgumentNodes().length + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, getReceiver(), function, argumentsCount);
    }

    @Specialization
    protected final Object executeBlock(VirtualFrame frame, IoBlock block) {
        IoLocals sender = block.getSender();
        IoMessage message = IoState.get(this).createMessage(getName(), getArgumentNodes());
        IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
        IoCall call = IoState.get(this).createCall(sender, sender, message, null, block, currentCoroutine);
        int argumentsCount = block.getNumArgs() + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, call, block, argumentsCount);
    }

    @Specialization
    protected final Object executeMethod(VirtualFrame frame, IoMethod method) {
        final Object receiver = getReceiver();
        final IoObject prototype;
        if (receiver instanceof IoObject) {
            prototype = (IoObject) receiver;
        } else {
            prototype = IoState.get(this).getPrototype(receiver);
        }
        IoLocals sender = IoState.get(this).createLocals(prototype, frame.materialize());
        IoMessage message = IoState.get(this).createMessage(getName(), getArgumentNodes());
        IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
        IoCall call = IoState.get(this).createCall(sender, receiver, message, null, method, currentCoroutine);
        int argumentsCount = method.getNumArgs() + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, call, method, argumentsCount);
    }

    @Fallback
    protected final Object executeOther(VirtualFrame frame, Object value) {
        return value;
    }

    @ExplodeLoop
    protected final Object execute(VirtualFrame frame, final Object receiver, IoInvokable invokable,
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
