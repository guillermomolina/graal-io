/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.nodes.sequences;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.expression.InvokeNode;
import org.truffle.io.nodes.literals.LongLiteralNode;
import org.truffle.io.nodes.literals.MessageLiteralNode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.Symbols;
import org.truffle.io.runtime.UndefinedNameException;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IOObject;

@NodeInfo(shortName = "atPut")
@NodeChild("receiverNode")
@NodeChild("indexNode")
@NodeField(name = "valueNode", type = ExpressionNode.class)
public abstract class SequenceAtPutNode extends ExpressionNode {

    static final TruffleString AT_PUT = Symbols.constant("atPut");
    static final int LIBRARY_LIMIT = 3;

    protected abstract ExpressionNode getValueNode();
    
    @Specialization(guards = "arrays.hasArrayElements(receiver)", limit = "LIBRARY_LIMIT")
    protected Object atArrayPut(VirtualFrame frame, Object receiver, Object index,
                    @CachedLibrary("receiver") InteropLibrary arrays,
                    @CachedLibrary("index") InteropLibrary numbers) {
        try {
            Object value = getValueNode().executeGeneric(frame);
            arrays.writeArrayElement(receiver, numbers.asLong(index), value);
        } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
            throw UndefinedNameException.undefinedField(this, index);
        }
        return receiver;
    }

    @Specialization(limit = "LIBRARY_LIMIT")
    protected Object atIOObjectPut(VirtualFrame frame, IOObject receiver, Object index,
            @CachedLibrary("index") InteropLibrary numbers) {
        try {
            Object member = IOObjectUtil.getOrDefault(receiver, AT_PUT, null);
            return getOrInvoke(frame, receiver, numbers.asLong(index), member);
        } catch (UnsupportedMessageException e) {
            throw UndefinedNameException.undefinedField(this, AT_PUT);
        }
    }

    @Specialization(limit = "LIBRARY_LIMIT")
    protected Object atObjectPut(VirtualFrame frame, Object receiver, Object index,
            @CachedLibrary("index") InteropLibrary numbers) {
        try {
            IOObject prototype = IOState.get(this).getPrototype(receiver);
            Object member = IOObjectUtil.getOrDefault(prototype, AT_PUT, null);
            return getOrInvoke(frame, receiver, numbers.asLong(index), member);
        } catch (UnsupportedMessageException e) {
            throw UndefinedNameException.undefinedField(this, AT_PUT);
        }
    }

    protected final Object getOrInvoke(VirtualFrame frame, final Object receiver, long index, final Object member) {
        if (member == null) {
            throw UndefinedNameException.undefinedField(this, AT_PUT);
        }
        if (member instanceof IOInvokable) {
            final IOInvokable invokable = (IOInvokable) member;
            final ExpressionNode[] argumentNodes = new ExpressionNode[2];
            argumentNodes[0] = new LongLiteralNode(index);
            argumentNodes[1] = getValueNode();
            final MessageLiteralNode messageNode = new MessageLiteralNode(AT_PUT, argumentNodes);
            final InvokeNode invokeNode = new InvokeNode(invokable, receiver, messageNode);
            Object result = invokeNode.executeGeneric(frame);
            return result;
        }
        return member;
    }
}
