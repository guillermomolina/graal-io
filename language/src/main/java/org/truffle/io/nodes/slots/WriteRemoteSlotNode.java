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

import org.truffle.io.nodes.expression.ExpressionNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "valueNode", type = ExpressionNode.class)
public abstract class WriteRemoteSlotNode extends RemoteSlotNode {

    @Specialization(guards = "isLongOrIllegal(frame)")
    protected long writeLong(VirtualFrame frame, long value) {
        final MaterializedFrame context = determineContext(frame);
        context.getFrameDescriptor().setSlotKind(getSlot(), FrameSlotKind.Long);
        context.setLong(getSlot(), value);
        return value;
    }

    @Specialization(guards = "isBooleanOrIllegal(frame)")
    protected boolean writeBoolean(VirtualFrame frame, boolean value) {
        final MaterializedFrame context = determineContext(frame);
        context.getFrameDescriptor().setSlotKind(getSlot(), FrameSlotKind.Boolean);
        context.setBoolean(getSlot(), value);
        return value;
    }

    @Specialization(replaces = { "writeLong", "writeBoolean" })
    protected Object write(VirtualFrame frame, Object value) {
        final MaterializedFrame context = determineContext(frame);
        context.getFrameDescriptor().setSlotKind(getSlot(), FrameSlotKind.Object);
        context.setObject(getSlot(), value);
        return value;
    }

    public abstract void executeWrite(VirtualFrame frame, Object value);

    protected boolean isLongOrIllegal(VirtualFrame frame) {
        final FrameSlotKind kind = determineContext(frame).getFrameDescriptor().getSlotKind(getSlot());
        return kind == FrameSlotKind.Long || kind == FrameSlotKind.Illegal;
    }

    protected boolean isBooleanOrIllegal(VirtualFrame frame) {
        final FrameSlotKind kind = determineContext(frame).getFrameDescriptor().getSlotKind(getSlot());
        return kind == FrameSlotKind.Boolean || kind == FrameSlotKind.Illegal;
    }
}
