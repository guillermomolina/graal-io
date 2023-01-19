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
package org.truffle.io.nodes.literals;

import org.truffle.io.IOLanguage;
import org.truffle.io.nodes.IONode;
import org.truffle.io.nodes.root.IORootNode;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.objects.IOLocals;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

@NodeInfo(shortName = "block")
public final class BlockLiteralNode extends IONode {

    @Child
    private IORootNode rootNode;
    private final TruffleString[] argNames;
    @Child
    private IONode receiverNode;

    @CompilationFinal
    private IOMethod cachedBlock;

    public BlockLiteralNode(final IORootNode rootNode, TruffleString[] argNames, final IONode receiverNode) {
        this.rootNode = rootNode;
        this.argNames = argNames;
        this.receiverNode = receiverNode;
    }

    @Override
    public IOMethod executeGeneric(VirtualFrame frame) {
        Object targetObject = receiverNode.executeGeneric(frame);
        final IOObject target;
        if (targetObject instanceof IOObject) {
            target = (IOObject) targetObject;
        } else {
            target = IOState.get(this).getPrototype(targetObject);
        }
        final IOLocals sender = IOState.get(this).createLocals(target, frame.materialize());

        IOLanguage l = IOLanguage.get(this);
        CompilerAsserts.partialEvaluationConstant(l);
        IOMethod block;
        if (l.isSingleContext()) {
            block = this.cachedBlock;
            if (block == null) {
                 CompilerDirectives.transferToInterpreterAndInvalidate();
                this.cachedBlock = block = IOState.get(this).createBlock(rootNode.getCallTarget(), argNames, sender);
            }
        } else {
            if (this.cachedBlock != null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.cachedBlock = null;
            }
            block = IOState.get(this).createBlock(rootNode.getCallTarget(), argNames, sender);
        }
        return block;
    }

    public IORootNode getValue() {
        return rootNode;
    }
}
