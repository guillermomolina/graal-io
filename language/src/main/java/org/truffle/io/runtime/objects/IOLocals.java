/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
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
package org.truffle.io.runtime.objects;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.Shape;

public class IOLocals extends IOObject {

    public static final int CALL_ARGUMENT_INDEX = 0;
    public static final int TARGET_ARGUMENT_INDEX = 0;
    public static final int FIRST_PARAMETER_ARGUMENT_INDEX = 1;

    public static final int CALL_SLOT_INDEX = 0;
    public static final int FIRST_USER_SLOT_INDEX = 1;

    public static final Shape SHAPE = Shape.newBuilder().layout(IOLocals.class).build();

    private final MaterializedFrame frame;

    public IOLocals(final MaterializedFrame frame) {
        this(IOPrototype.OBJECT, frame);
    }

    public IOLocals(IOObject prototype, final MaterializedFrame frame) {
        super(SHAPE, prototype);
        this.frame = frame;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public IOCall getCall() {
        Object call = frame.getArguments()[CALL_ARGUMENT_INDEX];
        if (call instanceof IOCall) {
            assert call instanceof IOCall;
            return (IOCall) call;
        }
        return null; // caller is not a block 
    }

    public boolean hasLocal(final Object name) {
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();

        for (int i = 0; i < frameDescriptor.getNumberOfSlots(); i++) {
            if (name.equals(frameDescriptor.getSlotName(i))) {
                return true;
            }
        }
        return false;
    }

    public Object getLocal(final Object name) {
        return getLocalOrDefault(name, null);
    }

    public Object getLocalOrDefault(final Object name, final Object defaultValue) {
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();

        for (int i = 0; i < frameDescriptor.getNumberOfSlots(); i++) {
            if (name.equals(frameDescriptor.getSlotName(i))) {
                return frame.getValue(i);
            }
        }
        return defaultValue;
    }
}
