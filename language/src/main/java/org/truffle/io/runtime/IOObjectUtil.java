package org.truffle.io.runtime;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.truffle.io.NotImplementedException;
import org.truffle.io.runtime.objects.IODate;
import org.truffle.io.runtime.objects.IOList;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IONil;
import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

public final class IOObjectUtil {
    private static int TO_STRING_MAX_DEPTH = 1;
    private static int TO_STRING_MAX_ELEMENTS = 10;
    private static boolean TO_STRING_INCLUDE_ARRAY_LENGTH = false;

    private IOObjectUtil() {
    }

    public static void putProperty(DynamicObject obj, Object key, Object value) {
        DynamicObjectLibrary.getUncached().put(obj, key, value);
    }

    public static Object getProperty(DynamicObject obj, Object key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(obj, key, null);
    }

    public static boolean hasProperty(DynamicObject obj, Object key) {
        return DynamicObjectLibrary.getUncached().containsKey(obj, key);
    }

    public static Object getOrDefault(IOObject obj, Object key, Object defaultValue) {
        List<IOObject> visitedProtos = new ArrayList<IOObject>();
        IOObject object = obj;
        while (!visitedProtos.contains(object)) {
            assert object != null;
            if (IOObjectUtil.hasProperty(object, key)) {
                return IOObjectUtil.getProperty(object, key);
            }
            visitedProtos.add(object);
            object = object.getPrototype();
        }
        return defaultValue;
    }

    public static boolean hasPrototype(IOObject obj, Object prototype) {
        List<IOObject> visitedProtos = new ArrayList<IOObject>();
        IOObject object = obj;
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

    public static String toString(IOObject object) {
        return toString(object, 0);
    }

    public static String toString(IOObject object, int depth) {
        if (object instanceof IOList) {
            return toString((IOList) object, depth);
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

    public static String toString(IOList object, int depth) {
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
        if (value == IONil.SINGLETON) {
            return "nil";
        }
        try {
            String asString =  InteropLibrary.getUncached().asString(value);
            if (value instanceof TruffleString) {
                return String.format("\"%s\"", asString);
            }
            return asString;
        } catch (UnsupportedMessageException e) {}
        if (value instanceof IOMethod) {
            return ((IOMethod)value).toString(depth);
        }
        if (value instanceof IOList) {
            return ((IOList)value).toString(depth);
        }
        if (value instanceof IOObject) {
            return ((IOObject)value).toString(depth);
        }
        return value.toString();
    }

    public static Date getDate(IODate date) {
        throw new NotImplementedException();
    }

}
