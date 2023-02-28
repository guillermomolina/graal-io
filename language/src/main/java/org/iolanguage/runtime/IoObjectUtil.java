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
package org.iolanguage.runtime;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.IoLanguage;
import org.iolanguage.NotImplementedException;
import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.runtime.objects.IoBaseObject;
import org.iolanguage.runtime.objects.IoDate;
import org.iolanguage.runtime.objects.IoFalse;
import org.iolanguage.runtime.objects.IoLocals;
import org.iolanguage.runtime.objects.IoNil;
import org.iolanguage.runtime.objects.IoObject;
import org.iolanguage.runtime.objects.IoPrototype;
import org.iolanguage.runtime.objects.IoTrue;

public final class IoObjectUtil {
    // private static int TO_STRING_MAX_DEPTH = 1;
    // private static int TO_STRING_MAX_ELEMENTS = 10;
    // private static boolean TO_STRING_INCLUDE_ARRAY_LENGTH = false;

    private IoObjectUtil() {
    }

    public static boolean hasSlot(Object object, Object key) {
        IoBaseObject objectOrProto = asIoBaseObject(object);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(object);
        }
        return hasSlot(objectOrProto, key);
    }

    public static boolean hasSlot(IoBaseObject object, Object key) {
        if (object instanceof IoObject) {
            return hasSlot((IoObject) object, key);
        }
        if (object instanceof IoLocals) {
            return ((IoLocals) object).hasLocal(key);
        }
        throw new ShouldNotBeHereException();
    }

    public static boolean hasSlot(IoObject object, Object key) {
        return hasSlot(DynamicObjectLibrary.getUncached(), object, key);
    }

    public static boolean hasSlot(DynamicObjectLibrary lib, IoObject object, Object key) {
        return lib.containsKey(object, key);
    }

    public static Object getOrDefault(Object object, Object key) {
        return getOrDefault(object, key, null);
    }

    public static Object getOrDefault(Object object, Object key, Object defaultValue) {
        IoBaseObject objectOrProto = asIoBaseObject(object);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(object);
        }
        return getOrDefault(objectOrProto, key, defaultValue);
    }

    public static Object getOrDefault(IoBaseObject objectOrProto, Object key, Object defaultValue) {
        if (objectOrProto instanceof IoObject) {
            return getOrDefault((IoObject) objectOrProto, key, defaultValue);
        }
        if (objectOrProto instanceof IoLocals) {
            return ((IoLocals) objectOrProto).getLocalOrDefault(key, defaultValue);
        }
        throw new ShouldNotBeHereException();
    }

    public static Object getOrDefault(IoObject object, Object key) {
        return getOrDefault(DynamicObjectLibrary.getUncached(), object, key, null);
    }

    public static Object getOrDefault(IoObject object, Object key, Object defaultValue) {
        return getOrDefault(DynamicObjectLibrary.getUncached(), object, key, defaultValue);
    }

    public static Object getOrDefault(DynamicObjectLibrary lib, IoObject object, Object key) {
        return getOrDefault(lib, object, key, null);
    }

    public static Object getOrDefault(DynamicObjectLibrary lib, IoObject object, Object key,
            Object defaultValue) {
        return lib.getOrDefault(object, key, defaultValue);
    }

    protected static IoBaseObject asIoBaseObject(Object object) {
        if (object instanceof IoBaseObject) {
            return (IoBaseObject) object;
        }
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(object);
        if (interop.isNull(object)) {
            return IoNil.SINGLETON;
        }
        if (interop.isBoolean(object)) {
            if ((Boolean) object == Boolean.TRUE) {
                return IoTrue.SINGLETON;
            }
            return IoFalse.SINGLETON;
        }
        return null;
    }

    public static IoBaseObject getPrototype(Object object) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(object);
        if (object instanceof String) {
            return IoPrototype.IMMUTABLE_SEQUENCE;
        }
        if (object instanceof TruffleString) {
            return IoPrototype.IMMUTABLE_SEQUENCE;
        }
        if (interop.fitsInLong(object)) {
            return IoPrototype.NUMBER;
        }
        if (interop.fitsInDouble(object)) {
            return IoPrototype.NUMBER;
        }
        IoBaseObject ioBaseObject = asIoBaseObject(object);
        if (ioBaseObject != null) {
            return ioBaseObject.getPrototype();
        }
        if (interop.hasMembers(object)) {
            return IoPrototype.OBJECT;
        }
        return null;
    }

    public static boolean hasPrototype(Object object, Object prototype) {
        IoBaseObject objectOrProto = asIoBaseObject(object);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(object);
        }
        if (objectOrProto == prototype) {
            return true;
        }
        return hasPrototype(objectOrProto, prototype);
    }

    public static boolean hasPrototype(IoBaseObject obj, Object prototype) {
        List<IoBaseObject> visitedProtos = new ArrayList<IoBaseObject>();
        IoBaseObject object = obj;
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
        if (object instanceof IoBaseObject) {
            return ((IoBaseObject) object).toString();
        }
        return toStringInner(object);
    }

    public static String toStringInner(Object object) {
        CompilerAsserts.neverPartOfCompilation();
        if (object == IoNil.SINGLETON) {
            return "nil";
        }
        try {
            String asString = InteropLibrary.getUncached().asString(object);
            /*if (object instanceof TruffleString) {
                return String.format("\"%s\"", asString);
            }*/
            return asString;
        } catch (UnsupportedMessageException e) {
        }
        if (object instanceof IoBaseObject) {
            return ((IoBaseObject) object).toStringInner();
        }
        if (object instanceof Double) {
            return doubleToString((Double) object);
        }
        if (object instanceof BigInteger) {
            return bigIntegerToString((BigInteger) object);
        }
        if (object instanceof Long) {
            return longToString((Long) object);
        }
        if (object instanceof IoLocals) {
            throw new NotImplementedException();
        }
        return object.toString();
    }

    public static String bigIntegerToString(BigInteger value) {
        if(!IoLanguage.getState().getStateOptions().numberLegacyFormat) {
            return value.toString();
        }
        throw new NotImplementedException();
    }

    public static String doubleToString(Double value) {
        if(!IoLanguage.getState().getStateOptions().numberLegacyFormat) {
            return String.valueOf(value);
        }
        if (Double.valueOf(value.intValue()).compareTo(value) == 0) {
            return String.format("%d", value.intValue());
        }
        if (value.doubleValue() > Integer.MAX_VALUE || value.doubleValue() < Integer.MIN_VALUE) {
            return String.format("%e", value.doubleValue());
        } else {
            String stringValue = String.format("%.16f", value.doubleValue());

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

    public static String longToString(Long value) {
        if(!IoLanguage.getState().getStateOptions().numberLegacyFormat) {
            return String.valueOf(value);
        }
        if (Long.valueOf(value.intValue()).compareTo(value) == 0) {
            return String.format("%d", value.intValue());
        }
        return String.format("%e", value.doubleValue());
    }

    public static Date getDate(IoDate date) {
        throw new NotImplementedException();
    }

    public static IoBaseObject lookupSlot(Object object, Object key) {
        IoBaseObject objectOrProto = asIoBaseObject(object);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(object);
        }
        return lookupSlot(objectOrProto, key);
    }

    public static IoBaseObject lookupSlot(IoBaseObject obj, Object key) {
        List<IoBaseObject> visitedProtos = new ArrayList<IoBaseObject>();
        IoBaseObject object = obj;
        while (!visitedProtos.contains(object)) {
            assert object != null;
            if (hasSlot(object, key)) {
                return object;
            }
            visitedProtos.add(object);
            object = object.getPrototype();
        }
        return null;
    }

    public static Object updateSlot(Object obj, Object key, Object value) {
        IoBaseObject objectOrProto = asIoBaseObject(obj);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(obj);
        }
        return updateSlot(objectOrProto, key, value);
    }

    public static Object updateSlot(IoBaseObject object, Object key, Object value) {
        IoBaseObject slotOwner = lookupSlot(object, key);
        if (slotOwner != null) {
            put(slotOwner, key, value);
            return value;
        }
        return null;
    }

    public static Object put(Object object, Object key, Object value) {
        IoBaseObject objectOrProto = asIoBaseObject(object);
        if (objectOrProto == null) {
            objectOrProto = getPrototype(object);
        }
        return put(objectOrProto, key, value);
    }

    public static Object put(IoBaseObject object, Object key, Object value) {
        if (object instanceof IoObject) {
            return put((IoObject) object, key, value);
        }
        if (object instanceof IoLocals) {
            return ((IoLocals) object).setLocal(key, value);
        }
        throw new ShouldNotBeHereException();
    }

    public static Object put(IoObject object, Object key, Object value) {
        return put(DynamicObjectLibrary.getUncached(), object, key, value);
    }

    public static Object put(DynamicObjectLibrary lib, IoObject object, Object key, Object value) {
        lib.put(object, key, value);
        return value;
    }

}
