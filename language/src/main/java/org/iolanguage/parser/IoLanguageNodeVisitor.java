package org.iolanguage.parser;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.iolanguage.IoLanguage;
import org.iolanguage.NotImplementedException;
import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.literals.FunctionLiteralNode;
import org.iolanguage.nodes.literals.MethodLiteralNode;
import org.iolanguage.nodes.literals.NilLiteralNode;
import org.iolanguage.parser.IoLanguageParser.ArgumentsContext;
import org.iolanguage.parser.IoLanguageParser.AssignmentContext;
import org.iolanguage.parser.IoLanguageParser.BlockMessageContext;
import org.iolanguage.parser.IoLanguageParser.DecimalContext;
import org.iolanguage.parser.IoLanguageParser.DoMessageContext;
import org.iolanguage.parser.IoLanguageParser.ElseMessageVariantsContext;
import org.iolanguage.parser.IoLanguageParser.ExpressionContext;
import org.iolanguage.parser.IoLanguageParser.ForMessageContext;
import org.iolanguage.parser.IoLanguageParser.ForeachMessageContext;
import org.iolanguage.parser.IoLanguageParser.GetSlotMessageContext;
import org.iolanguage.parser.IoLanguageParser.IdentifierContext;
import org.iolanguage.parser.IoLanguageParser.IfMessageVariantsContext;
import org.iolanguage.parser.IoLanguageParser.InlinedMessageContext;
import org.iolanguage.parser.IoLanguageParser.IolanguageContext;
import org.iolanguage.parser.IoLanguageParser.ListMessageContext;
import org.iolanguage.parser.IoLanguageParser.LiteralContext;
import org.iolanguage.parser.IoLanguageParser.MessageContext;
import org.iolanguage.parser.IoLanguageParser.MessageInvokeContext;
import org.iolanguage.parser.IoLanguageParser.MessageNextContext;
import org.iolanguage.parser.IoLanguageParser.ModifiedMessageContext;
import org.iolanguage.parser.IoLanguageParser.ModifiedMessageNextContext;
import org.iolanguage.parser.IoLanguageParser.NumberContext;
import org.iolanguage.parser.IoLanguageParser.OperationContext;
import org.iolanguage.parser.IoLanguageParser.OperationOrAssignmentContext;
import org.iolanguage.parser.IoLanguageParser.OperatorContext;
import org.iolanguage.parser.IoLanguageParser.OperatorMessageContext;
import org.iolanguage.parser.IoLanguageParser.ParenExpressionContext;
import org.iolanguage.parser.IoLanguageParser.PseudoVariableContext;
import org.iolanguage.parser.IoLanguageParser.RepeatMessageContext;
import org.iolanguage.parser.IoLanguageParser.ReturnMessageContext;
import org.iolanguage.parser.IoLanguageParser.SetSlotMessageContext;
import org.iolanguage.parser.IoLanguageParser.SlotNamesMessageContext;
import org.iolanguage.parser.IoLanguageParser.SubExpressionContext;
import org.iolanguage.parser.IoLanguageParser.ThisLocalContextMessageContext;
import org.iolanguage.parser.IoLanguageParser.TryMessageContext;
import org.iolanguage.parser.IoLanguageParser.WhileMessageContext;

public class IoLanguageNodeVisitor extends IoLanguageBaseVisitor<IoNode> {

    private static final TruffleLogger LOGGER = IoLanguage.getLogger(IoLanguageNodeVisitor.class);

    private NodeFactory factory;
    private Source source;

    private static final class BailoutErrorListener extends BaseErrorListener {
        private final Source source;

        BailoutErrorListener(Source source) {
            this.source = source;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingLiteral, int line, int charPositionInLine,
                String msg, RecognitionException e) {
            throwParseError(source, (Token) offendingLiteral, line, charPositionInLine, msg, e);
        }
    }

    public void SemErr(Token token, String message) {
        assert token != null;
        throwParseError(source, token.getLine(), token.getCharPositionInLine(), token, message);
    }

    private static void throwParseError(Source source, Token token, int lineNumber, int charPositionInLine,
            String message, RecognitionException e) {
        if (token == null) {
            LexerNoViableAltException lexerException = (LexerNoViableAltException) e;
            int lineNr = lineNumber > source.getLineCount() ? source.getLineCount() : lineNumber;
            if (lexerException.getInputStream().LA(1) == IntStream.EOF) {
                throw new IncompleteSourceException(message, e, lineNr, source);
            } else {
                throwParseError(source, lineNr, charPositionInLine, token, message);
            }
        } else {
            int lineNr = lineNumber > source.getLineCount() ? source.getLineCount() : lineNumber;
            if (token.getType() == Token.EOF) {
                throw new IncompleteSourceException(message, e, lineNr, source);
            } else {
                throwParseError(source, lineNr, charPositionInLine, token, message);
            }
        }
    }

