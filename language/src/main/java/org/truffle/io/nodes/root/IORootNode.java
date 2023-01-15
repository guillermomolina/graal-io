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
package org.truffle.io.nodes.root;

import java.util.ArrayList;
import java.util.List;

import org.truffle.io.IOLanguage;
import org.truffle.io.nodes.controlflow.MethodBodyNode;
import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.expression.BlockNode;
import org.truffle.io.nodes.variables.ReadArgumentNode;
import org.truffle.io.nodes.variables.WriteLocalVariableNode;
import org.truffle.io.runtime.IOState;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@NodeInfo(language = "IO", description = "The root of all IO execution trees")
public class IORootNode extends RootNode {
    @Child private ExpressionNode bodyNode;

    private boolean isCloningAllowed;

    private final SourceSection sourceSection;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private volatile WriteLocalVariableNode[] argumentNodesCache;

    public IORootNode(IOLanguage language, FrameDescriptor frameDescriptor, ExpressionNode bodyNode, SourceSection sourceSection) {
        super(language, frameDescriptor);
        this.bodyNode = bodyNode;
        this.sourceSection = sourceSection;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert IOState.get(this) != null;
        return bodyNode.executeGeneric(frame);
    }

    public ExpressionNode getBodyNode() {
        return bodyNode;
    }

    public void setCloningAllowed(boolean isCloningAllowed) {
        this.isCloningAllowed = isCloningAllowed;
    }

    @Override
    public boolean isCloningAllowed() {
        return isCloningAllowed;
    }

    @Override
    public String toString() {
        return "root";
    }

    public final WriteLocalVariableNode[] getDeclaredArguments() {
        WriteLocalVariableNode[] argumentNodes = argumentNodesCache;
        if (argumentNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            argumentNodesCache = argumentNodes = findArgumentNodes();
        }
        return argumentNodes;
    }

    private WriteLocalVariableNode[] findArgumentNodes() {
        List<WriteLocalVariableNode> writeArgNodes = new ArrayList<>(4);
        NodeUtil.forEachChild(this.getBodyNode(), new NodeVisitor() {

            private WriteLocalVariableNode wn; // The current write node containing a slot

            @Override
            public boolean visit(Node node) {
                // When there is a write node, search for IOReadArgumentNode among its children:
                if (node instanceof InstrumentableNode.WrapperNode) {
                    return NodeUtil.forEachChild(node, this);
                }
                if (node instanceof WriteLocalVariableNode) {
                    wn = (WriteLocalVariableNode) node;
                    boolean all = NodeUtil.forEachChild(node, this);
                    wn = null;
                    return all;
                } else if (wn != null && (node instanceof ReadArgumentNode)) {
                    writeArgNodes.add(wn);
                    return true;
                } else if (wn == null && (node instanceof ExpressionNode && !(node instanceof BlockNode || node instanceof MethodBodyNode))) {
                    // A different IO node - we're done.
                    return false;
                } else {
                    return NodeUtil.forEachChild(node, this);
                }
            }
        });
        return writeArgNodes.toArray(new WriteLocalVariableNode[writeArgNodes.size()]);
    }

}
