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
import org.truffle.io.nodes.IONode;
import org.truffle.io.nodes.literals.FunctionLiteralNode;
import org.truffle.io.nodes.literals.NilLiteralNode;
import org.truffle.io.parser.IOLanguageParser.ArgumentsContext;
import org.truffle.io.parser.IOLanguageParser.AssignmentContext;
import org.truffle.io.parser.IOLanguageParser.BlockMessageContext;
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
import org.truffle.io.parser.IOLanguageParser.NumberContext;
import org.truffle.io.parser.IOLanguageParser.OperationContext;
import org.truffle.io.parser.IOLanguageParser.PseudoVariableContext;
import org.truffle.io.parser.IOLanguageParser.ReturnMessageContext;
import org.truffle.io.parser.IOLanguageParser.SequenceContext;
import org.truffle.io.parser.IOLanguageParser.SubexpressionContext;
import org.truffle.io.parser.IOLanguageParser.TryMessageContext;
import org.truffle.io.parser.IOLanguageParser.WhileMessageContext;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;

public class IOLanguageNodeVisitor extends IOLanguageBaseVisitor<IONode> {

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
        throw new ParseException(source, line, col, length, "Error(s) parsing script:" + location + message);
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
    public IONode visitIolanguage(IolanguageContext ctx) {
        LOGGER.fine("Started visitIolanguage()");
        factory.enterNewScope(ctx.start.getStartIndex());
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IONode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(startPos, length);
        }
        final IONode result = factory.createFunction(bodyNode, startPos, length);
        assert result != null;
        LOGGER.fine("Ended visitIolanguage()");
        return result;
    }

    @Override
    public IONode visitExpression(final ExpressionContext ctx) {
        List<IONode> body = new ArrayList<>();

        for (final OperationContext operationCtx : ctx.operation()) {
            IONode operationNode = visitOperation(operationCtx);
            if (operationNode != null) {
                body.add(operationNode);
            }
        }
        final IONode result = factory.createExpression(body, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
        assert result != null;
        return result;
    }

    public IONode visitEmptyExpression(int startPos, int length) {
        List<IONode> body = new ArrayList<>();
        body.add(new NilLiteralNode());
        final IONode result = factory.createExpression(body, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IONode visitOperation(final OperationContext ctx) {
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
            IONode result = null;
            IONode leftNode = visitOperation(ctx.operation(0));
            IONode rightNode = visitOperation(ctx.operation(1));
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
    public IONode visitAssignment(final AssignmentContext ctx) {
        if (ctx.assign.getText().equals(":=")) {
            IONode receiverNode = null;
            if (ctx.sequence() != null) {
                receiverNode = visitSequence(ctx.sequence());
                assert receiverNode != null;
            }
            IONode assignmentNameNode = visitIdentifier(ctx.name);
            assert assignmentNameNode != null;
            IONode valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            IONode result = factory.createWriteSlot(receiverNode, assignmentNameNode, valueNode, start, length);
            assert result != null;
            return result;
        }
        throw new NotImplementedException();
    }

    @Override
    public IONode visitSequence(final SequenceContext ctx) {
        IONode result = null;
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
                IONode receiver = result;
                result = visitMessage(messageCtx, receiver);
            }
            assert result != null;
        }
        return result;
    }

    @Override
    public IONode visitSubexpression(SubexpressionContext ctx) {
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        final IONode expression;
        if (ctx.expression() != null) {
            expression = visitExpression(ctx.expression());
        } else {
            expression = visitEmptyExpression(startPos, length);
        }
        assert expression != null;
        final IONode result = factory.createParenExpression(expression, startPos, length);
        assert result != null;
        return result;
    }

    @Override
    public IONode visitLiteralMessage(LiteralMessageContext ctx) {
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
        if (ctx.ifMessage() != null) {
            return visitIfMessage(ctx.ifMessage());
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
    public IONode visitMessage(final MessageContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public IONode visitMessage(final MessageContext ctx, IONode receiverNode) {
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
        if (ctx.DO() != null) {
            return visitDoMessage(ctx, receiverNode);
        }
        IONode result = null;
        if (ctx.SLOT_NAMES() != null) {
            result = visitSlotNamesMessage(ctx, receiverNode);
            if (result != null)
                return result;
        }
        if (ctx.id != null) {
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            List<IONode> argumentNodes = createArgumentsList(ctx.arguments());
            result = factory.createInvokeSlot(receiverNode, ctx.id, argumentNodes, start, length);
            assert result != null;
            return result;
        }
        throw new ShouldNotBeHereException();
    }

    public IONode visitSlotNamesMessage(final MessageContext ctx, IONode receiverNode) {
        IONode result = null;
        if (receiverNode == null) {
            int start = ctx.start.getStartIndex();
            int length = ctx.stop.getStopIndex() - start + 1;
            result = factory.createListLocalSlotNames(start, length);
        }
        return result;
    }

    public IONode visitGetSlotMessage(final MessageContext ctx, IONode receiverNode) {
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        final IONode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        final IONode result = factory.createGetSlot(receiverNode, nameNode, start, length);
        assert result != null;
        return result;
    }

    public IONode visitNewSlotMessage(final MessageContext ctx, IONode receiverNode) {
        throw new NotImplementedException();
    }

    public IONode visitUpdateSlotMessage(final MessageContext ctx, IONode receiverNode) {
        throw new NotImplementedException();
    }

    public IONode visitSetSlotMessage(final MessageContext ctx, IONode receiverNode) {
        final IONode nameNode;
        if (ctx.name == null) {
            nameNode = visitExpression(ctx.expression(0));
        } else {
            nameNode = factory.createStringLiteral(ctx.name, true);
        }
        assert nameNode != null;
        IONode valueNode = visitExpression(ctx.value);
        assert valueNode != null;
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IONode result = factory.createWriteSlot(receiverNode, nameNode, valueNode, start, length);
        assert result != null;
        return result;
    }

    public IONode visitRepeatMessage(final MessageContext ctx, IONode r) {
        factory.startLoop();
        final IONode bodyNode;
        if (ctx.expression() == null || ctx.expression().isEmpty()) {
            int start = ctx.OPEN().getSymbol().getStartIndex();
            int length = ctx.CLOSE().getSymbol().getStopIndex() - start + 1;
            bodyNode = visitEmptyExpression(start, length);
        } else {
            bodyNode = visitExpression(ctx.expression(0));
        }
        IONode receiverNode = r;
        if (receiverNode == null) {
            receiverNode = factory.createReadSelf();
        }
        int start = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - start + 1;
        IONode result = factory.createRepeat(receiverNode, bodyNode, start, length);
        assert result != null;
        return result;
    }

    public IONode visitTargetExpression(final ExpressionContext ctx, int startPos, int length) {
        List<IONode> body = new ArrayList<>();
        if (ctx != null) {
            for (final OperationContext operationCtx : ctx.operation()) {
                IONode operationNode = visitOperation(operationCtx);
                if (operationNode != null) {
                    body.add(operationNode);
                }
            }
        }
        body.add(factory.createReadTarget());
        final IONode result = factory.createExpression(body, startPos, length);
        assert result != null;
        return result;
    }

    public IONode visitDoMessage(final MessageContext ctx, IONode receiverNode) {
        int bodyStart = ctx.OPEN().getSymbol().getStartIndex();
        int length = ctx.CLOSE().getSymbol().getStopIndex() - bodyStart + 1;
        factory.enterNewScope(bodyStart);
        final IONode bodyNode = visitTargetExpression(ctx.expression(0), bodyStart, length);
        FunctionLiteralNode functionNode = factory.createFunction(bodyNode, bodyStart, length);
        int start = ctx.start.getStartIndex();
        length = ctx.stop.getStopIndex() - start + 1;
        IONode result = factory.createDo(receiverNode, functionNode, start, length);
        assert result != null;
        return result;
    }

    @Override
    public IONode visitArguments(final ArgumentsContext ctx) {
        throw new ShouldNotBeHereException();
    }

    public List<IONode> createArgumentsList(final ArgumentsContext ctx) {
        List<IONode> argumentNodes = new ArrayList<>();
        if (ctx != null) {
            for (final ExpressionContext expressionCtx : ctx.expression()) {
                IONode argumentNode = visitExpression(expressionCtx);
                assert argumentNode != null;
                argumentNodes.add(argumentNode);
            }
        }
        return argumentNodes;
    }

    @Override
    public IONode visitReturnMessage(final ReturnMessageContext ctx) {
        IONode valueNode = null;
        if (ctx.operation() != null) {
            valueNode = visitOperation(ctx.operation());
            assert valueNode != null;
        }
        IONode returnNode = factory.createReturn(ctx.RETURN().getSymbol(), valueNode);
        return returnNode;
    }

    @Override
    public IONode visitIfMessage(IfMessageContext ctx) {
        if (ctx.ifMessage1() != null) {
            throw new NotImplementedException();
        }
        if (ctx.ifThenElseMessage() != null) {
            return visitIfThenElseMessage(ctx.ifThenElseMessage());
        }
        throw new ShouldNotBeHereException();
    }

    @Override
    public IONode visitIfThenElseMessage(IfThenElseMessageContext ctx) {
        IONode conditionNode = visitExpression(ctx.condition);
        IONode thenPartNode = visitExpression(ctx.thenPart);
        IONode elsePartNode = null;
        if (ctx.elsePart != null) {
            elsePartNode = visitExpression(ctx.elsePart);
        }
        IONode result = factory.createIf(ctx.IF().getSymbol(), conditionNode, thenPartNode, elsePartNode);
        assert result != null;
        return result;
    }

    @Override
    public IONode visitWhileMessage(WhileMessageContext ctx) {
        factory.startLoop();
        IONode conditionNode = visitExpression(ctx.condition);
        IONode bodyNode = visitExpression(ctx.body);
        IONode result = factory.createWhile(ctx.WHILE().getSymbol(), conditionNode, bodyNode);
        return factory.createLoopExpression(result, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IONode visitForMessage(ForMessageContext ctx) {
        // throw new NotImplementedException();
        factory.startLoop();
        IONode slotNameNode = visitIdentifier(ctx.identifier());
        IONode startValueNode = visitExpression(ctx.startPart);
        IONode endValueNode = visitExpression(ctx.endPart);
        IONode stepValueNode = null;
        if (ctx.stepPart != null) {
            stepValueNode = visitExpression(ctx.stepPart);
        }
        IONode bodyNode = visitExpression(ctx.body);
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IONode result = factory.createForSlot(slotNameNode, startValueNode, endValueNode, stepValueNode, bodyNode,
                startPos, length);
        assert result != null;
        return factory.createLoopExpression(result, startPos, length);
    }

    @Override
    public IONode visitListMessage(ListMessageContext ctx) {
        List<IONode> elementNodes = new ArrayList<>();
        for (final ExpressionContext expressionCtx : ctx.arguments().expression()) {
            IONode elementNode = visitExpression(expressionCtx);
            assert elementNode != null;
            elementNodes.add(elementNode);
        }
        return factory.createListLiteral(elementNodes, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IONode visitTryMessage(TryMessageContext ctx) {
        IONode bodyNode = visitExpression(ctx.expression());
        assert bodyNode != null;
        return factory.createTry(bodyNode, ctx.start.getStartIndex(),
                ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1);
    }

    @Override
    public IONode visitBlockMessage(BlockMessageContext ctx) {
        final int blockStartPos;
        if (ctx.expression() == null) {
            blockStartPos = ctx.CLOSE().getSymbol().getStartIndex();
        } else {
            blockStartPos = ctx.expression().start.getStartIndex();
        }
        factory.enterNewScope(blockStartPos);
        factory.setupLocals();
        if (ctx.parameterList() != null) {
            for (final IdentifierContext identifierCtx : ctx.parameterList().identifier()) {
                factory.addFormalParameter(identifierCtx.start);
            }
        }
        int startPos = ctx.start.getStartIndex();
        int length = ctx.stop.getStopIndex() - ctx.start.getStartIndex() + 1;
        IONode bodyNode = null;
        if (ctx.expression() != null) {
            bodyNode = visitExpression(ctx.expression());
        } else {
            bodyNode = visitEmptyExpression(blockStartPos, length);
        }
        final IONode result;
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
    public IONode visitIdentifier(IdentifierContext ctx) {
        return factory.createStringLiteral(ctx.start, false);
    }

    @Override
    public IONode visitLiteral(final LiteralContext ctx) {
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
    public IONode visitNumber(NumberContext ctx) {
        if (ctx.decimal() != null) {
            return visitDecimal(ctx.decimal());
        }
        throw new NotImplementedException();
    }

    @Override
    public IONode visitDecimal(DecimalContext ctx) {
        if (ctx.INTEGER() != null) {
            return factory.createNumericLiteral(ctx.INTEGER().getSymbol());
        }
        throw new NotImplementedException();
    }

    @Override
    public IONode visitPseudoVariable(PseudoVariableContext ctx) {
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
