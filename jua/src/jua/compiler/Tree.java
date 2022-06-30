package jua.compiler;

import jua.util.LineMap;

import java.net.URL;
import java.util.List;
import java.util.Map;

public abstract class Tree {

    public enum Tag {
        ROOT,
        FUNCDECL,
        CONSTDECL,
        BLOCK,
        IFELSE,
        WHILELOOP,
        DOLOOP,
        FORLOOP,
        FOREACHLOOP,
        SWITCH,
        CASE,
        BREAK,
        CONTINUE,
        FALLTHROUGH,
        RETURN,
        EMPTY,
        DISCARDED,
        ADD,
        SUB,
        MUL,
        DIV,
        REM,
        SL,
        SR,
        BITAND,
        BITOR,
        BITXOR,
        EQ,
        NEQ,
        GT,
        GE,
        LT,
        LE,
        LOGOR,
        LOGAND,
        NULLCOALESCE,
        ASG,
        ASG_ADD,
        ASG_SUB,
        ASG_MUL,
        ASG_DIV,
        ASG_REM,
        ASG_SL,
        ASG_SR,
        ASG_BITAND,
        ASG_BITOR,
        ASG_BITXOR,
        ASG_NULLCOALESCE,
        POS,
        NEG,
        LOGCMPL,
        BITCMPL,
        PRE_INC,
        POST_INC,
        PRE_DEC,
        POST_DEC,
        ARRAY_ACCESS,
        CLONE,
        TERNARY,
        LITERAL,
        ARRAY_LITERAL,
        VARIABLE,
        FUNC_CALL,
        PARENS,
        ERROR,

        // временные теги
        @Deprecated
        PRINT,
        @Deprecated
        PRINTLN
    }

    public int pos;

    public abstract Tag getTag();

    protected Tree(int pos) {
        this.pos = pos;
    }

    public final boolean hasTag(Tag t) { return getTag() == t; }

    public abstract void accept(Visitor visitor);

    public static class CompilationUnit extends Tree {

        public URL location;

        public LineMap lineMap;

        public List<? extends Tree> trees;

        public CompilationUnit(int pos, URL location, LineMap lineMap, List<? extends Tree> trees) {
            super(pos);
            this.location = location;
            this.lineMap = lineMap;
            this.trees = trees;
        }

        @Override
        public Tag getTag() {
            return Tag.ROOT;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitCompilationUnit(this);
        }
    }

    public static class AddExpression extends BinaryOp {

