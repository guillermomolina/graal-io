package org.truffle.io.runtime;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.NotImplementedException;
import org.truffle.io.runtime.objects.IOObject;

public final class IOObjectUtil {
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

    /*public static TruffleString toString(IOObject object, int depth) {
        TruffleStringBuilder sb = TruffleStringBuilder.create(TruffleString.Encoding.UTF_8);
        CompilerAsserts.neverPartOfCompilation();
        try {
    
            InteropLibrary objInterop = InteropLibrary.getFactory().getUncached(object);
            assert objInterop.hasMembers(object);
            Object keys = objInterop.getMembers(object);
            InteropLibrary keysInterop = InteropLibrary.getFactory().getUncached(keys);
            long keyCount = keysInterop.getArraySize(keys);
            if (keyCount == 0) {
                return IOSymbols.constant("{}");
            } else if (depth >= 3) {
                return IOSymbols.constant("{...}");
            }
            Trufflesb.append('{');
            for (long i = 0; i < keyCount; i++) {
                if (i > 0) {
                    Trufflesb.append(',');
                    ;
                    if (i >= 10) {
                        String str = "...";
                        TruffleStringBuilder.AppendJavaStringUTF16Node.getUncached().execute(sb, str, 0, str.length());
                        break;
                    }
                }
                String stringKey = null;
                try {
                    Object key = keysInterop.readArrayElement(keys, i);
                    assert InteropLibrary.getUncached().isString(key);
                    stringKey = (String) key;
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    stringKey = "<UNKNOWN>";
                }
                //Object value = objInterop.readMember(object, stringKey);
                TruffleStringBuilder.AppendJavaStringUTF16Node.getUncached().execute(sb, stringKey, 0,
                        stringKey.length());
                String str = ", ";
                TruffleStringBuilder.AppendJavaStringUTF16Node.getUncached().execute(sb, str, 0, str.length());
                //Strings.builderAppend(sb, toDisplayStringInner(value, allowSideEffects, format, depth, object));
            }
            Trufflesb.append('}');
        } catch (UnsupportedMessageException e) {
        }
        return TruffleStringBuilder.ToStringNode.getUncached().execute(sb);
    }*/

    public static String toString(IOObject object) {
        return toString(object, 0);
    }

    public static String toString(IOObject object, int depth) {
        StringBuilder sb = new StringBuilder();
        CompilerAsserts.neverPartOfCompilation();
        try {

            InteropLibrary objInterop = InteropLibrary.getFactory().getUncached(object);
            assert objInterop.hasMembers(object);
            Object keys = objInterop.getMembers(object);
            InteropLibrary keysInterop = InteropLibrary.getFactory().getUncached(keys);
            long keyCount = keysInterop.getArraySize(keys);
            if (keyCount == 0) {
                return "{}";
            } else if (depth >= 1) {
                return "{...}";
            }
            sb.append('{');
            for (long i = 0; i < keyCount; i++) {
                if (i > 0) {
                    sb.append(',');
                    ;
                    if (i >= 10) {
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
                sb.append(": ");
                Object value = null;
                try {
                    value = objInterop.readMember(object, stringKey);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    value = "<UNKNOWN>";
                }
                sb.append(toStringInner(value, depth + 1));
            }
            sb.append('}');
        } catch (UnsupportedMessageException e) {
        }
        return sb.toString();
    }

    public static String toStringInner(Object value, int depth) {
        CompilerAsserts.neverPartOfCompilation();
        if (value instanceof IOObject) {
            return toString((IOObject) value, depth);
        }
        if (value instanceof IOObject) {
            return toString((IOObject) value, depth);
        }
        if (value instanceof TruffleString) {
            try {
                return InteropLibrary.getUncached().asString(value);
            } catch (UnsupportedMessageException e) {
                return "<UNKNOWN>";
            }
        }
        throw new NotImplementedException();
    }
}
