/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the counter set forth below, permission is hereby granted to any
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
 * This license is subject to the following counter:
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
package org.iolanguage.nodes.controlflow;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CreateCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleStringIterator;

import org.iolanguage.IoLanguage;
import org.iolanguage.NotImplementedException;
import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.util.ToTruffleStringNode;
import org.iolanguage.runtime.IoObjectUtil;
import org.iolanguage.runtime.IoState;
import org.iolanguage.runtime.Symbols;
import org.iolanguage.runtime.exceptions.UndefinedNameException;
import org.iolanguage.runtime.objects.IoBaseObject;
import org.iolanguage.runtime.objects.IoCoroutine;
import org.iolanguage.runtime.objects.IoLocals;
import org.iolanguage.runtime.objects.IoMessage;
import org.iolanguage.runtime.objects.IoMethod;

@NodeInfo(shortName = "foreach", description = "The node implementing a foreach loop")
@NodeChild("receiver")
@NodeChild("method")
public abstract class ForeachNode extends IoNode {

    static final TruffleString SYMBOL_FOREACH = Symbols.constant("foreach");
    static final int LIBRARY_LIMIT = 3;

    @Specialization(guards = "isString(receiver)")
    protected Object foreachString(VirtualFrame frame, Object receiver, IoMethod method,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
            @Cached TruffleStringIterator.NextNode nextNode,
            @Cached BranchProfile invalidCodePointProfile) {
        var target = getTarget(frame, method, receiver);
        var tstring = toTruffleStringNode.execute(receiver);
        var tencoding = IoLanguage.STRING_ENCODING;
        var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);

        while (iterator.hasNext()) {
            int codePoint = nextNode.execute(iterator);

            if (codePoint == -1) {
                invalidCodePointProfile.enter();
                throw new NotImplementedException();
            }
            doInvoke(method, target, codePoint);
        }
        return receiver;
    }

    @Specialization(guards = "arrays.hasArrayElements(receiver)", limit = "LIBRARY_LIMIT")
    protected Object foreachArray(VirtualFrame frame, Object receiver, IoMethod method,
            @CachedLibrary("receiver") InteropLibrary arrays) {
        var target = getTarget(frame, method, receiver);
        try {
            long receiverLength = arrays.getArraySize(receiver);
            for (long i = 0; i < receiverLength; i++) {
                Object value = arrays.readArrayElement(receiver, i);
                doInvoke(method, target, value);
            }
        } catch (UnsupportedMessageException e) {
            throw UndefinedNameException.undefinedField(this, SYMBOL_FOREACH);
        } catch (InvalidArrayIndexException e) {
            throw new ShouldNotBeHereException();
        }
        return receiver;
    }

    protected boolean isString(Object a) {
        return a instanceof TruffleString;
    }

    protected final Object getTarget(VirtualFrame frame, IoMethod method, final Object receiver) {
        if (method.getCallSlotIsUsed()) {
            IoLocals sender = IoState.get(this).createLocals(receiver, frame.materialize());
            IoMessage message = IoState.get(this).createMessage(SYMBOL_FOREACH, new IoNode[0]);
            IoCoroutine currentCoroutine = IoState.get(this).getCurrentCoroutine();
            IoBaseObject prototype = IoObjectUtil.getPrototype(receiver);
            return IoState.get(this).createCall(sender, receiver, message, prototype, method, currentCoroutine);
        }
        return receiver;
    }

    protected final Object doInvoke(IoMethod method, final Object receiver, final Object argument) {
        Object[] argumentValues = new Object[2];
        argumentValues[IoLocals.TARGET_ARGUMENT_INDEX] = receiver;
        argumentValues[IoLocals.FIRST_PARAMETER_ARGUMENT_INDEX] = argument;
        Object result = DirectCallNode.create(method.getCallTarget()).call(argumentValues);
        return result;
    }

}
