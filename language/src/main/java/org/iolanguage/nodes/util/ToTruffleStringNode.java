/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.iolanguage.nodes.util;

import org.iolanguage.IoLanguage;
import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.nodes.IoTypes;
import org.iolanguage.runtime.Symbols;
import org.iolanguage.runtime.objects.IoNil;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * The node to normalize any value to an IO value. This is useful to reduce the number of values
 * expression nodes need to expect.
 */
@TypeSystemReference(IoTypes.class)
@GenerateUncached
public abstract class ToTruffleStringNode extends Node {

    static final int LIMIT = 5;

    private static final TruffleString SYMBOL_FOREIGN_OBJECT = Symbols.constant("[foreign object]");

    public abstract TruffleString execute(Object value);

    @Specialization
    protected static TruffleString fromNull(IoNil value) {
        return Symbols.NIL;
    }

    @Specialization
    protected static TruffleString fromString(String value,
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return fromJavaStringNode.execute(value, IoLanguage.STRING_ENCODING);
    }

    @Specialization
    protected static TruffleString fromTruffleString(TruffleString value) {
        return value;
    }

    @Specialization
    protected static TruffleString fromBoolean(boolean value) {
        return value ? Symbols.TRUE : Symbols.FALSE;
    }

    @Specialization
    @TruffleBoundary
    protected static TruffleString fromLong(long value,
            @Cached TruffleString.FromLongNode fromLongNode) {
        return fromLongNode.execute(value, IoLanguage.STRING_ENCODING, true);
    }

    @Specialization
    @TruffleBoundary
    protected static TruffleString fromDouble(double value,
                    @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return fromJavaStringNode.execute(String.valueOf(value), IoLanguage.STRING_ENCODING);
    }

    @Specialization(limit = "LIMIT")
    protected static TruffleString fromInterop(Object value,
            @CachedLibrary("value") InteropLibrary interop,
            @Cached TruffleString.FromLongNode fromLongNode,
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        try {
            if (interop.fitsInLong(value)) {
                return fromLongNode.execute(interop.asLong(value), IoLanguage.STRING_ENCODING, true);
            } else if (interop.fitsInDouble(value)) {
                return fromJavaStringNode.execute(String.valueOf(interop.asDouble(value)), IoLanguage.STRING_ENCODING);
            } else if (interop.isString(value)) {
                return fromJavaStringNode.execute(interop.asString(value), IoLanguage.STRING_ENCODING);
            } else if (interop.isNull(value)) {
                return Symbols.NIL;
            } else {
                return SYMBOL_FOREIGN_OBJECT;
            }
        } catch (UnsupportedMessageException e) {
            throw new ShouldNotBeHereException(e);
        }
    }
}
