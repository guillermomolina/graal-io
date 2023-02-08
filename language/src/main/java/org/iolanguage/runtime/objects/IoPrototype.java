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
package org.iolanguage.runtime.objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.IoLanguage;
import org.iolanguage.runtime.IoObjectUtil;
import org.iolanguage.runtime.Symbols;

@ExportLibrary(InteropLibrary.class)
public class IoPrototype extends IoDynamicObject {
    public static final TruffleString SYMBOL_TYPE = Symbols.constant("type");

    public static final IoPrototype OBJECT = new IoPrototype(null, Symbols.OBJECT);
    public static final IoPrototype NUMBER = new IoPrototype(OBJECT, Symbols.NUMBER);
    public static final IoPrototype SEQUENCE = new IoPrototype(OBJECT, Symbols.SEQUENCE);
    public static final IoPrototype BLOCK = new IoPrototype(OBJECT, Symbols.BLOCK);
    public static final IoPrototype LIST = new IoPrototype(OBJECT, Symbols.LIST);
    public static final IoPrototype DATE = new IoPrototype(OBJECT, Symbols.DATE);
    public static final IoPrototype SYSTEM = new IoPrototype(OBJECT, Symbols.SYSTEM);
    public static final IoPrototype MESSAGE = new IoPrototype(OBJECT, Symbols.MESSAGE);
    public static final IoPrototype CALL = new IoPrototype(OBJECT, Symbols.CALL);
    public static final IoPrototype COROUTINE = new IoPrototype(OBJECT, Symbols.COROUTINE);
    public static final IoPrototype EXCEPTION = new IoPrototype(OBJECT, Symbols.EXCEPTION);

    @CompilationFinal(dimensions = 1)
    public static final IoPrototype[] PRECEDENCE = new IoPrototype[] { NUMBER, SEQUENCE, BLOCK, LIST, DATE, OBJECT };

    @DynamicField
    private Object type;

    public IoPrototype(IoPrototype prototype, TruffleString type) {
        super(prototype);
        setSymbolType(type);
    }

    public TruffleString getSymbolType() {
        return (TruffleString) IoObjectUtil.getOrDefault(this, SYMBOL_TYPE);
    }

    public void setSymbolType(TruffleString type) {
        IoObjectUtil.put(this, SYMBOL_TYPE, type);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return IoLanguage.class;
    }

    @ExportMessage(name = "getMetaQualifiedName")
    @ExportMessage(name = "getMetaSimpleName")
    public Object getName() {
        return getSymbolType();
    }

    @ExportMessage(name = "toDisplayString")
    Object toDisplayString(boolean allowSideEffects) {
        return Symbols.fromJavaString(toString());
    }

    @Override
    public String toString(int depth) {
        if(depth == 0) {
            return String.format("%s_0x%08x: %s", getSymbolType(), hashCode(), IoObjectUtil.toString(this));
        }
        return String.format("%s_0x%08x", getSymbolType(), hashCode());
    }
}
