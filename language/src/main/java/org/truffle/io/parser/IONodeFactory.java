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
package org.truffle.io.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import org.truffle.io.IOLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.arithmetic.IOAddNodeGen;
import org.truffle.io.nodes.arithmetic.IODivNodeGen;
import org.truffle.io.nodes.arithmetic.IOMulNodeGen;
import org.truffle.io.nodes.arithmetic.IOSubNodeGen;
import org.truffle.io.nodes.controlflow.IOBreakNode;
import org.truffle.io.nodes.controlflow.IOContinueNode;
import org.truffle.io.nodes.controlflow.IODebuggerNode;
import org.truffle.io.nodes.controlflow.IOForNode;
import org.truffle.io.nodes.controlflow.IOIfNode;
import org.truffle.io.nodes.controlflow.IOMethodBodyNode;
import org.truffle.io.nodes.controlflow.IORepeatNode;
import org.truffle.io.nodes.controlflow.IOReturnNode;
import org.truffle.io.nodes.controlflow.IOWhileNode;
import org.truffle.io.nodes.expression.IOBlockNode;
import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.expression.IOParenExpressionNode;
import org.truffle.io.nodes.literals.IOBooleanLiteralNode;
import org.truffle.io.nodes.literals.IODoubleLiteralNode;
import org.truffle.io.nodes.literals.IOListLiteralNode;
import org.truffle.io.nodes.literals.IOLongLiteralNode;
import org.truffle.io.nodes.literals.IOMethodLiteralNode;
import org.truffle.io.nodes.literals.IONilLiteralNode;
import org.truffle.io.nodes.literals.IOStringLiteralNode;
import org.truffle.io.nodes.logic.IOEqualNodeGen;
import org.truffle.io.nodes.logic.IOLessOrEqualNodeGen;
import org.truffle.io.nodes.logic.IOLessThanNodeGen;
import org.truffle.io.nodes.logic.IOLogicalAndNode;
import org.truffle.io.nodes.logic.IOLogicalNotNodeGen;
import org.truffle.io.nodes.logic.IOLogicalOrNode;
import org.truffle.io.nodes.root.IORootNode;
import org.truffle.io.nodes.sequences.IOSequenceAtNodeGen;
import org.truffle.io.nodes.sequences.IOSequenceAtPutNodeGen;
import org.truffle.io.nodes.util.IOUnboxNodeGen;
import org.truffle.io.nodes.variables.IOInvokeLocalVariableNodeGen;
import org.truffle.io.nodes.variables.IOInvokePropertyNodeGen;
import org.truffle.io.nodes.variables.IOInvokeRemoteVariableNodeGen;
import org.truffle.io.nodes.variables.IOReadArgumentNode;
import org.truffle.io.nodes.variables.IOReadLocalVariableNodeGen;
import org.truffle.io.nodes.variables.IOReadPropertyNodeGen;
import org.truffle.io.nodes.variables.IOWriteLocalVariableNodeGen;
import org.truffle.io.nodes.variables.IOWritePropertyNodeGen;
import org.truffle.io.nodes.variables.IOWriteRemoteVariableNodeGen;
import org.truffle.io.runtime.IOSymbols;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public class IONodeFactory {

    static class MethodScope {
        protected final MethodScope outer;
        protected int methodBodyStartPos; // includes parameter list
        protected int parameterCount;
        protected FrameDescriptor.Builder frameDescriptorBuilder;
        protected final List<TruffleString> parameters;
        protected final Map<TruffleString, Integer> locals;
        protected boolean inLoop;

        protected List<IOExpressionNode> methodNodes;

        MethodScope(final MethodScope outer, int methodBodyStartPos) {
            this.outer = outer;
            this.methodBodyStartPos = methodBodyStartPos;
            this.parameters = new ArrayList<>();
            this.parameterCount = 0;
            this.frameDescriptorBuilder = FrameDescriptor.newBuilder();
            this.methodNodes = new ArrayList<>();
            this.locals = new HashMap<>();
            // this.inLoop = outer != null ? outer.inLoop : false;
            this.inLoop = false;
        }

        public Pair<Integer, Integer> find(TruffleString name) {
            MethodScope scope = this;
            int level = 0;
            while (scope != null) {
                if (scope.locals.containsKey(name)) {
                    return new Pair<Integer, Integer>(level, scope.locals.get(name));
                }
                level++;
                scope = scope.outer;
            }
            return null;
        }

        public Integer getOrAddLocal(TruffleString name, Integer argumentIndex) {
            Integer frameSlot = locals.get(name);
            if (frameSlot == null) {
                frameSlot = frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, name, argumentIndex);
                locals.put(name, frameSlot);
            }
            return frameSlot;
        }
    }

    private final Source source;
    private final TruffleString sourceString;
    private final IOLanguage language;
    private MethodScope methodScope;

    public IONodeFactory(IOLanguage language, Source source) {
        this.language = language;
        this.source = source;
        this.sourceString = IOSymbols.fromJavaString(source.getCharacters().toString());
    }

    public boolean isAtLobby() {
        return methodScope.outer == null;
    }

    public void startMethod(Token bodyStartToken) {
        methodScope = new MethodScope(methodScope, bodyStartToken.getStartIndex());
        addSelfParameter();
        addContextParameter();
    }

    public void addFormalParameter(Token nameToken) {
        assert methodScope.parameterCount >= 2;
        // final IOEvalArgumentNode readArg = new IOEvalArgumentNode(methodScope.parameterCount);
        final IOReadArgumentNode readArg = new IOReadArgumentNode(methodScope.parameterCount);
        int startPos = nameToken.getStartIndex();
        int length = nameToken.getText().length();
        readArg.setSourceSection(startPos, length);
        IOStringLiteralNode nameNode = createStringLiteral(nameToken, false);
        methodScope.parameters.add(nameNode.getValue());
        IOExpressionNode assignmentNode = createWriteVariable(nameNode, readArg, methodScope.parameterCount, startPos,
                length, true);
        assert assignmentNode != null;
        methodScope.methodNodes.add(assignmentNode);
        methodScope.parameterCount++;
    }

    public void addSelfParameter() {
        assert methodScope.parameterCount == 0;
        final IOReadArgumentNode readArg = new IOReadArgumentNode(methodScope.parameterCount);
        final IOStringLiteralNode selfNode;
        if (isAtLobby()) {
            selfNode = new IOStringLiteralNode(IOSymbols.UNDERSCORE);
        } else {
            selfNode = new IOStringLiteralNode(IOSymbols.SELF);
        }
        IOExpressionNode assignmentNode = createWriteVariable(selfNode, readArg, methodScope.parameterCount, 0, 0,
                true);
        assert assignmentNode != null;
        methodScope.methodNodes.add(assignmentNode);
        methodScope.parameterCount++;
    }

    public void addContextParameter() {
        assert methodScope.parameterCount == 1;
        methodScope.parameterCount++;
    }

    public IOMethodLiteralNode finishMethod(IOExpressionNode bodyNode, int startPos, int length) {
        IOMethodLiteralNode methodLiteralNode = null;
        if (bodyNode != null) {
            methodScope.methodNodes.add(bodyNode);
            final int bodyEndPos = bodyNode.getSourceEndIndex();
            int methodBodyLength = bodyEndPos - methodScope.methodBodyStartPos;
            final SourceSection methodSrc = source.createSection(methodScope.methodBodyStartPos, methodBodyLength);
            final IOExpressionNode methodBlock = createBlock(methodScope.methodNodes, methodScope.parameterCount,
                    methodScope.methodBodyStartPos, methodBodyLength);
            final IOMethodBodyNode methodBodyNode = new IOMethodBodyNode(methodBlock);
            methodBodyNode.setSourceSection(methodSrc.getCharIndex(), methodSrc.getCharLength());
            final IORootNode rootNode = new IORootNode(language, methodScope.frameDescriptorBuilder.build(),
                    methodBodyNode,
                    methodSrc);
            TruffleString[] parameters = methodScope.parameters.toArray(new TruffleString[methodScope.parameters.size()]);
            methodLiteralNode = new IOMethodLiteralNode(rootNode, parameters);
            methodLiteralNode.setSourceSection(startPos, length);
        }

        methodScope = methodScope.outer;
        return methodLiteralNode;
    }

    protected IOExpressionNode createBlock(List<IOExpressionNode> bodyNodes, int startPos, int length) {
        return createBlock(bodyNodes, 0, startPos, length);
    }

    protected IOExpressionNode createBlock(List<IOExpressionNode> bodyNodes, int skipCount, int startPos, int length) {
        if (containsNull(bodyNodes)) {
            return null;
        }

        List<IOExpressionNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenBlocks(bodyNodes, flattenedNodes);
        int n = flattenedNodes.size();
        for (int i = skipCount; i < n; i++) {
            IOExpressionNode expression = flattenedNodes.get(i);
            if (expression.hasSource() && !isHaltInCondition(expression)) {
                expression.addExpressionTag();
            }
        }
        IOBlockNode blockNode = new IOBlockNode(flattenedNodes.toArray(new IOExpressionNode[flattenedNodes.size()]));
        blockNode.setSourceSection(startPos, length);
        return blockNode;
    }

    public void startLoop() {
        methodScope.inLoop = true;
    }

    public IOExpressionNode createLoopBlock(IOExpressionNode loopNode, int startPos, int length) {
        List<IOExpressionNode> bodyNodes = new ArrayList<>();
        bodyNodes.add(loopNode);
        return createBlock(bodyNodes, 0, startPos, length);
    }

    private static boolean isHaltInCondition(IOExpressionNode expression) {
        return (expression instanceof IOIfNode) || (expression instanceof IOWhileNode);
    }

    private void flattenBlocks(Iterable<? extends IOExpressionNode> bodyNodes, List<IOExpressionNode> flattenedNodes) {
        for (IOExpressionNode n : bodyNodes) {
            if (n instanceof IOBlockNode) {
                flattenBlocks(((IOBlockNode) n).getExpressions(), flattenedNodes);
            } else {
                flattenedNodes.add(n);
            }
        }
    }

    IOExpressionNode createDebugger(Token debuggerToken) {
        final IODebuggerNode debuggerNode = new IODebuggerNode();
        srcFromToken(debuggerNode, debuggerToken);
        return debuggerNode;
    }

    public IOExpressionNode createBreak(Token breakToken) {
        if (methodScope.inLoop) {
            final IOBreakNode breakNode = new IOBreakNode();
            srcFromToken(breakNode, breakToken);
            return breakNode;
        }
        return null;
    }

    public IOExpressionNode createContinue(Token continueToken) {
        if (methodScope.inLoop) {
            final IOContinueNode continueNode = new IOContinueNode();
            srcFromToken(continueNode, continueToken);
            return continueNode;
        }
        return null;
    }

    public IOExpressionNode createWhile(Token whileToken, IOExpressionNode conditionNode, IOExpressionNode bodyNode) {
        IOWhileNode whileNode = null;
        if (conditionNode != null && bodyNode != null) {
            conditionNode.addExpressionTag();
            final int startPos = whileToken.getStartIndex();
            final int end = bodyNode.getSourceEndIndex();
            whileNode = new IOWhileNode(conditionNode, bodyNode);
            whileNode.setSourceSection(startPos, end - startPos);
        }
        return whileNode;
    }

    public IOExpressionNode createRepeat(IOExpressionNode receiverNode, IOExpressionNode bodyNode, int startPos, int length) {
        IORepeatNode repeatNode = null;
        if (receiverNode != null && bodyNode != null) {
            receiverNode.addExpressionTag();
            repeatNode = new IORepeatNode(receiverNode, bodyNode);
            repeatNode.setSourceSection(startPos, length);
        }
        return repeatNode;
    }

    public IOExpressionNode createFor(Token forToken, IOStringLiteralNode counterNode, IOExpressionNode startValueNode,
            IOExpressionNode endValueNode, IOExpressionNode stepValueNode, IOExpressionNode bodyNode) {
        IOForNode forNode = null;
        if (counterNode != null && startValueNode != null && endValueNode != null && bodyNode != null) {
            final int startPos = forToken.getStartIndex();
            final int length = bodyNode.getSourceEndIndex() - startPos;
            IOExpressionNode initialAssignmentNode = createWriteVariable(counterNode, startValueNode, startPos, length,
                    true);
            assert initialAssignmentNode != null;
            IOExpressionNode readControlNode = createReadLocalVariable(counterNode);
            assert readControlNode != null;
            initialAssignmentNode.addExpressionTag();
            readControlNode.addExpressionTag();
            startValueNode.addExpressionTag();
            endValueNode.addExpressionTag();
            if (stepValueNode != null) {
                stepValueNode.addExpressionTag();
            }
            forNode = new IOForNode(initialAssignmentNode, startValueNode, endValueNode, stepValueNode, readControlNode,
                    bodyNode);
            forNode.setSourceSection(startPos, length);
        }
        return forNode;
    }

    public IOExpressionNode createIf(Token ifToken, IOExpressionNode conditionNode, IOExpressionNode thenPartNode,
            IOExpressionNode elsePartNode) {
        if (conditionNode == null || thenPartNode == null) {
            return null;
        }

        conditionNode.addExpressionTag();
        final int startPos = ifToken.getStartIndex();
        final int end = elsePartNode == null ? thenPartNode.getSourceEndIndex() : elsePartNode.getSourceEndIndex();
        final IOIfNode ifNode = new IOIfNode(conditionNode, thenPartNode, elsePartNode);
        ifNode.setSourceSection(startPos, end - startPos);
        return ifNode;
    }

    public IOExpressionNode createReturn(Token t, IOExpressionNode valueNodeOrNull) {
        final int startPos = t.getStartIndex();
        final int length;
        IOExpressionNode valueNode = valueNodeOrNull;
        if (valueNode == null) {
            length = t.getText().length();
            valueNode = createReadSelf();
        } else {
            length = valueNode.getSourceEndIndex() - startPos;
        }
        final IOReturnNode returnNode = new IOReturnNode(valueNode);
        returnNode.setSourceSection(startPos, length);
        return returnNode;
    }

    public IOExpressionNode createBinary(Token opToken, IOExpressionNode leftNode, IOExpressionNode rightNode) {
        if (leftNode == null || rightNode == null) {
            return null;
        }
        final IOExpressionNode leftUnboxed = IOUnboxNodeGen.create(leftNode);
        final IOExpressionNode rightUnboxed = IOUnboxNodeGen.create(rightNode);

        final IOExpressionNode result;
        switch (opToken.getText()) {
            case "+":
                result = IOAddNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "*":
                result = IOMulNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "/":
                result = IODivNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "-":
                result = IOSubNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "<":
                result = IOLessThanNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "<=":
                result = IOLessOrEqualNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case ">":
                result = IOLogicalNotNodeGen.create(IOLessOrEqualNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case ">=":
                result = IOLogicalNotNodeGen.create(IOLessThanNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case "==":
                result = IOEqualNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "!=":
                result = IOLogicalNotNodeGen.create(IOEqualNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case "&&":
                result = new IOLogicalAndNode(leftUnboxed, rightUnboxed);
                break;
            case "||":
                result = new IOLogicalOrNode(leftUnboxed, rightUnboxed);
                break;
            default:
                throw new RuntimeException("unexpected operation: " + opToken.getText());
        }

        int startPos = leftNode.getSourceCharIndex();
        int length = rightNode.getSourceEndIndex() - startPos;
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public IOExpressionNode createWriteVariable(IOExpressionNode nameNode, IOExpressionNode valueNode, int startPos,
            int length, boolean forceLocal) {
        return createWriteVariable(nameNode, valueNode, null, startPos, length, forceLocal);
    }

    public IOExpressionNode createWriteVariable(IOExpressionNode nameNode, IOExpressionNode valueNode,
            Integer argumentIndex, int startPos, int length, boolean forceLocal) {
        if (nameNode == null || valueNode == null) {
            return null;
        }
        if (isAtLobby() && argumentIndex == null) {
            return null;
        }

        TruffleString name = ((IOStringLiteralNode) nameNode).executeGeneric(null);

        int contextLevel = Integer.MAX_VALUE;
        int frameSlot = -1;
        boolean newVariable = false;
        final Pair<Integer, Integer> foundSlot = methodScope.find(name);
        if (foundSlot != null) {
            contextLevel = foundSlot.a;
            frameSlot = foundSlot.b;
        }
        if (foundSlot == null || (forceLocal && contextLevel > 0)) {
            contextLevel = 0;
            frameSlot = methodScope.getOrAddLocal(name, argumentIndex);
            newVariable = true;
        }
        final IOExpressionNode result;
        assert frameSlot != -1;
        if (contextLevel == 0) {
            result = IOWriteLocalVariableNodeGen.create(valueNode, frameSlot, nameNode, newVariable);
        } else {
            assert contextLevel >= 0 && contextLevel < Integer.MAX_VALUE;
            result = IOWriteRemoteVariableNodeGen.create(valueNode, contextLevel, frameSlot, nameNode);
        }
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    // public IOExpressionNode createReadVariable(IOExpressionNode nameNode) {
    //     if (nameNode != null) {
    //         TruffleString name = ((IOStringLiteralNode) nameNode).executeGeneric(null);
    //         final IOExpressionNode result;
    //         final Pair<Integer, Integer> foundSlot = methodScope.find(name);
    //         if (foundSlot != null) {
    //             int contextLevel = foundSlot.a;
    //             int frameSlot = foundSlot.b;
    //             if (contextLevel == 0) {
    //                 result = IOReadLocalVariableNodeGen.create(frameSlot);
    //             } else {
    //                 result = IOReadRemoteVariableNodeGen.create(contextLevel, frameSlot);
    //             }
    //             if (nameNode.hasSource()) {
    //                 result.setSourceSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
    //             }
    //             result.addExpressionTag();
    //             return result;
    //         }
    //     }
    //     return null;
    // }

    public IOExpressionNode createReadLocalVariable(IOStringLiteralNode nameNode) {
        assert nameNode != null;
        TruffleString name = nameNode.executeGeneric(null);
        if (methodScope.locals.containsKey(name)) {
            int frameSlot = methodScope.locals.get(name);
            final IOExpressionNode result = IOReadLocalVariableNodeGen.create(frameSlot);
            if (nameNode.hasSource()) {
                result.setSourceSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
            }
            result.addExpressionTag();
            return result;
        }
        return null;
    }

    public IOExpressionNode createReadLocalVariable(IOExpressionNode nameNode, int startPos, int length) {
        if (isAtLobby()) {
            return null;
        }
        if (nameNode instanceof IOStringLiteralNode) {
            return createReadLocalVariable((IOStringLiteralNode) nameNode);
        }
        throw new NotImplementedException();
    }

    public IOExpressionNode createInvokeVariable(IOExpressionNode nameNode, List<IOExpressionNode> argumentNodes,
            int startPos, int length) {
        if (nameNode != null) {
            TruffleString name = ((IOStringLiteralNode) nameNode).executeGeneric(null);
            final IOExpressionNode result;
            final Pair<Integer, Integer> foundSlot = methodScope.find(name);
            if (foundSlot != null) {
                int contextLevel = foundSlot.a;
                int frameSlot = foundSlot.b;
                if (contextLevel == 0) {
                    result = IOInvokeLocalVariableNodeGen.create(frameSlot,
                            argumentNodes.toArray(new IOExpressionNode[argumentNodes.size()]));
                } else {
                    result = IOInvokeRemoteVariableNodeGen.create(contextLevel, frameSlot,
                            argumentNodes.toArray(new IOExpressionNode[argumentNodes.size()]));
                }
                result.setSourceSection(startPos, length);
                result.addExpressionTag();
                return result;
            }
        }
        return null;
    }

    public IOExpressionNode createReadSelf() {
        final IOStringLiteralNode selfNode;
        if (isAtLobby()) {
            selfNode = new IOStringLiteralNode(IOSymbols.UNDERSCORE);
        } else {
            selfNode = new IOStringLiteralNode(IOSymbols.SELF);
        }
        final IOExpressionNode result = createReadLocalVariable(selfNode);
        assert result != null;
        return result;
    }

    public IOStringLiteralNode createStringLiteral(Token literalToken, boolean removeQuotes) {
        final IOStringLiteralNode result = new IOStringLiteralNode(asTruffleString(literalToken, removeQuotes));
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    private TruffleString asTruffleString(Token literalToken, boolean removeQuotes) {
        int fromIndex = literalToken.getStartIndex();
        int length = literalToken.getStopIndex() - literalToken.getStartIndex() + 1;
        if (removeQuotes) {
            /* Remove the trailing and ending " */
            assert literalToken.getText().length() >= 2 && literalToken.getText().startsWith("\"")
                    && literalToken.getText().endsWith("\"");
            fromIndex += 1;
            length -= 2;
        }
        return sourceString.substringByteIndexUncached(fromIndex * 2, length * 2, IOLanguage.STRING_ENCODING, true);
    }

    public IOExpressionNode createNumericLiteral(Token literalToken) {
        IOExpressionNode result;
        try {
            result = new IOLongLiteralNode(Long.parseLong(literalToken.getText()));
        } catch (NumberFormatException ex) {
            try {
                result = new IODoubleLiteralNode(Double.parseDouble(literalToken.getText()));
            } catch (NumberFormatException e) {
                throw new RuntimeException("can not parse number: " + literalToken.getText());
            }
        }
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public IOExpressionNode createBoolean(Token literalToken) {
        IOExpressionNode result;
        result = new IOBooleanLiteralNode(literalToken.getText().equals("true"));
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public IOExpressionNode createNil(Token literalToken) {
        IOExpressionNode result;
        result = new IONilLiteralNode();
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public IOExpressionNode createListLiteral(List<IOExpressionNode> elementNodes, int startPos, int length) {
        final IOExpressionNode result = new IOListLiteralNode(
                elementNodes.toArray(new IOExpressionNode[elementNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IOExpressionNode createParenExpression(IOExpressionNode expressionNode, int startPos, int length) {
        if (expressionNode == null) {
            return null;
        }

        final IOParenExpressionNode result = new IOParenExpressionNode(expressionNode);
        result.setSourceSection(startPos, length);
        return result;
    }

    public IOExpressionNode createReadProperty(IOExpressionNode receiverNode, IOExpressionNode nameNode, int startPos,
            int length) {
        if (receiverNode == null || nameNode == null) {
            return null;
        }

        final IOExpressionNode result = IOReadPropertyNodeGen.create(receiverNode, nameNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IOExpressionNode createSequenceAt(IOExpressionNode receiverNode, IOExpressionNode indexNode, int startPos,
            int length) {
        if (receiverNode == null) {
            receiverNode = createReadSelf();
        }
        final IOExpressionNode result = IOSequenceAtNodeGen.create(receiverNode, indexNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        assert result != null;
        return result;
    }

    public IOExpressionNode createSequenceAtPut(IOExpressionNode receiverNode, IOExpressionNode indexNode,
            IOExpressionNode valueNode, int startPos, int length) {
        if (receiverNode == null || indexNode == null || valueNode == null) {
            return null;
        }

        final IOExpressionNode result = IOSequenceAtPutNodeGen.create(receiverNode, indexNode, valueNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public IOExpressionNode createReadSlot(IOExpressionNode receiverNode, IOExpressionNode nameNode, int startPos,
            int length) {
        IOExpressionNode result = null;
        if (receiverNode == null) {
            result = createReadLocalVariable(nameNode, startPos, length);
            if (result == null) {
                receiverNode = createReadSelf();
            }
        }
        if (result == null) {
            assert receiverNode != null;
            result = createReadProperty(receiverNode, nameNode, startPos, length);
        }
        assert result != null;
        return result;
    }

    public IOExpressionNode createWriteProperty(IOExpressionNode receiverNode, IOExpressionNode nameNode,
            IOExpressionNode valueNode, int startPos, int length) {
        if (receiverNode == null || nameNode == null || valueNode == null) {
            return null;
        }

        final IOExpressionNode result = IOWritePropertyNodeGen.create(receiverNode, nameNode, valueNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public IOExpressionNode createInvokeProperty(IOExpressionNode receiverNode, IOExpressionNode identifierNode,
            List<IOExpressionNode> argumentNodes, int startPos, int length) {
        if (identifierNode == null || containsNull(argumentNodes)) {
            return null;
        }

        final IOExpressionNode result = IOInvokePropertyNodeGen.create(receiverNode, identifierNode,
                argumentNodes.toArray(new IOExpressionNode[argumentNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public IOExpressionNode createInvokeSlot(IOExpressionNode receiverNode, IOExpressionNode identifierNode,
            List<IOExpressionNode> argumentNodes, int startPos, int length) {
        IOExpressionNode result = null;
        if (receiverNode == null) {
            result = createInvokeVariable(identifierNode, argumentNodes, startPos, length);
            if (result == null) {
                receiverNode = createReadSelf();
            }
        }
        if (result == null) {
            assert receiverNode != null;
            result = createInvokeProperty(receiverNode, identifierNode, argumentNodes, startPos, length);
        }
        return result;
    }

    public IOExpressionNode createWriteSlot(IOExpressionNode receiverNode, IOExpressionNode assignmentNameNode,
            IOExpressionNode valueNode, int startPos, int length) {
        IOExpressionNode result = null;
        if (receiverNode == null) {
            result = createWriteVariable(assignmentNameNode, valueNode, startPos, length, false);
            if (result == null) {
                receiverNode = createReadSelf();
            }
        }
        if (result == null) {
            assert receiverNode != null;
            result = createWriteProperty(receiverNode, assignmentNameNode, valueNode, startPos, length);
        }
        assert result != null;
        return result;
    }

    private static void srcFromToken(IOExpressionNode node, Token token) {
        node.setSourceSection(token.getStartIndex(), token.getText().length());
    }

    private static boolean containsNull(List<?> list) {
        for (Object e : list) {
            if (e == null) {
                return true;
            }
        }
        return false;
    }

}
