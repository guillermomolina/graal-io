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
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.truffle.io.IoLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.nodes.IoNode;
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
import org.truffle.io.nodes.controlflow.TryNode;
import org.truffle.io.nodes.controlflow.WhileNode;
import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.expression.InvokeNode;
import org.truffle.io.nodes.expression.MethodBodyNode;
import org.truffle.io.nodes.expression.ParenExpressionNode;
import org.truffle.io.nodes.expression.ThisLocalContextNodeGen;
import org.truffle.io.nodes.literals.BlockLiteralNode;
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
import org.truffle.io.nodes.root.IoRootNode;
import org.truffle.io.nodes.slots.ListLocalSlotNamesNode;
import org.truffle.io.nodes.slots.ReadArgumentNode;
import org.truffle.io.nodes.slots.ReadLocalSlotNodeGen;
import org.truffle.io.nodes.slots.ReadMemberNode;
import org.truffle.io.nodes.slots.WriteLocalSlotNodeGen;
import org.truffle.io.nodes.slots.WriteMemberNode;
import org.truffle.io.nodes.util.UnboxNodeGen;
import org.truffle.io.runtime.Symbols;
import org.truffle.io.runtime.objects.IoLocals;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

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

    public List<IoNode> setupLocals(IoNode bodyNode) {
        assert currentScope.argumentCount >= 1;
        List<IoNode> initializationNodes = new ArrayList<IoNode>(currentScope.argumentCount);
        int callSlotIndex = currentScope.findLocal(CALL_SYMBOL);
        if(callSlotIndex == -1) {
            // does not use call, intialize self with arg 0
            initializationNodes.add(createInitializationNode(0, 0));
        } else {
            initializationNodes.add(createInitializationNode(0, callSlotIndex));
            int selfSlotIndex = currentScope.findLocal(Symbols.SELF);
            assert selfSlotIndex == 0;
            StringLiteralNode nameNode = new StringLiteralNode(Symbols.SELF);
            IoNode initializeSelfNode = WriteLocalSlotNodeGen.create(createReadCallTarget(), selfSlotIndex, nameNode);
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
        List<IoNode> initializedBodyNode = setupLocals(bodyNode);
        final int bodyEndPos = bodyNode.getSourceEndIndex();
        int methodBodyLength = bodyEndPos - currentScope.bodyStartPos;
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
        leaveCurrentScope();
        if (hasLocals()) {
            homeNode = createReadCallSender(startPos, length);
        } else {
            homeNode = createReadTarget();
        }
        final BlockLiteralNode blockLiteralNode = new BlockLiteralNode(rootNode, argNames, homeNode);
        blockLiteralNode.setSourceSection(startPos, length);
        return blockLiteralNode;
    }

    public MethodLiteralNode createMethod(IoNode bodyNode, int startPos, int length) {
        final IoRootNode rootNode = createRoot(bodyNode, startPos, length);
        TruffleString[] argNames = currentScope.getArgumentNames();
        final MethodLiteralNode methodLiteralNode = new MethodLiteralNode(rootNode, argNames);
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
            valueNode = createReadSelf();
        } else {
            length = valueNode.getSourceEndIndex() - startPos;
        }
        final ReturnNode returnNode = new ReturnNode(valueNode);
        returnNode.setSourceSection(startPos, length);
        return returnNode;
    }

    public IoNode createBinary(Token opToken, IoNode leftNode, IoNode rightNode) {
        if (leftNode == null || rightNode == null) {
            return null;
        }
        final IoNode leftUnboxed = UnboxNodeGen.create(leftNode);
        final IoNode rightUnboxed = UnboxNodeGen.create(rightNode);

        final IoNode result;
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

    public IoNode createWriteProperty(IoNode receiverNode, IoNode nameNode,
            IoNode valueNode, int startPos, int length, boolean initialize) {
        if (receiverNode == null || nameNode == null || valueNode == null) {
            return null;
        }

        final IoNode result = new WriteMemberNode(receiverNode, nameNode, valueNode, initialize);
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
                    //result = createWriteRemoteSlot(receiverNode, nameNode, valueNode, startPos, length);
                } else {
                    receiverNode = createReadTarget();
                }
            }
        }
        if (result == null) {
            result = createWriteProperty(receiverNode, nameNode, valueNode, startPos, length, initialize);
        }
        assert result != null;
        return result;
    }

    public IoNode createWriteLocalSlot(IoNode nameNode, IoNode valueNode, int startPos, int length, boolean initialize) {
        if (nameNode == null || valueNode == null || !hasLocals()) {
            return null;
        }
        assert nameNode instanceof StringLiteralNode;
        TruffleString name = ((StringLiteralNode) nameNode).executeGeneric(null);
        final int slotIndex = initialize? currentScope.findOrAddLocal(name) : currentScope.findLocal(name);
        if (slotIndex < 0) {
            return null;
        }
        final IoNode result = WriteLocalSlotNodeGen.create(valueNode, slotIndex, nameNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    /*public IoNode createWriteRemoteSlot(IoNode sender, IoNode nameNode, IoNode valueNode, int startPos, int length) {
        if (nameNode == null || valueNode == null) {
            return null;
        }
        final IoNode result = WriteRemoteSlotNodeGen.create(senderNode, nameNode, valueNode, nameNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }*/

    public IoNode createReadLocalSlot(StringLiteralNode nameNode) {
        assert nameNode != null;
        TruffleString name = nameNode.executeGeneric(null);
        final int slotIndex = currentScope.findLocal(name);
        if (slotIndex < 0) {
            return null;
        }
        final IoNode result = ReadLocalSlotNodeGen.create(slotIndex);
        if (nameNode.hasSource()) {
            result.setSourceSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
        }
        result.addExpressionTag();
        return result;
    }

    public IoNode createReadLocalSlot(Token nameToken) {
        TruffleString name = asTruffleString(nameToken, false);
        final int slotIndex = currentScope.findLocal(name);
        if (slotIndex < 0) {
            return null;
        }
        final IoNode result = ReadLocalSlotNodeGen.create(slotIndex);
        srcFromToken(result, nameToken);
        result.addExpressionTag();
        return result;
    }

    public IoNode createReadLocalSlot(IoNode nameNode, int startPos, int length) {
        if (!hasLocals()) {
            return null;
        }
        if (nameNode instanceof StringLiteralNode) {
            return createReadLocalSlot((StringLiteralNode) nameNode);
        }
        throw new NotImplementedException();
    }

    public IoNode createForSlot(IoNode slotNameNode, IoNode startValueNode,
            IoNode endValueNode, IoNode stepValueNode, IoNode bodyNode, int startPos,
            int length) {
        throw new NotImplementedException();
        /*ForLocalSlotNode forNode = null;
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
            final IoNode result;
            if (contextLevel == 0) {
                result = new ForLocalSlotNode(slotFrameIndex, slotNameNode, startValueNode, endValueNode,
                        stepValueNode, bodyNode);
            } else {
                throw new NotImplementedException();
                //result = new IoForRemoteSlotNode(contextLevel, slotFrameIndex, slotNameNode, startValueNode, endValueNode, stepValueNode, bodyNode);
            }
            result.setSourceSection(startPos, length);
            result.addExpressionTag();
            return result;
        }
        return forNode;*/
    }

    public IoNode createReadSelf() {
        assert hasLocals();
        final StringLiteralNode selfNode = new StringLiteralNode(Symbols.SELF);
        final IoNode result = createReadLocalSlot(selfNode);
        assert result != null;
        return result;
    }

    public IoNode createReadTarget() {
        assert !hasLocals();
        IoNode result = new ReadArgumentNode(IoLocals.TARGET_ARGUMENT_INDEX);
        //result.addExpressionTag();
        assert result != null;
        return result;

    }

    public IoNode createReadCall() {
        assert hasLocals() == true;
        currentScope.findOrAddLocal(CALL_SYMBOL);
        final StringLiteralNode callNode = new StringLiteralNode(CALL_SYMBOL);
        final IoNode result = createReadLocalSlot(callNode);
        assert result != null;
        return result;
    }

    public IoNode createReadCallSender(int startPos, int length) {
        final StringLiteralNode senderNode = new StringLiteralNode(SENDER_SYMBOL);
        final IoNode result = createReadProperty(createReadCall(), senderNode, startPos, length);
        assert result != null;
        return result;
    }

    public IoNode createReadCallTarget() {
        final StringLiteralNode targetNode = new StringLiteralNode(TARGET_SYMBOL);
        final IoNode result = createReadProperty(createReadCall(), targetNode, 0, 0);
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

    public IoNode createReadProperty(IoNode receiverNode, IoNode nameNode, int startPos, int length) {
        if (receiverNode == null || nameNode == null) {
            return null;
        }

        final IoNode result = new ReadMemberNode(receiverNode, nameNode);
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
        if(receiverNode == null) {
            if (hasLocals()) {
                targetNode = createReadCall();
            } else {
                targetNode = createReadTarget();
            }
        } else {
            targetNode = receiverNode;
        }
        final IoNode result = ThisLocalContextNodeGen.create(targetNode);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createGetSlot(IoNode receiverNode, IoNode nameNode, int startPos, int length) {
        IoNode targetNode = receiverNode;
        if (receiverNode == null) {
            if (hasLocals()) {
                IoNode resultNode = createReadLocalSlot(nameNode, startPos, length);
                if (resultNode == null) {
                    resultNode = new NilLiteralNode();
                    resultNode.setSourceSection(startPos, length);
                    resultNode.addExpressionTag();
                }
                return resultNode;
            }
            targetNode = createReadTarget();
        }
        return createReadProperty(targetNode, nameNode, 0, 0);
    }

    public IoNode createGetSlot(IoNode nameNode, int startPos, int length) {
        if (hasLocals()) {
            IoNode resultNode = createReadLocalSlot(nameNode, startPos, length);
            if (resultNode == null) {
                resultNode = new NilLiteralNode();
                resultNode.setSourceSection(startPos, length);
                resultNode.addExpressionTag();
            }
            return resultNode;
        }
        return null;
    }

    public IoNode createInvokeSlot(IoNode receiverNode, Token identifierToken, List<IoNode> argumentNodes,
            int startPos, int length) {
        IoNode targetNode = receiverNode;
        IoNode valueNode = null;
        if (targetNode == null) {
            if (hasLocals()) {
                targetNode = createReadCallSender(startPos, length);
                valueNode = createReadLocalSlot(identifierToken);
                if (valueNode == null) {
                    targetNode = createReadSelf();
                }
            } else {
                targetNode = createReadTarget();
            }
        }
        TruffleString identifier = asTruffleString(identifierToken, false);
        assert targetNode != null;
        IoNode result = new InvokeNode(targetNode, valueNode, identifier,
                argumentNodes.toArray(new IoNode[argumentNodes.size()]));
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
        return result;
    }

    public IoNode createDo(IoNode receiverNode, IoNode functionNode, int startPos, int length) {
        IoNode targetNode = receiverNode;
        if (targetNode == null) {
            if (hasLocals()) {
                targetNode = createReadSelf();
            } else {
                targetNode = createReadTarget();
            }
        }
        TruffleString identifier = Symbols.fromJavaString("do");
        assert targetNode != null;
        IoNode result = new InvokeNode(targetNode, functionNode, identifier, new IoNode[0]);
        result.setSourceSection(startPos, length);
        result.addExpressionTag();
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
