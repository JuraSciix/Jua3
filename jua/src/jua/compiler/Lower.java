package jua.compiler;

import jua.compiler.Tree.*;

import java.util.*;
import java.util.stream.Collectors;

public class Lower implements Visitor {

    private final Map<String, Expression> literalConstants;

    private Tree result;

    /**
     * Остаточный результат от логических выражений. Пример: <pre>{@code
     * somecall() || true; // Это выражение заведомо true,
     *                     // но выкидывать вызов функции компилятор не имеет права.
     *                     // Вызов функции здесь - как раз остаточный результат,
     *                     // который будет помещен в список операторов родительского дерева (BlockStatement).}</pre>
     */
    // По идее это должно быть списком, но на практике сюда записывается только одно значение.
    private Expression formalExpression;

    @SuppressWarnings("unchecked")
    private <T extends Tree> T foldBody(Statement body) {
        if (body == null) return null;
        body.accept(this);
        if (formalExpression != null && body.getClass() != BlockStatement.class) {
            result = new BlockStatement(formalExpression.pos, new ArrayList<Statement>() {
                {
                    add(new DiscardedExpression(formalExpression.pos, formalExpression));
                    formalExpression = null;
                    add(body);
                }
            });
        }
        try {
            return (T) result;
        } finally {
            result = null;
        }
    }

    public Lower(CodeData codeData) {
        literalConstants = new HashMap<>();
        putFoldingNames(codeData.constants.keySet());
    }

    private void putFoldingNames(Set<String> names) {
        names.forEach(name -> literalConstants.put(name, null));
    }

