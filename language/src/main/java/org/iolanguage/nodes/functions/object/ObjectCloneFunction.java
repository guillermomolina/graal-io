/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.iolanguage.nodes.functions.object;

import org.iolanguage.NotImplementedException;
import org.iolanguage.nodes.expression.FunctionBodyNode;
import org.iolanguage.runtime.IoState;
import org.iolanguage.runtime.objects.IoNil;
import org.iolanguage.runtime.objects.IoObject;
import org.iolanguage.runtime.objects.IoPrototype;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Built-in function to create a clone object. Objects in IO are simply made up of name/value pairs.
 */
@NodeInfo(shortName = "clone")
@ImportStatic(IoState.class)
public abstract class ObjectCloneFunction extends FunctionBodyNode {

    @Specialization
    public long cloneLong(long value) {
        return value;
    }

    @Specialization
    public boolean cloneBoolean(boolean value) {
        return value;
    }

    @Specialization
    public Object cloneNil(IoNil value) {
        return value;
    }

    @Specialization
    public Object cloneIOPrototype(IoPrototype proto) {
        if(proto == IoPrototype.DATE) {
            return IoState.get(this).createDate();
        }
        if(proto == IoPrototype.OBJECT) {
            return IoState.get(this).cloneObject();
        }
        throw new NotImplementedException();
    }

    @Specialization(guards = "!values.isNull(obj)", limit = "3")
    public Object cloneIOObject(IoObject obj, @CachedLibrary("obj") InteropLibrary values) {
        return IoState.get(this).cloneObject(obj);
    }

    @Specialization(guards = "isString(value)")
    @TruffleBoundary
    public Object cloneString(Object value) {
        return value;
    }

    protected boolean isString(Object value) {
        return value instanceof TruffleString;
    }

}