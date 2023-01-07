/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.variables.IOScopedNode;
import org.truffle.io.nodes.variables.IOWriteLocalVariableNode;

/**
 * A expression node that just executes a list of other expressions.
 */
@NodeInfo(shortName = "block", description = "The node implementing a source code block")
public final class IOBlockNode extends IOExpressionNode implements BlockNode.ElementExecutor<IOExpressionNode> {

    /**
     * The block of child nodes. Using the block node allows Truffle to split the block into
     * multiple groups for compilation if the method is too big. This is an optional API.
     * Alternatively, you may just use your own block node, with a
     * {@link com.oracle.truffle.api.nodes.Node.Children @Children} field. However, this prevents
     * Truffle from compiling big methods, so these methods might fail to compile with a compilation
     * bailout.
     */
    @Child private BlockNode<IOExpressionNode> block;

    /**
     * All declared variables visible from this block (including all parent blocks). Variables
     * declared in this block only are from zero index up to {@link #parentBlockIndex} (exclusive).
     */
    @CompilationFinal(dimensions = 1) private IOWriteLocalVariableNode[] writeNodesCache;

    /**
     * Index of the parent block's variables in the {@link #writeNodesCache list of variables}.
     */
    @CompilationFinal private int parentBlockIndex = -1;

    public IOBlockNode(IOExpressionNode[] bodyNodes) {
        /*
         * Truffle block nodes cannot be empty, that is why we just set the entire block to null if
         * there are no elements. This is good practice as it safes memory.
         */
        this.block = bodyNodes.length > 0 ? BlockNode.create(bodyNodes, this) : null;
    }

    /**
     * Execute all block expressions. The block node makes sure that {@link ExplodeLoop full
     * unrolling} of the loop is triggered during compilation. This allows the
     * {@link IOExpressionNode#executeGeneric} method of all children to be inlined.
     */
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (this.block != null) {
            return this.block.executeGeneric(frame, BlockNode.NO_ARGUMENT);
        }
        throw new NotImplementedException();
    }

    public List<IOExpressionNode> getExpressions() {
        if (block == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(block.getElements()));
    }

    /**
     * Truffle nodes don't have a fixed execute signature. The {@link ElementExecutor} interface
     * tells the framework how block element nodes should be executed. The executor allows to add a
     * custom exception handler for each element, e.g. to handle a specific
     * {@link ControlFlowException} or to pass a customizable argument, that allows implement
     * startsWith semantics if needed. For IO we don't need to pass any argument as we just have
     * plain block nodes, therefore we pass {@link BlockNode#NO_ARGUMENT}. In our case the executor
     * does not need to remember any state so we reuse a singleton instance.
     */
    @Override
    public void executeVoid(VirtualFrame frame, IOExpressionNode node, int index, int argument) {
        node.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame, IOExpressionNode node, int index, int argument) {
        return node.executeGeneric(frame);
    }

    /**
     * All declared local variables accessible in this block. Variables declared in parent blocks
     * are included.
     */
    public IOWriteLocalVariableNode[] getDeclaredLocalVariables() {
        IOWriteLocalVariableNode[] writeNodes = writeNodesCache;
        if (writeNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeNodesCache = writeNodes = findDeclaredLocalVariables();
        }
        return writeNodes;
    }

    public int getParentBlockIndex() {
        return parentBlockIndex;
    }

    private IOWriteLocalVariableNode[] findDeclaredLocalVariables() {
        if (block == null) {
            return new IOWriteLocalVariableNode[]{};
        }
        // Search for those write nodes, which declare variables
        List<IOWriteLocalVariableNode> writeNodes = new ArrayList<>(4);
        int[] varsIndex = new int[]{0};
        NodeUtil.forEachChild(block, new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (node instanceof WrapperNode) {
                    NodeUtil.forEachChild(node, this);
                    return true;
                }
                if (node instanceof IOScopedNode) {
                    IOScopedNode scopedNode = (IOScopedNode) node;
                    scopedNode.setVisibleVariablesIndexOnEnter(varsIndex[0]);
                }
                // Do not enter any nested blocks.
                if (!(node instanceof IOBlockNode)) {
                    NodeUtil.forEachChild(node, this);
                }
                // Write to a variable is a declaration unless it exists already in a parent scope.
                if (node instanceof IOWriteLocalVariableNode) {
                    IOWriteLocalVariableNode wn = (IOWriteLocalVariableNode) node;
                    if (wn.isDeclaration()) {
                        writeNodes.add(wn);
                        varsIndex[0]++;
                    }
                }
                if (node instanceof IOScopedNode) {
                    IOScopedNode scopedNode = (IOScopedNode) node;
                    scopedNode.setVisibleVariablesIndexOnExit(varsIndex[0]);
                }
                return true;
            }
        });
        Node parentBlock = findBlock();
        IOWriteLocalVariableNode[] parentVariables = null;
        if (parentBlock instanceof IOBlockNode) {
            parentVariables = ((IOBlockNode) parentBlock).getDeclaredLocalVariables();
        }
        IOWriteLocalVariableNode[] variables = writeNodes.toArray(new IOWriteLocalVariableNode[writeNodes.size()]);
        parentBlockIndex = variables.length;
        if (parentVariables == null || parentVariables.length == 0) {
            return variables;
        } else {
            int parentVariablesIndex = ((IOBlockNode) parentBlock).getParentBlockIndex();
            int visibleVarsIndex = getVisibleVariablesIndexOnEnter();
            int allVarsLength = variables.length + visibleVarsIndex + parentVariables.length - parentVariablesIndex;
            IOWriteLocalVariableNode[] allVariables = Arrays.copyOf(variables, allVarsLength);
            System.arraycopy(parentVariables, 0, allVariables, variables.length, visibleVarsIndex);
            System.arraycopy(parentVariables, parentVariablesIndex, allVariables, variables.length + visibleVarsIndex, parentVariables.length - parentVariablesIndex);
            return allVariables;
        }
    }

}
