package org.truffle.io.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.truffle.io.IoLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.nodes.IoNode;
import org.truffle.io.nodes.literals.FunctionLiteralNode;
import org.truffle.io.nodes.literals.NilLiteralNode;
import org.truffle.io.parser.IoLanguageParser.ArgumentsContext;
import org.truffle.io.parser.IoLanguageParser.AssignmentContext;
import org.truffle.io.parser.IoLanguageParser.BlockMessageContext;
import org.truffle.io.parser.IoLanguageParser.DecimalContext;
import org.truffle.io.parser.IoLanguageParser.DoMessageContext;
import org.truffle.io.parser.IoLanguageParser.ElseMessageVariantsContext;
import org.truffle.io.parser.IoLanguageParser.ExpressionContext;
import org.truffle.io.parser.IoLanguageParser.ForMessageContext;
import org.truffle.io.parser.IoLanguageParser.GetSlotMessageContext;
import org.truffle.io.parser.IoLanguageParser.IdentifierContext;
import org.truffle.io.parser.IoLanguageParser.IfMessageVariantsContext;
import org.truffle.io.parser.IoLanguageParser.IolanguageContext;
import org.truffle.io.parser.IoLanguageParser.ListMessageContext;
import org.truffle.io.parser.IoLanguageParser.LiteralContext;
import org.truffle.io.parser.IoLanguageParser.LiteralMessageContext;
import org.truffle.io.parser.IoLanguageParser.MessageContext;
import org.truffle.io.parser.IoLanguageParser.MessageInvokeContext;
import org.truffle.io.parser.IoLanguageParser.MessageNextContext;
import org.truffle.io.parser.IoLanguageParser.NumberContext;
import org.truffle.io.parser.IoLanguageParser.OperationContext;
import org.truffle.io.parser.IoLanguageParser.PseudoVariableContext;
import org.truffle.io.parser.IoLanguageParser.RepeatMessageContext;
import org.truffle.io.parser.IoLanguageParser.ReturnMessageContext;
import org.truffle.io.parser.IoLanguageParser.SetSlotMessageContext;
import org.truffle.io.parser.IoLanguageParser.SlotNamesMessageContext;
import org.truffle.io.parser.IoLanguageParser.SubexpressionContext;
import org.truffle.io.parser.IoLanguageParser.ThisLocalContextMessageContext;
import org.truffle.io.parser.IoLanguageParser.TryMessageContext;
import org.truffle.io.parser.IoLanguageParser.WhileMessageContext;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;

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
        LOGGER.fine("Started visitIolanguage()");
        factory.enterNewScope(ctx.start.getStartIndex());
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IoNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(startPos, length);
        }
        final IoNode result = factory.createFunction(bodyNode, startPos, length);
        assert result != null;
        LOGGER.fine("Ended visitIolanguage()");
        return result;
    }

    @Override
    public IoNode visitExpression(final ExpressionContext ctx) {
        List<IoNode> body = new ArrayList<>();

        for (final OperationContext operationCtx : ctx.operation()) {
            IoNode operationNode = visitOperation(operationCtx);
            if (operationNode != null) {
                body.add(operationNode);
            }
        }
        final IoNode result = factory.createExpression(body, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
        assert result != null;
        return result;
    }

    public IoNode visitEmptyExpression(int startPos, int length) {
        List<IoNode> body = new ArrayList<>();
        body.add(new NilLiteralNode());
        final IoNode result = factory.createExpression(body, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IoNode visitOperation(final OperationContext ctx) {
        if (ctx.assignment() != null) {
            return visitAssignment(ctx.assignment());
        }
        if (ctx.message() != null) {
            return visitMessage(ctx.message());
        }
        if (ctx.op == null) {
            throw new ShouldNotBeHereException();
        }
        try {
            IoNode result = null;
            IoNode leftNode = visitOperation(ctx.operation(0));
            IoNode rightNode = visitOperation(ctx.operation(1));
            switch (ctx.op.getText()) {
                default:
                    result = factory.createBinary(ctx.op, leftNode, rightNode);
            }
            assert result != null;
            return result;
        } catch (RuntimeException exception) {
            throw new NotImplementedException();
        }
    }

    @Override
    public IoNode visitAssignment(final AssignmentContext ctx) {
        boolean initialize = false;
        switch (ctx.assign.getText()) {
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
                throw new NotImplementedException();
        }
        IoNode receiverNode = null;
        if (ctx.message() != null) {
            receiverNode = visitMessage(ctx.message());
            assert receiverNode != null;
        }
        IoNode assignmentNameNode = visitIdentifier(ctx.name);
        assert assignmentNameNode != null;
        IoNode valueNode = visitOperation(ctx.operation());
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IoNode result = factory.createWriteSlot(receiverNode, assignmentNameNode, valueNode, start, length, initialize);
        assert result != null;
        return result;
    }

    @Override
    public IoNode visitMessage(final MessageContext ctx) {
        if (ctx.subexpression() != null) {
            return visitSubexpression(ctx.subexpression());
        }
        if (ctx.literalMessage() != null) {
            return visitLiteralMessage(ctx.literalMessage());
        }

        IoNode receiver = null;
        if (ctx.literal() != null) {
            receiver = visitLiteral(ctx.literal());
        } else if (ctx.message() != null) {
            receiver = visitMessage(ctx.message());
        }
        if (ctx.messageNext() == null) {
            return receiver;
        }
        return visitMessageNext(ctx.messageNext(), receiver);
    }

    @Override
    public IoNode visitSubexpression(SubexpressionContext ctx) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        final IoNode expression;
        if (ctx.expression() != null) {
            expression = visitExpression(ctx.expression());
        } else {
            expression = visitEmptyExpression(startPos, length);
        }
        assert expression != null;
        final IoNode result = factory.createParenExpression(expression, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IoNode visitLiteralMessage(LiteralMessageContext ctx) {
        if (ctx.returnMessage() != null) {
            return visitReturnMessage(ctx.returnMessage());
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
        if (ctx.forMessage() != null) {
            return visitForMessage(ctx.forMessage());
        }
        if (ctx.whileMessage() != null) {
            return visitWhileMessage(ctx.whileMessage());
        }
        if (ctx.tryMessage() != null) {
            return visitTryMessage(ctx.tryMessage());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IoNode visitMessageNext(final MessageNextContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public IoNode visitMessageNext(final MessageNextContext ctx, IoNode receiverNode) {
        if (ctx.repeatMessage() != null) {
            return visitRepeatMessage(ctx.repeatMessage(), receiverNode);
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

    public IoNode visitMessageInvoke(final MessageInvokeContext ctx, IoNode receiverNode) {
        IoNode result = null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        List<IoNode> argumentNodes = createArgumentsList(ctx.arguments());
        result = factory.createInvokeSlot(receiverNode, ctx.identifier().start, argumentNodes, start, length);
        assert result != null;
        return result;
    }

    public IoNode visitSlotNamesMessage(final SlotNamesMessageContext ctx, IoNode receiverNode) {
        IoNode result = null;
        if (receiverNode == null) {
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            result = factory.createListLocalSlotNames(start, length);
            if(result != null) {
                return result;
            }
        }
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        List<IoNode> argumentNodes = new ArrayList<>();
        result = factory.createInvokeSlot(receiverNode, ctx.start, argumentNodes, start, length);
        assert result != null;
        return result;
    }

    public IoNode visitThisLocalContextMessage(final ThisLocalContextMessageContext ctx, IoNode receiverNode) {
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        final IoNode result =  factory.createThisLocalContext(receiverNode, start, length);
        assert result != null;
        return result;
    }

    public IoNode visitGetSlotMessage(final GetSlotMessageContext ctx, IoNode receiverNode) {
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        final IoNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression());
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        final IoNode result = factory.createGetSlot(receiverNode, nameNode, start, length);
        assert result != null;
        return result;
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
        IoNode valueNode = visitExpression(ctx.value);
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IoNode result = factory.createWriteSlot(receiverNode, nameNode, valueNode, start, length, initialize);
        assert result != null;
        return result;
    }

    public IoNode visitRepeatMessage(final RepeatMessageContext ctx, IoNode r) {
        factory.startLoop();
        final IoNode bodyNode;
        if (ctx.expression() == null) {
            int start = ctx.OPEN().getSymbol().getStartIndex();
            int length = ctx.CLOSE().getSymbol().getStopIndex() - start + 1;
            bodyNode = visitEmptyExpression(start, length);
        } else {
            bodyNode = visitExpression(ctx.expression());
        }
        IoNode receiverNode = r;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf();
        }
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IoNode result = factory.createRepeat(receiverNode, bodyNode, start, length);
        assert result != null;
        return result;
    }

    public IoNode visitTargetExpression(final ExpressionContext ctx, int startPos, int length) {
        List<IoNode> body = new ArrayList<>();
        if (ctx != null) {
            for (final OperationContext operationCtx : ctx.operation()) {
                IoNode operationNode = visitOperation(operationCtx);
                if (operationNode != null) {
                    body.add(operationNode);
                }
            }
        }
        body.add(factory.createReadTarget());
        final IoNode result = factory.createExpression(body, startPos, length);
        assert result != null;
        return result;
    }

    public IoNode visitDoMessage(final DoMessageContext ctx, IoNode receiverNode) {
        int bodyStart = ctx.OPEN().getSymbol().getStartIndex();
        int length = ctx.CLOSE().getSymbol().getStopIndex() - bodyStart + 1;
        factory.enterNewScope(bodyStart);
        final IoNode bodyNode = visitTargetExpression(ctx.expression(), bodyStart, length);
        FunctionLiteralNode functionNode = factory.createFunction(bodyNode, bodyStart, length);
        int start = ctx.start.getStartIndex();
        length = ctx.stop.getStopIndex() - start + 1;
        IoNode result = factory.createDo(receiverNode, functionNode, start, length);
        assert result != null;
        return result;
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
            if(ctx.ifArguments().elsePart != null) {
                elsePartNode = visitExpression(ctx.ifArguments().elsePart);
            }
        } else {
            conditionNode = visitExpression(ctx.ifMessage().condition);
            thenPartNode = visitExpression(ctx.thenMessage().thenPart);
            if (ctx.elseMessageVariants() != null) {
                elsePartNode = visitElseMessageVariants(ctx.elseMessageVariants());
            }    
        }
        IoNode result = factory.createIfThenElse(ctx.start, conditionNode, thenPartNode, elsePartNode);
        assert result != null;
        return result;
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
            if(ctx.ifArguments().elsePart != null) {
                elsePartNode = visitExpression(ctx.ifArguments().elsePart);
            }
        } else {
            conditionNode = visitExpression(ctx.elseifMessage().condition);
            thenPartNode = visitExpression(ctx.thenMessage().thenPart);
            if (ctx.elseMessageVariants() != null) {
                elsePartNode = visitElseMessageVariants(ctx.elseMessageVariants());
            }    
        }
        IoNode result = factory.createIfThenElse(ctx.start, conditionNode, thenPartNode, elsePartNode);
        assert result != null;
        return result;
    }
    
    @Override
    public IoNode visitWhileMessage(WhileMessageContext ctx) {
        factory.startLoop();
        IoNode conditionNode = visitExpression(ctx.condition);
        IoNode bodyNode = visitExpression(ctx.body);
        IoNode result = factory.createWhile(ctx.WHILE().getSymbol(), conditionNode, bodyNode);
        return factory.createLoopExpression(result, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IoNode visitForMessage(ForMessageContext ctx) {
        // throw new NotImplementedException();
        factory.startLoop();
        IoNode slotNameNode = visitIdentifier(ctx.identifier());
        IoNode startValueNode = visitExpression(ctx.startPart);
        IoNode endValueNode = visitExpression(ctx.endPart);
        IoNode stepValueNode = null;
        if (ctx.stepPart != null) {
            stepValueNode = visitExpression(ctx.stepPart);
        }
        IoNode bodyNode = visitExpression(ctx.body);
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IoNode result = factory.createForSlot(slotNameNode, startValueNode, endValueNode, stepValueNode, bodyNode,
                startPos, length);
        assert result != null;
        return factory.createLoopExpression(result, startPos, length);
    }

    @Override
    public IoNode visitListMessage(ListMessageContext ctx) {
        List<IoNode> elementNodes = new ArrayList<>();
        for (final ExpressionContext expressionCtx : ctx.arguments().expression()) {
            IoNode elementNode = visitExpression(expressionCtx);
            assert elementNode != null;
            elementNodes.add(elementNode);
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
        final IoNode result;
        if (ctx.BLOCK() != null) {
            result = factory.createBlock(bodyNode, startPos, length);
        } else if (ctx.METHOD() != null) {
            result = factory.createMethod(bodyNode, startPos, length);
        } else {
            throw new ShouldNotBeHereException();
        }
        assert result != null;
        return result;
    }

    @Override
    public IoNode visitIdentifier(IdentifierContext ctx) {
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
        throw new NotImplementedException();
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