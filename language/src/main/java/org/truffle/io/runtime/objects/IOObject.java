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
package org.truffle.io.runtime.objects;

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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

import org.truffle.io.IOLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.interop.IOType;

/**
 * Represents an IO object.
 *
 * This class defines operations that can be performed on IO Objects. While we could define all
 * these operations as individual AST nodes, we opted to define those operations by using
 * {@link com.oracle.truffle.api.library.Library a Truffle library}, or more concretely the
 * {@link InteropLibrary}. This has several advantages, but the primary one is that it allows IO
 * objects to be used in the interoperability message protocol, i.e. It allows other languages and
 * tooio to operate on IO objects without necessarily knowing they are IO objects.
 *
 * IO Objects are essentially instances of {@link DynamicObject} (objects whose members can be
 * dynamically added and removed). We also annotate the class with {@link ExportLibrary} with value
 * {@link InteropLibrary InteropLibrary.class}. This essentially ensures that the build system and
 * runtime know that this class specifies the interop messages (i.e. operations) that IO can do on
 * {@link IOObject} instances.
 *
 * @see ExportLibrary
 * @see ExportMessage
 * @see InteropLibrary
 */
@ExportLibrary(InteropLibrary.class)
public class IOObject extends DynamicObject {
    protected static final int CACHE_LIMIT = 3;
//    public static final Shape SHAPE = Shape.newBuilder().layout(IOObject.class).addConstantProperty(IOSymbols.PROTO, null, 0).build();
    public static final Shape SHAPE = Shape.newBuilder().layout(IOObject.class).build();

    protected IOObject prototype;

    public static Object getOrDefault(IOObject obj, Object key, Object defaultValue) {
       IOObject object = obj;
        while (object != null) {
            if (IOObjectUtil.hasProperty(object, key)) {
                return IOObjectUtil.getProperty(object, key);
            }
            object = object.getPrototype();
        }
        return defaultValue;
    }

    public IOObject() {
        super(SHAPE);
        this.prototype = null;
    }

    public IOObject(IOObject prototype) {
        super(SHAPE);
        this.prototype = prototype;
    }

    public IOObject getPrototype() {
        //return (IOObject)IOObjectUtil.getProperty(this, IOSymbols.PROTO);
        return prototype;
    }

    public void setPrototype(final IOObject prototype) {
        this.prototype = prototype;
        //IOObjectUtil.putProperty(this, IOSymbols.PROTO, prototype);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return IOLanguage.class;
    }

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doIOObject(IOObject receiver, IOObject other) {
            return TriState.valueOf(receiver == other);
        }

        @Fallback
        static TriState doOther(IOObject receiver, Object other) {
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
        return IOType.OBJECT;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(boolean allowSideEffects) {
        String string = String.format("Object_0x%X:", hashCode());
        return string;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    void removeMember(String member,
                    @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) throws UnknownIdentifierException {
        TruffleString memberTS = fromJavaStringNode.execute(member, IOLanguage.STRING_ENCODING);
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
        return objectLibrary.containsKey(this, fromJavaStringNode.execute(member, IOLanguage.STRING_ENCODING));
    }

    @ExportMessage
    boolean isMemberInsertable(String member,
                    @CachedLibrary("this") InteropLibrary receivers) {
        return !receivers.isMemberExisting(this, member);
    }

    @ExportMessage
    public final boolean isMemberInvocable(String member,
                    @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        if(existsMember(member, fromJavaStringNode, objectLibrary)) {
            throw new NotImplementedException();
        }
        return false;
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

    /**
     * {@link DynamicObjectLibrary} provides the polymorphic inline cache for reading properties.
     */
    @ExportMessage
    Object readMember(String name,
                    @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) throws UnknownIdentifierException {
        Object result = IOObject.getOrDefault(this, (Object)fromJavaStringNode.execute(name, IOLanguage.STRING_ENCODING), null);
        if (result == null) {
            /* Property does not exist. */
            throw UnknownIdentifierException.create(name);
        }
        return result;
    }

    /**
     * {@link DynamicObjectLibrary} provides the polymorphic inline cache for writing properties.
     */
    @ExportMessage
    void writeMember(String name, Object value,
                    @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        objectLibrary.put(this, fromJavaStringNode.execute(name, IOLanguage.STRING_ENCODING), value);
    }
 
    @ExportMessage
    public final Object invokeMember(String name, Object[] args,
                    @Cached @Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) throws UnsupportedMessageException, UnknownIdentifierException {
        throw new NotImplementedException();
        //Object result = objectLibrary
        //.execute(this, fromJavaStringNode.execute(name, IOLanguage.STRING_ENCODING), args);
        //return exportNode.execute(result);
    }   
}
