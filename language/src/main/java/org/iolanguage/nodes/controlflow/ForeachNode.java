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

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleStringIterator;

import org.iolanguage.IoLanguage;
import org.iolanguage.NotImplementedException;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.util.ToTruffleStringNodeGen;

@NodeInfo(shortName = "foreach", description = "The node implementing a for loop")
public final class ForeachNode extends IoNode {

    @Child
    private IoNode receiverNode;
    @Child
    private IoNode writeValueNode;
    @Child
    private IoNode bodyNode;

    public ForeachNode(IoNode receiverNode, IoNode writeValueNode, IoNode bodyNode) {
        this.receiverNode = receiverNode;
        this.writeValueNode = writeValueNode;
        this.bodyNode = bodyNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object receiver = receiverNode.executeGeneric(frame);
        if (receiver instanceof TruffleString) {
            return forEachString(frame, receiver);
        }
        return forEachArray(frame, receiver);
    }

    public Object forEachString(VirtualFrame frame, Object receiver) {
        var tstring = ToTruffleStringNodeGen.create().execute(receiver);
        var tencoding = IoLanguage.STRING_ENCODING;
        var iterator = TruffleString.CreateCodePointIteratorNode.getUncached().execute(tstring, tencoding,
                ErrorHandling.RETURN_NEGATIVE);
        var nextNode = TruffleStringIterator.NextNode.create();

        ForeachStringRepeatingNode repeatingNode = new ForeachStringRepeatingNode(iterator, nextNode, writeValueNode,
                bodyNode);
        Truffle.getRuntime().createLoopNode(repeatingNode).execute(frame);
        return receiver;
    }

    public Object forEachString2(VirtualFrame frame, Object receiver) {
        var tstring = ToTruffleStringNodeGen.create().execute(receiver);
        var tencoding = IoLanguage.STRING_ENCODING;
        var iterator = TruffleString.CreateCodePointIteratorNode.getUncached().execute(tstring, tencoding,
                ErrorHandling.RETURN_NEGATIVE);
        
        var iteratorInterop = InteropLibrary.getFactory().getUncached(iterator);
        ForeachArrayRepeatingNode repeatingNode = new ForeachArrayRepeatingNode(iterator, iteratorInterop, writeValueNode,
                bodyNode);
        Truffle.getRuntime().createLoopNode(repeatingNode).execute(frame);
        return receiver;
    }

    public Object forEachArray(VirtualFrame frame, Object receiver) {
        try {
            InteropLibrary interop = InteropLibrary.getFactory().getUncached(receiver);
            if(!interop.hasIterator(receiver)) {
                throw UnsupportedMessageException.create();
            }
            var iterator = interop.getIterator(receiver);
            var iteratorInterop = InteropLibrary.getFactory().getUncached(iterator);
            ForeachArrayRepeatingNode repeatingNode = new ForeachArrayRepeatingNode(iterator, iteratorInterop, writeValueNode,
                    bodyNode);
            Truffle.getRuntime().createLoopNode(repeatingNode).execute(frame);
            return receiver;
        } catch (UnsupportedMessageException e) {
            throw new NotImplementedException();
        }
    }

}
