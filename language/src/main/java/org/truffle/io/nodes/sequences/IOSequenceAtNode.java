/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.nodes.sequences;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOOutOfBoundsException;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.IOSymbols;
import org.truffle.io.runtime.IOUndefinedNameException;
import org.truffle.io.runtime.objects.IOObject;

/**
 * The node for reading a property of an object. When executed, this node:
 * <ol>
 * <li>evaluates the object expression on the left hand side of the object
 * access operator</li>
 * <li>evaluated the property name</li>
 * <li>reads the named property</li>
 * </ol>
 */
@NodeInfo(shortName = ".")
@NodeChild("receiverNode")
@NodeChild("indexNode")
public abstract class IOSequenceAtNode extends IOExpressionNode {

    static final TruffleString AT = IOSymbols.constant("at");
    static final int LIBRARY_LIMIT = 3;

    // @Specialization
    // protected Object atString(TruffleString receiver, long index,
    //         @Cached TruffleString.SubstringNode substringNode) {
    //     try {
    //         return substringNode.execute(receiver, (int) index, 1, IOLanguage.STRING_ENCODING, true);
    //     } catch (IndexOutOfBoundsException e) {
    //         throw IOOutOfBoundsException.outOfBoundsInteger(this, index);
    //     }
    // }

    @Specialization
    protected long atString(TruffleString receiver, long index,
            @Cached TruffleString.ReadCharUTF16Node readByteNode) {
        try {
            return (long) readByteNode.execute(receiver, (int) index);
        } catch (IndexOutOfBoundsException e) {
            throw IOOutOfBoundsException.outOfBoundsInteger(this, index);
        }
    }

    @Specialization(guards = "arrays.hasArrayElements(receiver)", limit = "LIBRARY_LIMIT")
    protected Object atArray(Object receiver, long index,
            @CachedLibrary("receiver") InteropLibrary arrays) {
        try {
            return arrays.readArrayElement(receiver, index);
        } catch (UnsupportedMessageException e) {
            throw IOUndefinedNameException.undefinedProperty(this, AT);
        } catch (InvalidArrayIndexException e) {
            throw IOOutOfBoundsException.outOfBoundsInteger(this, index);
        }
    }

    @Specialization
    protected Object atIOObject(IOObject receiver, long index) {
        Object value = IOObjectUtil.getOrDefault(receiver, AT, null);
        if (value == null) {
            throw IOUndefinedNameException.undefinedProperty(this, AT);
        }
        return value;
    }

    @Specialization
    protected Object atObject(Object receiver, long index) {
        IOObject prototype = IOState.get(this).getPrototype(receiver);
        Object value = IOObjectUtil.getOrDefault(prototype, AT, null);
        if (value == null) {
            throw IOUndefinedNameException.undefinedProperty(this, AT);
        }
        return value;
    }

}
