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
package org.iolanguage.nodes.functions.object;

import org.iolanguage.IoLanguage;
import org.iolanguage.nodes.functions.FunctionBodyNode;
import org.iolanguage.runtime.IoState;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

@NodeInfo(shortName = "doString")
public abstract class ObjectDoStringFunction extends FunctionBodyNode {
    static final int LIMIT = 2;

    @Specialization(guards = "stringsEqual(equalNodeCode, cachedCode, code)", limit = "LIMIT")
    public Object evalCached(Object self, TruffleString code,
                    @Cached("code") TruffleString cachedCode,
                    @Cached("create(parse(code))") DirectCallNode callNode,
                    @Cached TruffleString.EqualNode equalNodeCode) {
        return callNode.call(self);
    }

    @TruffleBoundary
    @Specialization(replaces = "evalCached")
    public Object evalUncached(Object self, TruffleString code) {
        return parse(code).call(self);
    }

    protected CallTarget parse(TruffleString code) {
        final Source source = Source.newBuilder(IoLanguage.ID, code.toJavaStringUncached(), "(eval)").build();
        return IoState.get(this).parse(source);
    }

    /* Work around findbugs warning in generate code. */
    protected static boolean stringsEqual(TruffleString.EqualNode node, TruffleString a, TruffleString b) {
        return node.execute(a, b, IoLanguage.STRING_ENCODING);
    }
}
