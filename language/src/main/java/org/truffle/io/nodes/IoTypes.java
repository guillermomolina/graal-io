/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.nodes;

import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.runtime.objects.IoFalse;
import org.truffle.io.runtime.objects.IoNil;
import org.truffle.io.runtime.objects.IoObject;
import org.truffle.io.runtime.objects.IoPrototype;
import org.truffle.io.runtime.objects.IoTrue;

@TypeSystem({boolean.class, long.class, double.class, IoObject.class})
public abstract class IoTypes {

    @TypeCheck(IoNil.class)
    public static boolean isIONull(Object value) {
        return value == IoNil.SINGLETON;
    }

    @TypeCast(IoNil.class)
    public static IoNil asIONull(Object value) {
        assert isIONull(value);
        return IoNil.SINGLETON;
    }

    @ImplicitCast
    public static long castSmallIntToInt(int value) {
        return value;
    }

    @ImplicitCast
    public static double castSmallFloatToFloat(float value) {
        return value;
    }

    @ImplicitCast
    public static double castSmallIntToFloat(int value) {
        return value;
    }

    @ImplicitCast
    public static double castIntToFloat(long value) {
        return value;
    }

    public static IoObject getPrototype(Object obj) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(obj);
        if (interop.isNull(obj)) {
            return IoNil.SINGLETON;
        } 
        if (interop.isBoolean(obj)) {          
            return (Boolean)obj == Boolean.TRUE ? IoTrue.SINGLETON : IoFalse.SINGLETON;
        } 
        if (obj instanceof IoObject) {
            return ((IoObject) obj).getPrototype();
        } 
        if (obj instanceof String) {
            return IoPrototype.SEQUENCE;
        } 
        if (obj instanceof TruffleString) {
            return IoPrototype.SEQUENCE;
        } 
        if (interop.fitsInLong(obj)) {
            return IoPrototype.NUMBER;
        } 
        if (interop.fitsInDouble(obj)) {
            return IoPrototype.NUMBER;
        } 
        if(interop.hasMembers(obj)) {
            return IoPrototype.OBJECT;
        }
        return null;
    }
}
