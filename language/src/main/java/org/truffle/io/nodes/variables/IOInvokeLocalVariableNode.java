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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.expression.IOInvokeNode;
import org.truffle.io.nodes.interop.NodeObjectDescriptor;
import org.truffle.io.runtime.objects.IOInvokable;

@NodeField(name = "slot", type = int.class)
@NodeField(name = "argumentNodes", type = IOExpressionNode[].class)
public abstract class IOInvokeLocalVariableNode extends IOExpressionNode {

    protected abstract int getSlot();

    protected abstract IOExpressionNode[] getArgumentNodes();

    @Specialization(guards = "frame.isLong(getSlot())")
    protected long invokeLong(VirtualFrame frame) {
        return frame.getLong(getSlot());
    }

    @Specialization(guards = "frame.isBoolean(getSlot())")
    protected boolean invokeBoolean(VirtualFrame frame) {
        return frame.getBoolean(getSlot());
    }

    @Specialization(replaces = { "invokeLong", "invokeBoolean" })
    protected Object invokeObject(VirtualFrame frame) {
        Object value;
        if (!frame.isObject(getSlot())) {
            CompilerDirectives.transferToInterpreter();
            value = frame.getValue(getSlot());
            frame.setObject(getSlot(), value);
        } else {
            value = frame.getObject(getSlot());
        }
        if (value instanceof IOInvokable) {
            final IOInvokeNode invokeNode = new IOInvokeNode((IOInvokable) value, frame.getObject(0),
                    getArgumentNodes());
            value = invokeNode.executeGeneric(frame);
        }
        return value;
    }

    // @Override
    // public boolean hasTag(Class<? extends Tag> tag) {
    //     return tag == ReadVariableTag.class || super.hasTag(tag);
    // }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor
                .readVariable((TruffleString) getRootNode().getFrameDescriptor().getSlotName(getSlot()));
    }

}
