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
import org.truffle.io.IOLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.nodes.expression.ExpressionNode;
import org.truffle.io.nodes.literals.FunctionLiteralNode;
import org.truffle.io.nodes.literals.NilLiteralNode;
import org.truffle.io.parser.IOLanguageParser.ArgumentsContext;
import org.truffle.io.parser.IOLanguageParser.AssignmentContext;
import org.truffle.io.parser.IOLanguageParser.DecimalContext;
import org.truffle.io.parser.IOLanguageParser.ExpressionContext;
import org.truffle.io.parser.IOLanguageParser.ForMessageContext;
import org.truffle.io.parser.IOLanguageParser.IdentifierContext;
import org.truffle.io.parser.IOLanguageParser.IfMessageContext;
import org.truffle.io.parser.IOLanguageParser.IfThenElseMessageContext;
import org.truffle.io.parser.IOLanguageParser.IolanguageContext;
import org.truffle.io.parser.IOLanguageParser.ListMessageContext;
import org.truffle.io.parser.IOLanguageParser.LiteralContext;
import org.truffle.io.parser.IOLanguageParser.LiteralMessageContext;
import org.truffle.io.parser.IOLanguageParser.MessageContext;
import org.truffle.io.parser.IOLanguageParser.MethodMessageContext;
import org.truffle.io.parser.IOLanguageParser.NumberContext;
import org.truffle.io.parser.IOLanguageParser.OperationContext;
import org.truffle.io.parser.IOLanguageParser.PseudoVariableContext;
import org.truffle.io.parser.IOLanguageParser.ReturnMessageContext;
import org.truffle.io.parser.IOLanguageParser.SequenceContext;
import org.truffle.io.parser.IOLanguageParser.SubexpressionContext;
import org.truffle.io.parser.IOLanguageParser.WhileMessageContext;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;

public class IOLanguageNodeVisitor extends IOLanguageBaseVisitor<ExpressionNode> {

