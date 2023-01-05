package jua.compiler;

import jua.compiler.Tokens.Token;
import jua.compiler.Tokens.TokenType;
import jua.compiler.Tree.*;
import jua.util.List;

import java.util.stream.Collectors;

import static jua.compiler.Tokens.TokenType.*;
import static jua.compiler.Types.*;

public final class JuaParser implements Parser {

    // todo: Рефакторинг и оптимизация.

    private static class ParseNodeExit extends Error {

        final int pos;

        final String msg;

        ParseNodeExit(int pos, String msg) {
            this.pos = pos;
            this.msg = msg;
        }
    }

    private final Source source;

    private final Lexer tokenizer;

    Token token;

    public JuaParser(Source source) {
        this.source = source;
        tokenizer = new Tokenizer(source);
    }

    @Override
    public CompilationUnit parseCompilationUnit() {
        List<Statement> stats = new List<>();
        List<FuncDef> funcDefs = new List<>();
        List<ConstDef> constDefs = new List<>();
        nextToken();
        while (!acceptToken(EOF)) {
            try {
                int pos = token.pos;
                if (acceptToken(FN)) {
                    funcDefs.add(parseFunction(pos));
                    continue;
                }
                if (acceptToken(CONST)) {
                    constDefs.add(parseConst(pos));
                    continue;
                }
                stats.add(parseStatement());
            } catch (ParseNodeExit e) {
                source.getLog().error(e.pos, e.msg);
            }
        }
        return new CompilationUnit(0, source, stats, funcDefs, constDefs);
    }

    @Override
    public Statement parseStatement() {
        int position = token.pos;

        switch (token.type) {
            case BREAK: {
                nextToken();
                return parseBreak(position);
            }
            case CASE: {
                nextToken();
                pError(position, "'case' is not allowed outside of switch.");
            }
            case CONST: {
                nextToken();
                pError(position, "constant declaration is not allowed here");
            }
            case CONTINUE: {
                nextToken();
                return parseContinue(position);
            }
            case DEFAULT: {
                nextToken();
                pError(position, "'default' is not allowed outside of switch.");
            }
            case DO: {
                nextToken();
                return parseDo(position);
            }
            case ELSE: {
                nextToken();
                pError(position, "'else' is not allowed without if-statement.");
            }
            case EOF: {
                nextToken();
                pError(position, "missing expected statement.");
            }
            case FALLTHROUGH: {
                nextToken();
                return parseFallthrough(position);
            }
            case FN: {
                nextToken();
                pError(position, "function declaration is not allowed here");
            }
            case FOR: {
                nextToken();
                return parseFor(position);
            }
            case IF: {
                nextToken();
                return parseIf(position);
            }
            case LBRACE: {
                nextToken();
                return parseBlock(position);
            }
            case RETURN: {
                nextToken();
                return parseReturn(position);
            }
            case SEMI: {
                nextToken();
                return new Block(position, new List<>());
            }
            case SWITCH: {
                nextToken();
                return parseSwitch(position);
            }
            case WHILE: {
                nextToken();
                return new WhileLoop(position, parseExpression(), parseStatement());
            }
            default: return parseUnusedExpression();
        }
    }

    private Statement parseBreak(int position) {
        expectToken(SEMI);
        return new Break(position);
    }

    private ConstDef parseConst(int position) {
        List<ConstDef.Definition> definitions = new List<>();

        do {
            try {
                Token name = token;
                expectToken(IDENTIFIER);
                expectToken(EQ);
                definitions.add(new ConstDef.Definition(name.toName(), parseExpression()));
            } catch (ParseNodeExit e) {
                source.getLog().error(e.pos, e.msg);
            }
        } while (acceptToken(COMMA));

        expectToken(SEMI);
        return new ConstDef(position, definitions);
    }

    private Statement parseContinue(int position) {
        expectToken(SEMI);
        return new Continue(position);
    }

    private Statement parseDo(int position) {
        Statement body = parseStatement();
        expectToken(WHILE);
        Expression cond = parseExpression();
        expectToken(SEMI);
        return new DoLoop(position, body, cond);
    }

