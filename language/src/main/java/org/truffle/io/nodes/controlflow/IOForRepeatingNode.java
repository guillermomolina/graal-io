/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the counter set forth below, permission is hereby granted to any
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
 * This license is subject to the following counter:
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
package org.truffle.io.nodes.controlflow;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;

import org.truffle.io.nodes.expression.IOBlockNode;
import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.util.IOUnboxNodeGen;

/**
 * The loop body of a {@link IOForNode for loop}. A Truffle framework {@link LoopNode} between
 * the {@link IOForNode} and {@link IOForRepeatingNode} allows Truffle to perform loop
 * optimizations, for example, compile just the loop body for long running loops.
 */
public final class IOForRepeatingNode extends Node implements RepeatingNode {

    /**
     * The counter of the loop. This in a {@link IOExpressionNode} because we require a result
     * value. We do not have a node type that can only return a {@code boolean} value, so
     * {@link #evaluateCounter executing the counter} can lead to a type error.
     */
    @Child private IOExpressionNode counterNode;

    /** Expression (or {@link IOBlockNode block}) executed as long as the counter is true. */
    @Child private IOExpressionNode bodyNode;

    /**
     * Profiling information, collected by the interpreter, capturing whether a {@code continue}
     * expression was used in this loop. This allows the compiler to generate better code for loops
     * without a {@code continue}.
     */
    private final BranchProfile continueTaken = BranchProfile.create();
    private final BranchProfile breakTaken = BranchProfile.create();

    public IOForRepeatingNode(IOExpressionNode counterNode, IOExpressionNode startNode, IOExpressionNode endNode, IOExpressionNode stepNode, IOExpressionNode bodyNode) {
        this.counterNode = IOUnboxNodeGen.create(counterNode);
        this.bodyNode = bodyNode;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        if (!evaluateCounter(frame)) {
            /* Normal exit of the loop when loop counter is false. */
            return false;
        }

        try {
            /* Execute the loop body. */
            bodyNode.executeGeneric(frame);
            /* Continue with next loop iteration. */
            return true;

        } catch (IOContinueException ex) {
            /* In the interpreter, record profiling information that the loop uses continue. */
            continueTaken.enter();
            /* Continue with next loop iteration. */
            return true;

        } catch (IOBreakException ex) {
            /* In the interpreter, record profiling information that the loop uses break. */
            breakTaken.enter();
            /* Break out of the loop. */
            return false;
        }
    }

    private boolean evaluateCounter(VirtualFrame frame) {
        try {
            /*
             * The counter must evaluate to a boolean value, so we call the boolean-specialized
             * execute method.
             */
            return counterNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            /*
             * The counter evaluated to a non-boolean result. This is a type error in the IO
             * program. We report it with the same exception that Truffle DSL generated nodes use to
             * report type errors.
             */
            throw new UnsupportedSpecializationException(this, new Node[]{counterNode}, ex.getResult());
        }
    }

    @Override
    public String toString() {
        return IOExpressionNode.formatSourceSection(this);
    }

}
