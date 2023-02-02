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
package org.truffle.io.runtime;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.truffle.io.NotImplementedException;
import org.truffle.io.runtime.objects.IoDate;
import org.truffle.io.runtime.objects.IoFalse;
import org.truffle.io.runtime.objects.IoList;
import org.truffle.io.runtime.objects.IoLocals;
import org.truffle.io.runtime.objects.IoNil;
import org.truffle.io.runtime.objects.IoObject;
import org.truffle.io.runtime.objects.IoPrototype;
import org.truffle.io.runtime.objects.IoTrue;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

public final class IoObjectUtil {
    private static int TO_STRING_MAX_DEPTH = 1;
    private static int TO_STRING_MAX_ELEMENTS = 10;
    private static boolean TO_STRING_INCLUDE_ARRAY_LENGTH = false;

    private IoObjectUtil() {
    }

    public static boolean hasSlot(Object obj, Object key) {
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        return hasSlotUncached(objectOrProto, key);
    }

    public static boolean hasSlotUncached(IoObject obj, Object key) {
        return hasSlot(DynamicObjectLibrary.getUncached(), obj, key);
    }

    public static boolean hasSlot(DynamicObjectLibrary lib, IoObject obj, Object key) {
        if (obj instanceof IoLocals) {
            IoLocals locals = (IoLocals) obj;
            return locals.hasLocal(key);
        }
        return lib.containsKey(obj, key);
    }

    public static Object getOrDefault(Object obj, Object key) {
        return getOrDefault(obj, key, null);
    }

    public static Object getOrDefault(Object obj, Object key, Object defaultValue) {
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        return getOrDefaultUncached(objectOrProto, key, defaultValue);
    }

    public static Object getOrDefaultUncached(IoObject obj, Object key) {
        return getOrDefault(DynamicObjectLibrary.getUncached(), obj, key, null);
    }

    public static Object getOrDefaultUncached(IoObject obj, Object key, Object defaultValue) {
        return getOrDefault(DynamicObjectLibrary.getUncached(), obj, key, defaultValue);
    }

    public static Object getOrDefault(DynamicObjectLibrary lib, IoObject obj, Object key) {
        return getOrDefault(obj, key, null);
    }

    public static Object getOrDefault(DynamicObjectLibrary lib, IoObject obj, Object key, Object defaultValue) {
        if (obj instanceof IoLocals) {
            IoLocals locals = (IoLocals) obj;
            return locals.getLocalOrDefault(key, defaultValue);
        }
        return lib.getOrDefault(obj, key, defaultValue);
    }

    protected static IoObject asIoObject(Object obj) {
        if (obj instanceof IoObject) {
            return (IoObject) obj;
        }
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(obj);
        if (interop.isNull(obj)) {
            return IoNil.SINGLETON;
        }
        if (interop.isBoolean(obj)) {
            if ((Boolean) obj == Boolean.TRUE) {
                return IoTrue.SINGLETON;
            }
            return IoFalse.SINGLETON;
        }
        return null;
    }

