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
import org.truffle.io.nodes.controlflow.RepeatNode;
import org.truffle.io.nodes.controlflow.ReturnNode;
import org.truffle.io.nodes.controlflow.WhileNode;
import org.truffle.io.nodes.expression.BlockNode;
import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.expression.MethodBodyNode;
import org.truffle.io.nodes.expression.ParenExpressionNode;
import org.truffle.io.nodes.literals.BooleanLiteralNode;
import org.truffle.io.nodes.literals.DoubleLiteralNode;
import org.truffle.io.nodes.literals.FunctionLiteralNode;
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
import org.truffle.io.nodes.slots.ForLocalSlotNode;
import org.truffle.io.nodes.slots.InvokeLocalSlotNodeGen;
import org.truffle.io.nodes.slots.InvokeMemberNodeGen;
import org.truffle.io.nodes.slots.InvokeRemoteSlotNodeGen;
import org.truffle.io.nodes.slots.ReadArgumentNode;
import org.truffle.io.nodes.slots.ReadLocalSlotNodeGen;
import org.truffle.io.nodes.slots.ReadMemberNodeGen;
import org.truffle.io.nodes.slots.WriteLocalSlotNodeGen;
import org.truffle.io.nodes.slots.WriteMemberNodeGen;
import org.truffle.io.nodes.slots.WriteRemoteSlotNodeGen;
import org.truffle.io.nodes.util.UnboxNodeGen;
import org.truffle.io.runtime.Symbols;
import org.truffle.io.runtime.objects.IOLocals;

public class NodeFactory {

    static class SlotScope {
        protected final SlotScope outer;
        protected int bodyStartPos;
        protected int parameterCount;
        protected FrameDescriptor.Builder frameDescriptorBuilder;
        protected final List<TruffleString> argumentsNames;
        protected final Map<TruffleString, Integer> localSlots;
        protected boolean inLoop;
        protected List<ExpressionNode> initializationNodes;

        SlotScope(final SlotScope outer, int bodyStartPos) {
            this.outer = outer;
            this.bodyStartPos = bodyStartPos;
            this.argumentsNames = new ArrayList<>();
            this.parameterCount = 0;
            this.frameDescriptorBuilder = FrameDescriptor.newBuilder();
            this.initializationNodes = new ArrayList<>();
            this.localSlots = new HashMap<>();
            this.inLoop = false;
        }

        public Pair<Integer, Integer> findSlot(TruffleString slotName) {
            SlotScope scope = this;
            int level = 0;
            while (scope != null) {
                if (scope.localSlots.containsKey(slotName)) {
                    return new Pair<Integer, Integer>(level, scope.localSlots.get(slotName));
                }
                level++;
                scope = scope.outer;
            }
            return null;
        }

        protected Integer getOrAddLocalSlot(TruffleString slotName, Integer argumentIndex) {
            Integer frameSlot = localSlots.get(slotName);
            if (frameSlot == null) {
                frameSlot = frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, slotName, argumentIndex);
                localSlots.put(slotName, frameSlot);
            }
            return frameSlot;
        }

        public Pair<Integer, Integer> findOrAddLocalSlot(TruffleString slotName, Integer argumentIndex,
                boolean forceLocalIfRemote) {
            int contextLevel = Integer.MAX_VALUE;
            int slotIndex = -1;
            final Pair<Integer, Integer> foundSlot = findSlot(slotName);
            if (foundSlot != null) {
                contextLevel = foundSlot.a;
                slotIndex = foundSlot.b;
            }
            if (foundSlot == null || (forceLocalIfRemote && contextLevel > 0)) {
                contextLevel = 0;
                slotIndex = getOrAddLocalSlot(slotName, argumentIndex);
            }
            return new Pair<Integer, Integer>(contextLevel, slotIndex);
        }