    private static void throwParseError(Source source, int line, int charPositionInLine, Token token, String message) {
        int col = charPositionInLine + 1;
        String location = "-- line " + line + " col " + col + ": ";
        int length = token == null ? 1 : Math.max(token.getStopIndex() - token.getStartIndex(), 0);
        throw new ParseException(source, line, col, length, "Error(s) parsing script:" + location + message);
    }

    public RootCallTarget parseIO(IoLanguage language, Source source) {
        IoLanguageLexer lexer = new IoLanguageLexer(CharStreams.fromString(source.getCharacters().toString()));
        IoLanguageParser parser = new IoLanguageParser(new CommonTokenStream(lexer));
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        BailoutErrorListener listener = new BailoutErrorListener(source);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);
        this.factory = new NodeFactory(language, source);
        this.source = source;
        FunctionLiteralNode functionNode = (FunctionLiteralNode) visitIolanguage(parser.iolanguage());
        return functionNode.getValue().getCallTarget();
    }

    @Override
    public IoNode visitIolanguage(IolanguageContext ctx) {
        LOGGER.fine("Started parsing io source code");
        factory.enterNewScope(ctx.start.getStartIndex());
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IoNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(startPos, length);
        }
        final IoNode resultNode = factory.createFunction(bodyNode, startPos, length);
        assert resultNode != null;
        LOGGER.fine("Ended parsing io source code");
        return resultNode;
    }

    @Override
    public IoNode visitExpression(final ExpressionContext ctx) {
        List<IoNode> body = new ArrayList<>();

        for (final OperationOrAssignmentContext operationOrAssignmentCtx : ctx.operationOrAssignment()) {
            IoNode operationNOrAssignmentode = visitOperationOrAssignment(operationOrAssignmentCtx);
            if (operationNOrAssignmentode != null) {
                body.add(operationNOrAssignmentode);
            }
        }
        final IoNode resultNode = factory.createExpression(body, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitEmptyExpression(int startPos, int length) {
        List<IoNode> body = new ArrayList<>();
        body.add(new NilLiteralNode());
        final IoNode resultNode = factory.createExpression(body, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitOperationOrAssignment(final OperationOrAssignmentContext ctx) {
        if (ctx.operation() != null) {
            return visitOperation(ctx.operation());
        }
        if (ctx.assignment() != null) {
            return visitAssignment(ctx.assignment());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitOperation(final OperationContext ctx) {
        if (ctx.subExpression() != null) {
            return visitSubExpression(ctx.subExpression());
        }
        if (ctx.unary != null) {
            IoNode rightNode = visitOperation(ctx.operation(0));
            final IoNode resultNode = factory.createUnary(ctx.unary, rightNode);
            assert resultNode != null;
            return resultNode;
        }
        if (ctx.op != null) {
            IoNode leftNode = visitOperation(ctx.operation(0));
            IoNode rightNode = visitOperation(ctx.operation(1));
            final IoNode resultNode = factory.createBinary(ctx.op, leftNode, rightNode);
            assert resultNode != null;
            return resultNode;
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitAssignment(final AssignmentContext ctx) {
        boolean initialize = false;
        String op = ctx.assign.getText();
        switch (op) {
            case "::=":
                initialize = true;
                break;
            case ":=":
                initialize = true;
                break;
            case "=":
                initialize = false;
                break;
            default:
                throw new RuntimeException("unexpected operation: " + op);
        }
        IoNode receiverNode = null;
        if (ctx.subExpression() != null) {
            receiverNode = visitSubExpression(ctx.subExpression());
            assert receiverNode != null;
        }
        IoNode assignmentNameNode = visitIdentifier(ctx.name);
        assert assignmentNameNode != null;
        IoNode valueNode = visitOperation(ctx.operation());
        assert valueNode != null;
        if (valueNode instanceof MethodLiteralNode) {
            LOGGER.fine("Added a method named " + ctx.name.getText());
        }
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        IoNode resultNode = factory.createWriteSlot(receiverNode, assignmentNameNode, valueNode, startPos, length,
                initialize);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitSubExpression(final SubExpressionContext ctx) {
        if (ctx.message() != null) {
            return visitMessage(ctx.message());
        }
        if (ctx.modifiedMessage() != null) {
            return visitModifiedMessage(ctx.modifiedMessage());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitMessage(final MessageContext ctx) {
        if (ctx.inlinedMessage() != null) {
            return visitInlinedMessage(ctx.inlinedMessage());
        }
        IoNode receiverNode = null;
        if (ctx.literal() != null) {
            receiverNode = visitLiteral(ctx.literal());
        } else if (ctx.message() != null) {
            receiverNode = visitMessage(ctx.message());
        } else if (ctx.parenExpression() != null) {
            receiverNode = visitParenExpression(ctx.parenExpression());
        }
        if (ctx.messageNext() != null) {
            receiverNode = visitMessageNext(ctx.messageNext(), receiverNode);
        }
        assert receiverNode != null;
        return receiverNode;
    }

    @Override
    public IoNode visitModifiedMessage(final ModifiedMessageContext ctx) {
        IoNode receiverNode = null;
        if (ctx.message() != null) {
            receiverNode = visitMessage(ctx.message());
        }
        receiverNode = visitModifiedMessageNext(ctx.modifiedMessageNext(), receiverNode);
        if (ctx.messageNext() != null) {
            receiverNode = visitMessageNext(ctx.messageNext(), receiverNode);
        }
        assert receiverNode != null;
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        return factory.createTryCatchUndefinedName(receiverNode, startPos, length);
    }

    @Override
    public IoNode visitParenExpression(ParenExpressionContext ctx) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        final IoNode expression;
        if (ctx.expression() != null) {
            expression = visitExpression(ctx.expression());
        } else {
            expression = visitEmptyExpression(startPos, length);
        }
        assert expression != null;
        final IoNode resultNode = factory.createParenExpression(expression, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitInlinedMessage(InlinedMessageContext ctx) {
        if (ctx.returnMessage() != null) {
            return visitReturnMessage(ctx.returnMessage());
        }
        if (ctx.operatorMessage() != null) {
            return visitOperatorMessage(ctx.operatorMessage());
        }
        if (ctx.continueMessage() != null) {
            return factory.createContinue(ctx.continueMessage().CONTINUE().getSymbol());
        }
        if (ctx.breakMessage() != null) {
            return factory.createBreak(ctx.breakMessage().BREAK().getSymbol());
        }
        if (ctx.listMessage() != null) {
            return visitListMessage(ctx.listMessage());
        }
        if (ctx.blockMessage() != null) {
            return visitBlockMessage(ctx.blockMessage());
        }
        if (ctx.ifMessageVariants() != null) {
            return visitIfMessageVariants(ctx.ifMessageVariants());
        }
        if (ctx.whileMessage() != null) {
            return visitWhileMessage(ctx.whileMessage());
        }
        if (ctx.tryMessage() != null) {
            return visitTryMessage(ctx.tryMessage());
        }
        throw new ShouldNotBeHereException();
    }

    public IoNode visitOperatorMessage(final OperatorMessageContext ctx) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        final IoNode nameNode = visitOperator(ctx.operator());
        final List<IoNode> argumentNodes = new ArrayList<>();
        if (ctx.operation() != null) {
            IoNode argumentNode = visitOperation(ctx.operation());
            assert argumentNode != null;
            argumentNodes.add(argumentNode);
        }
        final IoNode resultNode = factory.createInvokeSlot(null, nameNode, argumentNodes, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitMessageNext(final MessageNextContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public IoNode visitMessageNext(final MessageNextContext ctx, IoNode receiverNode) {
        if (ctx.repeatMessage() != null) {
            return visitRepeatMessage(ctx.repeatMessage(), receiverNode);
        }
        if (ctx.forMessage() != null) {
            return visitForMessage(ctx.forMessage(), receiverNode);
        }
        if (ctx.foreachMessage() != null) {
            return visitForeachMessage(ctx.foreachMessage(), receiverNode);
        }
        if (ctx.doMessage() != null) {
            return visitDoMessage(ctx.doMessage(), receiverNode);
        }
        if (ctx.getSlotMessage() != null) {
            return visitGetSlotMessage(ctx.getSlotMessage(), receiverNode);
        }
        if (ctx.setSlotMessage() != null) {
            return visitSetSlotMessage(ctx.setSlotMessage(), receiverNode);
        }
        if (ctx.slotNamesMessage() != null) {
            return visitSlotNamesMessage(ctx.slotNamesMessage(), receiverNode);
        }
        if (ctx.thisLocalContextMessage() != null) {
            return visitThisLocalContextMessage(ctx.thisLocalContextMessage(), receiverNode);
        }
        if (ctx.messageInvoke() != null) {
            return visitMessageInvoke(ctx.messageInvoke(), receiverNode);
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitModifiedMessageNext(final ModifiedMessageNextContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public IoNode visitModifiedMessageNext(final ModifiedMessageNextContext ctx, IoNode receiverNode) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        IoNode targetNode = receiverNode == null ? factory.createReadSelfOrTarget(startPos, length) : receiverNode;
        return visitMessageNext(ctx.messageNext(), targetNode);
    }

    public IoNode visitMessageInvoke(final MessageInvokeContext ctx, IoNode receiverNode) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        final List<IoNode> argumentNodes = createArgumentsList(ctx.arguments());
        final IoNode nameNode;
        if (ctx.identifier() != null) {
            nameNode = visitIdentifier(ctx.identifier());
        } else if (ctx.operator() != null) {
            if (receiverNode != null && argumentNodes.size() == 1) {
                final IoNode resultNode = factory.createBinary(ctx.operator().start, receiverNode,
                        argumentNodes.get(0));
                if (resultNode != null) {
                    return resultNode;
                }
            }
            nameNode = visitOperator(ctx.operator());
        } else {
            throw new ShouldNotBeHereException();
        }
        final IoNode resultNode = factory.createInvokeSlot(receiverNode, nameNode, argumentNodes, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitArguments(final ArgumentsContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public List<IoNode> createArgumentsList(final ArgumentsContext ctx) {
        List<IoNode> argumentNodes = new ArrayList<>();
        if (ctx != null) {
            for (final ExpressionContext expressionCtx : ctx.expression()) {
                IoNode argumentNode = visitExpression(expressionCtx);
                assert argumentNode != null;
                argumentNodes.add(argumentNode);
            }
        }
        return argumentNodes;
    }

    public List<IoNode> createArgumentsList(final ParenExpressionContext ctx) {
        List<IoNode> argumentNodes = new ArrayList<>();
        if (ctx != null) {
            IoNode argumentNode = visitParenExpression(ctx);
            assert argumentNode != null;
            argumentNodes.add(argumentNode);
        }
        return argumentNodes;
    }

    public IoNode visitSlotNamesMessage(final SlotNamesMessageContext ctx, IoNode receiverNode) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        IoNode resultNode = null;
        if (receiverNode == null) {
            resultNode = factory.createListLocalSlotNames(startPos, length);
        }
        if (resultNode != null) {
            return resultNode;
        }
        List<IoNode> argumentNodes = new ArrayList<>();
        final IoNode nameNode = factory.createStringLiteral(ctx.start, false);
        resultNode = factory.createInvokeSlot(receiverNode, nameNode, argumentNodes, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitThisLocalContextMessage(final ThisLocalContextMessageContext ctx, IoNode receiverNode) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        final IoNode resultNode = factory.createThisLocalContext(receiverNode, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitGetSlotMessage(final GetSlotMessageContext ctx, IoNode receiverNode) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        final IoNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression());
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        IoNode resultNode = null;
        if (receiverNode == null) {
            resultNode = factory.createGetSlot(nameNode, startPos, length);
        }
        if (resultNode != null) {
            return resultNode;
        }
        List<IoNode> argumentNodes = new ArrayList<>();
        argumentNodes.add(nameNode);
        final IoNode getSlotNameNode = factory.createStringLiteral(ctx.start, false);
        resultNode = factory.createInvokeSlot(receiverNode, getSlotNameNode, argumentNodes, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitSetSlotMessage(final SetSlotMessageContext ctx, IoNode receiverNode) {
        boolean initialize = ctx.UPDATE_SLOT() == null;
        final IoNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        assert nameNode != null;
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        final IoNode valueNode;
        if (ctx.value == null) {
            valueNode = factory.createNil(startPos, length);
        } else {
            valueNode = visitExpression(ctx.value);
        }
        assert valueNode != null;
        IoNode resultNode = factory.createWriteSlot(receiverNode, nameNode, valueNode, startPos, length, initialize);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitRepeatMessage(final RepeatMessageContext ctx, IoNode r) {
        factory.startLoop();
        final IoNode bodyNode;
        if (ctx.expression() == null) {
            int startPos = ctx.OPEN().getSymbol().getStartIndex();
            int length = ctx.CLOSE().getSymbol().getStopIndex() - startPos + 1;
            bodyNode = visitEmptyExpression(startPos, length);
        } else {
            bodyNode = visitExpression(ctx.expression());
        }
        IoNode receiverNode = r;
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf(startPos, length);
        }
        IoNode resultNode = factory.createRepeat(receiverNode, bodyNode, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitForMessage(ForMessageContext ctx, IoNode receiverNode) {
        factory.startLoop();
        IoNode nameNode = visitIdentifier(ctx.identifier());
        IoNode startValueNode = visitExpression(ctx.startPart);
        IoNode endValueNode = visitExpression(ctx.endPart);
        IoNode stepValueNode = null;
        if (ctx.stepPart != null) {
            stepValueNode = visitExpression(ctx.stepPart);
        }
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        IoNode initializationNode = factory.createWriteSlot(receiverNode, nameNode, startValueNode, startPos, length,
                true);
        IoNode bodyNode = visitExpression(ctx.body);
        IoNode resultNode = factory.createFor(receiverNode, nameNode, initializationNode, startValueNode,
                endValueNode, stepValueNode, bodyNode,
                startPos, length);
        assert resultNode != null;
        return factory.createLoopExpression(resultNode, startPos, length);
    }

    public IoNode visitForeachMessage(ForeachMessageContext ctx, IoNode receiverNode) {
        factory.startLoop();
        IoNode nameNode = visitIdentifier(ctx.identifier());
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - startPos + 1;
        IoNode nilNode = factory.createNil(startPos, length);
        IoNode writeValueNode = factory.createWriteSlot(null, nameNode, nilNode, startPos, length, true);
        IoNode bodyNode = visitExpression(ctx.body);
        IoNode resultNode = factory.createForeach(receiverNode, writeValueNode, bodyNode, startPos, length);
        assert resultNode != null;
        return factory.createLoopExpression(resultNode, startPos, length);
    }

    public IoNode visitTargetExpression(final ExpressionContext ctx, int startPos, int length) {
        List<IoNode> body = new ArrayList<>();
        if (ctx != null) {
            for (final OperationOrAssignmentContext operationOrAssignmentCtx : ctx.operationOrAssignment()) {
                IoNode operationNOrAssignmentode = visitOperationOrAssignment(operationOrAssignmentCtx);
                if (operationNOrAssignmentode != null) {
                    body.add(operationNOrAssignmentode);
                }
            }
        }
        body.add(factory.createReadTarget(startPos, length));
        final IoNode resultNode = factory.createExpression(body, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    public IoNode visitDoMessage(final DoMessageContext ctx, IoNode receiverNode) {
        int bodyStart = ctx.OPEN().getSymbol().getStartIndex();
        int length = ctx.CLOSE().getSymbol().getStopIndex() - bodyStart + 1;
        factory.enterNewScope(bodyStart);
        final IoNode bodyNode = visitTargetExpression(ctx.expression(), bodyStart, length);
        FunctionLiteralNode functionNode = factory.createFunction(bodyNode, bodyStart, length);
        int startPos = ctx.start.getStartIndex();
        length = ctx.stop.getStopIndex() - startPos + 1;
        IoNode resultNode = factory.createDo(receiverNode, functionNode, startPos, length);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitReturnMessage(final ReturnMessageContext ctx) {
        IoNode valueNode = null;
        if (ctx.operation() != null) {
            valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
        }
        IoNode returnNode = factory.createReturn(ctx.RETURN().getSymbol(), valueNode);
        return returnNode;
    }

    @Override
    public IoNode visitIfMessageVariants(IfMessageVariantsContext ctx) {
        IoNode conditionNode = null;
        IoNode thenPartNode = null;
        IoNode elsePartNode = null;
        if (ctx.ifArguments() != null) {
            conditionNode = visitExpression(ctx.ifArguments().condition);
            thenPartNode = visitExpression(ctx.ifArguments().thenPart);
            if (ctx.ifArguments().elsePart != null) {
                elsePartNode = visitExpression(ctx.ifArguments().elsePart);
            }
        } else {
            conditionNode = visitExpression(ctx.ifMessage().condition);
            thenPartNode = visitExpression(ctx.thenMessage().thenPart);
            if (ctx.elseMessageVariants() != null) {
                elsePartNode = visitElseMessageVariants(ctx.elseMessageVariants());
            }
        }
        IoNode resultNode = factory.createIfThenElse(ctx.start, conditionNode, thenPartNode, elsePartNode);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitElseMessageVariants(ElseMessageVariantsContext ctx) {
        if (ctx.elseMessage() != null) {
            return visitExpression(ctx.elseMessage().elsePart);
        }
        IoNode conditionNode = null;
        IoNode thenPartNode = null;
        IoNode elsePartNode = null;
        if (ctx.ifArguments() != null) {
            conditionNode = visitExpression(ctx.ifArguments().condition);
            thenPartNode = visitExpression(ctx.ifArguments().thenPart);
            if (ctx.ifArguments().elsePart != null) {
                elsePartNode = visitExpression(ctx.ifArguments().elsePart);
            }
        } else {
            conditionNode = visitExpression(ctx.elseifMessage().condition);
            thenPartNode = visitExpression(ctx.thenMessage().thenPart);
            if (ctx.elseMessageVariants() != null) {
                elsePartNode = visitElseMessageVariants(ctx.elseMessageVariants());
            }
        }
        IoNode resultNode = factory.createIfThenElse(ctx.start, conditionNode, thenPartNode, elsePartNode);
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitWhileMessage(WhileMessageContext ctx) {
        factory.startLoop();
        IoNode conditionNode = visitExpression(ctx.condition);
        IoNode bodyNode = visitExpression(ctx.body);
        IoNode resultNode = factory.createWhile(ctx.WHILE().getSymbol(), conditionNode, bodyNode);
        return factory.createLoopExpression(resultNode, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IoNode visitListMessage(ListMessageContext ctx) {
        List<IoNode> elementNodes = new ArrayList<>();
        if (ctx.arguments() != null) {
            for (final ExpressionContext expressionCtx : ctx.arguments().expression()) {
                IoNode elementNode = visitExpression(expressionCtx);
                assert elementNode != null;
                elementNodes.add(elementNode);
            }
        }
        return factory.createListLiteral(elementNodes, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IoNode visitTryMessage(TryMessageContext ctx) {
        IoNode bodyNode = visitExpression(ctx.expression());
        assert bodyNode != null;
        return factory.createTry(bodyNode, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IoNode visitBlockMessage(BlockMessageContext ctx) {
        final int blockStartPos;
        if (ctx.expression() == null) {
            blockStartPos = ctx.CLOSE().getSymbol().getStartIndex();
        } else {
            blockStartPos = ctx.expression().start.getStartIndex();
        }
        factory.enterNewLocalsScope(blockStartPos);
        if (ctx.parameterList() != null) {
            for (final IdentifierContext identifierCtx : ctx.parameterList().identifier()) {
                factory.addFormalParameter(identifierCtx.start);
            }
        }
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IoNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(blockStartPos, length);
        }
        final IoNode resultNode;
        if (ctx.BLOCK() != null) {
            resultNode = factory.createBlock(bodyNode, startPos, length);
        } else if (ctx.METHOD() != null) {
            resultNode = factory.createMethod(bodyNode, startPos, length);
        } else {
            throw new ShouldNotBeHereException();
        }
        assert resultNode != null;
        return resultNode;
    }

    @Override
    public IoNode visitIdentifier(IdentifierContext ctx) {
        return factory.createStringLiteral(ctx.start, false);
    }

    @Override
    public IoNode visitOperator(OperatorContext ctx) {
        return factory.createStringLiteral(ctx.start, false);
    }

    @Override
    public IoNode visitLiteral(final LiteralContext ctx) {
        if (ctx.str != null) {
            return factory.createStringLiteral(ctx.str, true);
        }
        if (ctx.number() != null) {
            return visitNumber(ctx.number());
        }
        if (ctx.pseudoVariable() != null) {
            return visitPseudoVariable(ctx.pseudoVariable());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitNumber(NumberContext ctx) {
        if (ctx.decimal() != null) {
            return visitDecimal(ctx.decimal());
        }
        if (ctx.FLOAT() != null) {
            return factory.createNumericLiteral(ctx.FLOAT().getSymbol());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitDecimal(DecimalContext ctx) {
        if (ctx.INTEGER() != null) {
            return factory.createNumericLiteral(ctx.INTEGER().getSymbol());
        }
        throw new NotImplementedException();
    }

    @Override
    public IoNode visitPseudoVariable(PseudoVariableContext ctx) {
        if (ctx.TRUE() != null) {
            return factory.createBoolean(ctx.TRUE().getSymbol());
        }
        if (ctx.FALSE() != null) {
            return factory.createBoolean(ctx.FALSE().getSymbol());
        }
        if (ctx.NIL() != null) {
            return factory.createNil(ctx.NIL().getSymbol());
        }
        throw new ShouldNotBeHereException();
    }
}
