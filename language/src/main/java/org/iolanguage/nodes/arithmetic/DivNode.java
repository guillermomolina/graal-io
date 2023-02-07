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
package org.iolanguage.nodes.arithmetic;

import org.iolanguage.nodes.expression.BinaryNode;
import org.iolanguage.runtime.exceptions.IoLanguageException;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * This class is similar to the extensively documented {@link AddNode}. Divisions by 0 throw the
 * same {@link ArithmeticException exception} as in Java, IO has no special handling for it to keep
 * the code simple.
 */
@NodeInfo(shortName = "/")
public abstract class DivNode extends BinaryNode {

  // otherwise, explicitly check for cornercase
  protected static boolean isCornercase(long a, long b) {
    return a != 0 && !(b == -1 && a == Long.MIN_VALUE);
  }

  // when b is positive, the result will fit long (if without remainder)
  @Specialization(rewriteOn = ArithmeticException.class, guards = "b > 0")
  protected long doLong1(long a, long b) {
    if (a % b == 0) {
      return a / b;
    }
    throw new ArithmeticException();
  }

  // otherwise, ensure a > 0 (this also excludes two cornercases):
  // when a == 0, result would be NegativeZero
  // when a == Long.MIN_VALUE && b == -1, result does not fit into long
  @Specialization(rewriteOn = ArithmeticException.class, guards = "a > 0")
  protected long doLong2(long a, long b) {
    return doLong1(a, b);
  }

  @Specialization(rewriteOn = ArithmeticException.class, guards = "isCornercase(a, b)")
  protected long doLong3(long a, long b) {
    return doLong1(a, b);
  }

  @Specialization(replaces = { "doLong1", "doLong2", "doLong3" })
  protected double doDouble(double a, double b) {
    return a / b;
  }

  @Fallback
  protected Object typeError(Object left, Object right) {
    throw IoLanguageException.typeError(this, left, right);
  }
}
