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
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;

public final class IoLocals extends IoObject {

    public static final int CALL_ARGUMENT_INDEX = 0;
    public static final int TARGET_ARGUMENT_INDEX = 0;
    public static final int FIRST_PARAMETER_ARGUMENT_INDEX = 1;

    public static final int CALL_SLOT_INDEX = 0;
    public static final int FIRST_USER_SLOT_INDEX = 1;

    private final MaterializedFrame frame;

    public IoLocals(final MaterializedFrame frame) {
        this(IoPrototype.OBJECT, frame);
    }

    public IoLocals(IoObject prototype, final MaterializedFrame frame) {
        super(prototype);
        this.frame = frame;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public IoCall getCall() {
        Object call = frame.getArguments()[CALL_ARGUMENT_INDEX];
        if (call instanceof IoCall) {
            assert call instanceof IoCall;
            return (IoCall) call;
        }
        return null; // caller is not a block 
    }

    public boolean hasLocal(final Object name) {
        return getLocalSlotIndex(name) != null;
    }

    public Integer getLocalSlotIndex(final Object name) {
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();

        for (int i = 0; i < frameDescriptor.getNumberOfSlots(); i++) {
            if (name.equals(frameDescriptor.getSlotName(i))) {
                return i;
            }
        }
        return null;
    }

    public Object getLocal(final Object name) {
        return getLocalOrDefault(name, null);
    }

    public Object getLocalOrDefault(final Object name, final Object defaultValue) {
        Integer slotIndex = getLocalSlotIndex(name);
        if (slotIndex != null) {
            return frame.getValue(slotIndex);
        }
        return defaultValue;
    }

    public Object setLocal(final Object name, final Object value) {
        Integer slotIndex = getLocalSlotIndex(name);
        if (slotIndex != null) {
            if (value instanceof Boolean) {
                frame.getFrameDescriptor().setSlotKind(slotIndex, FrameSlotKind.Boolean);
                frame.setBoolean(slotIndex, (Boolean) value);
            } else if (value instanceof Long) {
                frame.getFrameDescriptor().setSlotKind(slotIndex, FrameSlotKind.Long);
                frame.setLong(slotIndex, (Long) value);
            } else if (value instanceof Double) {
                frame.getFrameDescriptor().setSlotKind(slotIndex, FrameSlotKind.Double);
                frame.setDouble(slotIndex, (Double) value);
            } else {
                frame.getFrameDescriptor().setSlotKind(slotIndex, FrameSlotKind.Object);
                frame.setObject(slotIndex, value);
            }
            return value;
        }
        return null;
    }
}
