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
package org.iolanguage.nodes.slots;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.util.ToMemberNode;
import org.iolanguage.nodes.util.ToTruffleStringNode;
import org.iolanguage.runtime.IoObjectUtil;
import org.iolanguage.runtime.exceptions.UndefinedNameException;
import org.iolanguage.runtime.objects.IoBaseObject;
import org.iolanguage.runtime.objects.IoFalse;
import org.iolanguage.runtime.objects.IoPrototype;
import org.iolanguage.runtime.objects.IoTrue;

@NodeChild(value = "receiverNode", type = IoNode.class)
@NodeChild(value = "nameNode", type = IoNode.class)
public abstract class ReadMemberNode extends ReadNode {
    static final int LIBRARY_LIMIT = 3;

    @Specialization
    protected Object readLong(long receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        IoBaseObject slotOwner = IoObjectUtil.lookupSlot(IoPrototype.NUMBER, getName());
        setPrototype(slotOwner);
        return getMember();
    }

    @Specialization
    protected Object readDouble(double receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        IoBaseObject slotOwner = IoObjectUtil.lookupSlot(IoPrototype.NUMBER, getName());
        setPrototype(slotOwner);
        return getMember();
    }

    @Specialization
    protected Object readBoolean(boolean receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        IoPrototype proto = receiver? IoTrue.SINGLETON: IoFalse.SINGLETON;
        IoBaseObject slotOwner = IoObjectUtil.lookupSlot(proto, getName());
        setPrototype(slotOwner);
        return getMember();
    }

    @Specialization
    public Object readIoObject(IoBaseObject receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        setPrototype(IoObjectUtil.lookupSlot(receiver, getName()));
        return getMember();
    }

    @Specialization(guards = "isString(receiver)")
    protected Object readString(Object receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        IoBaseObject slotOwner = IoObjectUtil.lookupSlot(IoPrototype.SEQUENCE, getName());
        setPrototype(slotOwner);
        return getMember();
    }

    @Specialization(guards = { "!isIoBaseObject(receiver)", "objects.hasMembers(receiver)" }, limit = "LIBRARY_LIMIT")
    public Object readObject(Object receiver, Object name,
            @CachedLibrary("receiver") InteropLibrary objects,
            @Cached ToMemberNode asMember,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        try {
            setPrototype(IoObjectUtil.getPrototype(receiver));
            return objects.readMember(receiver, asMember.execute(name));
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            setPrototype(IoObjectUtil.lookupSlot(getPrototype(), getName()));
            return getMember();
        }
    }

    protected Object getMember() {
        Object value = null;
        if (getPrototype() != null) {
            value = IoObjectUtil.getOrDefault(getPrototype(), getName());
        }
        if (value == null) {
            throw UndefinedNameException.undefinedField(this, getName());
        }
        return value;
    }

    @Specialization
    protected Object readObject(Object receiver, Object name,
            @Cached ToTruffleStringNode toTruffleStringNode) {
        setReceiver(receiver);
        setName(toTruffleStringNode.execute(name));
        IoBaseObject slotOwner = IoObjectUtil.lookupSlot(receiver, getName());
        setPrototype(slotOwner);
        return getMember();
    }

    static boolean isString(Object a) {
        return a instanceof TruffleString;
    }

    static boolean isIoBaseObject(Object receiver) {
        return receiver instanceof IoBaseObject;
    }
}