    private Statement parseFallthrough(int position) {
        expectToken(SEMI);
        return new Fallthrough(position);
    }

    @Override
    public FuncDef parseFunctionDeclare() {
        int pos = token.pos;
        expectToken(FN);
        return parseFunction(pos);
    }

    private FuncDef parseFunction(int pos) {
        Name funcName = token.toName();
        expectToken(IDENTIFIER);
        expectToken(LPAREN);
        List<FuncDef.Parameter> params = new List<>();
        boolean comma = false, optionalState = false;

        while (!acceptToken(RPAREN)) {
            if (acceptToken(EOF) || comma && !acceptToken(COMMA)) {
                expectToken(RPAREN);
            }
            Token name0 = token;
            expectToken(IDENTIFIER);
            Name name1 = name0.toName();
            Expression optional = null;

            if (acceptToken(EQ)) {
                optional = parseExpression();
                optionalState = true;
            } else if (optionalState) {
                pError(name0.pos, "here must be a optional argument.");
            }
            params.add(new FuncDef.Parameter(name1, optional));
            comma = !acceptToken(COMMA);
        }
        Statement body = parseBody();
        return new FuncDef(pos, funcName, params, body);
    }

    private Statement parseBody() {
        int pos = token.pos;
        if (acceptToken(LBRACE)) return parseBlock(pos);
        if (acceptToken(EQ)) {
            Expression expr = parseExpression();
            expectToken(SEMI);
            return new Discarded(expr.pos, expr);
        }
        pError(pos, "Illegal function body");
        return null;
    }

    private Statement parseFor(int position) {
        boolean parens = acceptToken(LPAREN);
        List<Expression> init = null;

        if (!acceptToken(SEMI)) {
            init = parseExpressions();
            expectToken(SEMI);
        }
        Expression cond = null;

        if (!acceptToken(SEMI)) {
            cond = parseExpression();
            expectToken(SEMI);
        }
        List<Expression> step = null;

        if (parens) {
            if (!acceptToken(RPAREN)) {
                step = parseExpressions();
                expectToken(RPAREN);
            }
        } else {
            step = parseExpressions();
        }
        return new ForLoop(position, init, cond, step, parseStatement());
    }

    private Statement parseIf(int position) {
        Expression cond = parseExpression();
        Statement body = parseStatement();

        if (!acceptToken(ELSE)) {
            return new If(position, cond, body, null);
        }
        return new If(position, cond, body, parseStatement());
    }

    private Statement parseBlock(int position) {
        List<Statement> statements = new List<>();

        while (!acceptToken(RBRACE)) {
            if (acceptToken(EOF)) {
                expectToken(RBRACE);
            }
            try {
                statements.add(parseStatement());
            } catch (ParseNodeExit e) {
                source.getLog().error(e.pos, e.msg);
            }
        }
        return new Block(position, statements);
    }

    private Statement parseReturn(int position) {
        if (acceptToken(SEMI)) {
            return new Return(position, null);
        }
        Expression expr = null;
        try {
            expr = parseExpression();
        } catch (ParseNodeExit e) {
            source.getLog().error(e.pos, e.msg);
        }
        expectToken(SEMI);
        return new Return(position, expr);
    }

    private Statement parseSwitch(int position) {
        Expression selector = parseExpression();
        List<Case> cases = new List<>();
        expectToken(LBRACE);

        while (!acceptToken(RBRACE)) {
            int position1 = token.pos;

            if (acceptToken(CASE)) {
                try {
                    cases.add(parseCase(position1, false));
                } catch (ParseNodeExit e) {
                    source.getLog().error(e.pos, e.msg);
                }
            } else if (acceptToken(DEFAULT)) {
                try {
                    cases.add(parseCase(position1, true));
                } catch (ParseNodeExit e) {
                    source.getLog().error(e.pos, e.msg);
                }
            } else if (acceptToken(EOF)) {
                expectToken(RBRACE);
            } else {
                unexpected(token, List.of(CASE, DEFAULT, RBRACE));
            }
        }
        return new Switch(position, selector, cases);
    }

