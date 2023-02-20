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
package org.iolanguage.nodes.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleStringIterator;

import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.slots.WriteNode;
import org.iolanguage.runtime.exceptions.BreakException;
import org.iolanguage.runtime.exceptions.ContinueException;

public final class ForeachRepeatingNode extends Node implements RepeatingNode {

    @Child
    private TruffleStringIterator.NextNode nextNode;
    @Child
    private IoNode writeValueNode;
    @Child
    private IoNode bodyNode;

    private final TruffleStringIterator iterator;
    private final BranchProfile invalidCodePointProfile = BranchProfile.create();
    private final BranchProfile continueTaken = BranchProfile.create();
    private final BranchProfile breakTaken = BranchProfile.create();

    public ForeachRepeatingNode(TruffleStringIterator iterator, TruffleStringIterator.NextNode nextNode, IoNode writeValueNode, IoNode bodyNode) {
        this.iterator = iterator;
        this.nextNode = nextNode;
        this.writeValueNode = writeValueNode;
        this.bodyNode = bodyNode;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        if (!iterator.hasNext()) {
            return false;
        }

        int codePoint = nextNode.execute(iterator);

        if (codePoint == -1) {
            invalidCodePointProfile.enter();
            return false;
        }

        assert writeValueNode instanceof WriteNode;
        ((WriteNode)writeValueNode).executeWrite(frame, codePoint);

        try {
            //bodyNode.executeGeneric(frame);
            return true;

        } catch (ContinueException ex) {
            continueTaken.enter();
            return true;

        } catch (BreakException ex) {
            breakTaken.enter();
            return false;
        }
    }

    @Override
    public String toString() {
        return IoNode.formatSourceSection(this);
    }

}
