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
package org.iolanguage.runtime.objects;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

import org.iolanguage.NotImplementedException;
import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.runtime.Symbols;

@ExportLibrary(InteropLibrary.class)
public class IoSequence extends IoObject {
    enum ItemType {
        UINT8("uint8", Byte.BYTES, (byte) 0),
        INT8("int8", Byte.BYTES, (byte) 0),
        UINT16("uint16", Short.BYTES, (short) 0),
        INT16("int16", Short.BYTES, (short) 0),
        UINT32("uint32", Integer.BYTES, 0),
        INT32("int32", Integer.BYTES, 0),
        UINT64("uint64", Long.BYTES, 0L),
        INT64("int64", Long.BYTES, 0L),
        FLOAT32("float32", Float.BYTES, 0.0f),
        FLOAT64("float64", Double.BYTES, 0.0);

        private final TruffleString name;
        private final int size;
        private final Object initValue;

        ItemType(String name, int size, Object value) {
            this.name = Symbols.constant(name);
            this.size = size;
            this.initValue = value;
        }

        protected TruffleString getName() {
            return name;
        }

        protected int getSize() {
            return size;
        }

        protected Object getInitValue() {
            return initValue;
        }

        public static ItemType fromTruffleString(TruffleString name) {
            for (ItemType itemType : ItemType.values()) {
                if (itemType.name.equals(name)) {
                    return itemType;
                }
            }
            return null;
        }
    }

    enum Encoding {
        ASCII("ascii", 1),
        UTF8("utf8", 1),
        UCS2("ucs2", 2),
        UCS4("ucs4", 4),
        NUMBER("number", 4);

        private final TruffleString name;
        private final int size;

        Encoding(String name, int size) {
            this.name = Symbols.constant(name);
            this.size = size;
        }

        protected TruffleString getName() {
            return name;
        }

        protected int getSize() {
            return size;
        }

        public static ItemType fromTruffleString(TruffleString name) {
            for (ItemType itemType : ItemType.values()) {
                if (itemType.name.equals(name)) {
                    return itemType;
                }
            }
            return null;
        }
    }

    private ItemType itemType;
    private Encoding encoding;
    private ByteBuffer byteBuffer;

    public IoSequence() {
        this(ItemType.UINT8, Encoding.ASCII, 0);
    }

    public IoSequence(ItemType itemType, Encoding encoding, int size) {
        super(IoPrototype.SEQUENCE);
        this.itemType = itemType;
        this.encoding = encoding;
        this.byteBuffer = ByteBuffer.allocate(size * itemType.getSize());
    }

    public int getSize() {
        int capacity = byteBuffer.capacity();
        assert capacity % itemType.getSize() == 0;
        return capacity / itemType.getSize();
    }

    public void setSize(int newSize) {
        if (newSize < 0) {
            throw new NotImplementedException();
        }
        int currentSize = getSize();
        if (newSize != currentSize) {
            int newCapacity = newSize * itemType.getSize();
            ByteBuffer newByteBuffer = ByteBuffer.allocate(newCapacity);
            if (byteBuffer != null) {
                int currentCapacity = byteBuffer.capacity();
                int minCapacity = Math.min(newCapacity, currentCapacity);
                for (int index = 0; index < minCapacity; index++) {
                    newByteBuffer.put(index, byteBuffer.get(index));
                }
            }
            byteBuffer = newByteBuffer;
        }
        assert newSize == getSize();
    }

    public TruffleString getItemType() {
        return itemType.getName();
    }

    public TruffleString getEncoding() {
        return encoding.getName();
    }

    public void setItemType(TruffleString itemTypeName) {
        ItemType newItemType = ItemType.fromTruffleString(itemTypeName);
        if (newItemType == null) {
            throw new NotImplementedException();
        }
        int currentCapacity = byteBuffer.capacity();
        int newItemSize = newItemType.getSize();
        if (currentCapacity % newItemSize != 0) {
            int quot = currentCapacity / newItemSize;
            int newCapacity = (quot + 1) * newItemSize;
            setSize(newCapacity / itemType.getSize());
        }
        itemType = newItemType;
    }

    public long getInt8(int position) {
        return (long) byteBuffer.get(position);
    }

    public long getUInt8(int position) {
        return ((long) (byteBuffer.get(position) & (long) 0xff));
    }

    public void putInt8(int position, long value) {
        byteBuffer.put(position, (byte) value);
    }


    public void putUInt8(int position, long value) {
        byteBuffer.put(position, (byte) (value & 0xff));
    }

    public long getInt16(int position) {
        return (long) byteBuffer.getShort(position);
    }

    public long getUInt16(int position) {
        return (long) (byteBuffer.getShort(position) & 0xffff);
    }

    public void putInt16(int position, long value) {
        byteBuffer.putShort(position, (short) value);
    }