    private Case parseCase(int position, boolean isDefault) {
        List<Expression> expressions = null;

        if (!isDefault) {
            expressions = parseExpressions();
        }
        expectToken(COL);
        Statement body = parseStatement();
        return new Case(position, expressions, body);
    }

    private Statement parseUnusedExpression() {
        int position = token.pos;
        Expression expr = parseExpression();
        expectToken(SEMI);
        return new Discarded(position, expr);
    }

    private List<Expression> parseExpressions() {
        List<Expression> expressions = new List<>();

        do {
            expressions.add(parseExpression());
        } while (acceptToken(COMMA));

        return expressions;
    }

    @Override
    public Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseNullCoalesce();
        int position = token.pos;

        switch (token.type) {
            case AMPEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_BIT_AND, expr, parseAssignment());
            }
            case BAREQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_BIT_OR, expr, parseAssignment());
            }
            case CARETEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.BIT_XOR, expr, parseAssignment());
            }
            case EQ: {
                nextToken();
                return new Assign(position, expr, parseAssignment());
            }
            case GTGTEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_SL, expr, parseAssignment());
            }
            case LTLTEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_SR, expr, parseAssignment());
            }
            case MINUSEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_SUB, expr, parseAssignment());
            }
            case PERCENTEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_REM, expr, parseAssignment());
            }
            case PLUSEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_ADD, expr, parseAssignment());
            }
            case QUESQUESEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_NULLCOALSC, expr, parseAssignment());
            }
            case SLASHEQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_DIV, expr, parseAssignment());
            }
            case STAREQ: {
                nextToken();
                return new CompoundAssign(position, Tag.ASG_MUL, expr, parseAssignment());
            }
            default: return expr;
        }
    }

    private Expression parseNullCoalesce() {
        Expression expr = parseTernary();

        while (true) {
            int position = token.pos;

            if (acceptToken(QUESQUES)) {
                expr = new BinaryOp(position, Tag.NULLCOALSC, expr, parseTernary());
            } else {
                return expr;
            }
        }
    }

    private Expression parseTernary() {
        Expression expr = parseOr();

        while (true) {
            int position = token.pos;

            if (acceptToken(QUES)) {
                expr = parseTernary0(position, expr);
            } else {
                return expr;
            }
        }
    }

    private Expression parseTernary0(int position, Expression cond) {
        Expression right = parseExpression();
        expectToken(COL);
        return new TernaryOp(position, cond, right, parseExpression());
    }

    private Expression parseOr() {
        Expression expr = parseAnd();
        int position = token.pos;

        while (acceptToken(BARBAR)) {
            expr = new BinaryOp(position, Tag.OR, expr, parseAnd());
            position = token.pos;
        }
        return expr;
    }

    private Expression parseAnd() {
        Expression expr = parseBitOr();

        while (true) {
            int position = token.pos;

            if (acceptToken(AMPAMP)) {
                expr = new BinaryOp(position, Tag.AND, expr, parseEquality());
            } else {
                return expr;
            }
        }
    }


    private Expression parseBitOr() {
        Expression expr = parseBitXor();

        while (true) {
            int position = token.pos;

            if (acceptToken(BAR)) {
                expr = new BinaryOp(position, Tag.BIT_OR, expr, parseBitXor());
            } else {
                return expr;
            }
        }
    }

    private Expression parseBitXor() {
        Expression expr = parseBitAnd();

        while (true) {
            int position = token.pos;

            if (acceptToken(CARET)) {
                expr = new BinaryOp(position, Tag.BIT_XOR, expr, parseBitAnd());
            } else {
                return expr;
            }
        }
    }

    private Expression parseBitAnd() {
        Expression expr = parseEquality();

        while (true) {
            int position = token.pos;

            if (acceptToken(AMP)) {
                expr = new BinaryOp(position, Tag.BIT_AND, expr, parseEquality());
            } else {
                return expr;
            }
        }
    }

    private Expression parseEquality() {
        Expression expr = parseConditional();

        while (true) {
            int position = token.pos;

            if (acceptToken(EQEQ)) {
                expr = new BinaryOp(position, Tag.EQ, expr, parseConditional());
            } else if (acceptToken(BANGEQ)) {
                expr = new BinaryOp(position, Tag.NE, expr, parseConditional());
            } else {
                return expr;
            }
        }
    }

    private Expression parseConditional() {
        Expression expr = parseShift();

        while (true) {
            int position = token.pos;

            if (acceptToken(GT)) {
                expr = new BinaryOp(position, Tag.GT, expr, parseShift());
            } else if (acceptToken(GTEQ)) {
                expr = new BinaryOp(position, Tag.GE, expr, parseShift());
            } else if (acceptToken(LT)) {
                expr = new BinaryOp(position, Tag.LT, expr, parseShift());
            } else if (acceptToken(LTEQ)) {
                expr = new BinaryOp(position, Tag.LE, expr, parseShift());
            } else {
                return expr;
            }
        }
    }

    private Expression parseShift() {
        Expression expr = parseAdditive();

        while (true) {
            int position = token.pos;

            if (acceptToken(GTGT)) {
                expr = new BinaryOp(position, Tag.SR, expr, parseAdditive());
            } else if (acceptToken(LTLT)) {
                expr = new BinaryOp(position, Tag.SL, expr, parseAdditive());
            } else {
                return expr;
            }
        }
    }

    private Expression parseAdditive() {
        Expression expr = parseMultiplicative();

        while (true) {
            int position = token.pos;

            if (acceptToken(MINUS)) {
                expr = new BinaryOp(position, Tag.SUB, expr, parseMultiplicative());
            } else if (acceptToken(PLUS)) {
                expr = new BinaryOp(position, Tag.ADD, expr, parseMultiplicative());
            } else {
                return expr;
            }
        }
    }

    private Expression parseMultiplicative() {
        Expression expr = parseUnary();

        while (true) {
            int position = token.pos;

            if (acceptToken(PERCENT)) {
                expr = new BinaryOp(position, Tag.REM, expr, parseUnary());
            } else if (acceptToken(SLASH)) {
                expr = new BinaryOp(position, Tag.DIV, expr, parseUnary());
            } else if (acceptToken(STAR)) {
                expr = new BinaryOp(position, Tag.MUL, expr, parseUnary());
            } else {
                return expr;
            }
        }
    }

    private Expression parseUnary() {
        int position = token.pos;

        if (acceptToken(BANG)) {
            return new UnaryOp(position, Tag.NOT, parseUnary());
        }
        if (acceptToken(MINUS)) {
            return new UnaryOp(position, Tag.NEG, parseUnary());
        }
        if (acceptToken(MINUSMINUS)) {
            return new UnaryOp(position, Tag.PREDEC, parseUnary());
        }
        if (acceptToken(PLUS)) {
            return new UnaryOp(position, Tag.POS, parseUnary());
        }
        if (acceptToken(PLUSPLUS)) {
            return new UnaryOp(position, Tag.PREINC, parseUnary());
        }
        if (acceptToken(TILDE)) {
            return new UnaryOp(position, Tag.BIT_INV, parseUnary());
        }
        return parsePost();
    }

    private Expression parsePost() {
        Expression expr = parseCall();

        while (true) {
            int position = token.pos;

            if (acceptToken(MINUSMINUS)) {
                expr = new UnaryOp(position, Tag.POSTDEC, expr);
            } else if (acceptToken(PLUSPLUS)) {
                expr = new UnaryOp(position, Tag.POSTINC, expr);
            } else {
                return expr;
            }
        }
    }

    private Expression parseCall() {
        int pos = token.pos;
        Expression expr = parseAccess();

        if (acceptToken(LPAREN)) {
            List<Invocation.Argument> args = parseInvocationArgs();
            expectToken(RPAREN);
            return new Invocation(pos, expr, args);
        }

        return expr;
    }

    private Expression parseAccess() {
//        Expression expr = parsePrimary();
//        int position = currentToken.position;
//        int i = match(DOT) ? 1 : match(LBRACKET) ? 0 : -1;
//        return (i >= 0 ? parseArrayAccess(position, expr, i) : expr);

        Expression expression = parsePrimary();

        while (true) {
            int position = token.pos;

            if (acceptToken(DOT)) {
                Token token = this.token;
                expectToken(IDENTIFIER);
                expression = new MemberAccess(position, expression,
                        token.toName());
            } else if (acceptToken(LBRACKET)) {
                expression = new ArrayAccess(position, expression, parseExpression());
                expectToken(RBRACKET);
            } else {
                break;
            }
        }
        return expression;
    }

