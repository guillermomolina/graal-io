/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.runtime;

import org.truffle.io.IOLanguage;
import org.truffle.io.nodes.root.IOEvalRootNode;
import org.truffle.io.nodes.root.IOFunctionRootNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public final class IOSymbols {

    public static final TruffleString EMPTY_STRING = constant("");
    public static final TruffleString NULL = constant("NULL");
    public static final TruffleString NULL_LC = constant("null");
    public static final TruffleString SELF = constant("self");
    public static final TruffleString OBJECT = constant("Object");
    public static final TruffleString NUMBER = constant("Number");
    public static final TruffleString STRING = constant("String");
    public static final TruffleString LIST = constant("List");
    public static final TruffleString BLOCK = constant("Block");
    public static final TruffleString MESSAGE = constant("Message");
    public static final TruffleString CALL = constant("Call");

    public static TruffleString constant(String s) {
        return fromJavaString(s);
    }

    public static TruffleString fromJavaString(String s) {
        return TruffleString.fromJavaStringUncached(s, IOLanguage.STRING_ENCODING);
    }

    public static TruffleString fromObject(Object o) {
        if (o == null) {
            return NULL_LC;
        }
        if (o instanceof TruffleString) {
            return (TruffleString) o;
        }
        return fromJavaString(o.toString());
    }

    public static TruffleString getIORootName(RootNode rootNode) {
        if (rootNode instanceof IOFunctionRootNode) {
            return ((IOFunctionRootNode) rootNode).getTSName();
        } else if (rootNode instanceof IOEvalRootNode) {
            return IOEvalRootNode.getTSName();
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
