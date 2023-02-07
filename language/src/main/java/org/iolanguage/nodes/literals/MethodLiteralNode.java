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
package org.iolanguage.nodes.literals;

import org.iolanguage.IoLanguage;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.root.IoRootNode;
import org.iolanguage.runtime.IoState;
import org.iolanguage.runtime.objects.IoMethod;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

@NodeInfo(shortName = "method")
public final class MethodLiteralNode extends IoNode {

    @Child
    private IoRootNode value;
    private final TruffleString[] argNames;

    @CompilationFinal
    private IoMethod cachedMethod;
    private final boolean callSlotIsUsed;

    public MethodLiteralNode(final IoRootNode value, TruffleString[] argNames, final boolean callSlotIsUsed) {
        this.value = value;
        this.argNames = argNames;
        this.callSlotIsUsed = callSlotIsUsed;
    }

    @Override
    public IoMethod executeGeneric(VirtualFrame frame) {
        IoLanguage l = IoLanguage.get(this);
        CompilerAsserts.partialEvaluationConstant(l);

        IoMethod method;
        if (l.isSingleContext()) {
            method = this.cachedMethod;
            if (method == null) {
                /* We are about to change a @CompilationFinal field. */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /* First execution of the node: lookup the method in the method registry. */
                this.cachedMethod = method = IoState.get(this).createMethod(value.getCallTarget(), argNames, callSlotIsUsed);
            }
        } else {
            /*
             * We need to rest the cached method otherwise it might cause a memory leak.
             */
            if (this.cachedMethod != null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.cachedMethod = null;
            }
            // in the multi-context case we are not allowed to store
            // IOMethod objects in the AST. Instead we always perform the lookup in the hash map.
            method = IoState.get(this).createMethod(value.getCallTarget(), argNames, callSlotIsUsed);
        }
        return method;
    }

    public IoRootNode getValue() {
        return value;
    }
}
