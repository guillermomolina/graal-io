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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import org.truffle.io.IOLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.arithmetic.AddNodeGen;
import org.truffle.io.nodes.arithmetic.DivNodeGen;
import org.truffle.io.nodes.arithmetic.MulNodeGen;
import org.truffle.io.nodes.arithmetic.SubNodeGen;
import org.truffle.io.nodes.controlflow.BreakNode;
import org.truffle.io.nodes.controlflow.ContinueNode;
import org.truffle.io.nodes.controlflow.DebuggerNode;
import org.truffle.io.nodes.controlflow.IfNode;
import org.truffle.io.nodes.controlflow.MethodBodyNode;
import org.truffle.io.nodes.controlflow.RepeatNode;
import org.truffle.io.nodes.controlflow.ReturnNode;
import org.truffle.io.nodes.controlflow.WhileNode;
import org.truffle.io.nodes.expression.BlockNode;
import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.expression.ParenExpressionNode;
import org.truffle.io.nodes.literals.BooleanLiteralNode;
import org.truffle.io.nodes.literals.DoubleLiteralNode;
import org.truffle.io.nodes.literals.ListLiteralNode;
import org.truffle.io.nodes.literals.LongLiteralNode;
import org.truffle.io.nodes.literals.MethodLiteralNode;
import org.truffle.io.nodes.literals.NilLiteralNode;
import org.truffle.io.nodes.literals.StringLiteralNode;
import org.truffle.io.nodes.logic.EqualNodeGen;
import org.truffle.io.nodes.logic.LessOrEqualNodeGen;
import org.truffle.io.nodes.logic.LessThanNodeGen;
import org.truffle.io.nodes.logic.LogicalAndNode;
import org.truffle.io.nodes.logic.LogicalNotNodeGen;
import org.truffle.io.nodes.logic.LogicalOrNode;
import org.truffle.io.nodes.root.IORootNode;
import org.truffle.io.nodes.sequences.SequenceAtNodeGen;
import org.truffle.io.nodes.sequences.SequenceAtPutNodeGen;
import org.truffle.io.nodes.util.UnboxNodeGen;
import org.truffle.io.nodes.variables.ForLocalVariableNode;
import org.truffle.io.nodes.variables.InvokeLocalVariableNodeGen;
import org.truffle.io.nodes.variables.InvokePropertyNodeGen;
import org.truffle.io.nodes.variables.InvokeRemoteVariableNodeGen;
import org.truffle.io.nodes.variables.ReadArgumentNode;
import org.truffle.io.nodes.variables.ReadLocalVariableNodeGen;
import org.truffle.io.nodes.variables.ReadPropertyNodeGen;
import org.truffle.io.nodes.variables.WriteLocalVariableNodeGen;
import org.truffle.io.nodes.variables.WritePropertyNodeGen;
import org.truffle.io.nodes.variables.WriteRemoteVariableNodeGen;
import org.truffle.io.runtime.Symbols;

public class NodeFactory {

    static class MethodScope {
        protected final MethodScope outer;
        protected int methodBodyStartPos; // includes parameter list
        protected int parameterCount;
        protected FrameDescriptor.Builder frameDescriptorBuilder;
        protected final List<TruffleString> argNames;
        protected final Map<TruffleString, Integer> locals;
        protected boolean inLoop;

        protected List<ExpressionNode> methodNodes;

        MethodScope(final MethodScope outer, int methodBodyStartPos) {
            this.outer = outer;
            this.methodBodyStartPos = methodBodyStartPos;
            this.argNames = new ArrayList<>();
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

        protected Integer getOrAddLocal(TruffleString name, Integer argumentIndex) {
            Integer frameSlot = locals.get(name);
            if (frameSlot == null) {
                frameSlot = frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, name, argumentIndex);
                locals.put(name, frameSlot);
            }
            return frameSlot;
        }