//    private Expression parseArrayAccess(int position, Expression expr, int dot)
//            {
//        List<Expression> keys = new List<>();
//
//        while (true) {
//            if ((dot == 1) || match(DOT)) {
//                Token key = currentToken;
//                expect(IDENTIFIER);
//                keys.add(new StringExpression(currentToken.position, key.value()));
//                dot = 2;
//            } else if ((dot == 0) || match(LBRACKET)) {
//                keys.add(parseExpression());
//                expect(RBRACKET);
//                dot = 2;
//            } else {
//                return new ArrayAccess(position, expr, keys);
//            }
//        }
//    }

    private Expression parsePrimary() {
        Token token = this.token;
        nextToken();

        switch (token.type) {
            case EOF: {
                pError(token.pos, "missing expected expression.");
            }
            case FALSE: {
                return new Literal(token.pos, ofBoolean(false));
            }
            case FLOATLITERAL: {
                return parseFloat(token);
            }
            case IDENTIFIER: {
                return parseIdentifier(token);
            }
            case INTLITERAL: {
                return parseInt(token);
            }
            case LBRACE: {
                return parseArray(token.pos, RBRACE);
            }
            case LBRACKET: {
                return parseArray(token.pos, RBRACKET);
            }
            case LPAREN: {
                return parseParens(token.pos);
            }
            case NULL: {
                return new Literal(token.pos, nullType());
            }
            case STRINGLITERAL: {
                return new Literal(token.pos, new StringType(token.value()));
            }
            case TRUE: {
                return new Literal(token.pos, ofBoolean(true));
            }
            default:
                unexpected(token, List.of(
                        FALSE, FLOATLITERAL, IDENTIFIER,
                        INTLITERAL, LBRACE, LBRACKET,
                        LPAREN, NULL, STRINGLITERAL, TRUE
                ));
                return null; // UNREACHABLE
        }
    }

    private Expression parseFloat(Token token) {
        double d = Double.parseDouble(token.value());
        if (Double.isInfinite(d)) {
            pError(token.pos, "number too large.");
        }
        // todo: Может проверку стоит убрать?
        if ((d == 0.0) && !token.value().matches("\\.?0\\.?\\d*(?:[Ee][+-]\\d+)?$")) {
            pError(token.pos, "number too small.");
        }
        return new Literal(token.pos, new DoubleType(d));
    }

    private Expression parseIdentifier(Token token) {
        if (acceptToken(LPAREN)) {
            return parseInvocation(token);
        }
        return new Var(token.pos, token.toName());
    }

    private List<Invocation.Argument> parseInvocationArgs() {
        List<Invocation.Argument> args = new List<>();
        boolean comma = false;

        while (!matchesToken(RPAREN)) {
            if (acceptToken(EOF) || comma && !acceptToken(COMMA)) {
                expectToken(RPAREN);
            }
            Name name = null;
            Expression expr;
            if (matchesToken(IDENTIFIER)) {
                Token t = token;
                expr = parseExpression();
                if (expr.hasTag(Tag.VARIABLE) && acceptToken(COL)) {
                    name = t.toName();
                    expr = parseExpression();
                }
            } else {
                expr = parseExpression();
            }
            args.add(new Invocation.Argument(name, expr));
            comma = !acceptToken(COMMA);
        }

        return args;
    }

    private Expression parseInvocation(Token token) {
        List<Invocation.Argument> args = parseInvocationArgs();
        expectToken(RPAREN);
        return new Invocation(token.pos, new MemberAccess(token.pos, null, token.toName()), args);
    }

    private Expression parseInt(Token token) {
        try {
            return new Literal(token.pos, new LongType(Long.parseLong(token.value(), token.radix())));
        } catch (NumberFormatException e) {
            pError(token.pos, "number too large.");
            return null;
        }
    }

    private Expression parseArray(int position, TokenType enclosing) {
        List<ArrayLiteral.Entry> entries = new List<>();
        boolean comma = false;
        boolean isList = enclosing == RBRACKET; // []

        while (!acceptToken(enclosing)) {
            if (acceptToken(EOF) || comma && !acceptToken(COMMA)) {
                expectToken(enclosing);
            }
            try {
                int pos = token.pos;
                Expression key = null;
                Expression value = parseExpression();

                if (acceptToken(COL)) {
                    key = value;
                    value = parseExpression();
                    isList = false;
                }
                entries.add(new ArrayLiteral.Entry(pos, key, value));
            } catch (ParseNodeExit e) {
                source.getLog().error(e.pos, e.msg);
            }
            comma = !acceptToken(COMMA);
        }
        ArrayLiteral tree = new ArrayLiteral(position, entries);
        tree._isList = isList;
        return tree;
    }

    private Expression parseParens(int position) {
        Expression expr = parseExpression();
        expectToken(RPAREN);
        return new Parens(position, expr);
    }

    private void nextToken() {
        token = tokenizer.nextToken();
    }

    private boolean matchesToken(TokenType type) {
        return token.type == type;
    }
    
    private boolean acceptToken(TokenType type) {
        if (matchesToken(type)) {
            nextToken();
            return true;
        }
        return false;
    }

    private void expectToken(TokenType type) {
        try {
            if (matchesToken(type)) return;
            // Compile error: X expected, Y found.
            // Compile error: X expected.
            // Compile error: unexpected Y.
            String expected = type2string(type);
            String found = token2string(token);

            if (expected != null && found != null) {
                pError(token.pos, expected + " expected, " + found + " found.");
                return;
            }

            if (expected != null) {
                pError(token.pos, expected + " expected.");
                return;
            }

            if (found != null) {
                pError(token.pos, "unexpected " + found + ".");
                return;
            }

            pError(token.pos, "invalid syntax.");
        } finally {
            nextToken();
        }
    }

    static String token2string(Token token) {
        if (token.type == EOF) return "end of file";
        return type2string(token.type);
    }

    static String type2string(TokenType type) {
        switch (type) {
            case IDENTIFIER: return "name";
            case STRINGLITERAL: return "string";
            case INTLITERAL: return "integer number";
            case FLOATLITERAL: return "floating-point number";
            default: return type.value == null ? null : '\'' + type.value + '\'';
        }
    }

    private void unexpected(Token token) {
        pError(token.pos, "unexpected " + token2string(token) + '.');
    }

    private void unexpected(Token foundToken, List<TokenType> expectedTypes) {
        pError(token.pos, "unexpected " +
                token2string(foundToken) +
                ", expected instead: " +
                expectedTypes.stream().map(JuaParser::type2string).collect(Collectors.joining(", ")));
    }

    private void pError(int position, String message) {
        // При возникновении ошибок, в AST попадают нулевые значения.
        // Это порождает множество потенциальных ошибок, в том числе NPE
        // для последующих стадий компиляции.
        // Решение прерывать парсинг узла с ошибкой
        // позволяет избежать нулевых значений в дереве, но
        // портит лексическую последовательность и, впоследствии,
        // семантическую последовательность ошибок. Качественных
        // решений последней проблемы пока что не вижу.
        throw new ParseNodeExit(position, message);
    }
}
