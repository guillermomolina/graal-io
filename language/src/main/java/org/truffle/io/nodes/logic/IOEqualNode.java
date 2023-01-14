/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.nodes.logic;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.IOLanguage;
import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.nodes.expression.IOBinaryNode;
import org.truffle.io.runtime.objects.IOFalse;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IONil;
import org.truffle.io.runtime.objects.IOTrue;

/**
 * The {@code ==} operator of IO is defined on all types. Therefore, we need a
 * {@link #equal(Object, Object) implementation} that can handle all possible types including
 * interop types.
 * <p>
 * Note that we do not need the analogous {@code !=} operator, because we can just
 * {@link IOLogicalNotNode negate} the {@code ==} operator.
 */
@NodeInfo(shortName = "==")
public abstract class IOEqualNode extends IOBinaryNode {

    @Specialization
    public static final boolean doBoolean(final boolean left, final boolean right) {
      return left == right;
    }

    @Specialization
    public static final boolean doLong(final long left, final long right) {
      return left == right;
    }
  
    @Specialization
    public static final boolean doLong(final long left, final double right) {
      return left == right;
    }
    
    @Specialization
    public static final boolean doLong(final long left, final String right) {
      return false;
    }
    
    @Specialization
    @TruffleBoundary
    public static final boolean doDouble(final double left, final long right) {
      return left == right;
    }
  
    @Specialization
    public static final boolean doDouble(final double left, final double right) {
      return left == right;
    }
  
    @Specialization
    protected boolean doString(String left, String right) {
        return left.equals(right);
    }

    @Specialization
    protected boolean doTruffleString(TruffleString left, TruffleString right,
                    @Cached TruffleString.EqualNode equalNode) {
        return equalNode.execute(left, right, IOLanguage.STRING_ENCODING);
    }

    @Specialization
    protected boolean doNull(IONil left, IONil right) {
        return left == right;
    }

    @Specialization
    protected boolean doTrue(IOTrue left, IOTrue right) {
        return left == right;
    }

    @Specialization
    protected boolean doFalse(IOFalse left, IOFalse right) {
        return left == right;
    }

    @Specialization
    protected boolean doInvokable(IOInvokable left, Object right) {
        return left == right;
    }

    @Specialization(limit = "4")
    public boolean doGeneric(Object left, Object right,
                    @CachedLibrary("left") InteropLibrary leftInterop,
                    @CachedLibrary("right") InteropLibrary rightInterop) {
        try {
            if (leftInterop.isBoolean(left) && rightInterop.isBoolean(right)) {
                return doBoolean(leftInterop.asBoolean(left), rightInterop.asBoolean(right));
            } else if (leftInterop.isString(left) && rightInterop.isString(right)) {
                return doString(leftInterop.asString(left), (rightInterop.asString(right)));
            } else if (leftInterop.isNull(left) && rightInterop.isNull(right)) {
                return true;
            } else if (leftInterop.fitsInLong(left) && rightInterop.fitsInLong(right)) {
                return doLong(leftInterop.asLong(left), (rightInterop.asLong(right)));
            } else if (leftInterop.fitsInDouble(left) && rightInterop.fitsInDouble(right)) {
                return doDouble(leftInterop.asDouble(left), (rightInterop.asDouble(right)));
            } else if (leftInterop.hasIdentity(left) && rightInterop.hasIdentity(right)) {
                return leftInterop.isIdentical(left, right, rightInterop);
            } else {
                return false;
            }
        } catch (UnsupportedMessageException e) {
            throw new ShouldNotBeHereException(e);
        }
    }

}