    public static IoObject getPrototype(Object obj) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(obj);
        if (obj instanceof String) {
            return IoPrototype.SEQUENCE;
        }
        if (obj instanceof TruffleString) {
            return IoPrototype.SEQUENCE;
        }
        if (interop.fitsInLong(obj)) {
            return IoPrototype.NUMBER;
        }
        if (interop.fitsInDouble(obj)) {
            return IoPrototype.NUMBER;
        }
        IoObject asIoObject = asIoObject(obj);
        if (asIoObject != null) {
            return asIoObject.getPrototype();
        }
        if (interop.hasMembers(obj)) {
            return IoPrototype.OBJECT;
        }
        return null;
    }

    public static boolean hasPrototype(Object obj, Object prototype) {
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        if (objectOrProto == prototype) {
            return true;
        }
        return hasPrototype(objectOrProto, prototype);
    }

    public static boolean hasPrototype(IoObject obj, Object prototype) {
        List<IoObject> visitedProtos = new ArrayList<IoObject>();
        IoObject object = obj;
        while (!visitedProtos.contains(object)) {
            assert object != null;
            if (object == prototype) {
                return true;
            }
            visitedProtos.add(object);
            object = object.getPrototype();
        }
        return false;
    }

    public static String toString(Object object) {
        if (object instanceof IoObject) {
            return toString((IoObject) object);
        }
        return toStringInner(object, 0);
    }

    public static String toString(IoObject object) {
        return toString(object, 0);
    }

    public static String toString(IoObject object, int depth) {
        if (object instanceof IoList) {
            return toString((IoList) object, depth);
        }
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder sb = new StringBuilder();
        try {
            InteropLibrary objInterop = InteropLibrary.getFactory().getUncached(object);
            assert objInterop.hasMembers(object);
            Object keys = objInterop.getMembers(object);
            InteropLibrary keysInterop = InteropLibrary.getFactory().getUncached(keys);
            long keyCount = keysInterop.getArraySize(keys);
            if (keyCount == 0 || depth >= TO_STRING_MAX_DEPTH) {
                return "";
            }
            String spaces = "  ";
            spaces = spaces.repeat(depth + 1);
            sb.append("\n");
            sb.append(spaces);
            for (long i = 0; i < keyCount; i++) {
                if (i > 0) {
                    sb.append("\n");
                    sb.append(spaces);
                    if (i >= TO_STRING_MAX_ELEMENTS) {
                        sb.append("...");
                        break;
                    }
                }
                String stringKey = null;
                try {
                    Object key = keysInterop.readArrayElement(keys, i);
                    assert InteropLibrary.getUncached().isString(key);
                    stringKey = InteropLibrary.getUncached().asString(key);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    stringKey = "<UNKNOWN>";
                }
                sb.append(stringKey);
                sb.append(" = ");
                Object value = null;
                try {
                    value = objInterop.readMember(object, stringKey);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    value = "<UNKNOWN>";
                }
                sb.append(toStringInner(value, depth + 1));
            }
        } catch (UnsupportedMessageException e) {
        }
        return sb.toString();
    }

    public static String toString(IoList object, int depth) {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder sb = new StringBuilder();
        try {
            InteropLibrary interop = InteropLibrary.getFactory().getUncached(object);
            assert interop.hasArrayElements(object);
            long size = interop.getArraySize(object);
            if (size == 0) {
                return "";
            } else if (depth >= TO_STRING_MAX_DEPTH) {
                return String.format("<%d>", size);
            }
            boolean topLevel = depth == 0;
            if (topLevel && size >= 2 && TO_STRING_INCLUDE_ARRAY_LENGTH) {
                sb.append('<');
                sb.append(size);
                sb.append('>');
            }
            for (long i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(", ");
                    if (i >= TO_STRING_MAX_ELEMENTS) {
                        sb.append("...");
                        break;
                    }
                }
                Object value = null;
                try {
                    value = interop.readArrayElement(object, i);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    value = "<UNKNOWN>";
                }
                sb.append(toStringInner(value, depth + 1));
            }
        } catch (UnsupportedMessageException e) {
        }
        return sb.toString();
    }

    public static String toStringInner(Object value, int depth) {
        CompilerAsserts.neverPartOfCompilation();
        if (value == IoNil.SINGLETON) {
            return "nil";
        }
        try {
            String asString = InteropLibrary.getUncached().asString(value);
            /*if (value instanceof TruffleString) {
                return String.format("\"%s\"", asString);
            }*/
            return asString;
        } catch (UnsupportedMessageException e) {
        }
        if (value instanceof IoObject) {
            return ((IoObject) value).toString(depth);
        }
        if (value instanceof Double) {
            Double doubleValue = (Double) value;
            if (Double.valueOf(doubleValue.intValue()).compareTo(doubleValue) == 0) {
                return String.format("%d", doubleValue.intValue());
            } 
            if (doubleValue.doubleValue() > Integer.MAX_VALUE || doubleValue.doubleValue() < Integer.MIN_VALUE) {
                return String.format("%e", doubleValue.doubleValue());
            } else {
                String stringValue = String.format("%.16f", doubleValue.doubleValue());

                int l = stringValue.length() - 1;
                while (l > 0) {
                    if (stringValue.charAt(l) == '0') {
                        l--;
                        continue;
                    }
                    if (stringValue.charAt(l) == '.') {
                        l--;
                        break;
                    }
                    break;
                }
                String output = stringValue.substring(0, l + 1);
                return output;

            }
        }
        if (value instanceof Long) {
            Long longValue = (Long) value;
            if (Long.valueOf(longValue.intValue()).compareTo(longValue) == 0) {
                return String.format("%d", longValue.intValue());
            }
            return String.format("%e", longValue.doubleValue());
        }
        return value.toString();
    }

    public static Date getDate(IoDate date) {
        throw new NotImplementedException();
    }

    public static IoObject lookupSlot(Object obj, Object key) {
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        return lookupSlotUncached(objectOrProto, key);
    }

    public static IoObject lookupSlotUncached(IoObject obj, Object key) {
        if (obj instanceof IoLocals) {
            if (((IoLocals) obj).hasLocal(key)) {
                return (IoLocals) obj;
            }
        }
        return lookupSlot(DynamicObjectLibrary.getUncached(), obj, key);
    }

    public static IoObject lookupSlot(DynamicObjectLibrary lib, IoObject obj, Object key) {
        List<IoObject> visitedProtos = new ArrayList<IoObject>();
        IoObject object = obj;
        while (!visitedProtos.contains(object)) {
            assert object != null;
            if (hasSlot(lib, object, key)) {
                return object;
            }
            visitedProtos.add(object);
            object = object.getPrototype();
        }
        return null;
    }

    public static Object updateSlot(Object obj, Object key, Object value) {
        if (obj instanceof IoLocals) {
            IoLocals locals = (IoLocals) obj;
            if (locals.hasLocal(key)) {
                locals.setLocal(key, value);
                return value;
            }
        }
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        DynamicObjectLibrary lib = DynamicObjectLibrary.getUncached();
        IoObject slotOwner = lookupSlot(lib, objectOrProto, key);
        if (slotOwner != null) {
            put(lib, slotOwner, key, value);
            return value;
        }
        return null;
    }

    public static Object setSlot(Object obj, Object key, Object value) {
        if (obj instanceof IoLocals) {
            IoLocals locals = (IoLocals) obj;
            return locals.setLocal(key, value);
        }
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        putUncached(objectOrProto, key, value);
        return value;
    }

    public static void put(Object obj, Object key, Object value) {
        IoObject objectOrProto = asIoObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        putUncached(objectOrProto, key, value);
    }

    public static void putUncached(IoObject obj, Object key, Object value) {
        put(DynamicObjectLibrary.getUncached(), obj, key, value);
    }

    public static void put(DynamicObjectLibrary lib, IoObject obj, Object key, Object value) {
        if (obj instanceof IoLocals) {
            IoLocals locals = (IoLocals) obj;
            locals.setLocal(key, value);
        } else {
            lib.put(obj, key, value);
        }
    }

}
