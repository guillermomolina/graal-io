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
package org.iolanguage.parser;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

import org.antlr.v4.runtime.Token;
import org.iolanguage.IoLanguage;
import org.iolanguage.NotImplementedException;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.binary.AddNodeGen;
import org.iolanguage.nodes.binary.ConcatNodeGen;
import org.iolanguage.nodes.binary.DivNodeGen;
import org.iolanguage.nodes.binary.MulNodeGen;
import org.iolanguage.nodes.binary.SubNodeGen;
import org.iolanguage.nodes.controlflow.BreakNode;
import org.iolanguage.nodes.controlflow.ContinueNode;
import org.iolanguage.nodes.controlflow.DebuggerNode;
import org.iolanguage.nodes.controlflow.ForNode;
import org.iolanguage.nodes.controlflow.IfNode;
import org.iolanguage.nodes.controlflow.RepeatNode;
import org.iolanguage.nodes.controlflow.ReturnNode;
import org.iolanguage.nodes.controlflow.TryCatchUndefinedNameNode;
import org.iolanguage.nodes.controlflow.TryNode;
import org.iolanguage.nodes.controlflow.WhileNode;
import org.iolanguage.nodes.expression.DoReadNodeGen;
import org.iolanguage.nodes.expression.ExpressionNode;
import org.iolanguage.nodes.expression.InvokeNodeGen;
import org.iolanguage.nodes.expression.MethodBodyNode;
import org.iolanguage.nodes.expression.ParenExpressionNode;
import org.iolanguage.nodes.expression.ThisLocalContextNodeGen;
import org.iolanguage.nodes.literals.BlockLiteralNode;
import org.iolanguage.nodes.literals.BooleanLiteralNode;
import org.iolanguage.nodes.literals.DoubleLiteralNode;
import org.iolanguage.nodes.literals.FunctionLiteralNode;
import org.iolanguage.nodes.literals.ListLiteralNode;
import org.iolanguage.nodes.literals.LongLiteralNode;
import org.iolanguage.nodes.literals.MethodLiteralNode;
import org.iolanguage.nodes.literals.NilLiteralNode;
import org.iolanguage.nodes.literals.StringLiteralNode;
import org.iolanguage.nodes.logic.EqualNodeGen;
import org.iolanguage.nodes.logic.LessOrEqualNodeGen;
import org.iolanguage.nodes.logic.LessThanNodeGen;
import org.iolanguage.nodes.logic.LogicalAndNode;
import org.iolanguage.nodes.logic.LogicalNotNodeGen;
import org.iolanguage.nodes.logic.LogicalOrNode;
import org.iolanguage.nodes.root.IoRootNode;
import org.iolanguage.nodes.slots.ListLocalSlotNamesNode;
import org.iolanguage.nodes.slots.ReadArgumentNode;
import org.iolanguage.nodes.slots.ReadLocalSlotNodeGen;
import org.iolanguage.nodes.slots.ReadMemberNodeGen;
import org.iolanguage.nodes.slots.ReadNode;
import org.iolanguage.nodes.slots.ReadTargetNode;
import org.iolanguage.nodes.slots.WriteLocalSlotNodeGen;
import org.iolanguage.nodes.slots.WriteMemberNodeGen;
import org.iolanguage.nodes.util.UnboxNodeGen;
import org.iolanguage.runtime.Symbols;

public class NodeFactory {

    static class Scope {
        protected final Scope outer;
        protected int bodyStartPos;
        protected boolean inLoop;
        protected int argumentCount;
        protected final List<TruffleString> locals;

        Scope(final Scope outer, int bodyStartPos) {
            this.outer = outer;
            this.bodyStartPos = bodyStartPos;
            this.inLoop = false;
            this.argumentCount = 0;
            this.locals = new ArrayList<>();
        }

        boolean hasLocals() {
            return !locals.isEmpty();
        }

        boolean hasCall() {
            return locals.contains(CALL_SYMBOL);
        }

        int addArgument(TruffleString name) {
            int argumentIndex = argumentCount;
            if (addLocal(name) != argumentCount++) {
                throw new RuntimeException("Arguments should be set first: " + name);
            }
            return argumentIndex;
        }

        int addLocal(TruffleString name) {
            int localIndex = locals.size();
            if (!locals.add(name)) {
                throw new RuntimeException("Could not add local: " + name);
            }
            return localIndex;
        }

