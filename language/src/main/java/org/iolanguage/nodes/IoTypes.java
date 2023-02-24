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
 * LIABILITY, WHETHER IN AN ACTIoN OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTIoN WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.iolanguage.nodes;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;

import org.iolanguage.runtime.objects.IoBigInteger;
import org.iolanguage.runtime.objects.IoFalse;
import org.iolanguage.runtime.objects.IoLocals;
import org.iolanguage.runtime.objects.IoNil;
import org.iolanguage.runtime.objects.IoObject;
import org.iolanguage.runtime.objects.IoTrue;

@TypeSystem({boolean.class, long.class, double.class, IoObject.class, IoLocals.class})
public abstract class IoTypes {

    @TypeCheck(IoNil.class)
    public static boolean isIoNull(Object value) {
        return value == IoNil.SINGLETON;
    }

    @TypeCast(IoNil.class)
    public static IoNil asIoNull(Object value) {
        assert isIoNull(value);
        return IoNil.SINGLETON;
    }

    @TypeCheck(IoTrue.class)
    public static boolean isIoTrue(Object value) {
        return value == IoTrue.SINGLETON;
    }

    @TypeCast(IoTrue.class)
    public static IoTrue asIoTrue(Object value) {
        assert isIoTrue(value);
        return IoTrue.SINGLETON;
    }

    @TypeCheck(IoFalse.class)
    public static boolean isIoFalse(Object value) {
        return value == IoFalse.SINGLETON;
    }

    @TypeCast(IoFalse.class)
    public static IoFalse asIoFalse(Object value) {
        assert isIoFalse(value);
        return IoFalse.SINGLETON;
    }

    @ImplicitCast
    public static long castSmallIntToInt(int value) {
        return value;
    }

    @ImplicitCast
    @TruffleBoundary
    public static IoBigInteger castIntToBigInt(long value) {
        return new IoBigInteger(BigInteger.valueOf(value));
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

    @ImplicitCast
    @TruffleBoundary
    public static double castBigIntToFloat(IoBigInteger value) {
        return value.getValue().doubleValue();
    }

}
