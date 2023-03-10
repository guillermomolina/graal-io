/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * list (collectively the "Software"), free of charge and under any and all
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
package org.iolanguage.runtime.objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

import org.iolanguage.runtime.IoObjectUtil;

@ExportLibrary(InteropLibrary.class)
public class IoList extends IoObject {
    private List<Object> list;

    public IoList() {
        super(IoPrototype.LIST);
        this.list = new ArrayList<>();
    }

    public IoList(Object[] list) {
        super(IoPrototype.LIST);
        this.list = new ArrayList<Object>(Arrays.asList(list));;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("list(");
        boolean first = true;
        for (Object value: list) {
            if(first) {
                first = false;
            } else {
                sb.append(", ");
             }
            if (value == null) {
                sb.append("<UNKNOWN>");
            } else {
                sb.append(IoObjectUtil.toStringInner(value));
            }
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toStringInner() {
        return toString();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    boolean isValidIndex(long index) {
        return index >= 0;
    }

    @ExportMessage
    boolean isArrayElementInsertable(long index) {
        return index == list.size();
    }

    @ExportMessage
    long getArraySize() {
        return list.size();
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (!isValidIndex(index)) {
            throw InvalidArrayIndexException.create(index);
        }
        return list.get((int)index);
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value) throws InvalidArrayIndexException {
        if (!isValidIndex(index)) {
            throw InvalidArrayIndexException.create(index);
        }
        int aditional = (int)index  + 1 - list.size();
        while(aditional-- > 0) {
            list.add(IoNil.SINGLETON); 
        }
        list.set((int)index,value);
    }

    @ExportMessage
    public boolean hasIterator() {
        return true;
    }

    @ExportMessage
    public ListIterator getIterator() {
        return new ListIterator(this);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ListIterator implements TruffleObject {

        final IoList sequence;
        private long currentItemIndex;

        ListIterator(IoList sequence) {
            this.sequence = sequence;
        }

        @ExportMessage
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement(
                @CachedLibrary("this.sequence") InteropLibrary arrays) {
            try {
                return currentItemIndex < arrays.getArraySize(sequence);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        Object getIteratorNextElement(
                @CachedLibrary("this.sequence") InteropLibrary arrays,
                @Cached BranchProfile concurrentModification) throws StopIterationException {
            try {
                final long size = arrays.getArraySize(sequence);
                if (currentItemIndex >= size) {
                    throw StopIterationException.create();
                }

                final Object element = arrays.readArrayElement(sequence, currentItemIndex);
                currentItemIndex++;
                return element;
            } catch (InvalidArrayIndexException e) {
                concurrentModification.enter();
                throw StopIterationException.create();
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

}
