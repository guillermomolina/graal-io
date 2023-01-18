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
package org.truffle.io.nodes.literals;

import org.truffle.io.IOLanguage;
import org.truffle.io.nodes.expression.IONode;
import org.truffle.io.nodes.root.IORootNode;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOFunction;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Constant literal for a {@link IOBlock method} value, created when a method name occurs as
 * a literal in IO source code. Note that method redefinition can change the {@link CallTarget
 * call target} that is executed when calling the method, but the {@link IOBlock} for a name
 * never changes. This is guaranteed by the {@link IOMethodRegistry}.
 */
@NodeInfo(shortName = "function")
public final class FunctionLiteralNode extends IONode {

    final private TruffleString name;
    @Child private IORootNode value;

    @CompilationFinal private IOFunction cachedFunction;

    public FunctionLiteralNode(final TruffleString name, final IORootNode value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public IOFunction executeGeneric(VirtualFrame frame) {
        IOLanguage l = IOLanguage.get(this);
        CompilerAsserts.partialEvaluationConstant(l);

        IOFunction function;
        if (l.isSingleContext()) {
            function = this.cachedFunction;
            if (function == null) {
                /* We are about to change a @CompilationFinal field. */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /* First execution of the node: lookup the method in the method registry. */
                this.cachedFunction = function = IOState.get(this).createFunction(value.getCallTarget(), name);
            }
        } else {
            /*
             * We need to rest the cached method otherwise it might cause a memory leak.
             */
            if (this.cachedFunction != null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.cachedFunction = null;
            }
            // in the multi-context case we are not allowed to store
            // IOMethod objects in the AST. Instead we always perform the lookup in the hash map.
            function = IOState.get(this).createFunction(value.getCallTarget(), name);
        }
        return function;
    }

    public IORootNode getValue() {
        return value;
    }
}