    @Override
    public void visitAdd(AddExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof FloatExpression) && (rhs instanceof FloatExpression)) {
            ((FloatExpression) lhs).value += ((FloatExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof FloatExpression) && (rhs instanceof IntExpression)) {
            ((FloatExpression) lhs).value += ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof FloatExpression)) {
            long l = ((IntExpression) lhs).value;
            double r = ((FloatExpression) rhs).value;
            result = new FloatExpression(expression.pos, l + r);
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value += ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof StringExpression) && (rhs instanceof StringExpression)) {
            // .concat is not appropriate here
            ((StringExpression) lhs).value += (((StringExpression) rhs).value);
            result = lhs;
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitAnd(AndExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs);
        if (isFalse(lhs)) {
            setFalse(expression);
            return;
        }
        if (isTrue(lhs)) {
            result = getLowerExpression(expression.rhs);
            return;
        }
        Expression rhs = getLowerExpression(expression.rhs);
        if (isFalse(rhs)) {
            formalExpression = lhs;
            setFalse(expression);
            return;
        }
        if (isTrue(rhs)) {
            result = lhs;
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitArrayAccess(ArrayAccessExpression expression) {
        expression.hs.accept(this);
        expression.key = getLowerExpression(expression.key);
        result = expression;
    }

    @Override
    public void visitArray(ArrayExpression expression) {
        Map<Expression, Expression> map = new LinkedHashMap<>();
        expression.map.forEach((key, value) -> {
            if (key.isEmpty()) {
                map.put(key, getLowerExpression(value));
            } else {
                map.put(getLowerExpression(key), getLowerExpression(value));
            }
        });
        expression.map = map;
        result = expression;
    }

    @Override
    public void visitAssignAdd(AssignAddExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignBitAnd(AssignBitAndExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignBitOr(AssignBitOrExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignBitXor(AssignBitXorExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignDivide(AssignDivideExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignLeftShift(AssignShiftLeftExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssign(AssignExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignMultiply(AssignMultiplyExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignNullCoalesce(AssignNullCoalesceExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignRemainder(AssignRemainderExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignRightShift(AssignShiftRightExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitAssignSubtract(AssignSubtractExpression expression) {
        visitAssignment(expression);
    }

    @Override
    public void visitBitAnd(BitAndExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value &= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof BooleanExpression) && (rhs instanceof BooleanExpression)) {
            if ((lhs instanceof TrueExpression) && (rhs instanceof TrueExpression)) {
                result = lhs;
            } else {
                setFalse(expression);
            }
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitBitNot(BitNotExpression expression) {
        Expression hs = getLowerExpression(expression.hs).child();
        if (hs instanceof IntExpression) {
            ((IntExpression) hs).value = ~((IntExpression) hs).value;
            result = hs;
            return;
        }
        lowerUnary(expression, hs);
    }

    @Override
    public void visitBitOr(BitOrExpression expression) { expression.lhs.accept(this);
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value |= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof BooleanExpression) && (rhs instanceof BooleanExpression)) {
            if ((lhs instanceof TrueExpression) || (rhs instanceof TrueExpression)) {
                result = (lhs instanceof TrueExpression) ? lhs : rhs;
            } else {
                setFalse(expression);
            }
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitBitXor(BitXorExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value ^= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof BooleanExpression) && (rhs instanceof BooleanExpression)) {
            if (lhs.getClass() != rhs.getClass()) {
                setTrue(expression);
            } else {
                setFalse(expression);
            }
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitBlock(BlockStatement statement) {
        // special case
        ListIterator<Statement> iterator = statement.statements.listIterator();
        Expression prevResidual = formalExpression;
        formalExpression = null;
        while (iterator.hasNext()) {
            Statement lower = getLowerStatement(iterator.next());
            if (formalExpression != null) {
                iterator.remove();
                iterator.add(
                        // Остаточный результат заведомо является unused
                        new DiscardedExpression(formalExpression.pos, formalExpression));
                formalExpression = null;
                if (lower != null && !lower.isTag(Tag.EMPTY))
                    iterator.add(lower);
            } else {
                if (lower == null || lower.isTag(Tag.EMPTY))
                    iterator.remove();
                else
                    iterator.set(lower);
            }
        }
        result = statement;
        formalExpression = prevResidual;
    }

    @Override
    public void visitBreak(BreakStatement statement) {
        nothing(statement);
    }

    @Override
    public void visitCase(CaseStatement statement) {
        if (statement.expressions != null) // is not default case?
            statement.expressions = lowerList(statement.expressions);
        statement.body = getLowerStatement(statement.body);
        result = statement;
    }

    @Override
    public void visitClone(CloneExpression expression) {
        lowerUnary(expression, getLowerExpression(expression.hs).child());
    }

    @Override
    public void visitConstantDeclare(ConstantDeclareStatement statement) {
        int size = statement.names.size() & statement.expressions.size();
        for (int i = 0; i < size; i++) {
            String name = statement.names.get(i);
            if (statement.names.indexOf(name) != statement.names.lastIndexOf(name)) {
                throw new CompileError("duplicate name '" + name + "'.", statement.pos);
            }
            Expression expr = getLowerExpression(statement.expressions.get(i)).child();
            if (expr.isLiteral() ||
                    (expr instanceof NullExpression) ||
                    (expr instanceof BooleanExpression)) {
                literalConstants.put(name, expr);
            }
            statement.expressions.set(i, expr);
        }
        result = statement;
    }

    @Override
    public void visitContinue(ContinueStatement statement) {
        nothing(statement);
    }

    @Override
    public void visitDivide(DivideExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof FloatExpression) && (rhs instanceof FloatExpression)) {
            ((FloatExpression) lhs).value /= ((FloatExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof FloatExpression) && (rhs instanceof IntExpression)) {
            ((FloatExpression) lhs).value /= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof FloatExpression)) {
            long l = ((IntExpression) lhs).value;
            double r = ((FloatExpression) rhs).value;
            result = new FloatExpression(expression.pos, l / r);
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            long l = ((IntExpression) lhs).value;
            long r = ((IntExpression) rhs).value;
            if (r != 0L) {
                if (l % r == 0) {
                    ((IntExpression) lhs).value = (l / r);
                    result = lhs;
                } else {
                    result = new FloatExpression(expression.pos, (double) l / r);
                }
                return;
            }
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitDo(DoStatement tree) {
        tree.body = foldBody(tree.body);
        tree.cond = getLowerExpression(tree.cond);

        if (isTrue(tree.cond)) {
            tree.cond = null; // codegen will handle it.
        } else if (isFalse(tree.cond)) {
            result = tree.body;
            return;
        }
        result = tree;
    }

    @Override
    public void visitEqual(EqualExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        switch (compareLiterals(lhs, rhs)) {
            case 0:
                setTrue(expression);
                break;
            case 1:
            case -1:
            case 2:
                setFalse(expression);
                break;
            default:
                lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitFallthrough(FallthroughStatement statement) {
        nothing(statement);
    }

    @Override
    public void visitFalse(FalseExpression expression) {
        nothing(expression);
    }

    @Override
    public void visitFloat(FloatExpression expression) {
        nothing(expression);
    }

    @Override
    public void visitFor(ForStatement tree) {
        if (tree.init != null) tree.init = lowerList(tree.init);

        tree.cond = getLowerExpression(tree.cond);

        if (isFalse(tree.cond)) {
            // todo: Возможно, здесь стоит использовать CommaExpression
            result = new BlockStatement(tree.pos, tree.init.stream()
                    .map(expr -> (Statement) expr)
                    .collect(Collectors.toList()));
            return;
        }

        if (isTrue(tree.cond)) tree.cond = null; // codegen will handle it.

        if (tree.step != null) tree.step = lowerList(tree.step);

        tree.body = foldBody(tree.body);
        result = tree;
    }

    @Override
    public void visitFunctionCall(FunctionCallExpression expression) {
        expression.args = lowerList(expression.args);
        result = expression;
    }

    @Override
    public void visitFunctionDefine(FunctionDefineStatement statement) {
        statement.defaults = lowerList(statement.defaults);
        statement.body = foldBody(statement.body);
        result = statement;
    }

    @Override
    public void visitGreaterEqual(GreaterEqualExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        switch (compareLiterals(lhs, rhs)) {
            case 1:
            case 0:
                setTrue(expression);
                break;
            case -1:
            case 2:
                setFalse(expression);
                break;
            default:
                lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitGreater(GreaterExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        switch (compareLiterals(lhs, rhs)) {
            case 1:
                setTrue(expression);
                break;
            case 0:
            case -1:
            case 2:
                setFalse(expression);
                break;
            default:
                lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitIf(IfStatement tree) {
        tree.cond = getLowerExpression(tree.cond);

        if (isTrue(tree.cond)) {
            result = foldBody(tree.body);
            return;
        }
        if (isFalse(tree.cond)) {
            result = foldBody(tree.elseBody);
            return;
        }
        tree.body = foldBody(tree.body);
        tree.elseBody = foldBody(tree.elseBody);
        result = tree;
    }

    @Override
    public void visitInt(IntExpression expression) {
        nothing(expression);
    }

    @Override
    public void visitLeftShift(ShiftLeftExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value <<= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitLessEqual(LessEqualExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        switch (compareLiterals(lhs, rhs)) {
            case -1:
            case 0:
                setTrue(expression);
                break;
            case 1:
            case 2:
                setFalse(expression);
                break;
            default:
                lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitLess(LessExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        switch (compareLiterals(lhs, rhs)) {
            case -1:
                setTrue(expression);
                break;
            case 1:
            case 0:
            case 2:
                setFalse(expression);
                break;
            default:
                lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitMultiply(MultiplyExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof FloatExpression) && (rhs instanceof FloatExpression)) {
            ((FloatExpression) lhs).value *= ((FloatExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof FloatExpression) && (rhs instanceof IntExpression)) {
            ((FloatExpression) lhs).value *= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof FloatExpression)) {
            ((FloatExpression) rhs).value *= ((IntExpression) lhs).value;
            result = rhs;
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value *= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitNegative(NegativeExpression expression) {
        Expression hs = getLowerExpression(expression.hs).child();

        if (hs instanceof FloatExpression) {
            ((FloatExpression) hs).value = -((FloatExpression) hs).value;
            result = hs;
            return;
        }
        if (hs instanceof IntExpression) {
            ((IntExpression) hs).value = -((IntExpression) hs).value;
            result = hs;
            return;
        }
        lowerUnary(expression, hs);
    }

    @Override
    public void visitNotEqual(NotEqualExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        switch (compareLiterals(lhs, rhs)) {
            case -1:
            case 1:
            case 2:
                setTrue(expression);
                break;
            case 0:
                setFalse(expression);
                break;
            default:
                lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitNot(NotExpression expression) {
        Expression hs = getLowerExpression(expression.hs).child();

        if (isTrue(hs)) {
            setFalse(expression);
            return;
        }
        if (isFalse(hs)) {
            setTrue(expression);
            return;
        }
        lowerUnary(expression, hs);
    }

    @Override
    public void visitNullCoalesce(NullCoalesceExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if (!lhs.isNullable() || (lhs instanceof NullExpression)) {
            result = rhs;
        } else {
            lowerBinary(expression, lhs, rhs);
        }
    }

    @Override
    public void visitNull(NullExpression expression) {
        nothing(expression);
    }

    @Override
    public void visitOr(OrExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        if (isTrue(lhs)) {
            setTrue(expression);
            return;
        }
        if (isFalse(lhs)) {
            result = getLowerExpression(expression.rhs);
            return;
        }
        Expression rhs = getLowerExpression(expression.rhs).child();
        if (isFalse(rhs)) {
            result = lhs;
            return;
        }
        if (isTrue(rhs)) {
            formalExpression = lhs;
            setTrue(expression);
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitParens(ParensExpression expression) {
        // Удаление скобок
        result = getLowerExpression(expression.expr);
    }

    @Override
    public void visitPositive(PositiveExpression expression) {
        Expression hs = getLowerExpression(expression.hs).child();

        if ((hs instanceof FloatExpression) || (hs instanceof IntExpression)) {
            result = hs;
            return;
        }
        lowerUnary(expression, hs);
    }

    @Override
    public void visitPostDecrement(PostDecrementExpression expression) {
        visitIncrease(expression);
    }

    @Override
    public void visitPostIncrement(PostIncrementExpression expression) {
        visitIncrease(expression);
    }

    @Override
    public void visitPreDecrement(PreDecrementExpression expression) {
        visitIncrease(expression);
    }

    @Override
    public void visitPreIncrement(PreIncrementExpression expression) {
        visitIncrease(expression);
    }

    @Deprecated
    @Override
    public void visitPrintln(PrintlnStatement statement) {
        throw new AssertionError("deprecated");
    }

    @Deprecated
    @Override
    public void visitPrint(PrintStatement statement) {
        throw new AssertionError("deprecated");
    }

    @Override
    public void visitRemainder(RemainderExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof FloatExpression) && (rhs instanceof FloatExpression)) {
            double r = ((FloatExpression) rhs).value;
            if (r != 0D) {
                ((FloatExpression) lhs).value %= r;
                result = lhs;
                return;
            }
        }
        if ((lhs instanceof FloatExpression) && (rhs instanceof IntExpression)) {
            long r = ((IntExpression) rhs).value;
            if (r != 0L) {
                ((FloatExpression) lhs).value %= r;
                result = lhs;
                return;
            }
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof FloatExpression)) {
            long l = ((IntExpression) lhs).value;
            double r = ((FloatExpression) rhs).value;
            if (r != 0D) {
                result = new FloatExpression(expression.pos, l % r);
                return;
            }
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            long r = ((IntExpression) rhs).value;
            if (r != 0L) {
                ((IntExpression) lhs).value %= r;
                result = lhs;
                return;
            }
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitReturn(ReturnStatement statement) {
        if ((statement.expr) != null) {
            statement.expr = getLowerExpression(statement.expr);
        }
        result = statement;
    }

    @Override
    public void visitRightShift(ShiftRightExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value >>= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitString(StringExpression expression) {
        nothing(expression);
    }

    @Override
    public void visitSubtract(SubtractExpression expression) {
        Expression lhs = getLowerExpression(expression.lhs).child();
        Expression rhs = getLowerExpression(expression.rhs).child();
        if ((lhs instanceof FloatExpression) && (rhs instanceof FloatExpression)) {
            ((FloatExpression) lhs).value -= ((FloatExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof FloatExpression) && (rhs instanceof IntExpression)) {
            ((FloatExpression) lhs).value -= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof FloatExpression)) {
            long l = ((IntExpression) lhs).value;
            double r = ((FloatExpression) rhs).value;
            result = new FloatExpression(expression.pos, l - r);
            return;
        }
        if ((lhs instanceof IntExpression) && (rhs instanceof IntExpression)) {
            ((IntExpression) lhs).value -= ((IntExpression) rhs).value;
            result = lhs;
            return;
        }
        lowerBinary(expression, lhs, rhs);
    }

    @Override
    public void visitSwitch(SwitchStatement statement) {
        statement.selector = getLowerExpression(statement.selector);
        statement.cases = lowerList(statement.cases);
        result = statement;
    }

    @Override
    public void visitTernary(TernaryExpression expression) {
        Expression cond = getLowerExpression(expression.cond).child();
        Expression lhs = getLowerExpression(expression.lhs);
        Expression rhs = getLowerExpression(expression.rhs);
        if (isTrue(cond)) {
            result = lhs;
            return;
        }
        if (isFalse(cond)) {
            result = rhs;
            return;
        }
        expression.cond = cond;
        expression.lhs = lhs;
        expression.rhs = rhs;
        result = expression;
    }

    @Override
    public void visitTrue(TrueExpression expression) {
        nothing(expression);
    }

    @Override
    public void visitVariable(VariableExpression expression) {
        if (literalConstants.containsKey(expression.name)) {
            result = literalConstants.get(expression.name).copy(expression.pos);
        } else {
            nothing(expression);
        }
    }

    @Override
    public void visitWhile(WhileStatement tree) {
        tree.cond = getLowerExpression(tree.cond);

        if (isTrue(tree.cond)) {
            tree.cond = null; // codegen will handle it.
        } else if (isFalse(tree.cond)) {
            result = null;
            return;
        }
        tree.body = foldBody(tree.body);
        result = tree;
    }

    @Override
    public void visitDiscarded(DiscardedExpression expression) {
        expression.expression = getLowerExpression(expression.expression);
        result = expression;
    }

    private void visitAssignment(AssignmentExpression expression) {
        checkFolding(expression.var);
        expression.var = getLowerExpression(expression.var).child();
        expression.expr = getLowerExpression(expression.expr).child();
        result = expression;
    }

    private void visitIncrease(IncreaseExpression expression) {
        checkFolding(expression.hs);
        expression.hs = getLowerExpression(expression.hs).child();
        result = expression;
    }

    private void checkFolding(Expression expression) {
        expression = expression.child();
        if (!(expression instanceof VariableExpression)) {
            return;
        }
        if (literalConstants.containsKey(((VariableExpression) expression).name)) {
            throw new CompileError("assignment to constant is not allowed.", expression.pos);
        }
    }

    private void lowerBinary(BinaryExpression expression, Expression lhs, Expression rhs) {
        expression.lhs = lhs;
        expression.rhs = rhs;
        result = expression;
    }

    private void lowerUnary(UnaryExpression expression, Expression hs) {
        expression.hs = hs;
        result = expression;
    }

    @SuppressWarnings("unchecked")
    private <T extends Tree> List<T> lowerList(List<T> list) {
        return list.stream().map(n -> {
            n.accept(this);
            if (result == null) // empty statement
                return (T) Statement.EMPTY;
            try {
                return (T) result;
            } finally {
                result = null;
            }
        }).collect(Collectors.toList());
    }

    // todo: Переместить нижние методы в TreeInfo

    private boolean isTrue(Expression expr) {
        if (expr == null) return true;
        expr = expr.child();
        if (expr instanceof StringExpression) {
            return !((StringExpression) expr).value.isEmpty();
        }
        if (expr instanceof IntExpression) {
            return ((IntExpression) expr).value != 0L;
        }
        if (expr instanceof FloatExpression) {
            return ((FloatExpression) expr).value != 0D;
        }
        return (expr instanceof TrueExpression);
    }

    private boolean isFalse(Expression expr) {
        expr = expr.child();
        if (expr instanceof StringExpression) {
            return ((StringExpression) expr).value.isEmpty();
        }
        if (expr instanceof IntExpression) {
            return ((IntExpression) expr).value == 0L;
        }
        if (expr instanceof FloatExpression) {
            return ((FloatExpression) expr).value == 0D;
        }
        return (expr instanceof FalseExpression) || (expr instanceof NullExpression);
    }

    private void setTrue(Expression expression) {
        result = new TrueExpression(expression.pos);
    }

    private void setFalse(Expression expression) {
        result = new FalseExpression(expression.pos);
    }

    private void nothing(Tree tree) {
        result = tree;
    }

    private int compareLiterals(Expression a, Expression b) {
        if ((a instanceof FloatExpression) && (b instanceof FloatExpression)) {
            double l = ((FloatExpression) a).value;
            double r = ((FloatExpression) b).value;
            return compareNumbers(l, r);
        }
        if ((a instanceof FloatExpression) && (b instanceof IntExpression)) {
            double l = ((FloatExpression) a).value;
            long r = ((IntExpression) b).value;
            return compareNumbers(l, r);
        }
        if ((a instanceof IntExpression) && (b instanceof FloatExpression)) {
            long l = ((IntExpression) a).value;
            double r = ((FloatExpression) b).value;
            return compareNumbers(l, r);
        }
        if ((a instanceof IntExpression) && (b instanceof IntExpression)) {
            long l = ((IntExpression) a).value;
            long r = ((IntExpression) b).value;
            return Long.compare(l, r);
        }
        if ((a instanceof StringExpression) && (b instanceof StringExpression)) {
            String l = ((StringExpression) a).value;
            String r = ((StringExpression) b).value;
            return l.compareTo(r);
        }
        if ((a instanceof BooleanExpression) && (b instanceof BooleanExpression)) {
            return a.getClass() == b.getClass() ? 0 : 2;
        }
        if ((a instanceof NullExpression) && (b instanceof NullExpression)) {
            return 0;
        }
        return -2; // not applicable
    }

    private int compareNumbers(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return 2; // automatically false
        }
        return Double.compare(a, b);
    }

    private Expression getLowerExpression(Expression expression) {
        expression.accept(this);
        try {
            return (Expression) result;
        } finally {
            result = null;
        }
    }

    private Statement getLowerStatement(Statement statement) {
        statement.accept(this);
        if (result == null) { // empty
            return Statement.EMPTY;
        }
        try {
            return (Statement) result;
        } finally {
            result = null;
        }
    }
}