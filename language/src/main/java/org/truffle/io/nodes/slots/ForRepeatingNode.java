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
package org.truffle.io.nodes.slots;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;

import org.truffle.io.nodes.IoNode;
import org.truffle.io.nodes.controlflow.BreakException;
import org.truffle.io.nodes.controlflow.ContinueException;

public final class ForRepeatingNode extends Node implements RepeatingNode {

    @Child
    private IoNode hasEndedNode;
    @Child
    private IoNode bodyNode;
    @Child
    private IoNode stepSlotNode;

    private final BranchProfile continueTaken = BranchProfile.create();
    private final BranchProfile breakTaken = BranchProfile.create();

    public ForRepeatingNode(IoNode hasEndedNode, IoNode bodyNode, IoNode stepSlotNode) {
        this.hasEndedNode = hasEndedNode;
        this.bodyNode = bodyNode;
        this.stepSlotNode = stepSlotNode;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        if (!evaluateHasEndedNode(frame)) {
            return false;
        }

        try {
            bodyNode.executeGeneric(frame);
            stepSlotNode.executeGeneric(frame);
            return true;

        } catch (ContinueException ex) {
            continueTaken.enter();
            return true;

        } catch (BreakException ex) {
            breakTaken.enter();
            return false;
        }
    }

    private boolean evaluateHasEndedNode(VirtualFrame frame) {
        try {
            return hasEndedNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            throw new UnsupportedSpecializationException(this, new Node[] { hasEndedNode }, ex.getResult());
        }
    }

    @Override
    public String toString() {
        return IoNode.formatSourceSection(this);
    }

}
