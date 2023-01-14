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
import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.literals.IOMethodLiteralNode;
import org.truffle.io.nodes.literals.IONilLiteralNode;
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

public class IOLanguageNodeVisitor extends IOLanguageBaseVisitor<IOExpressionNode> {

    private static final TruffleLogger LOGGER = IOLanguage.getLogger(IOLanguageNodeVisitor.class);

    private IONodeFactory factory;
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
            // int start = lexerException.getStartIndex();
            // CharSequence line = lineNumber <= source.getLineCount() ? source.getCharacters(lineNumber) : "";
            // String substring = line.subSequence(0, Math.min(line.length(), start - source.getLineStartOffset(lineNumber) + 1)).toString();
            // String contents = token == null ? (substring.length() == 0 ? "" : substring.substring(substring.length() - 1)) : token.getText();
            int lineNr = lineNumber > source.getLineCount() ? source.getLineCount() : lineNumber;
            if (lexerException.getInputStream().LA(1) == IntStream.EOF) {
                throw new IOIncompleteSourceException(message, e, lineNr, source);
            } else {
                throwParseError(source, lineNr, charPositionInLine, token, message);
            }
        } else {
            // CharSequence line = lineNumber <= source.getLineCount() ? source.getCharacters(lineNumber) : "";
            // String substring = line.subSequence(0, Math.min(line.length(), token.getCharPositionInLine() + 1)).toString();
            // String contents = token == null ? (substring.length() == 0 ? "" : substring.substring(substring.length() - 1)) : token.getText();
            int lineNr = lineNumber > source.getLineCount() ? source.getLineCount() : lineNumber;
            if (token.getType() == Token.EOF) {
                throw new IOIncompleteSourceException(message, e, lineNr, source);
            } else {
                throwParseError(source, lineNr, charPositionInLine, token, message);
            }
        }
    }

    private static void throwParseError(Source source, int line, int charPositionInLine, Token token, String message) {
        int col = charPositionInLine + 1;
        String location = "-- line " + line + " col " + col + ": ";
        int length = token == null ? 1 : Math.max(token.getStopIndex() - token.getStartIndex(), 0);
        throw new IOParseException(source, line, col, length,
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
        this.factory = new IONodeFactory(language, source);
        this.source = source;
        IOMethodLiteralNode methodMessageNode = (IOMethodLiteralNode) visitIolanguage(parser.iolanguage());
        return methodMessageNode.getValue().getCallTarget();
    }

    @Override
    public IOExpressionNode visitIolanguage(IolanguageContext ctx) {
        LOGGER.fine("Started visitIolanguage()");
        factory.startMethod(ctx.start);
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IOExpressionNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(startPos, length);
        }
        final IOExpressionNode result = factory.finishMethod(bodyNode, startPos, length);
        assert result != null;
        LOGGER.fine("Ended visitIolanguage()");
        return result;
    }

    @Override
    public IOExpressionNode visitExpression(final ExpressionContext ctx) {
        List<IOExpressionNode> body = new ArrayList<>();

        for (final OperationContext operationCtx : ctx.operation()) {
            IOExpressionNode operationNode = visitOperation(operationCtx);
            if (operationNode != null) {
                body.add(operationNode);
            }
        }
        final IOExpressionNode result = factory.createBlock(body, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
        assert result != null;
        return result;
    }

    public IOExpressionNode visitEmptyExpression(int startPos, int length) {
        List<IOExpressionNode> body = new ArrayList<>();
        body.add(new IONilLiteralNode());
        final IOExpressionNode result = factory.createBlock(body, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IOExpressionNode visitOperation(final OperationContext ctx) {
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
            IOExpressionNode result = null;
            IOExpressionNode leftNode = visitOperation(ctx.operation(0));
            IOExpressionNode rightNode = visitOperation(ctx.operation(1));
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
    public IOExpressionNode visitAssignment(final AssignmentContext ctx) {
        if (ctx.assign.getText().equals(":=")) {
            IOExpressionNode receiverNode = null;
            if (ctx.sequence() != null) {
                receiverNode = visitSequence(ctx.sequence());
                assert receiverNode != null;
            }
            IOExpressionNode assignmentNameNode = visitIdentifier(ctx.name);
            assert assignmentNameNode != null;
            IOExpressionNode valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            IOExpressionNode result = factory.createWriteSlot(receiverNode, assignmentNameNode, valueNode, start,
                    length);
            assert result != null;
            return result;
        }
        throw new NotImplementedException();
    }

    @Override
    public IOExpressionNode visitSequence(final SequenceContext ctx) {
        IOExpressionNode result = null;
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
                IOExpressionNode receiver = result;
                result = visitMessage(messageCtx, receiver);
            }
            assert result != null;
        }
        return result;
    }

    @Override
    public IOExpressionNode visitSubexpression(SubexpressionContext ctx) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        final IOExpressionNode expression;
        if (ctx.expression() != null) {
            expression = visitExpression(ctx.expression());
        } else {
            expression = visitEmptyExpression(startPos, length);
        }
        assert expression != null;
        final IOExpressionNode result = factory.createParenExpression(expression, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IOExpressionNode visitLiteralMessage(LiteralMessageContext ctx) {
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
    public IOExpressionNode visitMessage(final MessageContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public IOExpressionNode visitMessage(final MessageContext ctx, IOExpressionNode receiverNode) {
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
            final IOExpressionNode identifierNode = factory.createStringLiteral(ctx.id, false);
            assert identifierNode != null;
            List<IOExpressionNode> argumentNodes = createArgumentsList(ctx.arguments());
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            IOExpressionNode result = factory.createInvokeSlot(receiverNode, identifierNode, argumentNodes, start,
                    length);
            assert result != null;
            return result;
        }
        throw new ShouldNotBeHereException();
    }

    public IOExpressionNode visitAtMessage(final MessageContext ctx, IOExpressionNode receiverNode) {
        final IOExpressionNode indexNode;
        if (ctx.decimal() == null) {
            indexNode = visitExpression(ctx.expression(0));
        } else {
            indexNode = visitDecimal(ctx.decimal());
        }
        assert indexNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IOExpressionNode result = factory.createSequenceAt(receiverNode, indexNode, start, length);
        assert result != null;
        return result;
    }

    public IOExpressionNode visitAtPutMessage(final MessageContext ctx, IOExpressionNode r) {
        final IOExpressionNode indexNode;
        if (ctx.decimal() == null) {
            indexNode = visitExpression(ctx.expression(0));
        } else {
            indexNode = visitDecimal(ctx.decimal());
        }
        assert indexNode != null;
        IOExpressionNode valueNode = visitExpression(ctx.value);
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IOExpressionNode receiverNode = r;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf();
        }
        IOExpressionNode result = factory.createSequenceAtPut(receiverNode, indexNode, valueNode, start, length);
        assert result != null;
        return result;
    }

    public IOExpressionNode visitGetSlotMessage(final MessageContext ctx, IOExpressionNode receiverNode) {
        final IOExpressionNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        assert nameNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IOExpressionNode result = factory.createReadSlot(receiverNode, nameNode, start, length);
        assert result != null;
        return result;
    }

    public IOExpressionNode visitNewSlotMessage(final MessageContext ctx, IOExpressionNode receiverNode) {
        throw new NotImplementedException();
    }

    public IOExpressionNode visitUpdateSlotMessage(final MessageContext ctx, IOExpressionNode receiverNode) {
        throw new NotImplementedException();
    }

    public IOExpressionNode visitSetSlotMessage(final MessageContext ctx, IOExpressionNode receiverNode) {
        final IOExpressionNode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        assert nameNode != null;
        IOExpressionNode valueNode = visitExpression(ctx.value);
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IOExpressionNode result = factory.createWriteSlot(receiverNode, nameNode, valueNode, start, length);
        assert result != null;
        return result;
    }

    public IOExpressionNode visitRepeatMessage(final MessageContext ctx, IOExpressionNode r) {
        factory.startLoop();
        IOExpressionNode bodyNode = visitExpression(ctx.expression(0));
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IOExpressionNode receiverNode = r;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf();
        }
        IOExpressionNode result = factory.createRepeat(receiverNode, bodyNode, start, length);
        assert result != null;
        return result;
    }

    @Override
    public IOExpressionNode visitArguments(final ArgumentsContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public List<IOExpressionNode> createArgumentsList(final ArgumentsContext ctx) {
        List<IOExpressionNode> argumentNodes = new ArrayList<>();
        if (ctx != null) {
            for (final ExpressionContext expressionCtx : ctx.expression()) {
                IOExpressionNode argumentNode = visitExpression(expressionCtx);
                assert argumentNode != null;
                argumentNodes.add(argumentNode);
            }
        }
        return argumentNodes;
    }

    @Override
    public IOExpressionNode visitReturnMessage(final ReturnMessageContext ctx) {
        IOExpressionNode valueNode = null;
        if (ctx.operation() != null) {
            valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
        }
        IOExpressionNode returnNode = factory.createReturn(ctx.RETURN().getSymbol(), valueNode);
        return returnNode;
    }

    @Override
    public IOExpressionNode visitIfMessage(IfMessageContext ctx) {
        if (ctx.ifMessage1() != null) {
            throw new NotImplementedException();
        }
        if (ctx.ifThenElseMessage() != null) {
            return visitIfThenElseMessage(ctx.ifThenElseMessage());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IOExpressionNode visitIfThenElseMessage(IfThenElseMessageContext ctx) {
        IOExpressionNode conditionNode = visitExpression(ctx.condition);
        IOExpressionNode thenPartNode = visitExpression(ctx.thenPart);
        IOExpressionNode elsePartNode = null;
        if (ctx.elsePart != null) {
            elsePartNode = visitExpression(ctx.elsePart);
        }
        IOExpressionNode result = factory.createIf(ctx.IF().getSymbol(), conditionNode, thenPartNode, elsePartNode);
        assert result != null;
        return result;
    }

    @Override
    public IOExpressionNode visitWhileMessage(WhileMessageContext ctx) {
        factory.startLoop();
        IOExpressionNode conditionNode = visitExpression(ctx.condition);
        IOExpressionNode bodyNode = visitExpression(ctx.body);
        IOExpressionNode result = factory.createWhile(ctx.WHILE().getSymbol(), conditionNode, bodyNode);
        return factory.createLoopBlock(result, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IOExpressionNode visitForMessage(ForMessageContext ctx) {
        throw new NotImplementedException();
        // factory.startLoopBlock();
        // IOExpressionNode counterNode = (IOExpressionNode)visitIdentifier(ctx.identifier());
        // IOExpressionNode startValueNode = visitExpressionList(ctx.startPart);
        // IOExpressionNode endValueNode = visitExpressionList(ctx.endPart);
        // IOExpressionNode stepValueNode = null;
        // if (ctx.stepPart != null) {
        //     stepValueNode = visitExpressionList(ctx.stepPart);
        // }
        // IOExpressionNode bodyNode = visitExpressionList(ctx.body);
        // IOExpressionNode result = factory.createFor(ctx.FOR().getSymbol(),
        //         counterNode, startValueNode,
        //         endValueNode, stepValueNode, bodyNode);
        // return factory.finishLoopBlock(result, ctx.start.getStartIndex(),
        //         ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IOExpressionNode visitListMessage(ListMessageContext ctx) {
        List<IOExpressionNode> elementNodes = new ArrayList<>();
        for (final ExpressionContext expressionCtx : ctx.arguments().expression()) {
            IOExpressionNode elementNode = visitExpression(expressionCtx);
            assert elementNode != null;
            elementNodes.add(elementNode);
        }
        return factory.createListLiteral(elementNodes, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IOExpressionNode visitMethodMessage(MethodMessageContext ctx) {
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
        IOExpressionNode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(bodyStartToken.getStartIndex(), length);
        }
        final IOExpressionNode result = factory.finishMethod(bodyNode, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IOExpressionNode visitIdentifier(IdentifierContext ctx) {
        return factory.createStringLiteral(ctx.start, false);
    }

    @Override
    public IOExpressionNode visitLiteral(final LiteralContext ctx) {
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
    public IOExpressionNode visitNumber(NumberContext ctx) {
        if (ctx.decimal() != null) {
            return visitDecimal(ctx.decimal());
        }
        throw new NotImplementedException();
    }

    @Override
    public IOExpressionNode visitDecimal(DecimalContext ctx) {
        if (ctx.INTEGER() != null) {
            return factory.createNumericLiteral(ctx.INTEGER().getSymbol());
        }
        throw new NotImplementedException();
    }

    @Override
    public IOExpressionNode visitPseudoVariable(PseudoVariableContext ctx) {
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
