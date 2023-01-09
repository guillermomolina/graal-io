/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;

/**
 * The IO type for a {@code nil} (i.e., undefined) value. In Truffle, it is generally discouraged
 * to use the Java {@code nil} value to represent the guest language {@code nil} value. It is not
 * possible to specialize on Java {@code nil} (since you cannot ask it for the Java class), and
 * there is always the danger of a spurious {@link NullPointerException}. Representing the guest
 * language {@code nil} as a singleton, as in {@link #SINGLETON this class}, is the recommended
 * practice.
 */
@ExportLibrary(InteropLibrary.class)
public final class IONil extends IOObject {

    /**
     * The canonical value to represent {@code nil} in IO.
     */
    public static final IONil SINGLETON = new IONil();
    private static final int IDENTITY_HASH = System.identityHashCode(SINGLETON);

    /**
     * Disallow instantiation from outside to ensure that the {@link #SINGLETON} is the only
     * instance.
     */
    private IONil() {
        super(IOPrototype.OBJECT);
    }

    /**
     * This method is, e.g., called when using the {@code nil} value in a string concatenation. So
     * changing it has an effect on IO programs.
     */
    @Override
    public String toString() {
        return "nil";
    }

    /**
     * {@link IONil} values are interpreted as null values by other languages.
     */
    @ExportMessage
    boolean isNull() {
        return true;
    }

    // @ExportMessage
    // protected static TriState isIdenticalOrUndefined(IONil receiver, Object other) {
    //     /*
    //      * IONil values are identical to other IONil values.
    //      */
    //     return TriState.valueOf(IONil.SINGLETON == other);
    // }

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doIONil(IONil receiver, IONil other) {
            return TriState.valueOf(IONil.SINGLETON == other);
        }

        @Fallback
        static TriState doOther(IONil receiver, Object other) {
            return TriState.valueOf(IONil.SINGLETON == other);
        }
    }

    @ExportMessage
    static int identityHashCode(IONil receiver) {
        /*
         * We do not use 0, as we want consistency with System.identityHashCode(receiver).
         */
        return IDENTITY_HASH;
    }

    @ExportMessage
    Object toDisplayString(boolean allowSideEffects) {
        return "nil";
    }
}
