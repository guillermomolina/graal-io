package org.truffle.io.runtime;

import java.util.ArrayList;
import java.util.List;

import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

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

    public static TruffleString toString(IOObject object, int depth) throws UnsupportedMessageException, InvalidArrayIndexException, UnknownIdentifierException {
        CompilerAsserts.neverPartOfCompilation();
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
        TruffleStringBuilder sb = TruffleStringBuilder.create(TruffleString.Encoding.UTF_8);
        TruffleStringBuilder.AppendCharUTF16Node.getUncached().execute(sb, '{');
        for (long i = 0; i < keyCount; i++) {
            if (i > 0) {
                TruffleStringBuilder.AppendCharUTF16Node.getUncached().execute(sb, ',');;
                if (i >= 10) {
                    String str = "...";
                    TruffleStringBuilder.AppendJavaStringUTF16Node.getUncached().execute(sb, str, 0, str.length());
                    break;
                }
            }
            Object key = keysInterop.readArrayElement(keys, i);
            assert InteropLibrary.getUncached().isString(key);
            String stringKey = (String)key;
            //Object value = objInterop.readMember(object, stringKey);
            TruffleStringBuilder.AppendJavaStringUTF16Node.getUncached().execute(sb, stringKey, 0, stringKey.length());
            String str = ", ";
            TruffleStringBuilder.AppendJavaStringUTF16Node.getUncached().execute(sb, str, 0, str.length());
            //Strings.builderAppend(sb, toDisplayStringInner(value, allowSideEffects, format, depth, object));
        }
        TruffleStringBuilder.AppendCharUTF16Node.getUncached().execute(sb, '}');
        return TruffleStringBuilder.ToStringNode.getUncached().execute(sb);
    }

}