        public Pair<Integer, Integer> findOrAddLocal(TruffleString name, Integer argumentIndex,
                boolean forceLocalIfRemote) {
            int contextLevel = Integer.MAX_VALUE;
            int frameSlot = -1;
            final Pair<Integer, Integer> foundSlot = find(name);
            if (foundSlot != null) {
                contextLevel = foundSlot.a;
                frameSlot = foundSlot.b;
            }
            if (foundSlot == null || (forceLocalIfRemote && contextLevel > 0)) {
                contextLevel = 0;
                frameSlot = getOrAddLocal(name, argumentIndex);
            }
            return new Pair<Integer, Integer>(contextLevel, frameSlot);
        }
    }

    private final Source source;
    private final TruffleString sourceString;
    private final IOLanguage language;
    private MethodScope methodScope;

    public NodeFactory(IOLanguage language, Source source) {
        this.language = language;
        this.source = source;
        this.sourceString = Symbols.fromJavaString(source.getCharacters().toString());
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
        final ReadArgumentNode readArg = new ReadArgumentNode(methodScope.parameterCount);
        int startPos = nameToken.getStartIndex();
        int length = nameToken.getText().length();
        readArg.setSourceSection(startPos, length);
        StringLiteralNode nameNode = createStringLiteral(nameToken, false);
        methodScope.argNames.add(nameNode.getValue());
        ExpressionNode assignmentNode = createWriteVariable(nameNode, readArg, methodScope.parameterCount, startPos,
                length, true);
        assert assignmentNode != null;
        methodScope.methodNodes.add(assignmentNode);
        methodScope.parameterCount++;
    }

    public void addSelfParameter() {
        assert methodScope.parameterCount == 0;
        final ReadArgumentNode readArg = new ReadArgumentNode(methodScope.parameterCount);
        final StringLiteralNode selfNode;
        if (isAtLobby()) {
            selfNode = new StringLiteralNode(Symbols.UNDERSCORE);
        } else {
            selfNode = new StringLiteralNode(Symbols.SELF);
        }
        ExpressionNode assignmentNode = createWriteVariable(selfNode, readArg, methodScope.parameterCount, 0, 0,
                true);
        assert assignmentNode != null;
        methodScope.methodNodes.add(assignmentNode);
        methodScope.parameterCount++;
    }

    public void addContextParameter() {
        assert methodScope.parameterCount == 1;
        final ReadArgumentNode readArg = new ReadArgumentNode(methodScope.parameterCount);
        final StringLiteralNode callNode;
        if (isAtLobby()) {
            callNode = new StringLiteralNode(Symbols.fromJavaString("$"));
        } else {
            callNode = new StringLiteralNode(Symbols.fromJavaString("call"));
        }
        ExpressionNode assignmentNode = createWriteVariable(callNode, readArg, methodScope.parameterCount, 0, 0,
                true);
        assert assignmentNode != null;
        methodScope.methodNodes.add(assignmentNode);
        methodScope.parameterCount++;
    }

    public MethodLiteralNode finishMethod(ExpressionNode bodyNode, int startPos, int length) {
        MethodLiteralNode methodLiteralNode = null;
        if (bodyNode != null) {
            methodScope.methodNodes.add(bodyNode);
            final int bodyEndPos = bodyNode.getSourceEndIndex();
            int methodBodyLength = bodyEndPos - methodScope.methodBodyStartPos;
            final SourceSection methodSrc = source.createSection(methodScope.methodBodyStartPos, methodBodyLength);
            final ExpressionNode methodBlock = createBlock(methodScope.methodNodes, methodScope.parameterCount,
                    methodScope.methodBodyStartPos, methodBodyLength);
            final MethodBodyNode methodBodyNode = new MethodBodyNode(methodBlock);
            methodBodyNode.setSourceSection(methodSrc.getCharIndex(), methodSrc.getCharLength());
            final IORootNode rootNode = new IORootNode(language, methodScope.frameDescriptorBuilder.build(),
                    methodBodyNode,
                    methodSrc);
            TruffleString[] argNames = methodScope.argNames
                    .toArray(new TruffleString[methodScope.argNames.size()]);
            methodLiteralNode = new MethodLiteralNode(rootNode, argNames);
            methodLiteralNode.setSourceSection(startPos, length);
        }

        methodScope = methodScope.outer;
        return methodLiteralNode;
    }

