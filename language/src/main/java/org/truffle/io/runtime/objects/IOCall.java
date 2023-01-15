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

public class IOCall extends IOObject { 
    private static final TruffleString SENDER = Symbols.constant("sender");
    private static final TruffleString MESSAGE = Symbols.constant("message");
    private static final TruffleString TARGET = Symbols.constant("target");
    private static final TruffleString SLOTCONTEXT = Symbols.constant("slotContext");
    private static final TruffleString ACTIVATED = Symbols.constant("activated");
    private static final TruffleString COROUTINE = Symbols.constant("coroutine");

    @DynamicField
    private Object sender;
    @DynamicField
    private Object message;
    @DynamicField
    private Object target;
    @DynamicField
    private Object slotContext;
    @DynamicField
    private Object activated;
    @DynamicField
    private Object coroutine;

    public IOCall(final IOContext sender, final IOMessage message, final Object target, final IOObject slotContext,
            final IOMethod activated, final IOObject coroutine) {
        setSender(sender);
        setMessage(message);
        setTarget(target);
        setSlotContext(slotContext);
        setActivated(activated);
        setCoroutine(coroutine);
    }

    public IOContext getSender() {
        return (IOContext)IOObjectUtil.getProperty(this, SENDER);
    }

    public void setSender(IOContext sender) {
        IOObjectUtil.putProperty(this, SENDER, sender);
    }

    public IOMessage getMessage() {
        return (IOMessage)IOObjectUtil.getProperty(this, MESSAGE);
    }

    public void setMessage(IOMessage message) {
        IOObjectUtil.putProperty(this, MESSAGE, message);
    }

    public Object getTarget() {
        return IOObjectUtil.getProperty(this, TARGET);
    }

    public void setTarget(Object target) {
        IOObjectUtil.putProperty(this, TARGET, target);
    }

    public IOObject getSlotContext() {
        return (IOObject)IOObjectUtil.getProperty(this, SLOTCONTEXT);
    }

    public void setSlotContext(IOObject slotcontext) {
        IOObjectUtil.putProperty(this, SLOTCONTEXT, slotcontext);
    }

    public IOMethod getActivated() {
        return (IOMethod)IOObjectUtil.getProperty(this, ACTIVATED);
    }

    public void setActivated(IOMethod activated) {
        IOObjectUtil.putProperty(this, ACTIVATED, activated);
    }

    public IOObject getCoroutine() {
        return (IOObject)IOObjectUtil.getProperty(this, COROUTINE);
    }

    public void setCoroutine(IOObject coroutine) {
        IOObjectUtil.putProperty(this, COROUTINE, coroutine);
    }
}