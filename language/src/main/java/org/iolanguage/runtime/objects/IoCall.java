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
package org.iolanguage.runtime.objects;

import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.runtime.IoObjectUtil;
import org.iolanguage.runtime.Symbols;

public class IoCall extends IoObject { 
    private static final TruffleString SYMBOL_SENDER = Symbols.constant("sender");
    private static final TruffleString SYMBOL_MESSAGE = Symbols.constant("message");
    private static final TruffleString SYMBOL_TARGET = Symbols.constant("target");
    private static final TruffleString SYMBOL_SLOT_CONTEXT = Symbols.constant("slotContext");
    private static final TruffleString SYMBOL_ACTIVATED = Symbols.constant("activated");
    private static final TruffleString SYMBOL_COROUTINE = Symbols.constant("coroutine");

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

    public IoCall(final IoLocals sender, final Object target, final IoMessage message, final IoBaseObject slotContext,
            final IoInvokable activated, final IoCoroutine coroutine) {
        super(IoPrototype.CALL);
        setSender(sender);
        setMessage(message);
        setTarget(target);
        setSlotContext(slotContext);
        setActivated(activated);
        setCoroutine(coroutine);
    }

    public IoLocals getSender() {
        return (IoLocals)IoObjectUtil.getOrDefault(this, SYMBOL_SENDER);
    }

    protected void setSender(IoLocals sender) {
        IoObjectUtil.put(this, SYMBOL_SENDER, sender);
    }

    public IoMessage getMessage() {
        return (IoMessage)IoObjectUtil.getOrDefault(this, SYMBOL_MESSAGE);
    }

    protected void setMessage(IoMessage message) {
        IoObjectUtil.put(this, SYMBOL_MESSAGE, message);
    }

    public Object getTarget() {
        return IoObjectUtil.getOrDefault(this, SYMBOL_TARGET);
    }

    protected void setTarget(Object target) {
        IoObjectUtil.put(this, SYMBOL_TARGET, target);
    }

    public IoBaseObject getSlotContext() {
        return (IoBaseObject)IoObjectUtil.getOrDefault(this, SYMBOL_SLOT_CONTEXT);
    }

    protected void setSlotContext(IoBaseObject slotcontext) {
        IoObjectUtil.put(this, SYMBOL_SLOT_CONTEXT, slotcontext);
    }

    public IoInvokable getActivated() {
        return (IoInvokable)IoObjectUtil.getOrDefault(this, SYMBOL_ACTIVATED);
    }

    protected void setActivated(IoInvokable activated) {
        IoObjectUtil.put(this, SYMBOL_ACTIVATED, activated);
    }

    public IoCoroutine getCoroutine() {
        return (IoCoroutine)IoObjectUtil.getOrDefault(this, SYMBOL_COROUTINE);
    }

    protected void setCoroutine(IoCoroutine coroutine) {
        IoObjectUtil.put(this, SYMBOL_COROUTINE, coroutine);
    }
}