    protected ExpressionNode createBlock(List<ExpressionNode> bodyNodes, int startPos, int length) {
        return createBlock(bodyNodes, 0, startPos, length);
    }

    protected ExpressionNode createBlock(List<ExpressionNode> bodyNodes, int skipCount, int startPos, int length) {
        if (containsNull(bodyNodes)) {
            return null;
        }

        List<ExpressionNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenBlocks(bodyNodes, flattenedNodes);
        int n = flattenedNodes.size();
        for (int i = skipCount; i < n; i++) {
            ExpressionNode expression = flattenedNodes.get(i);
            if (expression.hasSource() && !isHaltInCondition(expression)) {
                expression.addExpressionTag();
            }
        }
        BlockNode blockNode = new BlockNode(flattenedNodes.toArray(new ExpressionNode[flattenedNodes.size()]));
        blockNode.setSourceSection(startPos, length);
        return blockNode;
    }

    public void startLoop() {
        methodScope.inLoop = true;
    }

    public ExpressionNode createLoopBlock(ExpressionNode loopNode, int startPos, int length) {
        List<ExpressionNode> bodyNodes = new ArrayList<>();
        bodyNodes.add(loopNode);
        return createBlock(bodyNodes, 0, startPos, length);
    }

    private static boolean isHaltInCondition(ExpressionNode expression) {
        return (expression instanceof IfNode) || (expression instanceof WhileNode);
    }

    private void flattenBlocks(Iterable<? extends ExpressionNode> bodyNodes, List<ExpressionNode> flattenedNodes) {
        for (ExpressionNode n : bodyNodes) {
            if (n instanceof BlockNode) {
                flattenBlocks(((BlockNode) n).getExpressions(), flattenedNodes);
            } else {
                flattenedNodes.add(n);
            }
        }
    }

    ExpressionNode createDebugger(Token debuggerToken) {
        final DebuggerNode debuggerNode = new DebuggerNode();
        srcFromToken(debuggerNode, debuggerToken);
        return debuggerNode;
    }

    public ExpressionNode createBreak(Token breakToken) {
        if (methodScope.inLoop) {
            final BreakNode breakNode = new BreakNode();
            srcFromToken(breakNode, breakToken);
            return breakNode;
        }
        return null;
    }

    public ExpressionNode createContinue(Token continueToken) {
        if (methodScope.inLoop) {
            final ContinueNode continueNode = new ContinueNode();
            srcFromToken(continueNode, continueToken);
            return continueNode;
        }
        return null;
    }

    public ExpressionNode createWhile(Token whileToken, ExpressionNode conditionNode, ExpressionNode bodyNode) {
        WhileNode whileNode = null;
        if (conditionNode != null && bodyNode != null) {
            conditionNode.addExpressionTag();
            final int startPos = whileToken.getStartIndex();
            final int end = bodyNode.getSourceEndIndex();
            whileNode = new WhileNode(conditionNode, bodyNode);
            whileNode.setSourceSection(startPos, end - startPos);
        }
        return whileNode;
    }

    public ExpressionNode createRepeat(ExpressionNode receiverNode, ExpressionNode bodyNode, int startPos,
            int length) {
        RepeatNode repeatNode = null;
        if (receiverNode != null && bodyNode != null) {
            receiverNode.addExpressionTag();
            repeatNode = new RepeatNode(receiverNode, bodyNode);
            repeatNode.setSourceSection(startPos, length);
        }
        return repeatNode;
    }

