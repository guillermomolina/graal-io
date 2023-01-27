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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;

import org.truffle.io.IOLanguage;
import org.truffle.io.runtime.Symbols;

@ExportLibrary(InteropLibrary.class)
public abstract class IOBaseObject implements TruffleObject {

    protected IOObject prototype;

    public IOBaseObject() {
        this.prototype = IOPrototype.OBJECT;
    }

    public IOBaseObject(IOObject prototype) {
        this.prototype = prototype;
    }

    public IOObject getPrototype() {
        return prototype;
    }

    public void setPrototype(final IOObject prototype) {
        this.prototype = prototype;
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
        static TriState doIOBaseObject(IOBaseObject receiver, IOBaseObject other) {
            return TriState.valueOf(receiver == other);
        }

        @Fallback
        static TriState doOther(IOBaseObject receiver, Object other) {
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
        // if (depth == 0) {
        //     string += ":" + IOBaseObjectUtil.toString(this);
        // }
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
    boolean isMetaInstance(Object prototype) {
        return getPrototype() == prototype;
    }
}