        int findLocal(TruffleString name) {
            return locals.indexOf(name);
        }

        int findOrAddLocal(TruffleString name) {
            int index = findLocal(name);
            if (index != -1) {
                return index;
            }
            return addLocal(name);
        }

        FrameDescriptor buildFrameDescriptor() {
            final FrameDescriptor.Builder frameDescriptorBuilder = FrameDescriptor.newBuilder();
            for (int i = 0; i < locals.size(); i++) {
                Integer argumentIndex = i < argumentCount ? i : null;
                frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, locals.get(i), argumentIndex);
            }
            return frameDescriptorBuilder.build();
        }

        TruffleString[] getArgumentNames() {
            TruffleString[] arguments = new TruffleString[argumentCount];
            for (int i = 0; i < argumentCount; i++) {
                arguments[i] = locals.get(i);
            }
            return arguments;
        }

        boolean callSlotIsUsed() {
            return findLocal(CALL_SYMBOL) != -1;
        }
    }

    private final static TruffleString CALL_SYMBOL = Symbols.constant("call");
    private final static TruffleString TARGET_SYMBOL = Symbols.constant("target");
    private final static TruffleString SENDER_SYMBOL = Symbols.constant("sender");

    private final Source source;
    private final TruffleString sourceString;
    private final IoLanguage language;
    private Scope currentScope;

    public NodeFactory(IoLanguage language, Source source) {
        this.language = language;
        this.source = source;
        this.sourceString = Symbols.fromJavaString(source.getCharacters().toString());
    }

    public boolean hasLocals() {
        return currentScope.hasLocals();
    }

    public void enterNewScope(int startPos) {
        currentScope = new Scope(currentScope, startPos);
    }

    public void enterNewLocalsScope(int startPos) {
        enterNewScope(startPos);
        currentScope.addArgument(Symbols.SELF);
    }

    protected void leaveCurrentScope() {
        currentScope = currentScope.outer;
    }

    public FunctionLiteralNode createFunction(IoNode bodyNode, int startPos, int length) {
        if (bodyNode == null) {
            return null;
        }
        assert !hasLocals();
        final int bodyEndPos = bodyNode.getSourceEndIndex();
        int methodBodyLength = bodyEndPos - currentScope.bodyStartPos;
        final SourceSection methodSrc = source.createSection(currentScope.bodyStartPos, methodBodyLength);
        final MethodBodyNode methodBodyNode = new MethodBodyNode(bodyNode);
        methodBodyNode.setSourceSection(methodSrc.getCharIndex(), methodSrc.getCharLength());
        final IoRootNode rootNode = new IoRootNode(language, currentScope.buildFrameDescriptor(),
                methodBodyNode, methodSrc);
        final FunctionLiteralNode functionLiteralNode = new FunctionLiteralNode(Symbols.fromJavaString("do"), rootNode);
        leaveCurrentScope();
        functionLiteralNode.setSourceSection(startPos, length);
        return functionLiteralNode;
    }

    public int addFormalParameter(Token nameToken) {
        TruffleString name = asTruffleString(nameToken, false);
        return currentScope.addArgument(name);
    }

    public IoNode createInitializationNode(int argumentIndex, int localIndex) {
        final ReadArgumentNode readArgNode = new ReadArgumentNode(argumentIndex);
        StringLiteralNode nameNode = new StringLiteralNode(currentScope.locals.get(localIndex));
        return WriteLocalSlotNodeGen.create(readArgNode, localIndex, nameNode);
    }

    public List<IoNode> setupLocals(IoNode bodyNode, int startPos, int length) {
        assert currentScope.argumentCount >= 1;
        List<IoNode> initializationNodes = new ArrayList<IoNode>(currentScope.argumentCount);
        //int callSlotIndex = currentScope.findLocal(CALL_SYMBOL);
        int callSlotIndex = currentScope.findOrAddLocal(CALL_SYMBOL);
        if (callSlotIndex == -1) {
            // does not use call, intialize self with arg 0
            initializationNodes.add(createInitializationNode(0, 0));
        } else {
            initializationNodes.add(createInitializationNode(0, callSlotIndex));
            int selfSlotIndex = currentScope.findLocal(Symbols.SELF);
            assert selfSlotIndex == 0;
            StringLiteralNode nameNode = new StringLiteralNode(Symbols.SELF);
            IoNode initializeSelfNode = WriteLocalSlotNodeGen.create(createReadCallTarget(startPos, 0), selfSlotIndex,
                    nameNode);
            initializationNodes.add(initializeSelfNode);
        }
        for (int i = 1; i < currentScope.argumentCount; i++) {
            initializationNodes.add(createInitializationNode(i, i));
        }
        initializationNodes.add(bodyNode);
        return initializationNodes;
    }

    public IoRootNode createRoot(IoNode bodyNode, int startPos, int length) {
        assert bodyNode != null;
        List<IoNode> initializedBodyNode = setupLocals(bodyNode, startPos, 0);
        final int bodyEndPos = bodyNode.getSourceEndIndex();
        int methodBodyLength = bodyEndPos - currentScope.bodyStartPos + 1;
        final SourceSection methodSrc = source.createSection(currentScope.bodyStartPos, methodBodyLength);
        final IoNode methodBlock = createExpression(initializedBodyNode, currentScope.bodyStartPos, methodBodyLength);
        final MethodBodyNode methodBodyNode = new MethodBodyNode(methodBlock);
        methodBodyNode.setSourceSection(methodSrc.getCharIndex(), methodSrc.getCharLength());
        final IoRootNode rootNode = new IoRootNode(language, currentScope.buildFrameDescriptor(),
                methodBodyNode, methodSrc);
        return rootNode;
    }

    public BlockLiteralNode createBlock(IoNode bodyNode, int startPos, int length) {
        final IoRootNode rootNode = createRoot(bodyNode, startPos, length);
        TruffleString[] argNames = currentScope.getArgumentNames();
        final IoNode homeNode;
        boolean callSlotIsUsed = currentScope.callSlotIsUsed();
        leaveCurrentScope();
        if (hasLocals()) {
            homeNode = createReadCallSender(startPos, length);
        } else {
            homeNode = createReadTarget(startPos, length);
        }
        final BlockLiteralNode blockLiteralNode = new BlockLiteralNode(rootNode, argNames, homeNode, callSlotIsUsed);
        blockLiteralNode.setSourceSection(startPos, length);
        return blockLiteralNode;
    }

    public MethodLiteralNode createMethod(IoNode bodyNode, int startPos, int length) {
        final IoRootNode rootNode = createRoot(bodyNode, startPos, length);
        TruffleString[] argNames = currentScope.getArgumentNames();
        boolean callSlotIsUsed = currentScope.callSlotIsUsed();
        final MethodLiteralNode methodLiteralNode = new MethodLiteralNode(rootNode, argNames, callSlotIsUsed);
        methodLiteralNode.setSourceSection(startPos, length);
        leaveCurrentScope();
        return methodLiteralNode;
    }

    protected IoNode createExpression(List<IoNode> bodyNodes, int startPos, int length) {
        if (containsNull(bodyNodes)) {
            return null;
        }

        List<IoNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenNodes(bodyNodes, flattenedNodes);
        int n = flattenedNodes.size();
        for (int i = 0; i < n; i++) {
            IoNode expression = flattenedNodes.get(i);
            if (expression.hasSource()) {
                expression.addExpressionTag();
            }
        }
        ExpressionNode blockNode = new ExpressionNode(flattenedNodes.toArray(new IoNode[flattenedNodes.size()]));
        blockNode.setSourceSection(startPos, length);
        return blockNode;
    }

    public void startLoop() {
        currentScope.inLoop = true;
    }

    public IoNode createLoopExpression(IoNode loopNode, int startPos, int length) {
        List<IoNode> bodyNodes = new ArrayList<>();
        bodyNodes.add(loopNode);
        return createExpression(bodyNodes, startPos, length);
    }

    private void flattenNodes(Iterable<? extends IoNode> bodyNodes, List<IoNode> flattenedNodes) {
        for (IoNode n : bodyNodes) {
            if (n instanceof ExpressionNode) {
                flattenNodes(((ExpressionNode) n).getExpressions(), flattenedNodes);
            } else {
                flattenedNodes.add(n);
            }
        }
    }

    IoNode createDebugger(Token debuggerToken) {
        final DebuggerNode debuggerNode = new DebuggerNode();
        srcFromToken(debuggerNode, debuggerToken);
        return debuggerNode;
    }

    public IoNode createBreak(Token breakToken) {
        if (currentScope.inLoop) {
            final BreakNode breakNode = new BreakNode();
            srcFromToken(breakNode, breakToken);
            return breakNode;
        }
        return null;
    }

    public IoNode createContinue(Token continueToken) {
        if (currentScope.inLoop) {
            final ContinueNode continueNode = new ContinueNode();
            srcFromToken(continueNode, continueToken);
            return continueNode;
        }
        return null;
    }

    public IoNode createWhile(Token whileToken, IoNode conditionNode, IoNode bodyNode) {
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

    public IoNode createRepeat(IoNode receiverNode, IoNode bodyNode, int startPos,
            int length) {
        RepeatNode repeatNode = null;
        if (receiverNode != null && bodyNode != null) {
            receiverNode.addExpressionTag();
            repeatNode = new RepeatNode(receiverNode, bodyNode);
            repeatNode.setSourceSection(startPos, length);
        }
        return repeatNode;
    }

    public IoNode createTry(IoNode bodyNode, int startPos, int length) {
        IoNode tryNode = null;
        if (bodyNode != null) {
            tryNode = new TryNode(bodyNode);
            tryNode.addExpressionTag();
            tryNode.setSourceSection(startPos, length);
        }
        return tryNode;
    }

    public IoNode createIfThenElse(Token ifToken, IoNode conditionNode, IoNode thenPartNode,
            IoNode elsePartNode) {
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

    public IoNode createReturn(Token t, IoNode valueNodeOrNull) {
        final int startPos = t.getStartIndex();
        final int length;
        IoNode valueNode = valueNodeOrNull;
        if (valueNode == null) {
            length = t.getText().length();
            valueNode = createReadSelf(startPos, length);
        } else {
            length = valueNode.getSourceEndIndex() - startPos;
        }
        final ReturnNode returnNode = new ReturnNode(valueNode);
        returnNode.setSourceSection(startPos, length);
        return returnNode;
    }

    public IoNode createUnary(Token opToken, IoNode rightNode) {
        if (rightNode == null) {
            return null;
        }
        final IoNode rightUnboxed = UnboxNodeGen.create(rightNode);

        final IoNode result;
        String op = opToken.getText();
        switch (op) {
            case "-":
                result = SubNodeGen.create(new LongLiteralNode(0), rightUnboxed);
                break;
            case "!":
                result = LogicalNotNodeGen.create(rightUnboxed);
                break;
            default:
                throw new RuntimeException("unexpected operation: " + op);
        }

        int startPos = opToken.getStartIndex();
        int length = rightNode.getSourceEndIndex() - startPos;
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public IoNode createBinary(Token opToken, IoNode leftNode, IoNode rightNode) {
        if (leftNode == null || rightNode == null) {
            return null;
        }
        final IoNode leftUnboxed = UnboxNodeGen.create(leftNode);
        final IoNode rightUnboxed = UnboxNodeGen.create(rightNode);

        final IoNode result;
        String op = opToken.getText();
        switch (op) {
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
            case "and":
                result = new LogicalAndNode(leftUnboxed, rightUnboxed);
                break;
            case "||":
            case "or":
                result = new LogicalOrNode(leftUnboxed, rightUnboxed);
                break;
            case "..":
                result = ConcatNodeGen.create(leftUnboxed, rightUnboxed);
                break;
            default:
                throw new RuntimeException("unexpected operation: " + op);
        }

        int startPos = leftNode.getSourceCharIndex();
        int length = rightNode.getSourceEndIndex() - startPos;
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public IoNode createWriteSlot(IoNode receiverNode, IoNode nameNode,
            IoNode valueNode, int startPos, int length, boolean initialize) {
        IoNode result = null;
        if (receiverNode == null) {
            result = createWriteLocalSlot(nameNode, valueNode, startPos, length, initialize);
            if (result == null) {
                if (hasLocals()) {
                    receiverNode = createReadCallSender(startPos, length);
                } else {
                    receiverNode = createReadTarget(startPos, length);
                }
            }
        }
        if (result == null) {
            result = createWriteProperty(receiverNode, nameNode, valueNode, startPos, length, initialize);
        }
        assert result != null;
        return result;
    }

    public IoNode createWriteLocalSlot(IoNode nameNode, IoNode valueNode, int startPos, int length,
            boolean initialize) {
        if (nameNode == null || valueNode == null || !hasLocals()) {
            return null;
        }
        assert nameNode instanceof StringLiteralNode;
        TruffleString name = ((StringLiteralNode) nameNode).executeGeneric(null);
        final int slotIndex = initialize ? currentScope.findOrAddLocal(name) : currentScope.findLocal(name);
        if (slotIndex < 0) {
            return null;
        }
        final IoNode result = WriteLocalSlotNodeGen.create(valueNode, slotIndex, nameNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createWriteProperty(IoNode receiverNode, IoNode nameNode,
            IoNode valueNode, int startPos, int length, boolean initialize) {
        if (receiverNode == null || nameNode == null || valueNode == null) {
            return null;
        }

        final IoNode result = WriteMemberNodeGen.create(receiverNode, nameNode, valueNode, initialize);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();

        return result;
    }

    public ReadNode createReadLocalSlot(StringLiteralNode nameNode, int startPos, int length) {
        assert nameNode != null;
        if (!hasLocals()) {
            return null;
        }
        TruffleString name = nameNode.executeGeneric(null);
        final int slotIndex;
        if (name.equals(CALL_SYMBOL)) {
            slotIndex = currentScope.findOrAddLocal(name);
        } else {
            slotIndex = currentScope.findLocal(name);
        }
        if (slotIndex < 0) {
            return null;
        }
        final ReadNode result = ReadLocalSlotNodeGen.create(slotIndex);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public ReadNode createReadLocalSlot(IoNode nameNode, int startPos, int length) {
        if (!hasLocals()) {
            return null;
        }
        if (nameNode instanceof StringLiteralNode) {
            return createReadLocalSlot((StringLiteralNode) nameNode, startPos, length);
        }
        throw new NotImplementedException();
    }

    public IoNode createForSlot(IoNode receiverNode, IoNode nameNode, IoNode initializationNode, IoNode startValueNode,
            IoNode endValueNode, IoNode stepValueNode, IoNode bodyNode, int startPos, int length) {
        if (nameNode != null && startValueNode != null && endValueNode != null && bodyNode != null) {
            IoNode readValueNode = createReadSlot(receiverNode, nameNode, startPos, length);
            startValueNode.addExpressionTag();
            endValueNode.addExpressionTag();
            if (stepValueNode == null) {
                stepValueNode = new LongLiteralNode(1);
            }
            IoNode nextValueNode = AddNodeGen.create(readValueNode, stepValueNode);
            nextValueNode.setSourceSection(startPos, length);
            nextValueNode.addExpressionTag();
            IoNode stepNode = createWriteSlot(receiverNode, nameNode, nextValueNode, startPos, length, false);
            final IoNode result = new ForNode(initializationNode, stepNode, readValueNode, startValueNode, endValueNode,
                    stepValueNode, bodyNode);
            result.setSourceSection(startPos, length);
            result.addExpressionTag();
            return result;
        }
        return null;
    }

    public ReadNode createReadSelf(int startPos, int length) {
        assert hasLocals();
        final StringLiteralNode selfNode = new StringLiteralNode(Symbols.SELF);
        final ReadNode result = createReadLocalSlot(selfNode, startPos, length);
        assert result != null;
        return result;
    }

    public ReadNode createReadTarget(int startPos, int length) {
        assert !hasLocals();
        ReadNode result = new ReadTargetNode();
        assert result != null;
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;

    }

    public ReadNode createReadSelfOrTarget(int startPos, int length) {
        if (hasLocals()) {
            return createReadCall(startPos, length);
            //            return createReadSelf(startPos, length);
        }
        return createReadTarget(startPos, length);
    }

    public ReadNode createReadCall(int startPos, int length) {
        assert hasLocals() == true;
        currentScope.findOrAddLocal(CALL_SYMBOL);
        final StringLiteralNode callNode = new StringLiteralNode(CALL_SYMBOL);
        final ReadNode result = createReadLocalSlot(callNode, startPos, length);
        assert result != null;
        return result;
    }

    public ReadNode createReadCallSender(int startPos, int length) {
        final StringLiteralNode senderNode = new StringLiteralNode(SENDER_SYMBOL);
        final ReadNode result = createReadProperty(createReadCall(startPos, length), senderNode, startPos, length);
        assert result != null;
        return result;
    }

    public ReadNode createReadCallTarget(int startPos, int length) {
        final StringLiteralNode targetNode = new StringLiteralNode(TARGET_SYMBOL);
        final ReadNode result = createReadProperty(createReadCall(startPos, length), targetNode, startPos, length);
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
        return sourceString.substringByteIndexUncached(fromIndex * 2, length * 2, IoLanguage.STRING_ENCODING, true);
    }

    public IoNode createNumericLiteral(Token literalToken) {
        IoNode result;
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

    public IoNode createBoolean(Token literalToken) {
        IoNode result;
        result = new BooleanLiteralNode(literalToken.getText().equals("true"));
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public IoNode createNil(Token literalToken) {
        IoNode result;
        result = new NilLiteralNode();
        srcFromToken(result, literalToken);
        result.addExpressionTag();
        return result;
    }

    public IoNode createNil(int startPos, int length) {
        IoNode result;
        result = new NilLiteralNode();
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createListLiteral(List<IoNode> elementNodes, int startPos, int length) {
        final IoNode result = new ListLiteralNode(
                elementNodes.toArray(new IoNode[elementNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createParenExpression(IoNode expressionNode, int startPos, int length) {
        if (expressionNode == null) {
            return null;
        }

        final ParenExpressionNode result = new ParenExpressionNode(expressionNode);
        result.setSourceSection(startPos, length);
        return result;
    }

    public ReadNode createReadProperty(IoNode receiverNode, IoNode nameNode, int startPos, int length) {
        if (receiverNode == null || nameNode == null) {
            return null;
        }

        final ReadNode result = ReadMemberNodeGen.create(receiverNode, nameNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createListLocalSlotNames(int startPos, int length) {
        if (hasLocals()) {
            final IoNode result = new ListLocalSlotNamesNode();
            result.setSourceSection(startPos, length);
            result.addExpressionTag();
            return result;
        }
        return null;
    }

    public IoNode createThisLocalContext(IoNode receiverNode, int startPos, int length) {
        final IoNode targetNode;
        if (receiverNode == null) {
            if (hasLocals()) {
                targetNode = createReadCall(startPos, length);
            } else {
                targetNode = createReadTarget(startPos, length);
            }
        } else {
            targetNode = receiverNode;
        }
        final IoNode result = ThisLocalContextNodeGen.create(targetNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createGetSlot(IoNode nameNode, int startPos, int length) {
        if (!hasLocals()) {
            return null;
        }
        IoNode resultNode = createReadLocalSlot(nameNode, startPos, length);
        if (resultNode == null) {
            resultNode = new NilLiteralNode();
            resultNode.setSourceSection(startPos, length);
            resultNode.addExpressionTag();
        }
        return resultNode;
    }

    public ReadNode createReadSlot(IoNode receiverNode, IoNode nameNode, int startPos, int length) {
        IoNode targetNode = receiverNode;
        if (receiverNode == null) {
            if (hasLocals()) {
                ReadNode resultNode = createReadLocalSlot(nameNode, startPos, length);
                if (resultNode != null) {
                    return resultNode;
                }
                targetNode = createReadCallSender(startPos, length);
            } else {
                targetNode = createReadTarget(startPos, length);
            }
        }
        return createReadProperty(targetNode, nameNode, 0, 0);
    }

    public IoNode createInvokeSlot(IoNode receiverNode, IoNode nameNode, List<IoNode> argumentNodes,
            int startPos, int length) {
        if (nameNode == null) {
            return null;
        }
        ReadNode valueNode = createReadSlot(receiverNode, nameNode, startPos, length);
        assert valueNode != null;
        final IoNode result = InvokeNodeGen.create(valueNode, argumentNodes.toArray(new IoNode[argumentNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createDo(IoNode receiverNode, IoNode functionNode, int startPos, int length) {
        IoNode targetNode = receiverNode == null ? createReadSelfOrTarget(startPos, length) : receiverNode;
        assert targetNode != null;
        ReadNode valueNode = DoReadNodeGen.create(receiverNode, functionNode);
        valueNode.setSourceSection(startPos, length);
        valueNode.addExpressionTag();
        assert valueNode != null;
        final IoNode result = InvokeNodeGen.create(valueNode, new IoNode[0]);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createTryCatchUndefinedName(IoNode receiverNode, int startPos, int length) {
        final IoNode result = new TryCatchUndefinedNameNode(receiverNode);
        result.addExpressionTag();
        result.setSourceSection(startPos, length);
        return result;
    }

    private static void srcFromToken(IoNode node, Token token) {
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