        public AddExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.ADD, lhs, rhs);
        }
    }

    public static class AndExpression extends ConditionalExpression {

        public AndExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.LOGAND, lhs, rhs);
        }

        @Override
        public boolean isCondition() {
            return true;
        }
    }

    public static class ArrayAccess extends Expression {

        public Expression array;
        public Expression key;

        public ArrayAccess(int pos, Expression hs, Expression key) {
            super(pos);
            this.array = hs;
            this.key = key;
        }

        @Override
        public Tag getTag() {
            return Tag.ARRAY_ACCESS;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitArrayAccess(this);
        }
    }

    public static class ArrayLiteral extends Expression {

        // todo: Заменить это на List со своей структурой
        public Map<Expression, Expression> map;

        public ArrayLiteral(int pos, Map<Expression, Expression> map) {
            super(pos);
            this.map = map;
        }

        @Override
        public boolean isAccessible() {
            return true;
        }

        @Override
        public boolean isCloneable() {
            return true;
        }

        @Override
        public Tag getTag() {
            return Tag.ARRAY_LITERAL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitArray(this);
        }
    }

    public static class AssignAddExpression extends AssignOp {

        public AssignAddExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_ADD;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignAdd(this);
        }
    }

    public static class AssignBitAndExpression extends AssignOp {

        public AssignBitAndExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_BITAND;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignBitAnd(this);
        }
    }

    public static class AssignBitOrExpression extends AssignOp {

        public AssignBitOrExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_BITOR;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignBitOr(this);
        }
    }

    public static class AssignBitXorExpression extends AssignOp {

        public AssignBitXorExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_BITXOR;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignBitXor(this);
        }
    }

    public static class AssignDivideExpression extends AssignOp {

        public AssignDivideExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_DIV;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignDivide(this);
        }
    }

    public static class AssignExpression extends AssignOp {

        public AssignExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignOp(this);
        }
    }

    public abstract static class AssignOp extends Expression {

        public Expression var;

        public Expression expr;

        protected AssignOp(int pos, Expression var, Expression expr) {
            super(pos);
            this.var = var;
            this.expr = expr;
        }

        @Override
        public boolean isAccessible() {
            return expr.isAccessible();
        }

        @Override
        public boolean isCloneable() {
            return expr.isCloneable();
        }

        @Override
        public boolean isNullable() {
            return expr.isNullable();
        }
    }

    public static class AssignMultiplyExpression extends AssignOp {

        public AssignMultiplyExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_MUL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignMultiply(this);
        }
    }

    public static class AssignNullCoalesceExpression extends AssignOp {

        public AssignNullCoalesceExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_NULLCOALESCE;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignNullCoalesce(this);
        }
    }

    public static class AssignRemainderExpression extends AssignOp {

        public AssignRemainderExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_REM;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignRemainder(this);
        }
    }

    public static class AssignShiftLeftExpression extends AssignOp {

        public AssignShiftLeftExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_SL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignLeftShift(this);
        }
    }

    public static class AssignShiftRightExpression extends AssignOp {

        public AssignShiftRightExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_SR;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignRightShift(this);
        }
    }

    public static class AssignSubtractExpression extends AssignOp {

        public AssignSubtractExpression(int pos, Expression var, Expression expr) {
            super(pos, var, expr);
        }

        @Override
        public Tag getTag() {
            return Tag.ASG_SUB;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitAssignSubtract(this);
        }
    }

    public static class BinaryOp extends Expression {

        public Tag tag;

        public Expression lhs;

        public Expression rhs;

        public BinaryOp(int pos, Tag tag, Expression lhs, Expression rhs) {
            super(pos);
            this.tag = tag;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public Tag getTag() {
            return tag;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitBinaryOp(this);
        }
    }

    public static class BitAndExpression extends BinaryOp {

        public BitAndExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.BITAND, lhs, rhs);
        }
    }

    public static class BitNotExpression extends UnaryOp {

        public BitNotExpression(int pos, Expression hs) {
            super(pos, Tag.BITCMPL, hs);
        }
    }

    public static class BitOrExpression extends BinaryOp {

        public BitOrExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.BITOR, lhs, rhs);
        }
    }

    public static class BitXorExpression extends BinaryOp {

        public BitXorExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.BITXOR, lhs, rhs);
        }
    }

    public static class Block extends Statement {

        public List<Statement> statements;

        public Block(int pos, List<Statement> statements) {
            super(pos);
            this.statements = statements;
        }

        @Override
        public Tag getTag() {
            return Tag.BLOCK;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitBlock(this);
        }
    }

    public static class BooleanExpression extends Literal {

        public BooleanExpression(int pos, boolean value) {
            super(pos, value);
        }
    }

    public static class Break extends Statement {

        @Override
        public Tag getTag() {
            return Tag.BREAK;
        }

        public Break(int pos) {
            super(pos);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitBreak(this);
        }
    }

    public static class Case extends Statement {

        public List<Expression> expressions;

        public Statement body;

        public Case(int pos, List<Expression> expressions, Statement body) {
            super(pos);
            this.expressions = expressions;
            this.body = body;
        }

        @Override
        public Tag getTag() {
            return Tag.CASE;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitCase(this);
        }
    }

    // todo: remove
    public static class CloneExpression extends UnaryOp {

        public CloneExpression(int pos, Expression hs) {
            super(pos, Tag.CLONE, hs);
        }
    }

    public abstract static class ConditionalExpression extends BinaryOp {

        protected ConditionalExpression(int pos, Tag tag, Expression lhs, Expression rhs) {
            super(pos, tag, lhs, rhs);
        }

        @Override
        public boolean isCondition() {
            return true;
        }
    }

    public static class ConstantDecl extends Statement {

        public List<String> names;

        public List<Expression> expressions;

        public ConstantDecl(int pos, List<String> names, List<Expression> expressions) {
            super(pos);
            this.names = names;
            this.expressions = expressions;
        }

        @Override
        public Tag getTag() {
            return Tag.CONSTDECL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitConstantDeclare(this);
        }
    }

    public static class Continue extends Statement {

        @Override
        public Tag getTag() {
            return Tag.CONTINUE;
        }

        public Continue(int pos) {
            super(pos);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitContinue(this);
        }
    }

    public static class DivideExpression extends BinaryOp {

        public DivideExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.DIV, lhs, rhs);
        }

    }

    public static class DoLoop extends Statement {

        public Statement body;

        public Expression cond;

        public DoLoop(int pos, Statement body, Expression cond) {
            super(pos);
            this.body = body;
            this.cond = cond;
        }

        @Override
        public Tag getTag() {
            return Tag.DOLOOP;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitDoLoop(this);
        }
    }

    public static class EqualExpression extends ConditionalExpression {

        public EqualExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.EQ, lhs, rhs);
        }
    }

    public abstract static class Expression extends Statement {

        // todo: remove
        public static Expression empty() {
            return new Expression(0) {
                @Override
                public Tag getTag() {
                    return null;
                }

                @Override
                public boolean isEmpty() {
                    return true;
                }

                @Override
                public void accept(Visitor visitor) {}
            };
        }

        protected Expression(int pos) {
            super(pos);
        }

        // todo: Почти все эти методы лишние, часть из них нужно переместить в jua.compiler.TreeInfo

        public boolean isAccessible() {
            switch (getTag()) {
                case PARENS:
                case VARIABLE:
                case ARRAY_LITERAL:
                case ARRAY_ACCESS:
                case FUNC_CALL:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isAssignable() {
            switch (getTag()) {
                case PARENS:
                    return ((Parens) this).expr.isAssignable();
                case VARIABLE:
                case ARRAY_ACCESS:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isCloneable() {
            return true;
        }

        public boolean isCondition() {
            return false;
        }

        public boolean isLiteral() {
            return false;
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isNullable() {
            return false;
        }

        public Expression child() {
            return this;
        }
    }

    public static class Fallthrough extends Statement {

        @Override
        public Tag getTag() {
            return Tag.FALLTHROUGH;
        }

        public Fallthrough(int pos) {
            super(pos);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitFallthrough(this);
        }
    }

    public static class FalseExpression extends BooleanExpression {

        public FalseExpression(int pos) {
            super(pos, false);
        }
    }

    public static class FloatExpression extends Literal {

        public FloatExpression(int pos, double value) {
            super(pos, value);
        }
    }

    public static class ForLoop extends Statement {

        public List<Expression> init;

        public Expression cond;

        public List<Expression> step;

        public Statement body;

        public ForLoop(int pos, List<Expression> init, Expression cond, List<Expression> step, Statement body) {
            super(pos);
            this.init = init;
            this.cond = cond;
            this.step = step;
            this.body = body;
        }

        @Override
        public Tag getTag() {
            return Tag.FORLOOP;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitFor(this);
        }
    }

    public static class Invocation extends Expression {

        public final String name;

        public List<Expression> args;

        public Invocation(int pos, String name, List<Expression> args) {
            super(pos);
            this.name = name;
            this.args = args;
        }

        @Override
        public boolean isAccessible() {
            return true;
        }

        @Override
        public boolean isCloneable() {
            return true;
        }

        @Override
        public boolean isNullable() {
            return true;
        }

        @Override
        public Tag getTag() {
            return Tag.FUNC_CALL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitInvocation(this);
        }
    }

    public static class FunctionDecl extends Statement {

        // todo: Заменить строки на свои структуры (механизм уже готов, его нужно только внедрить)

        public final String name;

        public final List<String> params;

        public List<Expression> defaults;

        public Statement body;

        public FunctionDecl(int pos,
                            String name,
                            List<String> params,
                            List<Expression> defaults,
                            Statement body) {
            super(pos);
            this.name = name;
            this.params = params;
            this.defaults = defaults;
            this.body = body;
        }

        @Override
        public Tag getTag() {
            return Tag.FUNCDECL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitFunctionDecl(this);
        }
    }

    public static class GreaterEqualExpression extends ConditionalExpression {

        public GreaterEqualExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.GE, lhs, rhs);
        }
    }

    public static class GreaterExpression extends ConditionalExpression {

        public GreaterExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.GT, lhs, rhs);
        }
    }

    public static class If extends Statement {

        public Expression cond;

        public Statement body;

        public Statement elseBody;

        public If(int pos, Expression cond, Statement body) {
            this(pos, cond, body, null);
        }

        public If(int pos, Expression cond, Statement body, Statement elseBody) {
            super(pos);
            this.cond = cond;
            this.body = body;
            this.elseBody = elseBody;
        }

        @Override
        public Tag getTag() {
            return Tag.IFELSE;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitIf(this);
        }
    }

    public abstract static class IncreaseExpression extends UnaryOp {

        protected IncreaseExpression(int pos, Tag tag, Expression hs) {
            super(pos, tag, hs);
        }
    }

    public static class Literal extends Expression {

        public Object value;

        public Literal(int pos, Object value) {
            super(pos);
            this.value = value;
        }

        public boolean isInteger() { return value instanceof Long || value instanceof Integer; }
        public boolean isFloatingPoint() { return value instanceof Double || value instanceof Float; }
        public boolean isNumber() { return value instanceof Number; }
        public boolean isBoolean() { return value instanceof Boolean; }
        public boolean isString() { return value instanceof String; }
        public boolean isNull() { return value == null; }
        public long longValue() { return ((Number) value).longValue(); }
        public double doubleValue() { return ((Number) value).doubleValue(); }
        public boolean booleanValue() { return (Boolean) value; }
        public String stringValue() { return String.valueOf(value); }

        @Override
        public boolean isLiteral() {
            return true;
        }

        @Override
        public Tag getTag() {
            return Tag.LITERAL;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitLiteral(this);
        }
    }

    public static class IntExpression extends Literal {

        public IntExpression(int pos, long value) {
            super(pos, value);
        }

        @Override
        public boolean isLiteral() {
            return true;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitInt(this);
        }
    }

    public static class LessEqualExpression extends ConditionalExpression {

        public LessEqualExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.LE, lhs, rhs);
        }
    }

    public static class LessExpression extends ConditionalExpression {

        public LessExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.LT, lhs, rhs);
        }
    }

    public static class MultiplyExpression extends BinaryOp {

        public MultiplyExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.MUL, lhs, rhs);
        }
    }

    public static class NegativeExpression extends UnaryOp {

        public NegativeExpression(int pos, Expression hs) {
            super(pos, Tag.NEG, hs);
        }
    }

    public static class NotEqualExpression extends ConditionalExpression {

        public NotEqualExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.NEQ, lhs, rhs);
        }
    }

    public static class NotExpression extends UnaryOp {

        public NotExpression(int pos, Expression hs) {
            super(pos, Tag.LOGCMPL ,hs);
        }
    }

    public static class NullCoalesceExpression extends BinaryOp {

        public NullCoalesceExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.NULLCOALESCE, lhs, rhs);
        }
    }

    public static class NullExpression extends Literal {

        public NullExpression(int pos) {
            super(pos, null);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitNull(this);
        }
    }

    public static class OrExpression extends ConditionalExpression {

        public OrExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.LOGOR, lhs, rhs);
        }
    }

    public static class Parens extends Expression {

        public Expression expr;

        public Parens(int pos, Expression expr) {
            super(pos);
            this.expr = expr;
        }

        @Override
        public Expression child() {
            return expr.child();
        }

        @Override
        public Tag getTag() {
            return Tag.PARENS;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitParens(this);
        }
    }

    @Deprecated
    public static class Position {

        public final String filename;

        public final int line;

        public final int offset;

        public Position(String filename, int line, int offset) {
            this.filename = filename;
            this.line = line;
            this.offset = offset;
        }
    }

    public static class PositiveExpression extends UnaryOp {

        public PositiveExpression(int pos, Expression hs) {
            super(pos, Tag.POS, hs);
        }
    }

    public static class PostDecrementExpression extends IncreaseExpression {

        public PostDecrementExpression(int pos, Expression hs) {
            super(pos, Tag.POST_DEC, hs);
        }
    }

    public static class PostIncrementExpression extends IncreaseExpression {

        public PostIncrementExpression(int pos, Expression hs) {
            super(pos, Tag.POST_INC, hs);
        }
    }

    public static class PreDecrementExpression extends IncreaseExpression {

        public PreDecrementExpression(int pos, Expression hs) {
            super(pos,Tag.PRE_DEC, hs);
        }
    }

    public static class PreIncrementExpression extends IncreaseExpression {

        public PreIncrementExpression(int pos, Expression hs) {
            super(pos, Tag.PRE_INC,hs);
        }
    }

    @Deprecated
    public static class PrintlnStatement extends Statement {

        public List<Expression> expressions;

        public PrintlnStatement(int pos, List<Expression> expressions) {
            super(pos);
            this.expressions = expressions;
        }

        @Override
        public Tag getTag() {
            return Tag.PRINTLN;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPrintln(this);
        }
    }

    @Deprecated
    // todo: Заменить это на вызов функций с соответствующими именами
    public static class PrintStatement extends Statement {

        public List<Expression> expressions;

        public PrintStatement(int pos, List<Expression> expressions) {
            // print и println с какой-то там версии больше не являются языковыми конструкциями.
            super(pos);
            this.expressions = expressions;
        }

        @Override
        public Tag getTag() {
            return Tag.PRINT;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPrint(this);
        }
    }

    public static class RemainderExpression extends BinaryOp {

        public RemainderExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.REM, lhs, rhs);
        }
    }

    public static class Return extends Statement {

        public Expression expr;

        @Override
        public Tag getTag() {
            return Tag.RETURN;
        }

        public Return(int pos) {
            this(pos, null);
        }

        public Return(int pos, Expression expr) {
            super(pos);
            this.expr = expr;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitReturn(this);
        }
    }

    public static class ShiftLeftExpression extends BinaryOp {

        public ShiftLeftExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.SL, lhs, rhs);
        }
    }

    public static class ShiftRightExpression extends BinaryOp {

        public ShiftRightExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.SR, lhs, rhs);
        }
    }

    public abstract static class Statement extends Tree implements Cloneable {

        public static final Statement EMPTY = null; // todo

        protected Statement(int pos) {
            super(pos);
        }

        public Statement copy(int pos) {//todo: remove
            try {
                Statement clone = (Statement) super.clone();
                clone.pos = pos;
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new InternalError(e);
            }
        }
    }

    public static class StringExpression extends Literal {

        public StringExpression(int pos, String value) {
            super(pos, value);
        }
    }

    public static class SubtractExpression extends BinaryOp {

        public SubtractExpression(int pos, Expression lhs, Expression rhs) {
            super(pos, Tag.SUB, lhs, rhs);
        }
    }

    public static class Switch extends Statement {

        public Expression selector;

        public List<Case> cases;

        public Switch(int pos, Expression selector, List<Case> cases) {
            super(pos);
            this.selector = selector;
            this.cases = cases;
        }

        @Override
        public Tag getTag() {
            return Tag.SWITCH;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitSwitch(this);
        }
    }

    public static class TernaryOp extends Expression {

        public Expression cond;

        public Expression lhs;

        public Expression rhs;

        public TernaryOp(int pos, Expression cond, Expression lhs, Expression rhs) {
            super(pos);
            this.cond = cond;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public boolean isAccessible() {
            return lhs.isAccessible() && rhs.isAccessible();
        }

        @Override
        public boolean isCloneable() {
            return lhs.isCloneable() && rhs.isCloneable();
        }

        @Override
        public boolean isNullable() {
            return lhs.isNullable() || rhs.isNullable();
        }

        @Override
        public Tag getTag() {
            return Tag.TERNARY;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitTernaryOp(this);
        }
    }

    public static class TrueExpression extends BooleanExpression {

        public TrueExpression(int pos) {
            super(pos, true);
        }
    }

    public static class UnaryOp extends Expression {

        public Tag tag;
        public Expression hs;

        protected UnaryOp(int pos, Tag tag, Expression hs) {
            super(pos);
            this.tag = tag;
            this.hs = hs;
        }

        @Override
        public Tag getTag() {
            return tag;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitUnaryOp(this);
        }
    }

    public static class Discarded extends Expression {

        public Expression expression;

        public Discarded(int pos, Expression expression) {
            super(pos);
            this.expression = expression;
        }

        @Override
        public Tag getTag() {
            return Tag.DISCARDED;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitDiscarded(this);
        }
    }

    public static class Var extends Expression {

        // todo: Заменить это на свою структуру (механизм уже готов, его нужно только внедрить)
        public final String name;

        public Var(int pos, String name) {
            super(pos);
            this.name = name;
        }

        @Override
        public boolean isAccessible() {
            return true;
        }

        @Override
        public boolean isAssignable() {
            return true;
        }

        @Override
        public boolean isCloneable() {
            return true;
        }

        @Override
        public boolean isNullable() {
            return true;
        }

        @Override
        public Tag getTag() {
            return Tag.VARIABLE;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitVariable(this);
        }
    }

    public static class WhileLoop extends Statement {

        public Expression cond;

        public Statement body;

        public WhileLoop(int pos, Expression cond, Statement body) {
            super(pos);
            this.cond = cond;
            this.body = body;
        }

        @Override
        public Tag getTag() {
            return Tag.WHILELOOP;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitWhileLoop(this);
        }
    }

    public static interface Visitor {
        void visitCompilationUnit(CompilationUnit tree);

        void visitAdd(AddExpression expression);

        void visitAnd(AndExpression expression);

        void visitArrayAccess(ArrayAccess expression);

        void visitArray(ArrayLiteral expression);

        void visitAssignAdd(AssignAddExpression expression);

        void visitAssignBitAnd(AssignBitAndExpression expression);

        void visitAssignBitOr(AssignBitOrExpression expression);

        void visitAssignBitXor(AssignBitXorExpression expression);

        void visitAssignDivide(AssignDivideExpression expression);

        void visitAssignLeftShift(AssignShiftLeftExpression expression);

        void visitAssignOp(AssignExpression expression);

        void visitAssignMultiply(AssignMultiplyExpression expression);

        void visitAssignNullCoalesce(AssignNullCoalesceExpression expression);

        void visitAssignRemainder(AssignRemainderExpression expression);

        void visitAssignRightShift(AssignShiftRightExpression expression);

        void visitAssignSubtract(AssignSubtractExpression expression);

        void visitBitAnd(BitAndExpression expression);

        void visitBitNot(BitNotExpression expression);

        void visitBitOr(BitOrExpression expression);

        void visitBitXor(BitXorExpression expression);

        void visitBlock(Block statement);

        void visitBreak(Break statement);

        void visitCase(Case statement);

        void visitClone(CloneExpression expression);

        void visitConstantDeclare(ConstantDecl statement);

        void visitContinue(Continue statement);

        void visitDivide(DivideExpression expression);

        void visitDoLoop(DoLoop statement);

        void visitEqual(EqualExpression expression);

        void visitFallthrough(Fallthrough statement);

        void visitFalse(FalseExpression expression);

        void visitFloat(FloatExpression expression);

        void visitFor(ForLoop statement);

        void visitInvocation(Invocation expression);

        void visitFunctionDecl(FunctionDecl statement);

        void visitGreaterEqual(GreaterEqualExpression expression);

        void visitGreater(GreaterExpression expression);

        void visitIf(If statement);

        void visitInt(IntExpression expression);

        void visitLeftShift(ShiftLeftExpression expression);

        void visitLessEqual(LessEqualExpression expression);

        void visitLess(LessExpression expression);

        void visitMultiply(MultiplyExpression expression);

        void visitNegative(NegativeExpression expression);

        void visitNotEqual(NotEqualExpression expression);

        void visitNot(NotExpression expression);

        void visitNullCoalesce(NullCoalesceExpression expression);

        void visitNull(NullExpression expression);

        void visitOr(OrExpression expression);

        void visitParens(Parens expression);

        void visitPositive(PositiveExpression expression);

        void visitPostDecrement(PostDecrementExpression expression);

        void visitPostIncrement(PostIncrementExpression expression);

        void visitPreDecrement(PreDecrementExpression expression);

        void visitPreIncrement(PreIncrementExpression expression);

        @Deprecated
        void visitPrintln(PrintlnStatement statement);

        @Deprecated
        void visitPrint(PrintStatement statement);

        void visitRemainder(RemainderExpression expression);

        void visitReturn(Return statement);

        void visitRightShift(ShiftRightExpression expression);

        void visitString(StringExpression expression);

        void visitSubtract(SubtractExpression expression);

        void visitSwitch(Switch statement);

        void visitTernaryOp(TernaryOp expression);

        void visitTrue(TrueExpression expression);

        void visitVariable(Var expression);

        void visitWhileLoop(WhileLoop statement);

        void visitDiscarded(Discarded expression);

        void visitBinaryOp(BinaryOp tree);

        void visitUnaryOp(UnaryOp tree);

        void visitAssignOp(AssignOp tree);

        void visitLiteral(Literal tree);
    }
}
