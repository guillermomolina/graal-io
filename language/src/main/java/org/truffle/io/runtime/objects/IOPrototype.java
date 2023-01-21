/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.IOLanguage;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.Symbols;

@ExportLibrary(InteropLibrary.class)
public class IOPrototype extends IOObject {
    public static final TruffleString TYPE = Symbols.constant("type");

    public static final IOPrototype OBJECT = new IOPrototype(null, Symbols.OBJECT, (l, v) -> l.hasMembers(v) || l.isBoolean(v));
    public static final IOPrototype NUMBER = new IOPrototype(OBJECT, Symbols.NUMBER,
            (l, v) -> l.fitsInLong(v) || l.fitsInDouble(v));
    public static final IOPrototype SEQUENCE = new IOPrototype(OBJECT, Symbols.SEQUENCE, (l, v) -> l.isString(v));
    public static final IOPrototype BLOCK = new IOPrototype(OBJECT, Symbols.BLOCK, (l, v) -> l.isExecutable(v));
    public static final IOPrototype LIST = new IOPrototype(OBJECT, Symbols.LIST, (l, v) -> l.hasArrayElements(v));
    public static final IOPrototype DATE = new IOPrototype(OBJECT, Symbols.DATE, (l, v) -> v instanceof IODate);
    public static final IOPrototype SYSTEM = new IOPrototype(OBJECT, Symbols.SYSTEM, (l, v) -> v == IOPrototype.SYSTEM);
    public static final IOPrototype MESSAGE = new IOPrototype(OBJECT, Symbols.MESSAGE, (l, v) -> v == IOPrototype.MESSAGE);
    public static final IOPrototype CALL = new IOPrototype(OBJECT, Symbols.CALL, (l, v) -> v == IOPrototype.CALL);
    public static final IOPrototype LOCALS = new IOPrototype(OBJECT, Symbols.LOCALS, (l, v) -> v == IOPrototype.LOCALS);
    public static final IOPrototype COROUTINE = new IOPrototype(OBJECT, Symbols.COROUTINE, (l, v) -> v == IOPrototype.COROUTINE);

    @CompilationFinal(dimensions = 1)
    public static final IOPrototype[] PRECEDENCE = new IOPrototype[] { NUMBER, SEQUENCE, BLOCK, LIST, DATE, OBJECT };

    private final TypeCheck isInstance;

    @DynamicField
    private Object type;

    public IOPrototype(IOPrototype prototype, TruffleString type, TypeCheck isInstance) {
        super(prototype);
        this.isInstance = isInstance;
        setType(type);
    }

    public TruffleString getType() {
        return (TruffleString) IOObjectUtil.getOrDefaultUncached(this, TYPE);
    }

    public void setType(TruffleString type) {
        IOObjectUtil.putUncached(this, TYPE, type);
    }

    public boolean isInstance(Object value, InteropLibrary interop) {
        CompilerAsserts.partialEvaluationConstant(this);
        return isInstance.check(interop, value);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return IOLanguage.class;
    }

    @ExportMessage(name = "getMetaQualifiedName")
    @ExportMessage(name = "getMetaSimpleName")
    public Object getName() {
        return getType();
    }

    @ExportMessage(name = "toDisplayString")
    Object toDisplayString(boolean allowSideEffects) {
        return Symbols.fromJavaString(toString());
    }

    @Override
    public String toString(int depth) {
        if(depth == 0) {
            return String.format("%s_0x%08x: %s", getType(), hashCode(), IOObjectUtil.toString(this));
        }
        return String.format("%s_0x%08x", getType(), hashCode());
    }

    @ExportMessage
    static class IsMetaInstance {

        @Specialization(guards = "type == cachedType", limit = "3")
        static boolean doCached(IOPrototype type, Object value,
                @Cached("type") IOPrototype cachedType,
                @CachedLibrary("value") InteropLibrary valueLib) {
            return cachedType.isInstance.check(valueLib, value);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static boolean doGeneric(IOPrototype type, Object value) {
            return type.isInstance.check(InteropLibrary.getFactory().getUncached(), value);
        }
    }

    @FunctionalInterface
    interface TypeCheck {
        boolean check(InteropLibrary lib, Object value);
    }

}
