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
package org.truffle.io.nodes.slots;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.nodes.IoNode;
import org.truffle.io.nodes.util.ToMemberNode;
import org.truffle.io.nodes.util.ToTruffleStringNode;
import org.truffle.io.runtime.IoObjectUtil;
import org.truffle.io.runtime.IoState;
import org.truffle.io.runtime.objects.IoNil;
import org.truffle.io.runtime.objects.IoObject;

@NodeInfo(shortName = ".")
@NodeChild("receiverNode")
@NodeChild("nameNode")
public abstract class ReadMemberNode extends IoNode {

    static final int LIBRARY_LIMIT = 3;
 
    @Specialization(limit = "LIBRARY_LIMIT")
    protected Object readIOObject(IoObject receiver, Object name,
            @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        TruffleString nameTS = toTruffleStringNode.execute(name);
        Object value = IoObjectUtil.getOrDefaultUncached(receiver, nameTS, IoNil.SINGLETON);
        return value;
    }

    @Specialization(guards = "!isIOObject(receiver)", limit = "LIBRARY_LIMIT")
    protected Object readObject(Object receiver, Object name,
            @CachedLibrary("receiver") InteropLibrary objects,
            @Cached ToMemberNode asMember) {
        try {
            return objects.readMember(receiver, asMember.execute(name));
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            return IoNil.SINGLETON;
        }
    }

    @Specialization
    protected Object read(Object receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        IoObject prototype = IoState.get(this).getPrototype(receiver);
        TruffleString nameTS = toTruffleStringNode.execute(name);
        Object value = IoObjectUtil.getOrDefaultUncached(prototype, nameTS, IoNil.SINGLETON);
        return value;    
    }

    static boolean isIOObject(Object receiver) {
        return receiver instanceof IoObject;
    }

}
