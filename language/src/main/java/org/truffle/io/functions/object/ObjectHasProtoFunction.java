/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.functions.object;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;

import org.truffle.io.nodes.expression.FunctionBodyNode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOObject;

/**
 * Built-in function that returns true if the given operand is of a given meta-object. Meta-objects
 * may be values of the current or a foreign value.
 */
@NodeInfo(shortName = "hasProto")
public abstract class ObjectHasProtoFunction extends FunctionBodyNode {

    /*@Specialization
    public boolean hasProtoLong(long value, IOObject prototype) {
        IOObject numberProto = IOState.get(this).getPrototype(value);
        return IOObjectUtil.hasPrototype(numberProto, prototype);
    }

    @Specialization
    public boolean hasProtoBoolean(boolean value, IOObject prototype) {
        IOObject booleanProto = IOState.get(this).getPrototype(value);
        return IOObjectUtil.hasPrototype(booleanProto, prototype);
    }

    @Specialization
    public boolean hasProtoIOObject(IOObject value, IOObject prototype) {
        return IOObjectUtil.hasPrototype(value, prototype);
    }*/

    @Specialization
    public boolean hasProtoIOObject(Object value, IOObject prototype) {
        IOObject objectProto = IOState.get(this).getPrototype(value);
        return IOObjectUtil.hasPrototype(objectProto, prototype);
    }

    @Specialization(limit = "3", guards = "metaLib.isMetaObject(metaObject)", replaces = /*{"hasProtoLong", "hasProtoBoolean", "hasProtoObject",*/ "hasProtoIOObject"/* } */)
    @TruffleBoundary
    public boolean hasProtoMetaObject(Object value, Object metaObject,
                    @CachedLibrary("metaObject") InteropLibrary metaLib) {
        try {
            return metaLib.isMetaInstance(metaObject, value);
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
    }

    @Specialization
    public boolean hasProtoObject(Object value, Object prototype) {
        IOObject objectProto = IOState.get(this).getPrototype(value);
        return IOObjectUtil.hasPrototype(objectProto, prototype);
    }
   
    /*@Specialization
    public boolean hasProto(Object value, Object metaObject) {
        return false;
    }*/
}