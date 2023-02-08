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
package org.iolanguage.runtime.objects;

import org.iolanguage.IoLanguage;
import org.iolanguage.runtime.IoObjectUtil;
import org.iolanguage.runtime.Symbols;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(InteropLibrary.class)
public class IoObject extends DynamicObject implements IoTruffleObject {
    protected static final int CACHE_LIMIT = 3;

    public static final Shape SHAPE = Shape.newBuilder().layout(IoObject.class).build();

    protected IoObject prototype;

    public IoObject() {
        super(SHAPE);
        this.prototype = IoPrototype.OBJECT;
    }

    public IoObject(final Shape shape, IoObject prototype) {
        super(shape);
        this.prototype = prototype;
    }

    public IoObject(IoObject prototype) {
        super(SHAPE);
        this.prototype = prototype;
    }

    public IoObject getPrototype() {
        return prototype;
    }

    public void setPrototype(final IoObject prototype) {
        this.prototype = prototype;
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
        static TriState doIOObject(IoObject receiver, IoObject other) {
            return TriState.valueOf(receiver == other);
        }

        @Fallback
        static TriState doOther(IoObject receiver, Object other) {
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
        return toString(0);
    }

    public String toString(int depth) {
        String string = String.format("Object_0x%08x", hashCode());
        if (depth == 0) {
            //string += ":" + IoObjectUtil.toString(this);
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
    void removeMember(String member,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary) throws UnknownIdentifierException {
        TruffleString memberTS = fromJavaStringNode.execute(member, IoLanguage.STRING_ENCODING);
        if (objectLibrary.containsKey(this, memberTS)) {
            objectLibrary.removeKey(this, memberTS);
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    Object getMembers(boolean includeInternal,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        return new Keys(objectLibrary.getKeyArray(this));
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    boolean existsMember(String member,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        return objectLibrary.containsKey(this, fromJavaStringNode.execute(member, IoLanguage.STRING_ENCODING));
    }

    @ExportMessage
    boolean isMemberInsertable(String member,
            @CachedLibrary("this") InteropLibrary receivers) {
        return !receivers.isMemberExisting(this, member);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Keys implements TruffleObject {

        private final Object[] keys;

        Keys(Object[] keys) {
            this.keys = keys;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return keys[(int) index];
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }
    }

    @ExportMessage
    Object readMember(String name,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode)
            throws UnknownIdentifierException {
        TruffleString nameTS = fromJavaStringNode.execute(name, IoLanguage.STRING_ENCODING);
        Object result = IoObjectUtil.getOrDefaultUncached(this, nameTS, null);
        if (result == null) {
            throw UnknownIdentifierException.create(name);
        }
        return result;
    }

    @ExportMessage
    void writeMember(String name, Object value,
            @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode) {
        IoObjectUtil.putUncached(this, fromJavaStringNode.execute(name, IoLanguage.STRING_ENCODING), value);
    }
}