    public void putUInt16(int position, long value) {
        byteBuffer.putShort(position, (short) (value & 0xffff));
    }

    public long getInt32(int position) {
        return (long) byteBuffer.getInt(position);
    }

    public long getUInt32(int position) {
        return ((long) byteBuffer.getInt(position) & 0xffffffffL);
    }

    public void putInt32(int position, long value) {
        byteBuffer.putInt(position, (int) value);
    }

    public void putUInt32(int position, long value) {
        byteBuffer.putInt(position, (int) (value & 0xffffffffL));
    }

    public long getInt64(int position) {
        return byteBuffer.getLong(position);
    }

    public long getUInt64(int position) throws ArithmeticException {
        long value = byteBuffer.getLong(position);
        if (value < 0) {
            throw new ArithmeticException();
        }
        return value;
    }

    public double getUInt64AsDouble(int position) throws ArithmeticException {
        byte[] buffer = new byte[5];
        System.arraycopy(byteBuffer, position, buffer, 1, 4);
        BigInteger bigInteger = new BigInteger(buffer);
        return bigInteger.doubleValue();
    }

    public void putInt64(int position, long value) {
        byteBuffer.putLong(position, value);
    }

    public void putUInt64(int position, long value) {
        if (value >= 0) {
            byteBuffer.putLong(position, value);
        }
    }
  
    public void putUInt64(int position, double value) {
        if (value >= 0.0) {
            throw new NotImplementedException();
        }
    }
  
    public double getFloat32(int position) {
        return (double) byteBuffer.getFloat(position);
    }
  
    public void putFloat32(int position, double value) {
        byteBuffer.putFloat(position, (float) value);
    }

    public double getFloat64(int position) {
        return (double) byteBuffer.getDouble(position);
    }
 
    public void putFloat64(int position, double value) {
        byteBuffer.putDouble(position, value);
    }

    @Override
    public String toString() {
        return StandardCharsets.UTF_8.decode(byteBuffer).toString();
    }

    @Override
    public String toStringInner() {
        return toString();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return true;
    }

    @ExportMessage
    boolean isArrayElementModifiable(long index) {
        return true;
    }

    @ExportMessage
    boolean isArrayElementInsertable(long index) {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return getSize();
    }

    @ExportMessage
    Object readArrayElement(long indexAsLong) throws InvalidArrayIndexException {
        int index = (int) indexAsLong;
        if (index < 0) {
            throw InvalidArrayIndexException.create(index);
        }
        if (index >= getSize()) {
            setSize((int) index + 1);
        }
        switch (itemType) {
            case INT8:
                return getInt8(index);
            case UINT8:
                return getUInt8(index);
            case INT16:
                return getInt16(index);
            case UINT16:
                return getUInt16(index);
            case INT32:
                return getInt32(index);
            case UINT32:
                return getUInt32(index);
            case INT64:
                return getInt64(index);
            case UINT64:
                try {
                    return getUInt64(index);
                } catch (ArithmeticException e) {
                    return getUInt64AsDouble(index);
                }
            case FLOAT32:
                return getFloat32(index);
            case FLOAT64:
                return getFloat64(index);
            default:
                throw new ShouldNotBeHereException();
        }
    }

    @ExportMessage
    public void writeArrayElement(long indexAsLong, Object value) throws InvalidArrayIndexException {
        int index = (int) indexAsLong;
        if (index < 0) {
            throw InvalidArrayIndexException.create(index);
        }
        if (index >= getSize()) {
            setSize((int) index + 1);
        }
        final long valueAsLong;
        final double valueAsDouble;
        if(value instanceof Long) {
            valueAsLong = ((Long)value).longValue();
            valueAsDouble = ((Long)value).doubleValue();
        } else if(value instanceof Double) {
            valueAsLong = ((Double)value).longValue();
            valueAsDouble = ((Double)value).doubleValue();
        } else {
            throw new NotImplementedException();
        }
        switch (itemType) {
            case INT8:
                putInt8(index, valueAsLong);
                break;
            case UINT8:
                putUInt8(index, valueAsLong);
                break;
            case INT16:
                putInt16(index, valueAsLong);
                break;
            case UINT16:
                putUInt16(index, valueAsLong);
                break;
            case INT32:
                putInt32(index, valueAsLong);
                break;
            case UINT32:
                putUInt32(index, valueAsLong);
                break;
            case INT64:
                putInt64(index, valueAsLong);
                break;
            case UINT64:
                putUInt64(index, valueAsLong);
                break;
            case FLOAT32:
                putFloat32(index, valueAsDouble);
                break;
            case FLOAT64:
                putFloat64(index, valueAsDouble);
                break;
            default:
                throw new ShouldNotBeHereException();
        }    
 }

}
