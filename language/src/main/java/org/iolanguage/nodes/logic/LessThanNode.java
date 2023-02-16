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
package org.iolanguage.nodes.logic;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;

import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.nodes.binary.AddNode;
import org.iolanguage.nodes.expression.BinaryNode;
import org.iolanguage.nodes.util.ToTruffleStringNode;
import org.iolanguage.runtime.exceptions.IoLanguageException;

/**
 * This class is similar to the extensively documented {@link AddNode}. The only difference: the
 * specialized methods return {@code boolean} instead of the input types.
 */
@NodeInfo(shortName = "<")
public abstract class LessThanNode extends BinaryNode {

    @Specialization
    public static final boolean doLong(final long left, final long right) {
      return left < right;
    }
  
    @Specialization
    public static final boolean doLong(final long left, final double right) {
      return doDouble(left, right);
    }
 
    @Specialization
    @TruffleBoundary
    public static final boolean doDouble(final double left, final long right) {
      return doDouble(left, (double) right);
    }
  
    @Specialization
    public static final boolean doDouble(final double left, final double right) {
      return left < right;
    }

    @Specialization(limit = "4")
    public final Object doGeneric(Object left, Object right,
                    @CachedLibrary("left") InteropLibrary leftInterop,
                    @Cached ToTruffleStringNode toTruffleStringNodeRight) {
        try {
            Object rightAsNumber = StringToNumber(toTruffleStringNodeRight.execute(right).toJavaStringUncached());
            if (leftInterop.fitsInLong(left) && rightAsNumber instanceof Long) {
                return doLong(leftInterop.asLong(left), (Long)rightAsNumber);  
            } else if (leftInterop.fitsInDouble(left) && rightAsNumber instanceof Double) {
                return doDouble(leftInterop.asDouble(left), (Double)rightAsNumber);
            } else {
                throw IoLanguageException.typeError(this, left, right);
            }
        } catch (UnsupportedMessageException e) {
            throw new ShouldNotBeHereException(e);
        }
    }

    public static final Object StringToNumber(String string) {
      try {
        return Long.parseLong(string);
      } catch(NumberFormatException e1) {
        try {
          return Double.parseDouble(string);
        } catch(NumberFormatException e2) {
          return null;
        }
      }
    }
}
