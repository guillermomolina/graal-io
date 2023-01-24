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
package org.truffle.io.nodes.slots;

import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.IONode;
import org.truffle.io.nodes.interop.NodeObjectDescriptor;
import org.truffle.io.runtime.objects.IOCall;
import org.truffle.io.runtime.objects.IOLocals;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@NodeField(name = "contextLevel", type = int.class)
@NodeField(name = "slot", type = int.class)
public abstract class RemoteSlotNode extends IONode {

    protected abstract int getContextLevel();

    protected abstract int getSlot();

    private static final ValueProfile frameType = ValueProfile.createClassProfile();

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == ReadVariableTag.class || super.hasTag(tag);
    }

    protected TruffleString getSlotName(final MaterializedFrame ctx) {
        return (TruffleString) ctx.getFrameDescriptor().getSlotName(getSlot());
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor
                .readMember((TruffleString) getRootNode().getFrameDescriptor().getSlotName(getSlot()));
    }

    public final boolean accessesOuterContext() {
        return getContextLevel() > 0;
    }

    @ExplodeLoop
    protected final MaterializedFrame determineContext(final VirtualFrame frame) {
        Object argument = frame.getArguments()[IOLocals.CALL_ARGUMENT_INDEX];
        if (!(argument instanceof IOCall)) {
            // sender is not a block
            throw new NotImplementedException();
        }
        IOCall call = (IOCall) argument;
        IOLocals locals = call.getSymbolSender();

        int i = getContextLevel() - 1;
        while (i > 0) {
            call = (IOCall) locals.getCall();           
            if (call == null) {
                throw new NotImplementedException();
            }
            locals = call.getSymbolSender();
            i--;
        }

        // Graal needs help here to see that this is always a MaterializedFrame
        // so, we record explicitly a class profile
        return frameType.profile(locals.getFrame());

    }

}
