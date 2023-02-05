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

import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.IoNode;
import org.truffle.io.nodes.util.ToTruffleStringNode;
import org.truffle.io.runtime.objects.IoCall;
import org.truffle.io.runtime.objects.IoLocals;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;

@NodeChild(value = "senderNode", type = IoNode.class)
@NodeChild(value = "nameNode", type = IoNode.class)
public abstract class ReadRemoteSlotNode extends ReadNode {
    private int slot = -1;

    protected int getSlot() {
        if(slot < 0) {
            throw new NotImplementedException();
        }
        return slot;
    }

    @Specialization(guards = "ctx.isLong(getSlot())")
    protected long readLong(VirtualFrame frame, IoLocals sender, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Shared("all") @Bind("determineContext(frame, sender, toTruffleStringNode.execute(name))") final MaterializedFrame ctx)
            throws FrameSlotTypeException {
        return ctx.getLong(getSlot());
    }

    @Specialization(guards = "ctx.isDouble(getSlot())")
    protected double readDouble(VirtualFrame frame, IoLocals sender, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Shared("all") @Bind("determineContext(frame, sender, toTruffleStringNode.execute(name))") final MaterializedFrame ctx)
            throws FrameSlotTypeException {
        return ctx.getDouble(getSlot());
    }

    @Specialization(guards = "ctx.isBoolean(getSlot())")
    protected boolean readBoolean(VirtualFrame frame, IoLocals sender, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Shared("all") @Bind("determineContext(frame, sender, toTruffleStringNode.execute(name))") final MaterializedFrame ctx)
            throws FrameSlotTypeException {
        return ctx.getBoolean(getSlot());
    }

    @Specialization(replaces = { "readLong", "readDouble", "readBoolean" })
    protected Object readObject(VirtualFrame frame, IoLocals sender, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Shared("all") @Bind("determineContext(frame, sender, toTruffleStringNode.execute(name))") final MaterializedFrame ctx)
            throws FrameSlotTypeException {
        if (!ctx.isObject(getSlot())) {
            CompilerDirectives.transferToInterpreter();
            Object result = ctx.getValue(getSlot());
            ctx.setObject(getSlot(), result);
            return result;
        }
        return ctx.getObject(getSlot());
    }

    @ExplodeLoop
    protected final MaterializedFrame determineContext(final VirtualFrame frame, IoLocals sender, TruffleString name) {
        Object argument = frame.getArguments()[IoLocals.CALL_ARGUMENT_INDEX];
        if (!(argument instanceof IoCall)) {
            // sender is not a block
            throw new NotImplementedException();
        }
        IoCall call = (IoCall) argument;
        IoLocals locals = call.getSender();

        int i = getContextLevel() - 1;
        while (i > 0) {
            call = (IoCall) locals.getCall();
            if (call == null) {
                throw new NotImplementedException();
            }
            locals = call.getSender();
            i--;
        }

        // Graal needs help here to see that this is always a MaterializedFrame
        // so, we record explicitly a class profile
        return frameType.profile(locals.getFrame());

    }
}
