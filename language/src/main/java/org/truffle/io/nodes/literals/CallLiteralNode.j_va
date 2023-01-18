/*
 * Copyright (c) 2012, 2019, Guillermo Adrián Molina. All rights reserved.
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
package org.truffle.io.nodes.literals;

import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOCall;
import org.truffle.io.runtime.objects.IOCoroutine;
import org.truffle.io.runtime.objects.IOLocals;
import org.truffle.io.runtime.objects.IOMessage;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "Call with")
public final class CallLiteralNode extends ExpressionNode {

    @Child
    private ExpressionNode targetNode;
    @Child
    private ExpressionNode messageNode;
    @Child
    private ExpressionNode senderNode;
    @Child
    private ExpressionNode activatedNode;
    @Child
    private ExpressionNode coroutineNode;

    public CallLiteralNode(final ExpressionNode targetNode, final ExpressionNode messageNode,
            final ExpressionNode senderNode, final ExpressionNode activatedNode,
            final ExpressionNode coroutineNode) {
        this.targetNode = targetNode;
        this.messageNode = messageNode;
        this.senderNode = senderNode;
        this.activatedNode = activatedNode;
        this.coroutineNode = coroutineNode;
    }

    @Override
    public IOCall executeGeneric(VirtualFrame frame) {
        IOLocals sender = (IOLocals) senderNode.executeGeneric(frame);
        Object target = targetNode.executeGeneric(frame);
        IOMessage message = (IOMessage) messageNode.executeGeneric(frame);
        IOLocals slotContext = IOState.get(this).createLocals(frame.materialize());
        IOBlock activated = (IOBlock) activatedNode.executeGeneric(frame);
        IOCoroutine coroutine = (IOCoroutine) coroutineNode.executeGeneric(frame);
        return IOState.get(this).createCall(sender, target, message, slotContext, activated, coroutine);
    }
}
