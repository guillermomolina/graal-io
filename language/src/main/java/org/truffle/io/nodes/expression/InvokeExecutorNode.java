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

import org.truffle.io.nodes.IONode;
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

@NodeField(name = "receiver", type = Object.class)
@NodeField(name = "message", type = IOMessage.class)
@NodeChild("invokableNode")
public abstract class InvokeExecutorNode extends IONode {

    @Specialization(guards = "!values.isNull(invokable)", limit = "3")
    protected final Object executeNull(VirtualFrame frame, final Object receiver, final IOMessage message,
            Object invokable, @CachedLibrary("invokable") InteropLibrary values) {
        throw UndefinedNameException.undefinedField(this, message.getName());
    }

    @Specialization
    protected final Object executeFunction(VirtualFrame frame, final Object receiver,
            final IOMessage message, IOFunction function) {
        final int argumentsCount = message.getArgumentNodes().length + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, receiver, function, message, argumentsCount);
    }

    @Specialization
    protected final Object executeBlock(VirtualFrame frame, final Object receiver,
            final IOMessage message, IOBlock block) {
        assert receiver instanceof IOObject;
        IOLocals sender = block.getSender();
        IOCoroutine currentCoroutine = IOState.get(this).getCurrentCoroutine();
        IOCall call = IOState.get(this).createCall(sender, sender, message, null, block, currentCoroutine);
        int argumentsCount = block.getNumArgs() + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, call, block, message, argumentsCount);
    }

    @Specialization
    protected final Object executeMethod(VirtualFrame frame, final Object receiver,
            final IOMessage message, IOMethod method) {
        assert receiver instanceof IOObject;
        IOLocals sender = IOState.get(this).createLocals((IOObject) receiver, frame.materialize());
        IOCoroutine currentCoroutine = IOState.get(this).getCurrentCoroutine();
        IOCall call = IOState.get(this).createCall(sender, receiver, message, null, method, currentCoroutine);
        int argumentsCount = method.getNumArgs() + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return execute(frame, call, method, message, argumentsCount);
    }

    @Fallback
    protected final Object executeOther(VirtualFrame frame, final Object receiver, final IOMessage message,
            Object value) {
        return value;
    }

    @ExplodeLoop
    protected final Object execute(VirtualFrame frame, final Object receiver, IOInvokable invokable,
            final IOMessage message, final int argumentsCount) {
        IONode[] argumentNodes = message.getArgumentNodes();
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
