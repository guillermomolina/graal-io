/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.builtins.object;

import org.truffle.io.NotImplementedException;
import org.truffle.io.builtins.IOBuiltinNode;
import org.truffle.io.runtime.IOState;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "slotNames")
@ImportStatic(IOState.class)
public abstract class ObjectSlotNamesBuiltin extends IOBuiltinNode {

    @Specialization(guards = "objInterop.hasMembers(receiver)")
    @TruffleBoundary
    public Object slotNames(Object receiver,
            @CachedLibrary(limit = "3") InteropLibrary objInterop) {
        try {
            assert objInterop.hasMembers(receiver);
            Object keys = objInterop.getMembers(receiver);
            InteropLibrary keysInterop = InteropLibrary.getFactory().getUncached(keys);
            long keyCount = keysInterop.getArraySize(keys);
            Object[] objectSlotNames = new Object[(int) keyCount];
            for (int i = 0; i < keyCount; i++) {
                try {
                    objectSlotNames[i] = keysInterop.readArrayElement(keys, i);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    throw new NotImplementedException();
                }
            }
            //Object[] frameSlotNames = getFrameSlotNames(frame);
            return IOState.get(this).createList(objectSlotNames);
        } catch (UnsupportedMessageException e) {
        }
        return IOState.get(this).createList(new Object[0]);
    }

    @Specialization
    public Object slotNames(Object receiver) {
        throw new NotImplementedException();
    }

    public Object[] getFrameSlotNames(VirtualFrame frame) {
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        int count = frameDescriptor.getNumberOfSlots();
        Object[] slotNames = new Object[count];
        for (int i = 0; i < count; i++) {
            slotNames[i] = frameDescriptor.getSlotName(i);
        }
        return slotNames;
    }

}