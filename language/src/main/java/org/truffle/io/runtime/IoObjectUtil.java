package org.truffle.io.runtime;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.IoTypes;
import org.truffle.io.runtime.objects.IoDate;
import org.truffle.io.runtime.objects.IoList;
import org.truffle.io.runtime.objects.IoLocals;
import org.truffle.io.runtime.objects.IoNil;
import org.truffle.io.runtime.objects.IoObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

public final class IoObjectUtil {
    private static int TO_STRING_MAX_DEPTH = 1;
    private static int TO_STRING_MAX_ELEMENTS = 10;
    private static boolean TO_STRING_INCLUDE_ARRAY_LENGTH = false;

    private IoObjectUtil() {
    }

    public static void putUncached(DynamicObject obj, Object key, Object value) {
        put(DynamicObjectLibrary.getUncached(), obj, key, value);
    }

    public static void put(DynamicObjectLibrary lib, DynamicObject obj, Object key, Object value) {
        lib.put(obj, key, value);
    }

    public static boolean containsKeyUncached(DynamicObject obj, Object key) {
        return containsKey(DynamicObjectLibrary.getUncached(), obj, key);
    }

    public static boolean containsKey(DynamicObjectLibrary lib, DynamicObject obj, Object key) {
        return lib.containsKey(obj, key);
    }

    public static Object getOrDefaultUncached(DynamicObject obj, Object key) {
        return getOrDefault(DynamicObjectLibrary.getUncached(), obj, key, null);
    }

    public static Object getOrDefaultUncached(DynamicObject obj, Object key, Object defaultValue) {
        return getOrDefault(DynamicObjectLibrary.getUncached(), obj, key, defaultValue);
    }

    public static Object getOrDefault(DynamicObjectLibrary lib, DynamicObject obj, Object key) {
        return lib.getOrDefault(obj, key, null);
    }

    public static Object getOrDefault(DynamicObjectLibrary lib, DynamicObject obj, Object key, Object defaultValue) {
        return lib.getOrDefault(obj, key, defaultValue);
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
            String asString =  InteropLibrary.getUncached().asString(value);
            if (value instanceof TruffleString) {
                return String.format("\"%s\"", asString);
            }
            return asString;
        } catch (UnsupportedMessageException e) {}
        /*if (value instanceof IOBlock) {
            return ((IOBlock)value).toString(depth);
        }
        if (value instanceof IOMethod) {
            return ((IOMethod)value).toString(depth);
        }
        if (value instanceof IOList) {
            return ((IOList)value).toString(depth);
        }*/
        if (value instanceof IoObject) {
            return ((IoObject)value).toString(depth);
        }
        return value.toString();
    }

    public static Date getDate(IoDate date) {
        throw new NotImplementedException();
    }
/*
    public static Object getSlotOrDefault(DynamicObject obj, Object key) {
        return getSlotOrDefault(DynamicObjectLibrary.getUncached(), obj, key, null);
    }

    public static Object getSlotOrDefault(DynamicObjectLibrary lib, DynamicObject obj, Object key, Object defaultValue) {
        if(obj instanceof IoObject) {
            if(obj instanceof IoLocals) {
                Object value = ((IoLocals)obj).getLocal(key);
                if (value != null) {
                    return value;
                }
            }
            IoObject prototype = findPrototypeWithSlot(lib, (IoObject)obj, key);
            if(prototype == null) {
                return defaultValue;
            }
            return lib.getOrDefault(prototype, key, defaultValue);
        }
        return lib.getOrDefault(obj, key, defaultValue);
    }
 
    protected static Object getSlotOrDefault(DynamicObjectLibrary lib, IoObject obj, Object key, Object defaultValue) {
        List<IoObject> visitedProtos = new ArrayList<IoObject>();
        IoObject object = obj;
        while (!visitedProtos.contains(object)) {
            assert object != null;
            Object value = getOrDefault(lib, object, key);
            if (value != null) {
                return value;
            }
            visitedProtos.add(object);
            object = object.getPrototype();
        }
        return defaultValue;
    }
*/
    public static IoObject lookupSlot(Object obj, Object key) {
        if(obj instanceof IoLocals) {
            throw new NotImplementedException();
        }
        if(obj instanceof IoObject) {
            return lookupSlotUncached((IoObject)obj, key);
        }
        return lookupSlot(IoTypes.getPrototype(obj), key);
    }

    public static IoObject lookupSlotUncached(IoObject obj, Object key) {      
        return lookupSlot(DynamicObjectLibrary.getUncached(), obj, key);
    }

    public static IoObject lookupSlot(DynamicObjectLibrary lib, IoObject obj, Object key) {      
        List<IoObject> visitedProtos = new ArrayList<IoObject>();
        IoObject object = obj;
        while (!visitedProtos.contains(object)) {
            assert object != null;
            containsKey(lib, object, key);
            if (containsKey(lib, object, key)) {
                return object;
            }
            visitedProtos.add(object);
            object = object.getPrototype();
        }
        return null;
    }

}