    private static final TruffleLogger LOGGER = IOLanguage.getLogger(IOLanguageNodeVisitor.class);

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
        throw new ParseException(source, line, col, length,
                String.format("Error(s) parsing script:%n" + location + message));
    }

    public RootCallTarget parseIO(IOLanguage language, Source source) {
        IOLanguageLexer lexer = new IOLanguageLexer(CharStreams.fromString(source.getCharacters().toString()));
        IOLanguageParser parser = new IOLanguageParser(new CommonTokenStream(lexer));
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
    public ExpressionNode visitIolanguage(IolanguageContext ctx) {
        LOGGER.fine("Started visitIolanguage()");
        factory.startDo(ctx.start);
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        ExpressionNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(startPos, length);
        }
        final ExpressionNode result = factory.finishDo(bodyNode, startPos, length);
        assert result != null;
        LOGGER.fine("Ended visitIolanguage()");
        return result;
    }

    @Override
    public ExpressionNode visitExpression(final ExpressionContext ctx) {
        List<ExpressionNode> body = new ArrayList<>();

        for (final OperationContext operationCtx : ctx.operation()) {
            ExpressionNode operationNode = visitOperation(operationCtx);
            if (operationNode != null) {
                body.add(operationNode);
            }
        }
        final ExpressionNode result = factory.createBlock(body, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
        assert result != null;
        return result;
    }

    public ExpressionNode visitEmptyExpression(int startPos, int length) {
        List<ExpressionNode> body = new ArrayList<>();
        body.add(new NilLiteralNode());
        final ExpressionNode result = factory.createBlock(body, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public ExpressionNode visitOperation(final OperationContext ctx) {
        if (ctx.assignment() != null) {
            return visitAssignment(ctx.assignment());
        }
        if (ctx.sequence() != null) {
            return visitSequence(ctx.sequence());
        }
        if (ctx.op == null) {
            throw new ShouldNotBeHereException();
        }
        try {
            ExpressionNode result = null;
            ExpressionNode leftNode = visitOperation(ctx.operation(0));
            ExpressionNode rightNode = visitOperation(ctx.operation(1));
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
    public ExpressionNode visitAssignment(final AssignmentContext ctx) {
        if (ctx.assign.getText().equals(":=")) {
            ExpressionNode receiverNode = null;
            if (ctx.sequence() != null) {
                receiverNode = visitSequence(ctx.sequence());
                assert receiverNode != null;
            }
            ExpressionNode assignmentNameNode = visitIdentifier(ctx.name);
            assert assignmentNameNode != null;
            ExpressionNode valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            ExpressionNode result = factory.createWriteSlot(receiverNode, assignmentNameNode, valueNode, start,
                    length);
            assert result != null;
            return result;
        }
        throw new NotImplementedException();
    }

    @Override
    public ExpressionNode visitSequence(final SequenceContext ctx) {
        ExpressionNode result = null;
        if (ctx.literal() != null) {
            result = visitLiteral(ctx.literal());
            assert result != null;
        } else if (ctx.literalMessage() != null) {
            result = visitLiteralMessage(ctx.literalMessage());
            assert result != null;
        } else if (ctx.subexpression() != null) {
            result = visitSubexpression(ctx.subexpression());
            assert result != null;
        }
        if (ctx.message() != null && !ctx.message().isEmpty()) {
            for (final MessageContext messageCtx : ctx.message()) {
                ExpressionNode receiver = result;
                result = visitMessage(messageCtx, receiver);
            }
            assert result != null;
        }
        return result;
    }

    @Override
    public ExpressionNode visitSubexpression(SubexpressionContext ctx) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        final ExpressionNode expression;
        if (ctx.expression() != null) {
            expression = visitExpression(ctx.expression());
        } else {
            expression = visitEmptyExpression(startPos, length);
        }
        assert expression != null;
        final ExpressionNode result = factory.createParenExpression(expression, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public ExpressionNode visitLiteralMessage(LiteralMessageContext ctx) {
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
        if (ctx.methodMessage() != null) {
            return visitMethodMessage(ctx.methodMessage());
        }
        if (ctx.ifMessage() != null) {
            return visitIfMessage(ctx.ifMessage());
        }
        if (ctx.forMessage() != null) {
            return visitForMessage(ctx.forMessage());
        }
        if (ctx.whileMessage() != null) {
            return visitWhileMessage(ctx.whileMessage());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public ExpressionNode visitMessage(final MessageContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public ExpressionNode visitMessage(final MessageContext ctx, ExpressionNode receiverNode) {
        if (ctx.AT() != null) {
            return visitAtMessage(ctx, receiverNode);
        }
        if (ctx.AT_PUT() != null) {
            return visitAtPutMessage(ctx, receiverNode);
        }
        if (ctx.GET_SLOT() != null) {
            return visitGetSlotMessage(ctx, receiverNode);
        }
        if (ctx.NEW_SLOT() != null) {
            return visitNewSlotMessage(ctx, receiverNode);
        }
        if (ctx.SET_SLOT() != null) {
            return visitSetSlotMessage(ctx, receiverNode);
        }
        if (ctx.UPDATE_SLOT() != null) {
            return visitUpdateSlotMessage(ctx, receiverNode);
        }
        if (ctx.REPEAT() != null) {
            return visitRepeatMessage(ctx, receiverNode);
        }
        if (ctx.id != null) {
            final ExpressionNode identifierNode = factory.createStringLiteral(ctx.id, false);
            assert identifierNode != null;
            List<ExpressionNode> argumentNodes = createArgumentsList(ctx.arguments());
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            ExpressionNode result = factory.createInvokeSlot(receiverNode, identifierNode, argumentNodes, start,
                    length);
            assert result != null;
            return result;
        }
        throw new ShouldNotBeHereException();
    }

    public ExpressionNode visitAtMessage(final MessageContext ctx, ExpressionNode receiverNode) {
        final ExpressionNode indexNode;
        if (ctx.decimal() == null) {
            indexNode = visitExpression(ctx.expression(0));
        } else {
            indexNode = visitDecimal(ctx.decimal());
        }
        assert indexNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        ExpressionNode result = factory.createSequenceAt(receiverNode, indexNode, start, length);
        assert result != null;
        return result;
    }

    public ExpressionNode visitAtPutMessage(final MessageContext ctx, ExpressionNode r) {
        final ExpressionNode indexNode;
        if (ctx.decimal() == null) {
            indexNode = visitExpression(ctx.expression(0));
        } else {
            indexNode = visitDecimal(ctx.decimal());
        }
        assert indexNode != null;
        ExpressionNode valueNode = visitExpression(ctx.value);
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        ExpressionNode receiverNode = r;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf();
        }
        ExpressionNode result = factory.createSequenceAtPut(receiverNode, indexNode, valueNode, start, length);
        assert result != null;
        return result;
    }

    public ExpressionNode visitGetSlotMessage(final MessageContext ctx, ExpressionNode receiverNode) {
        final ExpressionNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        assert nameNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        ExpressionNode result = factory.createReadSlot(receiverNode, nameNode, start, length);
        assert result != null;
        return result;
    }

    public ExpressionNode visitNewSlotMessage(final MessageContext ctx, ExpressionNode receiverNode) {
        throw new NotImplementedException();
    }

    public ExpressionNode visitUpdateSlotMessage(final MessageContext ctx, ExpressionNode receiverNode) {
        throw new NotImplementedException();
    }

    public ExpressionNode visitSetSlotMessage(final MessageContext ctx, ExpressionNode receiverNode) {
        final ExpressionNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        assert nameNode != null;
        ExpressionNode valueNode = visitExpression(ctx.value);
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        ExpressionNode result = factory.createWriteSlot(receiverNode, nameNode, valueNode, start, length);
        assert result != null;
        return result;
    }

    public ExpressionNode visitRepeatMessage(final MessageContext ctx, ExpressionNode r) {
        factory.startLoop();
        ExpressionNode bodyNode = visitExpression(ctx.expression(0));
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        ExpressionNode receiverNode = r;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf();
        }
        ExpressionNode result = factory.createRepeat(receiverNode, bodyNode, start, length);
        assert result != null;
        return result;
    }

    @Override
    public ExpressionNode visitArguments(final ArgumentsContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public List<ExpressionNode> createArgumentsList(final ArgumentsContext ctx) {
        List<ExpressionNode> argumentNodes = new ArrayList<>();
        if (ctx != null) {
            for (final ExpressionContext expressionCtx : ctx.expression()) {
                ExpressionNode argumentNode = visitExpression(expressionCtx);
                assert argumentNode != null;
                argumentNodes.add(argumentNode);
            }
        }
        return argumentNodes;
    }

    @Override
    public ExpressionNode visitReturnMessage(final ReturnMessageContext ctx) {
        ExpressionNode valueNode = null;
        if (ctx.operation() != null) {
            valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
        }
        ExpressionNode returnNode = factory.createReturn(ctx.RETURN().getSymbol(), valueNode);
        return returnNode;
    }

    @Override
    public ExpressionNode visitIfMessage(IfMessageContext ctx) {
        if (ctx.ifMessage1() != null) {
            throw new NotImplementedException();
        }
        if (ctx.ifThenElseMessage() != null) {
            return visitIfThenElseMessage(ctx.ifThenElseMessage());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public ExpressionNode visitIfThenElseMessage(IfThenElseMessageContext ctx) {
        ExpressionNode conditionNode = visitExpression(ctx.condition);
        ExpressionNode thenPartNode = visitExpression(ctx.thenPart);
        ExpressionNode elsePartNode = null;
        if (ctx.elsePart != null) {
            elsePartNode = visitExpression(ctx.elsePart);
        }
        ExpressionNode result = factory.createIf(ctx.IF().getSymbol(), conditionNode, thenPartNode, elsePartNode);
        assert result != null;
        return result;
    }

    @Override
    public ExpressionNode visitWhileMessage(WhileMessageContext ctx) {
        factory.startLoop();
        ExpressionNode conditionNode = visitExpression(ctx.condition);
        ExpressionNode bodyNode = visitExpression(ctx.body);
        ExpressionNode result = factory.createWhile(ctx.WHILE().getSymbol(), conditionNode, bodyNode);
        return factory.createLoopBlock(result, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public ExpressionNode visitForMessage(ForMessageContext ctx) {
        // throw new NotImplementedException();
        factory.startLoop();
        ExpressionNode slotNameNode = visitIdentifier(ctx.identifier());
        ExpressionNode startValueNode = visitExpression(ctx.startPart);
        ExpressionNode endValueNode = visitExpression(ctx.endPart);
        ExpressionNode stepValueNode = null;
        if (ctx.stepPart != null) {
            stepValueNode = visitExpression(ctx.stepPart);
        }
        ExpressionNode bodyNode = visitExpression(ctx.body);
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        ExpressionNode result = factory.createForSlot(slotNameNode, startValueNode, endValueNode, stepValueNode, bodyNode,
                startPos, length);
        assert result != null;
        return factory.createLoopBlock(result, startPos, length);
    }

    @Override
    public ExpressionNode visitListMessage(ListMessageContext ctx) {
        List<ExpressionNode> elementNodes = new ArrayList<>();
        for (final ExpressionContext expressionCtx : ctx.arguments().expression()) {
            ExpressionNode elementNode = visitExpression(expressionCtx);
            assert elementNode != null;
            elementNodes.add(elementNode);
        }
        return factory.createListLiteral(elementNodes, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public ExpressionNode visitMethodMessage(MethodMessageContext ctx) {
        final Token bodyStartToken;
        if (ctx.expression() == null) {
            bodyStartToken = ctx.CLOSE().getSymbol();
        } else {
            bodyStartToken = ctx.expression().start;
        }
        factory.startMethod(bodyStartToken);
        if (ctx.parameterList() != null) {
            for (final IdentifierContext identifierCtx : ctx.parameterList().identifier()) {
                factory.addFormalParameter(identifierCtx.start);
            }
        }
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        ExpressionNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(bodyStartToken.getStartIndex(), length);
        }
        final ExpressionNode result = factory.finishMethod(bodyNode, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public ExpressionNode visitIdentifier(IdentifierContext ctx) {
        return factory.createStringLiteral(ctx.start, false);
    }

    @Override
    public ExpressionNode visitLiteral(final LiteralContext ctx) {
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
    public ExpressionNode visitNumber(NumberContext ctx) {
        if (ctx.decimal() != null) {
            return visitDecimal(ctx.decimal());
        }
        throw new NotImplementedException();
    }

    @Override
    public ExpressionNode visitDecimal(DecimalContext ctx) {
        if (ctx.INTEGER() != null) {
            return factory.createNumericLiteral(ctx.INTEGER().getSymbol());
        }
        throw new NotImplementedException();
    }

    @Override
    public ExpressionNode visitPseudoVariable(PseudoVariableContext ctx) {
        if (ctx.TRUE() != null) {
            return factory.createBoolean(ctx.TRUE().getSymbol());
        }
        if (ctx.FALSE() != null) {
            return factory.createBoolean(ctx.FALSE().getSymbol());
        }
        if (ctx.NIL() != null) {
            return factory.createNil(ctx.NIL().getSymbol());
        }
        if (ctx.SELF() != null) {
            return factory.createReadSelf();
        }
        throw new ShouldNotBeHereException();
    }
}
