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
package org.truffle.io.nodes.expression;

import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.IONode;
import org.truffle.io.runtime.IOObjectUtil;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.UndefinedNameException;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOCall;
import org.truffle.io.runtime.objects.IOCoroutine;
import org.truffle.io.runtime.objects.IOFunction;
import org.truffle.io.runtime.objects.IOLocals;
import org.truffle.io.runtime.objects.IOMessage;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "()")
public final class InvokeNode extends IONode {

    @Child
    protected IONode receiverNode;
    @Child
    protected IONode valueNode;
    @Child
    protected IONode messageNode;

    public InvokeNode(final IONode receiverNode, final IONode valueNode, final IONode messageNode) {
        this.receiverNode = receiverNode;
        this.valueNode = valueNode;
        this.messageNode = messageNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object receiver = receiverNode.executeGeneric(frame);
        IOMessage message = (IOMessage) messageNode.executeGeneric(frame);   
        Object value = null;
        if(valueNode == null)  {
            IOObject prototype = null;
            if (receiver instanceof IOObject) {        
                prototype = (IOObject)receiver;
            } else  {
                prototype = IOState.get(this).getPrototype(receiver);
            }
            value = IOObjectUtil.getOrDefaultUncached(prototype, message.getName());
        } else {
            value = valueNode.executeGeneric(frame);
        }
        if (value == null) {
            throw UndefinedNameException.undefinedField(this, message.getName());
        }
        if (value instanceof IOFunction) {
            return executeFunction(frame, receiver, (IOFunction) value, message);
        }
        if (value instanceof IOMethod) {
            return executeMethod(frame, receiver, (IOMethod) value, message);
        } 
        if (value instanceof IOBlock) {
            return executeBlock(frame, receiver, (IOBlock) value, message);
        }
        return value;
    }

    @ExplodeLoop
    protected final Object executeFunction(VirtualFrame frame, final Object receiver, IOFunction function,
            final IOMessage message) {
        IONode[] argumentNodes = message.getArgumentNodes();
        CompilerAsserts.compilationConstant(argumentNodes.length + 1);
        Object[] argumentValues = new Object[argumentNodes.length + 1];
        argumentValues[0] = receiver;
        for (int i = 0; i < argumentNodes.length; i++) {
            argumentValues[i + 1] = argumentNodes[i].executeGeneric(frame);
        }
        DirectCallNode callNode = DirectCallNode.create(function.getCallTarget());
        Object result = callNode.call(argumentValues);
        return result;
    }

    protected final Object executeBlock(VirtualFrame frame, final Object receiver, IOBlock block,
            final IOMessage message) {
        throw new NotImplementedException();
        // IOLocals sender = IOState.get(this).createLocals(block.getFrame());
        // return executeBlock(block, frame, sender);
    }

    protected final Object executeMethod(VirtualFrame frame, final Object receiver, IOMethod method,
            final IOMessage message) {
        assert receiver instanceof IOObject;
        IOLocals sender = IOState.get(this).createLocals((IOObject) receiver, frame.materialize());
        return execute(frame, receiver, method, message, sender);
    }

    @ExplodeLoop
    protected final Object execute(VirtualFrame frame, final Object receiver, final IOMethod method,
            final IOMessage message, IOLocals sender) {
        IONode[] argumentNodes = message.getArgumentNodes();
        IOCoroutine currentCoroutine = IOState.get(this).getCurrentCoroutine();
        IOCall call = IOState.get(this).createCall(sender, receiver, message, null, method, currentCoroutine);
        int argumentsCount = method.getNumArgs() + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        CompilerAsserts.compilationConstant(argumentsCount);
        Object[] argumentValues = new Object[argumentsCount];
        argumentValues[0] = call;
        for (int i = 0; i < method.getNumArgs(); i++) {
            argumentValues[i + IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX] = argumentNodes[i].executeGeneric(frame);
        }
        Object result = DirectCallNode.create(method.getCallTarget()).call(argumentValues);
        return result;
    }

}
