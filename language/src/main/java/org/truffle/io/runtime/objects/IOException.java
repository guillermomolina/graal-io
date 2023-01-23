/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
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

import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.Symbols;

import com.oracle.truffle.api.strings.TruffleString;

public class IOException extends IOObject { 
    private static final TruffleString ERROR = Symbols.constant("error");
    private static final TruffleString COROUTINE = Symbols.constant("coroutine");
    private static final TruffleString CAUGHT_MESSAGE = Symbols.constant("caughtMessage");
    private static final TruffleString NESTED_EXCEPTION = Symbols.constant("nestedException");
    private static final TruffleString ORIGINAL_CALL = Symbols.constant("originalCall");

    @DynamicField
    private TruffleString caughtMessage;
    @DynamicField
    private IOCoroutine coroutine;

    public IOException(final TruffleString error, final IOCoroutine coroutine) {
        super(IOPrototype.EXCEPTION);
        setError(error);
        setCoroutine(coroutine);
    }


    public IOException(final TruffleString error, final IOCoroutine coroutine, final IOMessage caughtMessage) {
        this(error, coroutine);
        setCaughtMessage(caughtMessage);
    }

    public TruffleString getError() {
        return (TruffleString)IOObjectUtil.getOrDefaultUncached(this, ERROR);
    }

    protected void setError(TruffleString error) {
        IOObjectUtil.putUncached(this, ERROR, error);
    }

    public IOCoroutine getCoroutine() {
        return (IOCoroutine)IOObjectUtil.getOrDefaultUncached(this, COROUTINE);
    }

    protected void setCoroutine(IOCoroutine coroutine) {
        IOObjectUtil.putUncached(this, COROUTINE, coroutine);
    }

    public IOMessage getCaughtMessage() {
        return (IOMessage)IOObjectUtil.getOrDefaultUncached(this, CAUGHT_MESSAGE);
    }

    protected void setCaughtMessage(IOMessage caughtMessage) {
        IOObjectUtil.putUncached(this, CAUGHT_MESSAGE, caughtMessage);
    }

    public IOException getNestedException() {
        return (IOException)IOObjectUtil.getOrDefaultUncached(this, NESTED_EXCEPTION);
    }

    protected void setNestedException(IOException nestedException) {
        IOObjectUtil.putUncached(this, NESTED_EXCEPTION, nestedException);
    }

    public IOCall getOriginalCall() {
        return (IOCall)IOObjectUtil.getOrDefaultUncached(this, ORIGINAL_CALL);
    }

    protected void setOriginalCall(IOCall originalCall) {
        IOObjectUtil.putUncached(this, ORIGINAL_CALL, originalCall);
    }
}