    public ExpressionNode createIf(Token ifToken, ExpressionNode conditionNode, ExpressionNode thenPartNode,
            ExpressionNode elsePartNode) {
        if (conditionNode == null || thenPartNode == null) {
            return null;
        }

        conditionNode.addExpressionTag();
        final int startPos = ifToken.getStartIndex();
        final int end = elsePartNode == null ? thenPartNode.getSourceEndIndex() : elsePartNode.getSourceEndIndex();
        final IfNode ifNode = new IfNode(conditionNode, thenPartNode, elsePartNode);
        ifNode.setSourceSection(startPos, end - startPos);
        return ifNode;
    }

    public ExpressionNode createReturn(Token t, ExpressionNode valueNodeOrNull) {
        final int startPos = t.getStartIndex();
        final int length;
        ExpressionNode valueNode = valueNodeOrNull;
        if (valueNode == null) {
            length = t.getText().length();
            valueNode = createReadSelf();
        } else {
            length = valueNode.getSourceEndIndex() - startPos;
        }
        final ReturnNode returnNode = new ReturnNode(valueNode);
        returnNode.setSourceSection(startPos, length);
        return returnNode;
    }

    public ExpressionNode createBinary(Token opToken, ExpressionNode leftNode, ExpressionNode rightNode) {
        if (leftNode == null || rightNode == null) {
            return null;
        }
        final ExpressionNode leftUnboxed = UnboxNodeGen.create(leftNode);
        final ExpressionNode rightUnboxed = UnboxNodeGen.create(rightNode);

        final ExpressionNode result;
        switch (opToken.getText()) {
            case "+":
                result = AddNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "*":
                result = MulNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "/":
                result = DivNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "-":
                result = SubNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "<":
                result = LessThanNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "<=":
                result = LessOrEqualNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case ">":
                result = LogicalNotNodeGen.create(LessOrEqualNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case ">=":
                result = LogicalNotNodeGen.create(LessThanNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case "==":
                result = EqualNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            case "!=":
                result = LogicalNotNodeGen.create(EqualNodeGen.create(leftUnboxed, rightUnboxed));
                break;
            case "&&":
                result = new LogicalAndNode(leftUnboxed, rightUnboxed);
                break;
            case "||":
                result = new LogicalOrNode(leftUnboxed, rightUnboxed);
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

    public ExpressionNode createWriteVariable(ExpressionNode nameNode, ExpressionNode valueNode, int startPos,
            int length, boolean forceLocal) {
        return createWriteVariable(nameNode, valueNode, null, startPos, length, forceLocal);
    }

    public ExpressionNode createWriteVariable(ExpressionNode nameNode, ExpressionNode valueNode,
            Integer argumentIndex, int startPos, int length, boolean forceLocal) {
        if (nameNode == null || valueNode == null) {
            return null;
        }
        if (isAtLobby() && argumentIndex == null) {
            return null;
        }
        assert nameNode instanceof StringLiteralNode;
        TruffleString name = ((StringLiteralNode) nameNode).executeGeneric(null);
        final Pair<Integer, Integer> foundSlot = methodScope.findOrAddLocal(name, argumentIndex, forceLocal);
        int contextLevel = foundSlot.a;
        int frameSlot = foundSlot.b;
        final ExpressionNode result;
        assert frameSlot != -1;
        if (contextLevel == 0) {
            result = WriteLocalVariableNodeGen.create(valueNode, frameSlot, nameNode);
        } else {
            assert contextLevel >= 0 && contextLevel < Integer.MAX_VALUE;
            result = WriteRemoteVariableNodeGen.create(valueNode, contextLevel, frameSlot);
        }
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    // public IOExpressionNode createReadVariable(IOExpressionNode nameNode) {
    //     if (nameNode != null) {
    //         assert nameNode instanceof IOStringLiteralNode; 
    //         TruffleString name = ((IOStringLiteralNode) nameNode).executeGeneric(null);
    //         final IOExpressionNode result;
    //         final Pair<Integer, Integer> foundSlot = methodScope.find(name);
    //         if (foundSlot != null) {
    //             int contextLevel = foundSlot.a;
    //             int frameSlot = foundSlot.b;
    //             if (contextLevel == 0) {
    //                 result = ReadLocalVariableNodeGen.create(frameSlot);
    //             } else {
    //                 result = ReadRemoteVariableNodeGen.create(contextLevel, frameSlot);
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

    public ExpressionNode createReadLocalVariable(StringLiteralNode nameNode) {
        assert nameNode != null;
        TruffleString name = nameNode.executeGeneric(null);
        if (methodScope.locals.containsKey(name)) {
            int frameSlot = methodScope.locals.get(name);
            final ExpressionNode result = ReadLocalVariableNodeGen.create(frameSlot);
            if (nameNode.hasSource()) {
                result.setSourceSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
            }
            result.addExpressionTag();
            return result;
        }
        return null;
    }

    public ExpressionNode createReadLocalVariable(ExpressionNode nameNode, int startPos, int length) {
        if (isAtLobby()) {
            return null;
        }
        if (nameNode instanceof StringLiteralNode) {
            return createReadLocalVariable((StringLiteralNode) nameNode);
        }
        throw new NotImplementedException();
    }

    public ExpressionNode createInvokeVariable(ExpressionNode slotNameNode, List<ExpressionNode> argumentNodes,
            int startPos, int length) {
        if (slotNameNode != null) {
            assert slotNameNode instanceof StringLiteralNode;
            TruffleString name = ((StringLiteralNode) slotNameNode).executeGeneric(null);
            final Pair<Integer, Integer> foundSlot = methodScope.find(name);
            if (foundSlot != null) {
                int contextLevel = foundSlot.a;
                int frameSlot = foundSlot.b;
                final ExpressionNode result;
                if (contextLevel == 0) {
                    result = InvokeLocalVariableNodeGen.create(frameSlot,
                            argumentNodes.toArray(new ExpressionNode[argumentNodes.size()]));
                } else {
                    result = InvokeRemoteVariableNodeGen.create(contextLevel, frameSlot,
                            argumentNodes.toArray(new ExpressionNode[argumentNodes.size()]));
                }
                result.setSourceSection(startPos, length);
                result.addExpressionTag();
                return result;
            }
        }
        return null;
    }

    public ExpressionNode createForVariable(ExpressionNode slotNameNode, ExpressionNode startValueNode,
            ExpressionNode endValueNode, ExpressionNode stepValueNode, ExpressionNode bodyNode, int startPos,
            int length) {
        ForLocalVariableNode forNode = null;
        if (slotNameNode != null && startValueNode != null && endValueNode != null && bodyNode != null) {
            assert slotNameNode instanceof StringLiteralNode;
            TruffleString name = ((StringLiteralNode) slotNameNode).executeGeneric(null);
            final Pair<Integer, Integer> foundSlot = methodScope.findOrAddLocal(name, null, false);
            int contextLevel = foundSlot.a;
            int slotFrameIndex = foundSlot.b;
            startValueNode.addExpressionTag();
            endValueNode.addExpressionTag();
            if (stepValueNode != null) {
                stepValueNode.addExpressionTag();
            }
            final ExpressionNode result;
            if (contextLevel == 0) {
                result = new ForLocalVariableNode(slotFrameIndex, slotNameNode, startValueNode, endValueNode,
                        stepValueNode, bodyNode);
            } else {
                throw new NotImplementedException();
                //result = new IOForRemoteVariableNode(contextLevel, slotFrameIndex, slotNameNode, startValueNode, endValueNode, stepValueNode, bodyNode);
            }
            result.setSourceSection(startPos, length);
            result.addExpressionTag();
            return result;
        }
        return forNode;
    }

    public ExpressionNode createReadSelf() {
        final StringLiteralNode selfNode;
        if (isAtLobby()) {
            selfNode = new StringLiteralNode(Symbols.UNDERSCORE);
        } else {
            selfNode = new StringLiteralNode(Symbols.SELF);
        }
        final ExpressionNode result = createReadLocalVariable(selfNode);
        assert result != null;
        return result;
    }

    public StringLiteralNode createStringLiteral(Token literalToken, boolean removeQuotes) {
        final StringLiteralNode result = new StringLiteralNode(asTruffleString(literalToken, removeQuotes));
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

    public ExpressionNode createNumericLiteral(Token literalToken) {
        ExpressionNode result;
        try {
            result = new LongLiteralNode(Long.parseLong(literalToken.getText()));
        } catch (NumberFormatException ex) {
            try {
                result = new DoubleLiteralNode(Double.parseDouble(literalToken.getText()));
            } catch (NumberFormatException e) {
                throw new RuntimeException("can not parse number: " + literalToken.getText());
            }
        }
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public ExpressionNode createBoolean(Token literalToken) {
        ExpressionNode result;
        result = new BooleanLiteralNode(literalToken.getText().equals("true"));
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public ExpressionNode createNil(Token literalToken) {
        ExpressionNode result;
        result = new NilLiteralNode();
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public ExpressionNode createListLiteral(List<ExpressionNode> elementNodes, int startPos, int length) {
        final ExpressionNode result = new ListLiteralNode(
                elementNodes.toArray(new ExpressionNode[elementNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public ExpressionNode createParenExpression(ExpressionNode expressionNode, int startPos, int length) {
        if (expressionNode == null) {
            return null;
        }

        final ParenExpressionNode result = new ParenExpressionNode(expressionNode);
        result.setSourceSection(startPos, length);
        return result;
    }

    public ExpressionNode createReadProperty(ExpressionNode receiverNode, ExpressionNode nameNode, int startPos,
            int length) {
        if (receiverNode == null || nameNode == null) {
            return null;
        }

        final ExpressionNode result = ReadPropertyNodeGen.create(receiverNode, nameNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public ExpressionNode createSequenceAt(ExpressionNode receiverNode, ExpressionNode indexNode, int startPos,
            int length) {
        if (receiverNode == null) {
            receiverNode = createReadSelf();
        }
        final ExpressionNode result = SequenceAtNodeGen.create(receiverNode, indexNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        assert result != null;
        return result;
    }

    public ExpressionNode createSequenceAtPut(ExpressionNode receiverNode, ExpressionNode indexNode,
            ExpressionNode valueNode, int startPos, int length) {
        if (receiverNode == null || indexNode == null || valueNode == null) {
            return null;
        }

        final ExpressionNode result = SequenceAtPutNodeGen.create(receiverNode, indexNode, valueNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public ExpressionNode createReadSlot(ExpressionNode receiverNode, ExpressionNode nameNode, int startPos,
            int length) {
        ExpressionNode result = null;
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

    public ExpressionNode createWriteProperty(ExpressionNode receiverNode, ExpressionNode nameNode,
            ExpressionNode valueNode, int startPos, int length) {
        if (receiverNode == null || nameNode == null || valueNode == null) {
            return null;
        }

        final ExpressionNode result = WritePropertyNodeGen.create(receiverNode, nameNode, valueNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public ExpressionNode createInvokeProperty(ExpressionNode receiverNode, ExpressionNode identifierNode,
            List<ExpressionNode> argumentNodes, int startPos, int length) {
        if (identifierNode == null || containsNull(argumentNodes)) {
            return null;
        }

        final ExpressionNode result = InvokePropertyNodeGen.create(receiverNode, identifierNode,
                argumentNodes.toArray(new ExpressionNode[argumentNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public ExpressionNode createInvokeSlot(ExpressionNode receiverNode, ExpressionNode identifierNode,
            List<ExpressionNode> argumentNodes, int startPos, int length) {
        ExpressionNode result = null;
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

    public ExpressionNode createWriteSlot(ExpressionNode receiverNode, ExpressionNode assignmentNameNode,
            ExpressionNode valueNode, int startPos, int length) {
        ExpressionNode result = null;
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

    private static void srcFromToken(ExpressionNode node, Token token) {
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
