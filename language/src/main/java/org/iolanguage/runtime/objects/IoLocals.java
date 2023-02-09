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
package org.iolanguage.runtime.objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

import org.iolanguage.IoLanguage;
import org.iolanguage.runtime.IoObjectUtil;
import org.iolanguage.runtime.Symbols;

@ExportLibrary(InteropLibrary.class)
public final class IoLocals implements IoBaseObject {

    public static final int CALL_ARGUMENT_INDEX = 0;
    public static final int TARGET_ARGUMENT_INDEX = 0;
    public static final int FIRST_PARAMETER_ARGUMENT_INDEX = 1;

    public static final int CALL_SLOT_INDEX = 0;
    public static final int FIRST_USER_SLOT_INDEX = 1;

    protected IoBaseObject prototype;
    private final MaterializedFrame frame;

    public IoLocals(final MaterializedFrame frame) {
        this(IoPrototype.OBJECT, frame);
    }

    public IoLocals(IoBaseObject prototype, final MaterializedFrame frame) {
        this.prototype = prototype;
        this.frame = frame;
    }

    public IoBaseObject getPrototype() {
        return prototype;
    }

    public void setPrototype(final IoBaseObject prototype) {
        this.prototype = prototype;
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

    public Object[] getSlotNames() {
        final FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        int count = frameDescriptor.getNumberOfSlots();
        Object[] slotNames = new Object[count];
        for (int i = 0; i < count; i++) {
            slotNames[i] = frameDescriptor.getSlotName(i);
        }
        return slotNames;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return IoLanguage.class;
    }

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doIOObject(IoLocals receiver, IoLocals other) {
            return TriState.valueOf(receiver == other);
        }

        @Fallback
        static TriState doOther(IoLocals receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    int identityHashCode() {
        return System.identityHashCode(this);
    }

    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() {
        return getPrototype();
    }

    @Override
    public String toString() {
        return toStringInner();
    }

    public String toStringInner() {
        String string = String.format("Object_0x%08x", hashCode());
        final FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        int count = frameDescriptor.getNumberOfSlots();
        for (int i = 0; i < count; i++) {
           // TruffleString slotName = (TruffleString)frameDescriptor.getSlotName(i);
           // Object slotValue = frame.getValue(i);
        }
        return string;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(boolean allowSideEffects) {
        return Symbols.fromJavaString(toString());
    }

    @ExportMessage
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage(name = "getMetaQualifiedName")
    @ExportMessage(name = "getMetaSimpleName")
    public Object getName() {
        return Symbols.OBJECT;
    }

    @ExportMessage
    boolean isMetaInstance(Object instance) {
        return IoObjectUtil.hasPrototype(instance, this);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) {
        return new IoList(getSlotNames());
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    boolean existsMember(String member,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode) {
        return hasLocal(fromJavaStringNode.execute(member, IoLanguage.STRING_ENCODING));
    }

    @ExportMessage
    boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    Object readMember(String name,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode)
            throws UnknownIdentifierException {
        TruffleString nameTS = fromJavaStringNode.execute(name, IoLanguage.STRING_ENCODING);
        Object result = getLocalOrDefault(nameTS, null);
        if (result == null) {
            throw UnknownIdentifierException.create(name);
        }
        return result;
    }

    @ExportMessage
    void writeMember(String name, Object value,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode) throws UnknownIdentifierException {
        TruffleString nameTS = fromJavaStringNode.execute(name, IoLanguage.STRING_ENCODING);
        Object result = setLocal(nameTS, value);
        if (result == null) {
            throw UnknownIdentifierException.create(name);
        }
    }
}
