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

import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.expression.IOInvokeMethodNode;
import org.truffle.io.nodes.util.IOToTruffleStringNode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.IOUndefinedNameException;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.strings.TruffleString;

@NodeChild("recevierNode")
@NodeChild("nameNode")
@NodeField(name = "argumentNodes", type = IOExpressionNode[].class)
public abstract class IOInvokePropertyNode extends IOExpressionNode {

    protected abstract IOExpressionNode[] getArgumentNodes();

    @Specialization
    protected Object invokeLong(VirtualFrame frame, long receiver, Object name,
            @Cached IOToTruffleStringNode toTruffleStringNode) {
        IOObject prototype = IOState.get(this).getPrototype(receiver);
        TruffleString nameTS = toTruffleStringNode.execute(name);
        Object value = IOObjectUtil.getOrDefault(prototype, nameTS, null);
        if (value == null) {
            throw IOUndefinedNameException.undefinedProperty(toTruffleStringNode, nameTS);
        }
        if (value instanceof IOMethod) {
            value = invokeMethod(frame, (IOMethod) value, receiver);
        }
        return value;
    }

    @Specialization
    protected Object invokeBoolean(VirtualFrame frame, boolean receiver, Object name,
            @Cached IOToTruffleStringNode toTruffleStringNode) {
        TruffleString nameTS = toTruffleStringNode.execute(name);
        IOObject prototype = IOState.get(this).getPrototype(receiver);
        Object value = IOObjectUtil.getOrDefault(prototype, nameTS, null);
        if (value == null) {
            throw IOUndefinedNameException.undefinedProperty(toTruffleStringNode, nameTS);
        }
        if (value instanceof IOMethod) {
            value = invokeMethod(frame, (IOMethod) value, receiver);
        }
        return value;
    }

    @Specialization
    protected Object invokeIOObject(VirtualFrame frame, IOObject receiver, Object name,
            @Cached IOToTruffleStringNode toTruffleStringNode) {
        TruffleString nameTS = toTruffleStringNode.execute(name);
        Object value = IOObjectUtil.getOrDefault(receiver, nameTS, null);
        if (value == null) {
            value = IOObjectUtil.getOrDefault(receiver, nameTS, null);
            throw IOUndefinedNameException.undefinedProperty(toTruffleStringNode, nameTS);
        }
        if (value instanceof IOMethod) {
            value = invokeMethod(frame, (IOMethod) value, receiver);
        }
        return value;
    }

    @Specialization
    protected Object invokeGeneric(VirtualFrame frame, Object receiver, Object name,
            @Cached IOToTruffleStringNode toTruffleStringNode) {
        IOObject prototype = IOState.get(this).getPrototype(receiver);
        TruffleString nameTS = toTruffleStringNode.execute(name);
        Object value = IOObjectUtil.getOrDefault(prototype, nameTS, null);
        if (value == null) {
            throw IOUndefinedNameException.undefinedProperty(toTruffleStringNode, nameTS);
        }
        if (value instanceof IOMethod) {
            value = invokeMethod(frame, (IOMethod) value, receiver);
        }
        return value;
    }

    protected final Object invokeMethod(VirtualFrame frame, final IOMethod method, final Object receiver) {
        final IOInvokeMethodNode invokeMethodNode = new IOInvokeMethodNode(method, receiver, getArgumentNodes());
        Object result = invokeMethodNode.executeGeneric(frame);
        return result;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.CallTag.class) {
            return true;
        }
        return super.hasTag(tag);
    }

}
