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
package org.truffle.io.nodes.variables;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.expression.InvokeNode;
import org.truffle.io.nodes.literals.MessageLiteralNode;
import org.truffle.io.nodes.util.ToTruffleStringNode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.UndefinedNameException;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IOObject;

@NodeChild("recevierNode")
@NodeChild("nameNode")
@NodeField(name = "argumentNodes", type = ExpressionNode[].class)
public abstract class InvokePropertyNode extends ExpressionNode {

    protected abstract ExpressionNode[] getArgumentNodes();

    @Specialization
    protected Object invokeIOObject(VirtualFrame frame, IOObject receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        TruffleString nameTS = toTruffleStringNode.execute(name);
        return getOrInvoke(frame, receiver, nameTS, receiver);
    }

    @Specialization
    protected Object invokeGeneric(VirtualFrame frame, Object receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        IOObject prototype = IOState.get(this).getPrototype(receiver);
        TruffleString nameTS = toTruffleStringNode.execute(name);
        return getOrInvoke(frame, receiver, nameTS, prototype);
    }

    protected final Object getOrInvoke(VirtualFrame frame, final Object receiver, final TruffleString nameTS,
            final IOObject prototype) {
        Object value = IOObjectUtil.getOrDefault(prototype, nameTS, null);
        if (value == null) {
            throw UndefinedNameException.undefinedProperty(this, nameTS);
        }
        if (value instanceof IOInvokable) {
            final IOInvokable invokable = (IOInvokable) value;
            MessageLiteralNode messageNode = new MessageLiteralNode(nameTS, getArgumentNodes());
            final InvokeNode invokeNode = new InvokeNode(invokable, receiver, messageNode);
            Object result = invokeNode.executeGeneric(frame);
            return result;
        }
        return value;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.CallTag.class) {
            return true;
        }
        return super.hasTag(tag);
    }

}
