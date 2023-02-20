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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CreateCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleStringIterator;

import org.iolanguage.IoLanguage;
import org.iolanguage.NotImplementedException;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.util.ToTruffleStringNode;
import org.iolanguage.runtime.Symbols;

@NodeInfo(shortName = "foreach", description = "The node implementing a foreach loop")
@NodeChild("receiver")
@NodeField(name = "writeValueNode", type = IoNode.class)
@NodeField(name = "bodyNode", type = IoNode.class)
public abstract class ForeachNode extends IoNode {

    public abstract IoNode getBodyNode();

    public abstract IoNode getWriteValueNode();

    static final TruffleString SYMBOL_FOREACH = Symbols.constant("foreach");
    static final int LIBRARY_LIMIT = 3;

    @Specialization(guards = "isString(receiver)")
    protected Object foreachString(VirtualFrame frame, Object receiver,
            @Cached ToTruffleStringNode toTruffleStringNode,
            @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
            @Cached TruffleStringIterator.NextNode nextNode) {
        var tstring = toTruffleStringNode.execute(receiver);
        var tencoding = IoLanguage.STRING_ENCODING;
        var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);

        ForeachRepeatingNode repeatingNode = new ForeachRepeatingNode(iterator, nextNode, getWriteValueNode(), getBodyNode());
        LoopNode loopNode = Truffle.getRuntime().createLoopNode(repeatingNode);

        loopNode.execute(frame);

        return receiver;
    }

    @Specialization(guards = "arrays.hasArrayElements(receiver)", limit = "LIBRARY_LIMIT")
    protected Object foreachArray(VirtualFrame frame, Object receiver,
            @CachedLibrary("receiver") InteropLibrary arrays) {
        throw new NotImplementedException();
        // try {
        //     long receiverLength = arrays.getArraySize(receiver);
        //     for (long i = 0; i < receiverLength; i++) {
        //         Object value = arrays.readArrayElement(receiver, i);
        //         assert getWriteValueNode() instanceof WriteNode;
        //         WriteNode writeValueNode = (WriteNode) getWriteValueNode();
        //         writeValueNode.executeWrite(frame, value);
        //     }
        // } catch (UnsupportedMessageException e) {
        //     throw UndefinedNameException.undefinedField(this, SYMBOL_FOREACH);
        // } catch (InvalidArrayIndexException e) {
        //     throw new ShouldNotBeHereException();
        // }
        // return receiver;
    }

    protected boolean isString(Object a) {
        return a instanceof TruffleString;
    }

}