        public boolean hasLocals() {
            return !localSlots.isEmpty();
        }
    }

    private final Source source;
    private final TruffleString sourceString;
    private final IOLanguage language;
    private SlotScope currentScope;

    public NodeFactory(IOLanguage language, Source source) {
        this.language = language;
        this.source = source;
        this.sourceString = Symbols.fromJavaString(source.getCharacters().toString());
    }

    public boolean hasLocals() {
        return currentScope.hasLocals();
    }

    public void startDo(Token bodyStartToken) {
        currentScope = new SlotScope(currentScope, bodyStartToken.getStartIndex());
    }

    public FunctionLiteralNode finishDo(ExpressionNode bodyNode, int startPos, int length) {
        FunctionLiteralNode functionLiteralNode = null;
        if (bodyNode != null) {
            assert !hasLocals();
            final int bodyEndPos = bodyNode.getSourceEndIndex();
            int methodBodyLength = bodyEndPos - currentScope.bodyStartPos;
            final SourceSection methodSrc = source.createSection(currentScope.bodyStartPos, methodBodyLength);
            final MethodBodyNode methodBodyNode = new MethodBodyNode(bodyNode);
            methodBodyNode.setSourceSection(methodSrc.getCharIndex(), methodSrc.getCharLength());
            final IORootNode rootNode = new IORootNode(language, currentScope.frameDescriptorBuilder.build(),
                    methodBodyNode, methodSrc);
            functionLiteralNode = new FunctionLiteralNode(Symbols.fromJavaString("do"), rootNode);
            functionLiteralNode.setSourceSection(startPos, length);
        }
        currentScope = currentScope.outer;
        return functionLiteralNode;
    }

    public void startMethod(Token bodyStartToken) {
        currentScope = new SlotScope(currentScope, bodyStartToken.getStartIndex());
        setupLocals();
    }

    public void addFormalParameter(Token nameToken) {
        assert currentScope.parameterCount >= IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;
        final ReadArgumentNode readArg = new ReadArgumentNode(currentScope.parameterCount);
        int startPos = nameToken.getStartIndex();
        int length = nameToken.getText().length();
        readArg.setSourceSection(startPos, length);
        StringLiteralNode nameNode = createStringLiteral(nameToken, false);
        currentScope.argumentsNames.add(nameNode.getValue());
        ExpressionNode assignmentNode = createWriteSlot(nameNode, readArg, currentScope.parameterCount, startPos,
                length, true);
        assert assignmentNode != null;
        currentScope.initializationNodes.add(assignmentNode);
        currentScope.parameterCount++;
    }

    public void setupLocals() {
        assert currentScope.parameterCount == 0;
        final ReadArgumentNode readCallArgumentNode = new ReadArgumentNode(IOLocals.CALL_ARGUMENT_INDEX);
        currentScope.parameterCount = IOLocals.FIRST_PARAMETER_ARGUMENT_INDEX;

        final StringLiteralNode callIdentifierNode = new StringLiteralNode(Symbols.fromJavaString("call"));
        ExpressionNode callAssignmentNode = createWriteSlot(callIdentifierNode, readCallArgumentNode, 0, 0, 0,
                true);
        assert callAssignmentNode != null;
        currentScope.initializationNodes.add(callAssignmentNode);

        final StringLiteralNode targetIdentifierNode = new StringLiteralNode(Symbols.fromJavaString("target"));
        ExpressionNode readTargetNode = createReadProperty(callAssignmentNode, targetIdentifierNode, 0, 0);
        final StringLiteralNode selfIdentifierNode = new StringLiteralNode(Symbols.SELF);
        ExpressionNode selfAssignmentNode = createWriteSlot(selfIdentifierNode, readTargetNode, 0, 0,
                true);
        assert selfAssignmentNode != null;
        currentScope.initializationNodes.add(selfAssignmentNode);

    }

    public MethodLiteralNode finishMethod(ExpressionNode bodyNode, int startPos, int length) {
        MethodLiteralNode methodLiteralNode = null;
        if (bodyNode != null) {
            currentScope.initializationNodes.add(bodyNode);
            final int bodyEndPos = bodyNode.getSourceEndIndex();
            int methodBodyLength = bodyEndPos - currentScope.bodyStartPos;
            final SourceSection methodSrc = source.createSection(currentScope.bodyStartPos, methodBodyLength);
            final ExpressionNode methodBlock = createBlock(currentScope.initializationNodes,
                    currentScope.parameterCount,
                    currentScope.bodyStartPos, methodBodyLength);
            final MethodBodyNode methodBodyNode = new MethodBodyNode(methodBlock);
            methodBodyNode.setSourceSection(methodSrc.getCharIndex(), methodSrc.getCharLength());
            final IORootNode rootNode = new IORootNode(language, currentScope.frameDescriptorBuilder.build(),
                    methodBodyNode,
                    methodSrc);
            TruffleString[] argNames = currentScope.argumentsNames
                    .toArray(new TruffleString[currentScope.argumentsNames.size()]);
            methodLiteralNode = new MethodLiteralNode(rootNode, argNames);
            methodLiteralNode.setSourceSection(startPos, length);
        }

        currentScope = currentScope.outer;
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
        currentScope.inLoop = true;
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
        if (currentScope.inLoop) {
            final BreakNode breakNode = new BreakNode();
            srcFromToken(breakNode, breakToken);
            return breakNode;
        }
        return null;
    }

    public ExpressionNode createContinue(Token continueToken) {
        if (currentScope.inLoop) {
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

    public ExpressionNode createWriteSlot(ExpressionNode nameNode, ExpressionNode valueNode, int startPos,
            int length, boolean forceLocal) {
        return createWriteSlot(nameNode, valueNode, null, startPos, length, forceLocal);
    }

    public ExpressionNode createWriteSlot(ExpressionNode nameNode, ExpressionNode valueNode,
            Integer argumentIndex, int startPos, int length, boolean forceLocal) {
        if (nameNode == null || valueNode == null) {
            return null;
        }
        if (!hasLocals() && argumentIndex == null) {
            return null;
        }
        assert nameNode instanceof StringLiteralNode;
        TruffleString name = ((StringLiteralNode) nameNode).executeGeneric(null);
        final Pair<Integer, Integer> foundSlot = currentScope.findOrAddLocalSlot(name, argumentIndex, forceLocal);
        int contextLevel = foundSlot.a;
        int frameSlot = foundSlot.b;
        final ExpressionNode result;
        assert frameSlot != -1;
        if (contextLevel == 0) {
            result = WriteLocalSlotNodeGen.create(valueNode, frameSlot, nameNode);
        } else {
            assert contextLevel >= 0 && contextLevel < Integer.MAX_VALUE;
            result = WriteRemoteSlotNodeGen.create(valueNode, contextLevel, frameSlot);
        }
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    // public IOExpressionNode createReadSlot(IOExpressionNode nameNode) {
    //     if (nameNode != null) {
    //         assert nameNode instanceof IOStringLiteralNode; 
    //         TruffleString name = ((IOStringLiteralNode) nameNode).executeGeneric(null);
    //         final IOExpressionNode result;
    //         final Pair<Integer, Integer> foundSlot = methodScope.find(name);
    //         if (foundSlot != null) {
    //             int contextLevel = foundSlot.a;
    //             int frameSlot = foundSlot.b;
    //             if (contextLevel == 0) {
    //                 result = ReadLocalSlotNodeGen.create(frameSlot);
    //             } else {
    //                 result = ReadRemoteSlotNodeGen.create(contextLevel, frameSlot);
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

    public ExpressionNode createReadLocalSlot(StringLiteralNode nameNode) {
        assert nameNode != null;
        TruffleString name = nameNode.executeGeneric(null);
        if (currentScope.localSlots.containsKey(name)) {
            int frameSlot = currentScope.localSlots.get(name);
            final ExpressionNode result = ReadLocalSlotNodeGen.create(frameSlot);
            if (nameNode.hasSource()) {
                result.setSourceSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
            }
            result.addExpressionTag();
            return result;
        }
        return null;
    }

    public ExpressionNode createReadLocalSlot(ExpressionNode nameNode, int startPos, int length) {
        if (!hasLocals()) {
            return null;
        }
        if (nameNode instanceof StringLiteralNode) {
            return createReadLocalSlot((StringLiteralNode) nameNode);
        }
        throw new NotImplementedException();
    }

    public ExpressionNode createInvokeSlot(ExpressionNode slotNameNode, List<ExpressionNode> argumentNodes,
            int startPos, int length) {
        if (slotNameNode != null) {
            assert slotNameNode instanceof StringLiteralNode;
            TruffleString name = ((StringLiteralNode) slotNameNode).executeGeneric(null);
            final Pair<Integer, Integer> foundSlot = currentScope.findSlot(name);
            if (foundSlot != null) {
                int contextLevel = foundSlot.a;
                int frameSlot = foundSlot.b;
                final ExpressionNode result;
                if (contextLevel == 0) {
                    result = InvokeLocalSlotNodeGen.create(frameSlot,
                            argumentNodes.toArray(new ExpressionNode[argumentNodes.size()]));
                } else {
                    result = InvokeRemoteSlotNodeGen.create(contextLevel, frameSlot,
                            argumentNodes.toArray(new ExpressionNode[argumentNodes.size()]));
                }
                result.setSourceSection(startPos, length);
                result.addExpressionTag();
                return result;
            }
        }
        return null;
    }

    public ExpressionNode createForSlot(ExpressionNode slotNameNode, ExpressionNode startValueNode,
            ExpressionNode endValueNode, ExpressionNode stepValueNode, ExpressionNode bodyNode, int startPos,
            int length) {
        ForLocalSlotNode forNode = null;
        if (slotNameNode != null && startValueNode != null && endValueNode != null && bodyNode != null) {
            assert slotNameNode instanceof StringLiteralNode;
            TruffleString name = ((StringLiteralNode) slotNameNode).executeGeneric(null);
            final Pair<Integer, Integer> foundSlot = currentScope.findOrAddLocalSlot(name, null, false);
            int contextLevel = foundSlot.a;
            int slotFrameIndex = foundSlot.b;
            startValueNode.addExpressionTag();
            endValueNode.addExpressionTag();
            if (stepValueNode != null) {
                stepValueNode.addExpressionTag();
            }
            final ExpressionNode result;
            if (contextLevel == 0) {
                result = new ForLocalSlotNode(slotFrameIndex, slotNameNode, startValueNode, endValueNode,
                        stepValueNode, bodyNode);
            } else {
                throw new NotImplementedException();
                //result = new IOForRemoteSlotNode(contextLevel, slotFrameIndex, slotNameNode, startValueNode, endValueNode, stepValueNode, bodyNode);
            }
            result.setSourceSection(startPos, length);
            result.addExpressionTag();
            return result;
        }
        return forNode;
    }

    public ExpressionNode createReadSelf() {
        if (!hasLocals()) {
            return new ReadArgumentNode(IOLocals.TARGET_ARGUMENT_INDEX);
        }
        final StringLiteralNode selfNode = new StringLiteralNode(Symbols.SELF);
        final ExpressionNode result = createReadLocalSlot(selfNode);
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

        final ExpressionNode result = ReadMemberNodeGen.create(receiverNode, nameNode);
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
            result = createReadLocalSlot(nameNode, startPos, length);
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

        final ExpressionNode result = WriteMemberNodeGen.create(receiverNode, nameNode, valueNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public ExpressionNode createInvokeProperty(ExpressionNode receiverNode, ExpressionNode identifierNode,
            List<ExpressionNode> argumentNodes, int startPos, int length) {
        if (identifierNode == null || containsNull(argumentNodes)) {
            return null;
        }

        final ExpressionNode result = InvokeMemberNodeGen.create(receiverNode, identifierNode,
                argumentNodes.toArray(new ExpressionNode[argumentNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public ExpressionNode createInvokeSlot(ExpressionNode receiverNode, ExpressionNode identifierNode,
            List<ExpressionNode> argumentNodes, int startPos, int length) {
        ExpressionNode result = null;
        if (receiverNode == null) {
            result = createInvokeSlot(identifierNode, argumentNodes, startPos, length);
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
            result = createWriteSlot(assignmentNameNode, valueNode, startPos, length, false);
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
