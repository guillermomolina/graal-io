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
package org.iolanguage.nodes.slots;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.nodes.IoNode;
import org.iolanguage.runtime.IoState;
import org.iolanguage.runtime.objects.IoBlock;
import org.iolanguage.runtime.objects.IoCall;
import org.iolanguage.runtime.objects.IoCoroutine;
import org.iolanguage.runtime.objects.IoFunction;
import org.iolanguage.runtime.objects.IoInvokable;
import org.iolanguage.runtime.objects.IoLocals;
import org.iolanguage.runtime.objects.IoMessage;
import org.iolanguage.runtime.objects.IoMethod;
import org.iolanguage.runtime.objects.IoObject;

@NodeInfo(shortName = "()")
@NodeChild(value = "valueNode", type = ReadNode.class)
@NodeField(name = "argumentNodes", type = IoNode[].class)
public abstract class InvokeNode extends IoNode {

    public abstract ReadNode getValueNode();

    public abstract IoNode[] getArgumentNodes();

    public Object getReceiver() {
        Object receiver = getValueNode().getReceiver();;
        assert receiver != null;
        return receiver;
    }

    public IoObject getPrototype() {
        IoObject prototype = getValueNode().getPrototype();
        assert prototype != null;
        return prototype;
    }

    public TruffleString getName() {
        TruffleString name = getValueNode().getName();
        assert name != null;
        return name;
    }
    
    @Specialization
    public long readLong(VirtualFrame frame, long value) {
        return value;
    }

    @Specialization
    public double readDouble(VirtualFrame frame, double value) {
        return value;
    }

    @Specialization
    public boolean readBoolean(VirtualFrame frame, boolean value) {
        return value;
    }

    @Specialization
    protected final Object invokeFunction(VirtualFrame frame, IoFunction function) {
        final int argumentsCount = getArgumentNodes().length + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return doInvoke(frame, function, getReceiver(), argumentsCount);
    }

    @Specialization
    protected final Object invokeBlock(VirtualFrame frame, IoBlock block) {
        final Object target;
        if (block.getCallSlotIsUsed()) {
            IoLocals sender = block.getSender();
            IoMessage message = IoState.get(this).createMessage(getName(), getArgumentNodes());
            IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
            IoCall call = IoState.get(this).createCall(sender, sender, message, getPrototype(), block,
                    currentCoroutine);
            target = call;
        } else {
            target = getReceiver();
        }
        int argumentsCount = block.getNumArgs() + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return doInvoke(frame, block, target, argumentsCount);
    }

    @Specialization
    protected final Object invokeMethod(VirtualFrame frame, IoMethod method) {
        final Object target;
        if (method.getCallSlotIsUsed()) {
            IoLocals sender = IoState.get(this).createLocals(getReceiver(), frame.materialize());
            IoMessage message = IoState.get(this).createMessage(getName(), getArgumentNodes());
            IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
            IoCall call = IoState.get(this).createCall(sender, getReceiver(), message, getPrototype(), method, currentCoroutine);
            target = call;
        } else {
            target = getReceiver();
        }
        int argumentsCount = method.getNumArgs() + IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        return doInvoke(frame, method, target, argumentsCount);
    }

    @Fallback
    protected final Object getValue(VirtualFrame frame, Object value) {
        return value;
    }

    @ExplodeLoop
    protected final Object doInvoke(VirtualFrame frame, IoInvokable invokable, final Object receiver, final int argumentsCount) {
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
