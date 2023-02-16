/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
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
package org.iolanguage.nodes.functions.sequence;

import java.util.regex.Pattern;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.NotImplementedException;
import org.iolanguage.nodes.expression.FunctionBodyNode;
import org.iolanguage.nodes.util.ToTruffleStringNode;
import org.iolanguage.runtime.IoState;
import org.iolanguage.runtime.objects.IoNil;

@NodeInfo(shortName = "split")
public abstract class SequenceSplitFunction extends FunctionBodyNode {

    static final int LIBRARY_LIMIT = 3;

    @Specialization(guards = "isString(receiver)")
    protected Object splitString(Object receiver, Object delimiters,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        String receiverString = toJavaStringNode.execute(toTruffleStringNode.execute(receiver));
        final String delimitersString;
        if (delimiters == IoNil.SINGLETON) {
            delimitersString = " ";
        } else {
            delimitersString = toJavaStringNode.execute(toTruffleStringNode.execute(delimiters));
        }

        return IoState.get(this).createList(receiverString.split(Pattern.quote(delimitersString)));
    }

    @Specialization(guards = "arrays.hasArrayElements(receiver)", limit = "LIBRARY_LIMIT")
    protected Object splitArray(Object receiver, Object delimiters,
            @CachedLibrary("receiver") InteropLibrary arrays) {
        throw new NotImplementedException();
    }

    protected boolean isString(Object a) {
        return a instanceof TruffleString;
    }
}
