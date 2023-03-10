/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(InteropLibrary.class)
public abstract class IoInvokable extends IoObject {

    public static final int INLINE_CACHE_SIZE = 2;

    /** The current implementation of this method. */
    private final RootCallTarget callTarget;
    private final boolean hasCall; 
    
    public IoInvokable(IoBaseObject prototype, final RootCallTarget callTarget) {
        super(prototype);
        this.callTarget = callTarget;
        this.hasCall = false;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    public boolean hasLocals() {
        return false;
    }

    public boolean hasLCall() {
        return hasCall;
    }

    @Override
    public abstract String toStringInner();
    
    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    @TruffleBoundary
    SourceSection getSourceLocation() {
        return getCallTarget().getRootNode().getSourceSection();
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doIOInvokable(IoInvokable receiver, IoInvokable other) {
            return receiver == other ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(IoInvokable receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    static int identityHashCode(IoInvokable receiver) {
        return System.identityHashCode(receiver);
    }

    @ReportPolymorphism
    @ExportMessage
    abstract static class Execute {

        @Specialization(limit = "INLINE_CACHE_SIZE", //
                guards = "method.getCallTarget() == cachedTarget")
        protected static Object doDirect(IoInvokable method, Object[] arguments,
                @Cached("method.getCallTarget()") RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)") DirectCallNode callNode) {

            /* Inline cache hit, we are safe to execute the cached call target. */
            Object returnValue = callNode.call(arguments);
            return returnValue;
        }

        @Specialization(replaces = "doDirect")
        protected static Object doIndirect(IoInvokable method, Object[] arguments,
                @Cached IndirectCallNode callNode) {
            return callNode.call(method.getCallTarget(), arguments);
        }
    }

}
