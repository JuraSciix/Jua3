package jua.compiler;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import jua.compiler.Tree.*;
import jua.compiler.Types.Type;
import jua.interpreter.instruction.*;
import jua.runtime.JuaFunction;
import jua.util.Assert;

import java.util.List;

import static jua.compiler.InstructionUtils.*;
import static jua.compiler.TreeInfo.removeParens;

public final class MinorGen extends Gen {

    /**
     *
     */
    @Deprecated
    static final int STATE_ROOTED = (1 << 0);

    /**
     * Состояние кода, в котором нельзя определять функции и константы.
     */
    @Deprecated
    static final int STATE_NO_DECLS = (1 << 1);

    /**
     * Состояние кода, в котором любое обрабатываемое выражение должно оставлять за собой какое-либо значение.
     */
    @Deprecated
    static final int STATE_RESIDUAL = (1 << 2);

    /**
     * Состояние кода, в котором любое обрабатываемое выражение должно приводиться к логическому виду.
     */
    @Deprecated
    static final int STATE_COND = (1 << 3);

    /**
     * Состояние кода, в котором все логические выражения должны инвертироваться.
     */
    @Deprecated
    static final int STATE_COND_INVERT = (1 << 4);

    /**
     * Состояние кода, в котором текущий обрабатываемый цикл считается бесконечным.
     */
    @Deprecated
    static final int STATE_INFINITY_LOOP = (1 << 5);

    /**
     * Состояние кода, в котором оператор switch не является конечным.
     */
    @Deprecated
    static final int STATE_ALIVE_SWITCH = (1 << 6);

    @Deprecated private final IntArrayList breakChains = new IntArrayList();
    @Deprecated private final IntArrayList continueChains = new IntArrayList();
    @Deprecated private final IntArrayList fallthroughChains = new IntArrayList();
    @Deprecated private final IntArrayList conditionalChains = new IntArrayList();

    /**
     * Состояние кода.
     */
    private int state = 0; // unassigned state

    Code code;
    private Log log;

    private final ProgramLayout programLayout;

    MinorGen(ProgramLayout programLayout) {
        this.programLayout = programLayout;
    }

    // todo: исправить этот low-cohesion

    @Override
    public void visitCompilationUnit(CompilationUnit tree) {
        code = tree.code;
        log = tree.source.getLog();
        code.pushContext(0);
        code.pushState();
        int prev_state = state;
        setState(STATE_ROOTED);
        scan(tree.stats);
        state = prev_state;
        code.addInstruction(Halt.INSTANCE);
        code.popState();
    }

    private boolean isState(int state_flag) {
        return (state & state_flag) != 0;
    }

    private void unsetState(int state_flag) {
        state &= ~state_flag;
    }

    private void setState(int state_flag) {
        state |= state_flag;
    }

    private void genFlowAnd(BinaryOp tree) {
        CondItem lcond = genCond(tree.lhs);
        lcond.resolveTrueJumps();
        CondItem rcond = genCond(tree.rhs);
        result = new CondItem(rcond.opcodePC, rcond.truejumps, mergeFalsejumps(lcond, rcond));
    }

    private void genFlowOr(BinaryOp tree) {
        CondItem lcond = genCond(tree.lhs).negate();
        lcond.resolveTrueJumps();
        CondItem rcond = genCond(tree.rhs);
        result = new CondItem(rcond.opcodePC, mergeFalsejumps(lcond, rcond), rcond.truejumps);
    }

    static IntArrayList mergeFalsejumps(CondItem lcond, CondItem rcond) {
        IntArrayList falsejumps = new IntArrayList(lcond.falsejumps.size() + rcond.falsejumps.size());
        falsejumps.addAll(lcond.falsejumps);
        falsejumps.addAll(rcond.falsejumps);
        return falsejumps;
    }

    static IntArrayList mergeTruejumps(CondItem lcond, CondItem rcond) {
        IntArrayList truejumps = new IntArrayList(lcond.truejumps.size() + rcond.truejumps.size());
        truejumps.addAll(lcond.truejumps);
        truejumps.addAll(rcond.truejumps);
        return truejumps;
    }

    private final StackItem stackItem = new StackItem();

    private final EmptyItem emptyItem = new EmptyItem();

    @Override
    public void visitArrayAccess(ArrayAccess tree) {
        genExpr(tree.expr).load();
        genExpr(tree.index).load();
        result = new AccessItem(tree.pos);
    }

    @Override
    public void visitArrayLiteral(ArrayLiteral tree) {
        code.putPos(tree.pos);
        emitNewArray();
        result = genArrayInitializr(tree.entries);
    }

    Item genArrayInitializr(List<ArrayLiteral.Entry> entries) {
        long implicitIndex = 0L;
        Item item = stackItem;
        for (ArrayLiteral.Entry entry : entries) {
            item.duplicate();
            if (entry.key == null) {
                code.putPos(entry.pos);
                emitPushLong(implicitIndex++);
            } else {
                genExpr(entry.key).load();
            }
            genExpr(entry.value).load();
            new AccessItem(entry.pos).store();
        }
        return item;
    }

    private void emitNewArray() {
        code.addInstruction(Newarray.INSTANCE);
    }

    private void genBinary(BinaryOp tree) {
        genExpr(tree.lhs).load();
        genExpr(tree.rhs).load();
        code.putPos(tree.pos);
        code.addInstruction(fromBinaryOpTag(tree.tag));
        result = stackItem;
    }

    @Override
    public void visitBreak(Break tree) {
        // todo: Эта проверка должна находиться в другом этапе
        if (flow == null) {
            cError(tree.pos, "'break' is not allowed outside of loop/switch.");
            return;
        }
        code.putPos(tree.pos);
        flow.exitjumps.add(emitGoto());
        code.dead();
        flow.interrupted = true;
    }

    private int emitGoto() {
        return code.addInstruction(new Goto());
    }

    @Override
    public void visitSwitch(Tree.Switch tree) {
        genExpr(tree.expr).load();
        flow = new FlowEnv(flow, true);
        flow.switchStartPC = code.currentIP();
        code.putPos(tree.pos);
        code.addInstruction(new Fake(-1)); // Резервируем место под инструкцию

        for (Case c : tree.cases) {
            c.accept(this);
            flow.resolveCont();
            flow.contjumps.clear();
        }

        if (flow.switchDefaultOffset == -1) {
            // Явного default-case не было
            flow.switchDefaultOffset = code.currentIP() - flow.switchStartPC;
        }

        code.setInstruction(flow.switchStartPC,
                new jua.interpreter.instruction.Switch(
                        flow.caseLabelsConstantIndexes.toIntArray(),
                        flow.switchCaseOffsets.toIntArray(),
                        flow.switchDefaultOffset
                )
        );

        flow.resolveExit();

        if (!flow.interrupted) {
            // Ни один кейз не был закрыт с помощью break.
            // Это значит, что после switch находится недостижимый код.
            code.dead();
        }

        flow = flow.parent;
    }

    @Override
    public void visitCase(Case tree) {
        if (tree.labels == null) {
            // default
            flow.switchDefaultOffset = code.currentIP() - flow.switchStartPC;
        } else {
            for (Expression label : tree.labels) {
                // todo: Эта проверка должна находиться в другом этапе
                if (!removeParens(label).hasTag(Tag.LITERAL)) {
                    log.error(label.pos, "Constant expected");
                    continue;
                }
                flow.caseLabelsConstantIndexes.add(genExpr(label).constantIndex());
                // Это не ошибка. Следующая строчка должна находиться именно в цикле
                // Потому что инструкция switch ассоциирует значения к переходам в масштабе 1 к 1.
                flow.switchCaseOffsets.add(code.currentIP() - flow.switchStartPC);
            }
        }

        boolean caseBodyAlive = generateBranch(tree.body);

        if (caseBodyAlive) {
            // Неявный break
            flow.exitjumps.add(emitGoto());
            flow.interrupted = true;
        }
    }

    @Override
    public void visitConstDef(ConstDef tree) {
        cError(tree.pos, "constants declaration is not allowed here.");
    }

    @Override
    public void visitContinue(Continue tree) {
        // todo: Эта проверка должна находиться в другом этапе
        if (!searchEnv(false)) {
            cError(tree.pos, "'continue' is not allowed outside of loop.");
            return;
        }
        code.putPos(tree.pos);
        flow.contjumps.add(emitGoto());
        code.dead();
    }

    private boolean searchEnv(boolean isSwitch) {
        for (FlowEnv env = flow; env != null; env = env.parent)
            if (env.isSwitch == isSwitch)
                return true;
        return false;
    }

    @Override
    public void visitDoLoop(DoLoop tree) {
        genLoop(tree, null, tree.cond, null, tree.body, false);
    }

    @Deprecated
    private int pushMakeConditionChain() {
        int newChain = code.makeChain();
        conditionalChains.push(newChain);
        return newChain;
    }

    @Deprecated
    private int popConditionChain() {
        return conditionalChains.popInt();
    }

    @Deprecated
    private int peekConditionChain() {
        return conditionalChains.topInt();
    }

    @Override
    public void visitFallthrough(Fallthrough tree) {
        // todo: Эта проверка должна находиться в другом этапе
        if (!searchEnv(true)) {
            cError(tree.pos, "'fallthrough' is not allowed outside of switch.");
            return;
        }
        code.putPos(tree.pos);
        flow.contjumps.add(emitGoto());
        code.dead();
    }

    @Override
    public void visitFor(ForLoop tree) {
        genLoop(tree, tree.init, tree.cond, tree.step, tree.body, true);
    }

    @Override
    public void visitInvocation(Invocation tree) {
        if (!tree.callee.hasTag(Tag.MEMACCESS) || ((MemberAccess) tree.callee).expr != null) {
            log.error(tree.pos, "Only a function calling allowed");
            return;
        }
        Name callee = ((MemberAccess) tree.callee).member;

        for (Invocation.Argument argument : tree.args) {
            if (argument.name != null) {
                log.error(argument.name.pos, "Named arguments not supported yet");
                return;
            }
        }

        int nargs = tree.args.size();

        switch (callee.value) {
            case "print":
                visitInvocationArgs(tree.args);
                code.putPos(tree.pos);
                code.addInstruction(new Print(nargs));
                result = emptyItem;
                break;

            case "println":
                visitInvocationArgs(tree.args);
                code.putPos(tree.pos);
                code.addInstruction(new Println(nargs));
                result = emptyItem;
                break;

            case "length":
            case "sizeof":
                require_nargs(tree, 1);
                genExpr(tree.args.get(0).expr).load();
                code.putPos(tree.pos);
                code.addInstruction(Length.INSTANCE);
                result = stackItem;
                break;

            case "gettype":
            case "typeof":
                require_nargs(tree, 1);
                genExpr(tree.args.get(0).expr).load();
                code.putPos(tree.pos);
                code.addInstruction(Gettype.INSTANCE);
                result = stackItem;
                break;

            case "ns_time":
                require_nargs(tree, 0);
                code.putPos(tree.pos);
                code.addInstruction(NsTime.INSTANCE);
                result = stackItem;
                break;

            default:
                int fn_idx = programLayout.tryFindFunc(callee);
                visitInvocationArgs(tree.args);
                code.putPos(tree.pos);
                if (nargs > 0xff) {
                    cError(tree.pos, "Too many arguments");
                }
                code.addInstruction(new Call((short) fn_idx, (byte) nargs));
                result = stackItem;
        }
    }

    private void require_nargs(Invocation tree, int nargs) {
        if (tree.args.size() != nargs) {
            log.error(tree.pos, "Required arguments count mismatch (" +
                    "required: " + nargs + ", " +
                    "provided: " + tree.args.size() +
                    ")");
        }
    }

    private void visitInvocationArgs(List<Invocation.Argument> args) {
        args.forEach(argument -> genExpr(argument.expr).load());
    }

    Source funcSource;

    JuaFunction resultFunc;

    @Override
    public void visitFuncDef(FuncDef tree) {
        if (code != null) {
            cError(tree.pos, "Function declaration is not allowed here");
            return;
        }
        code = tree.code;
        log = funcSource.getLog();

        code.pushContext(tree.pos);
        code.pushState();

        {
            int nOptionals = 0;
            for (FuncDef.Parameter param : tree.params) {
                Name name = param.name;
                if (code.localExists(name.value)) {
                    cError(name.pos, "Duplicate parameter named '" + name.value + "'.");
                }
                int localIdx = code.resolveLocal(name.value);
                if (param.expr != null) {
                    Expression expr = removeParens(param.expr);
                    if (!expr.hasTag(Tag.LITERAL)) {
                        cError(expr.pos, "The values of the optional parameters can only be literals");
                        continue;
                    }
                    code.get_cpb().putDefaultLocalEntry(localIdx, ((Literal) expr).type.getConstantIndex());
                    nOptionals++;
                }
            }

            assert tree.body != null;

            if (tree.body.hasTag(Tag.BLOCK)) {
                if (generateBranch(tree.body)) {
                    emitRetnull();
                }
            } else {
                Assert.check(tree.body instanceof Expression, "Function body neither block ner expression");
                genExpr((Expression) tree.body).load();
                emitReturn();
            }

            resultFunc = JuaFunction.fromCode(
                    tree.name.value,
                    tree.params.size() - nOptionals,
                    tree.params.size(),
                    code.buildCodeSegment(),
                    funcSource.name
            );
        }

        code.popState();
        code.popContext();
        code = null;
    }

    private boolean declarationsUnallowedHere() {
        return isState(STATE_NO_DECLS);
    }

    public void visitGreaterEqual(BinaryOp expression) {
//        beginCondition();
//        if (expression.lhs instanceof IntExpression) {
//            visitExpression(expression.rhs);
//            code.addFlow(ListDequeUtils.peekLastInt(conditionalChains), invertCond
//                    ? new Ifle(((IntExpression) expression.lhs).value)
//                    : new Ifgt(((IntExpression) expression.lhs).value));
//            code.decStack();
//        } else if (expression.rhs instanceof IntExpression) {
//            visitExpression(expression.lhs);
//            code.addFlow(ListDequeUtils.peekLastInt(conditionalChains), invertCond
//                    ? new Ifge(((IntExpression) expression.rhs).value)
//                    : new Iflt(((IntExpression) expression.rhs).value));
//            code.decStack();
//        } else {
//            visitBinaryOp(expression);
//            code.addFlow(TreeInfo.line(expression), ListDequeUtils.peekLastInt(conditionalChains),
//                    invertCond ? new Ifcmpge() : new Ifcmplt());
//            code.decStack(2);
//        }
//        endCondition();

        genCmp(expression);
    }

    public void visitGreater(BinaryOp expression) {
//        beginCondition();
//        if (expression.lhs instanceof IntExpression) {
//            visitExpression(expression.rhs);
//            code.addFlow(ListDequeUtils.peekLastInt(conditionalChains), invertCond
//                    ? new Ifge(((IntExpression) expression.lhs).value)
//                    : new Iflt(((IntExpression) expression.lhs).value));
//            code.decStack();
//        } else if (expression.rhs instanceof IntExpression) {
//            visitExpression(expression.lhs);
//            code.addFlow(ListDequeUtils.peekLastInt(conditionalChains), invertCond
//                    ? new Ifle(((IntExpression) expression.rhs).value)
//                    : new Ifgt(((IntExpression) expression.rhs).value));
//            code.decStack();
//        } else {
//            visitBinaryOp(expression);
//            code.addFlow(TreeInfo.line(expression), ListDequeUtils.peekLastInt(conditionalChains),
//                    invertCond ? new Ifcmpgt() : new Ifcmple());
//            code.decStack(2);
//        }
//        endCondition();

        genCmp(expression);
    }

    @Override
    public void visitIf(If tree) {
        CondItem cond = genCond(tree.cond);
        cond.resolveTrueJumps();
        boolean alive = generateBranch(tree.thenbody);
        if (tree.elsebody != null) {
            if (alive) {
                int skipperPC = emitGoto();
                cond.resolveFalseJumps();
                generateBranch(tree.elsebody);
                resolveJump(skipperPC);
            } else {
                cond.resolveFalseJumps();
                alive = generateBranch(tree.elsebody);
            }
        } else {
            cond.resolveFalseJumps();
        }

        if (!alive) {
            code.dead();
        }
    }

    private void assertStacktopEquality(int limitstacktop) {
        Assert.check(code.curStackTop() == limitstacktop, "limitstacktop mismatch (" +
                "before: " + limitstacktop + ", " +
                "after: " + code.curStackTop() + ", " +
                "code line num: " + code.current_lineNumber +
                ")");
    }

    public void visitLessEqual(BinaryOp expression) {
//        beginCondition();
//        Expression lhs = expression.lhs;
//        Expression rhs = expression.rhs;
//        int line = TreeInfo.line(expression);
//        if (TreeInfo.resolveShort(lhs) >= 0) {
//            visitExpression(rhs);
//            int shortVal = TreeInfo.resolveShort(lhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifge(shortVal) : new Iflt(shortVal)),
//                    peekConditionChain(), -1);
//        } else if (TreeInfo.resolveShort(rhs) >= 0) {
//            visitExpression(rhs);
//            int shortVal = TreeInfo.resolveShort(lhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifle(shortVal) : new Ifgt(shortVal)),
//                    peekConditionChain(), -1);
//        } else {
//            visitExpression(lhs);
//            visitExpression(rhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifcmple() : new Ifcmpgt()),
//                    peekConditionChain(), -2);
//        }
//        endCondition();

        genCmp(expression);
    }

    public void visitLess(BinaryOp expression) {
//        beginCondition();
//        if (expression.lhs instanceof IntExpression) {
//            visitExpression(expression.rhs);
//            code.addFlow(ListDequeUtils.peekLastInt(conditionalChains), invertCond
//                    ? new Ifgt(((IntExpression) expression.lhs).value)
//                    : new Ifle(((IntExpression) expression.lhs).value));
//            code.decStack();
//        } else if (expression.rhs instanceof IntExpression) {
//            visitExpression(expression.lhs);
//            code.addFlow(ListDequeUtils.peekLastInt(conditionalChains), invertCond
//                    ? new Iflt(((IntExpression) expression.rhs).value)
//                    : new Ifge(((IntExpression) expression.rhs).value));
//            code.decStack();
//        } else {
//            visitBinaryOp(expression);
//            code.addFlow(TreeInfo.line(expression), ListDequeUtils.peekLastInt(conditionalChains),
//                    invertCond ? new Ifcmplt() : new Ifcmpge());
//            code.decStack(2);
//        }
//        endCondition();

        genCmp(expression);
    }

    static boolean isShortIntegerLiteral(Expression tree) {
        if (tree == null) return false;
        if (tree.hasTag(Tag.PARENS)) tree = (Expression) removeParens(tree);
        if (!tree.hasTag(Tag.LITERAL)) return false;
        Literal literal = (Literal) tree;
        if (!literal.type.isLong()) return false;
        long value = literal.type.longValue();
        return (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE);
    }

    private short unpackShortIntegerLiteral(Expression tree) {
        tree = removeParens(tree);
        return (short) ((Literal) tree).type.longValue();
    }

    private void genCmp(BinaryOp tree) {
        JumpInstruction opcode;
        if (isShortIntegerLiteral(tree.lhs)) {
            genExpr(tree.rhs).load();
            opcode = InstructionUtils.fromConstComparisonOpTag(tree.tag,
                    unpackShortIntegerLiteral(tree.lhs));
        } else if (isShortIntegerLiteral(tree.rhs)) {
            genExpr(tree.lhs).load();
            opcode = InstructionUtils.fromConstComparisonOpTag(tree.tag,
                    unpackShortIntegerLiteral(tree.rhs));
        } else if (isNull(tree.lhs)) {
            genExpr(tree.rhs).load();
            opcode = new Ifnonnull();
        } else if (isNull(tree.rhs)) {
            genExpr(tree.lhs).load();
            opcode = new Ifnonnull();
        } else {
            genExpr(tree.lhs).load();
            genExpr(tree.rhs).load();
            opcode = InstructionUtils.fromComparisonOpTag(tree.tag);
        }
        code.putPos(tree.pos);
        result = new CondItem(code.addInstruction(opcode));
    }

    public void visitNotEqual(BinaryOp expression) {
//        beginCondition();
//        Expression lhs = expression.lhs;
//        Expression rhs = expression.rhs;
//        int line = TreeInfo.line(expression);
//        if (lhs instanceof NullExpression) {
//            visitExpression(rhs);
//            code.addChainedInstruction(line,
//                    invertCond ? new Ifnonnull() : new Ifnull(),
//                    peekConditionChain(), -1);
//        } else if (rhs instanceof NullExpression) {
//            visitExpression(lhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifnonnull() : new Ifnull()),
//                    peekConditionChain(), -1);
//        } else if (TreeInfo.resolveShort(lhs) >= 0) {
//            visitExpression(rhs);
//            int shortVal = TreeInfo.resolveShort(lhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifne(shortVal) : new Ifeq(shortVal)),
//                    peekConditionChain(), -1);
//        } else if (TreeInfo.resolveShort(rhs) >= 0) {
//            visitExpression(rhs);
//            int shortVal = TreeInfo.resolveShort(lhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifne(shortVal) : new Ifeq(shortVal)),
//                    peekConditionChain(), -1);
//        } else {
//            visitExpression(lhs);
//            visitExpression(rhs);
//            code.addChainedInstruction(line,
//                    (invertCond ? new Ifcmpne() : new Ifcmpeq()),
//                    peekConditionChain(), -2);
//        }
//        endCondition();

        genCmp(expression);
    }

    private void genNullCoalescing(BinaryOp tree) {
        genExpr(tree.lhs).load();
        emitDup();
        code.putPos(tree.pos);
        Ifnonnull cond = new Ifnonnull();
        code.putPos(tree.pos);
        int condPC = code.addInstruction(cond);
        code.addInstruction(Pop.INSTANCE);
        genExpr(tree.rhs).load();
        cond.offset = code.currentIP() - condPC;
        result = stackItem;
    }

    @Override
    public void visitParens(Parens tree) {
        code.putPos(tree.pos);
        tree.expr.accept(this);
    }

    @Override
    public void visitAssign(Assign tree) {
        Expression var = removeParens(tree.var);
        switch (var.getTag()) {
            case MEMACCESS:
            case ARRAYACCESS:
            case VARIABLE:
                Item varitem = genExpr(tree.var);
                genExpr(tree.expr).load();
                result = new AssignItem(tree.pos, varitem);
                break;

            default:
                log.error(var.pos, "Assignable expression expected");
        }
    }

    private void generateUnary(UnaryOp tree) {
        switch (tree.tag) {
            case POSTDEC: case PREDEC:
            case POSTINC: case PREINC:
                genIncrease(tree);
                break;
            case NOT:
                result = genCond(tree.expr).negate();
                break;
            default:
                genExpr(tree.expr).load();
                code.putPos(tree.pos);
                code.addInstruction(InstructionUtils.fromUnaryOpTag(tree.tag));
                result = stackItem;
        }
    }

    @Override
    public void visitReturn(Tree.Return tree) {
        code.putPos(tree.pos);
        if (isNull(tree.expr)) {
            emitRetnull();
        } else {
            genExpr(tree.expr).load();
            emitReturn();
        }
    }

    private void emitRetnull() {
        code.addInstruction(ReturnNull.INSTANCE);
        code.dead();
    }

    private static boolean isNull(Expression tree) {
        return tree == null || tree.getTag() == Tag.LITERAL && ((Literal) tree).type == null;
    }

    @Override
    public void visitTernaryOp(TernaryOp tree) {
        int limitstacktop = code.curStackTop();
        code.putPos(tree.pos);
        CondItem cond = genCond(tree.cond);
        cond.resolveTrueJumps();
        int a = code.curStackTop();
        genExpr(tree.thenexpr).load();
        int exiterPC = emitGoto();
        cond.resolveFalseJumps();
        code.curStackTop(a);
        genExpr(tree.elseexpr).load();
        resolveJump(exiterPC);
        assertStacktopEquality(limitstacktop + 1);
        result = stackItem;
    }

    @Override
    public void visitVariable(Var tree) {
        Name name = tree.name;
        if (programLayout.hasConstant(name)) {
            result = new ConstantItem(tree.pos, name);
        } else {
            result = new LocalItem(tree.pos, name);
        }
    }

    @Override
    public void visitMemberAccess(MemberAccess tree) {
        genExpr(tree.expr).load();
        emitPushString(tree.member.value);
        result = new AccessItem(tree.pos);
    }

    @Override
    public void visitWhileLoop(WhileLoop tree) {
        genLoop(tree, null, tree.cond, null, tree.body, true);
    }

    FlowEnv flow;

    private void genLoop(
            Statement loop,
            List<Expression> init, Expression cond, List<Expression> steps,
            Statement body, boolean testFirst) {
        flow = new FlowEnv(flow, false);

        if (init != null) init.forEach(expr -> genExpr(expr).drop());

        boolean truecond = isCondTrue(cond);
        int loopstartPC, limitstacktop;
        if (testFirst && !truecond) {
            code.putPos(loop.pos);
            int skipBodyPC = emitGoto();
            loopstartPC = code.currentIP();
            limitstacktop = code.curStackTop();
            generateBranch(body);
            if (steps != null) steps.forEach(expr -> genExpr(expr).drop());
            resolveJump(skipBodyPC);
        } else {
            loopstartPC = code.currentIP();
            limitstacktop = code.curStackTop();
            generateBranch(body);
            if (steps != null) steps.forEach(expr -> genExpr(expr).drop());
        }
        flow.resolveCont();
        if (truecond) {
            Goto back2start = new Goto();
            back2start.offset = loopstartPC - code.currentIP();
            code.addInstruction(back2start);
            if (!flow.interrupted) code.dead();
        } else {
            CondItem condItem = genCond(cond).negate();
            condItem.resolveTrueJumps();
            condItem.resolveFalseJumps(loopstartPC);
        }
        flow.resolveExit();

        assertStacktopEquality(limitstacktop);

        flow = flow.parent;
    }

    static boolean isCondTrue(Expression tree) {
        if (tree == null) return true;
        if (tree.hasTag(Tag.PARENS)) tree = removeParens(tree);
        if (!tree.hasTag(Tag.LITERAL)) return false;
        Type type = ((Literal) tree).type;
        if (type.isBoolean()) return type.booleanValue();
        if (type.isString()) return !type.stringValue().isEmpty();
        if (type.isLong()) return type.longValue() != 0L;
        if (type.isDouble()) return type.doubleValue() != 0.0D;
        return false;
    }

    private void visitExpression(Expression expression) {
        int prev_state = state;
        setState(STATE_RESIDUAL);
        expression.accept(this);
        state = prev_state;
    }

    public void visitBinaryOp(BinaryOp tree) {
        switch (tree.tag) {
            case FLOW_AND:
                genFlowAnd(tree);
                break;
            case FLOW_OR:
                genFlowOr(tree);
                break;
            case EQ: case NE:
            case GT: case GE:
            case LT: case LE:
                genCmp(tree);
                break;
            case NULLCOALESCE:
                genNullCoalescing(tree);
                break;
            default:
                genBinary(tree);
        }
    }

    @Override
    public void visitUnaryOp(UnaryOp tree) {
        generateUnary(tree);
    }

    @Override
    public void visitCompoundAssign(CompoundAssign tree) {
        Expression var = removeParens(tree.dst);

        switch (var.getTag()) {
            case MEMACCESS:
            case ARRAYACCESS:
            case VARIABLE:
                break;

            default:
                log.error(var.pos, "Assignable expression expected");
        }

        Item varitem = genExpr(tree.dst);
        varitem.duplicate();
        if (tree.hasTag(Tag.ASG_NULLCOALESCE)) {
            varitem.load().duplicate();
            String tmp = code.acquireSyntheticName(); // synthetic0
            emitVStore(tmp);
            Ifnonnull cond = new Ifnonnull();
            code.putPos(tree.pos);
            int cPC = code.addInstruction(cond);
            int sp1 = code.curStackTop();
            genExpr(tree.src).load().duplicate();
            emitVStore(tmp);
            varitem.store();
            int sp2 = code.curStackTop();
            Goto e = new Goto();
            int ePC = code.currentIP();
            code.addInstruction(e);
            cond.offset = code.currentIP() - cPC;
            code.curStackTop(sp1);
            varitem.drop();
            e.offset = code.currentIP() - ePC;
            assertStacktopEquality(sp2);
            result = new Item() {
                @Override
                Item load() {
                    emitVLoad(tmp);
                    drop();
                    return stackItem;
                }

                @Override
                void drop() {
                    code.addInstruction(ConstNull.INSTANCE);
                    emitVStore(tmp);
                    code.releaseSyntheticName(tmp);
                }
            };
        } else {
            varitem.load();
            genExpr(tree.src).load();
            code.addInstruction(fromBinaryAsgOpTag(tree.tag));
            result = new AssignItem(tree.pos, varitem);
        }
    }

    @Override
    public void visitLiteral(Literal tree) {
        result = new LiteralItem(tree.pos, tree.type);
    }

    private void visitList(List<? extends Tree> expressions) {
        int prev_state = state;
        setState(STATE_RESIDUAL);
        for (Tree expr : expressions)
            expr.accept(this);
        state = prev_state;
    }

    @Deprecated
    private void generateCondition(Expression tree) {
//        assert tree != null;
//        int prev_state = state;
//        setState(STATE_COND);
//        visitExpression(tree);
//        state = prev_state;
//        if (TreeInfo.isConditionalTag(tree.getTag())) {
//            return;
//        }
//        // todo: Здешний код отвратителен. Следует переписать всё с нуля...
////        code.addInstruction(Bool.INSTANCE);
//        code.putPos(tree.pos);
//        code.addChainedInstruction(isState(STATE_COND_INVERT) ? Iftrue::new : Iffalse::new,
//                peekConditionChain(), -1);
    }

    @Override
    public void visitDiscarded(Discarded tree) {
//        visitStatement(tree.expr);
//        switch (tree.expr.getTag()) {
//            case ASSIGN: case ASG_ADD: case ASG_SUB: case ASG_MUL:
//            case ASG_DIV: case ASG_REM: case ASG_AND: case ASG_OR:
//            case ASG_XOR: case ASG_SL: case ASG_SR: case ASG_NULLCOALESCE:
//            case PREINC: case PREDEC: case POSTINC: case POSTDEC:
//                break;
//            default:
//                code.addInstruction(Pop.INSTANCE, -1);
//        }

        genExpr(tree.expr).drop();
    }

    @Deprecated
    private void genAssignment(int pos, Tag tag, Expression lhs, Expression rhs) {
//        Expression var = expression.var.child();
//        checkAssignable(var);
//        int line = line(expression);
//        if (var instanceof ArrayAccess) {
//            ArrayAccess var0 = (ArrayAccess) var;
//            visitExpression(var0.hs);
//            visitExpression(var0.key);
//            if (state != null) {
//                emitDup2(line);
//                emitALoad(line(var0.key));
//                state.emit(line);
//            } else {
//                visitExpression(expression.expr);
//            }
//            if (isUsed())
//                // Здесь используется var0.key потому что
//                // он может быть дальше, чем var0, а если бы он был ближе
//                // к началу файла, то это было бы некорректно для таблицы линий
//                emitDup_x2(line(var0.key));
//            emitAStore(line);
//        } else if (var instanceof Var) {
//            if (state != null) {
//                visitExpression(var);
//                state.emit(line);
//            } else {
//                visitExpression(expression.expr);
//                if (isUsed()) {
//                    emitDup(line(var));
//                }
//            }
//            emitVStore(line, ((Var) var).name);
//        }

//        switch (lhs.getTag()) {
//            case ARRAYACCESS: {
//                ArrayAccess arrayAccess = (ArrayAccess) lhs;
//                code.putPos(arrayAccess.pos);
//                visitExpression(arrayAccess.expr);
//                visitExpression(arrayAccess.index);
//                if (tag == Tag.ASG_NULLCOALESCE) {
//                    int el = code.makeChain();
//                    int ex = code.makeChain();
//                    emitDup2();
//                    emitALoad();
//                    code.addChainedInstruction(Ifnonnull::new, el, -1);
//                    int sp_cache = code.curStackTop();
//                    visitExpression(rhs);
//                    if (isUsed()) {
//                        emitDupX2();
//                    }
//                    code.putPos(arrayAccess.pos);
//                    emitAStore();
//                    emitGoto(ex);
//                    int sp_cache2 = code.curStackTop();
//                    code.resolveChain(el);
//                    code.curStackTop(sp_cache);
//                    if (isUsed()) {
//                        code.putPos(arrayAccess.pos);
//                        emitALoad();
//                    } else {
//                        code.addInstruction(Pop2.INSTANCE, -2);
//                    }
//                    code.resolveChain(ex);
//                    assert code.curStackTop() == sp_cache2;
//                } else {
//                    if (tag != Tag.ASSIGN) {
//                        emitDup2();
//                        code.putPos(arrayAccess.pos);
//                        emitALoad();
//                        visitExpression(rhs);
//                        code.putPos(pos);
//                        code.addInstruction(asg2state(tag), -1);
//                    } else {
//                        visitExpression(rhs);
//                    }
//                    if (isUsed()) {
//                        emitDupX2();
//                    }
//                    code.putPos(arrayAccess.pos);
//                    emitAStore();
//                }
//                break;
//            }
//            case VARIABLE: {
//                Var variable = (Var) lhs;
//                if (tag == Tag.ASG_NULLCOALESCE) {
//                    int ex = code.makeChain();
//                    visitExpression(lhs);
//                    code.addChainedInstruction(Ifnonnull::new, ex, -1);
//                    visitExpression(rhs);
//                    if (isUsed()) {
//                        emitDup();
//                    }
//                    code.putPos(pos);
//                    emitVStore(variable.name.value);
//                    if (isUsed()) {
//                        int el = code.makeChain();
//                        emitGoto(el);
//                        code.resolveChain(ex);
//                        visitExpression(lhs);
//                        code.resolveChain(el);
//                    } else {
//                        code.resolveChain(ex);
//                    }
//                } else {
//                    if (tag != Tag.ASSIGN) {
//                        visitExpression(lhs);
//                        visitExpression(rhs);
//                        code.putPos(pos);
//                        code.addInstruction(asg2state(tag), -1);
//                    } else {
//                        visitExpression(rhs);
//                    }
//                    if (isUsed()) {
//                        emitDup();
//                    }
//                    code.putPos(pos);
//                    emitVStore(variable.name.value);
//                }
//                break;
//            }
//            default: cError(lhs.pos, "assignable expression expected.");
//        }
    }

    // todo: В будущем планируется заменить поле expressionDepth на более удобный механизм.
    private boolean isUsed() {
        return isState(STATE_RESIDUAL);
    }
    @Deprecated
    private void enableUsed() {

    }
    @Deprecated
    private void disableUsed() {

    }

    private void genIncrease(UnaryOp tree) {
        Expression var = removeParens(tree.expr);

        // todo: Делать нормально уже лень. Рефакторинг

        switch (var.getTag()) {
            case MEMACCESS:
            case ARRAYACCESS:
            case VARIABLE:
                Item varitem = genExpr(tree.expr);
                boolean post = tree.hasTag(Tag.POSTINC) || tree.hasTag(Tag.POSTDEC);
                boolean inc = tree.hasTag(Tag.POSTINC) || tree.hasTag(Tag.PREINC);

                if (varitem instanceof LocalItem) {
                    result = new Item() {
                        final LocalItem localitem = (LocalItem) varitem;
                        @Override
                        Item load() {
                            if (post) {
                                localitem.load();
                                drop();
                            } else {
                                drop();
                                localitem.load();
                            }
                            return stackItem;
                        }

                        @Override
                        void drop() {
                            if (inc) {
                                code.putPos(tree.pos);
                                localitem.inc();
                            } else {
                                code.putPos(tree.pos);
                                localitem.dec();
                            }
                        }
                    };
                } else {
                    varitem.duplicate();
                    varitem.load();
                    result = new Item() {
                        @Override
                        Item load() {
                            if (post) {
                                varitem.stash();
                                drop();
                            } else {
                                code.putPos(tree.pos);
                                code.addInstruction(fromUnaryOpTag(tree.tag));
                                new AssignItem(tree.pos, varitem).load();
                            }
                            return stackItem;
                        }

                        @Override
                        void drop() {
                            code.putPos(tree.pos);
                            code.addInstruction(fromUnaryOpTag(tree.tag));
                            new AssignItem(tree.pos, varitem).drop();
                        }
                    };
                }
                break;

            default:
                log.error(tree.expr.pos, "Assignable expression expected");
        }
    }

    /** Генерирует код оператора в дочерней ветке и возвращает жива ли она. */
    private boolean generateBranch(Statement statement) {
        return genBranch(statement);
    }

    private void visitStatement(Statement statement) {
        if (statement == null) return;
        int prev_state = state;
        setState(STATE_NO_DECLS);
        statement.accept(this);
        state = prev_state;
    }

    @Deprecated
    private void beginCondition() {
        if (isState(STATE_COND)) {
            return;
        }
        pushMakeConditionChain();
    }

    @Deprecated
    private void endCondition() {
//        if (isState(STATE_COND)) {
//            return;
//        }
//        int ex = code.makeChain();
//        emitPushTrue();
//        gotobranch(ex);
//        code.resolveChain(popConditionChain());
//        emitPushFalse();
//        code.resolveChain(ex);
    }

    private void emitPushLong(long value) {
        if (isShort(value)) {
            code.addInstruction(new Push((short) value));
        } else {
            code.addInstruction(new Ldc(code.resolveLong(value)));
        }
    }

    private void emitPushDouble(double value) {
        long lv = (long) value;
        if (false && lv == value && isShort(lv)) {
//            code.addInstruction(new Push(Operand.Type.LONG, (short) lv), 1);
        } else {
            code.addInstruction(new Ldc(code.resolveDouble(value)));
        }
    }

    private void emitPushString(String value) {
        code.addInstruction(new Ldc(code.resolveString(value)));
    }

    private static boolean isShort(long value) {
        return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
    }

    // emit methods

    private void emitPushTrue() { code.addInstruction(ConstTrue.CONST_TRUE); }
    private void emitPushFalse() { code.addInstruction(ConstFalse.CONST_FALSE); }
    @Deprecated private void emitGoto(int chainId) {  }
    private void emitDup() { code.addInstruction(Dup.INSTANCE); }
    private void emitDupX1() { code.addInstruction(Dup_x1.INSTANCE); }
    private void emitDupX2() { code.addInstruction(Dup_x2.INSTANCE); }
    private void emitDup2() { code.addInstruction(Dup2.INSTANCE); }
    private void emitDup2X1() { code.addInstruction(Dup2_x1.INSTANCE); }
    private void emitDup2X2() { code.addInstruction(Dup2_x2.INSTANCE); }
    private void emitAdd() { code.addInstruction(Add.INSTANCE); }
    private void emitAnd() { code.addInstruction(And.INSTANCE); }
    private void emitOr() { code.addInstruction(Or.INSTANCE); }
    private void emitXor() { code.addInstruction(Xor.INSTANCE); }
    private void emitDiv() { code.addInstruction(Div.INSTANCE); }
    private void emitLhs() { code.addInstruction(Shl.INSTANCE); }
    private void emitMul() { code.addInstruction(Mul.INSTANCE); }
    private void emitRem() { code.addInstruction(Rem.INSTANCE); }
    private void emitRhs() { code.addInstruction(Shr.INSTANCE); }
    private void emitSub() { code.addInstruction(Sub.INSTANCE); }
    private void emitALoad() { code.addInstruction(Aload.INSTANCE); }
    private void emitVLoad(String name) { code.addInstruction(new Vload(code.resolveLocal(name))); }
    private void emitAStore() { code.addInstruction(Astore.INSTANCE); } // todo: Тут тоже было sp<0, вроде при generateArrayCreation.
    private void emitVStore(String name) { code.addInstruction(new Vstore(code.resolveLocal(name))); }
    @Deprecated private void emitCaseBody(Statement body) {
        code.resolveChain(fallthroughChains.popInt());
        fallthroughChains.push(code.makeChain());
        int prev_state = state;
        setState(STATE_INFINITY_LOOP);
        visitStatement(body);
        if (!isState(STATE_INFINITY_LOOP)) emitGoto(breakChains.topInt());
        state = prev_state;
    }
    private void emitReturn() {
        code.addInstruction(jua.interpreter.instruction.Return.RETURN);
        code.dead();
    }

    private void cError(int position, String message) {
        log.error(position, message);
    }

    Item result;

    Item genExpr(Expression tree) {
        Item prevItem = result;
        try {
            tree.accept(this);
            return result;
        } finally {
            result = prevItem;
        }
    }

    CondItem genCond(Expression tree) {
        return genExpr(tree).toCond();
    }

    abstract class Item {

        Item load() { throw new AssertionError(this); }

        void store() { throw new AssertionError(this); }

        void drop() { throw new AssertionError(this); }

        void duplicate() { throw new AssertionError(this); }

        void stash() { throw new AssertionError(this); }

        CondItem toCond() {
            load();
            return new CondItem(code.addInstruction(new Ifz()));
        }

        int constantIndex() { throw new AssertionError(this); }
    }

    /** Динамическое значение. */
    class StackItem extends Item {

        @Override
        Item load() { return this; }

        @Override
        void drop() {
            load();
            code.addInstruction(Pop.INSTANCE);
        }

        @Override
        void duplicate() {
            emitDup();
        }

        @Override
        void stash() {
            emitDup();
        }
    }

    /** Синтетический результат. */
    class EmptyItem extends Item {

        @Override
        Item load() {
            code.addInstruction(ConstNull.INSTANCE);
            return stackItem;
        }

        @Override
        void drop() { /* no-op */ }
    }

    /** Литерал. */
    class LiteralItem extends Item {

        final int pos;

        final Type type;

        LiteralItem(int pos, Type type) {
            this.pos = pos;
            this.type = type;
        }

        @Override
        Item load() {
            code.putPos(pos);
            if (type.isLong()) {
                emitPushLong(type.longValue());
            } else if (type.isDouble()) {
                emitPushDouble(type.doubleValue());
            } else if (type.isBoolean()) {
                if (type.booleanValue()) {
                    emitPushTrue();
                } else {
                    emitPushFalse();
                }
            } else if (type.isString()) {
                emitPushString(type.stringValue());
            } else if (type.isNull()) {
                code.addInstruction(ConstNull.INSTANCE);
            } else {
                throw new AssertionError(type);
            }
            return stackItem;
        }

        @Override
        void drop() { /* no-op */ }

        @Override
        int constantIndex() { return type.getConstantIndex(); }
    }

    /** Обращение к локальной переменной. */
    class LocalItem extends Item {

        final int pos;
        final Name name;

        LocalItem(int pos, Name name) {
            this.pos = pos;
            this.name = name;
        }

        @Override
        Item load() {
            code.putPos(pos);
            emitVLoad(name.value);
            return stackItem;
        }

        @Override
        void drop() {
            load();
            code.addInstruction(Pop.INSTANCE);
        }

        @Override
        void duplicate() { /* no-op */ }

        @Override
        void store() {
            emitVStore(name.value);
        }

        @Override
        void stash() {
            emitDup();
        }

        void inc() {
            code.addInstruction(new Vinc(code.resolveLocal(name)));
        }

        void dec() {
            code.addInstruction(new Vdec(code.resolveLocal(name)));
        }
    }

    /** Обращение к элементу массива. */
    class AccessItem extends Item {

        final int pos;

        AccessItem(int pos) {
            this.pos = pos;
        }

        @Override
        Item load() {
            code.putPos(pos);
            emitALoad();
            return stackItem;
        }

        @Override
        void store() {
            emitAStore();
        }

        @Override
        void drop() {
            code.addInstruction(Pop2.INSTANCE);
        }

        @Override
        void duplicate() {
            emitDup2();
        }

        @Override
        void stash() {
            emitDupX2();
        }
    }

    /** Обращение к рантайм константе. */
    class ConstantItem extends Item {

        final int pos;
        final Name name;

        ConstantItem(int pos, Name name) {
            this.pos = pos;
            this.name = name;
        }

        @Override
        Item load() {
            code.putPos(pos);
            code.addInstruction(new Getconst(programLayout.tryFindConst(name), name));
            return stackItem;
        }
    }

    /** Присвоение. */
    class AssignItem extends Item {

        final int pos;
        final Item var;

        AssignItem(int pos, Item var) {
            this.pos = pos;
            this.var = var;
        }

        @Override
        Item load() {
            var.stash();
            code.putPos(pos);
            var.store();
            return stackItem;
        }

        @Override
        void drop() {
            code.putPos(pos);
            var.store();
        }

        @Override
        void stash() {
            var.stash();
        }
    }

    /** Условное разветвление. */
    class CondItem extends Item {

        final int opcodePC;
        final IntArrayList truejumps;
        final IntArrayList falsejumps;

        CondItem(int opcodePC) {
            this(opcodePC, new IntArrayList(), new IntArrayList());
        }

        CondItem(int opcodePC, IntArrayList truejumps, IntArrayList falsejumps) {
            this.opcodePC = opcodePC;
            this.truejumps = truejumps;
            this.falsejumps = falsejumps;

            falsejumps.add(0, opcodePC);
        }

        @Override
        Item load() {
            resolveTrueJumps();
            emitPushTrue();
            int skipPC = emitGoto();
            resolveFalseJumps();
            emitPushFalse();
            resolveJump(skipPC);
            return stackItem;
        }

        @Override
        void drop() {
            load();
            code.addInstruction(Pop.INSTANCE);
        }

        @Override
        CondItem toCond() { return this; }

        CondItem negate() {
            code.setInstruction(opcodePC, code.getJump(opcodePC).negate());
            CondItem condItem = new CondItem(opcodePC, falsejumps, truejumps);
            condItem.truejumps.removeInt(0);
            return condItem;
        }

        void resolveTrueJumps() {
            resolveTrueJumps(code.currentIP());
        }

        void resolveTrueJumps(int pc) {
            resolveChain(truejumps, pc);
        }

        void resolveFalseJumps() {
            resolveFalseJumps(code.currentIP());
        }

        void resolveFalseJumps(int pc) {
            resolveChain(falsejumps, pc);
        }
    }

    class FlowEnv {

        final FlowEnv parent;
        final boolean isSwitch;

        /** continue-прыжки, если isSwitch=true, то fallthrough */
        final IntArrayList contjumps = new IntArrayList();
        /** break-прыжки */
        final IntArrayList exitjumps = new IntArrayList();

        /** Указатель на инструкцию, где находится switch. */
        int switchStartPC;
        /** Индексы констант из ключей кейзов. Равно null когда isSwitch=false */
        final IntList caseLabelsConstantIndexes;
        /** Точка входа (IP) для каждого кейза. Равно null когда isSwitch=false */
        final IntList switchCaseOffsets;
        /** Указатель на точку входа в default-case */
        int switchDefaultOffset = -1;

        /**
         * Истинно, если в цикле присутствуют break. Нужно, чтобы определять вечные циклы
         * Истинно, если в switch присутствует break. Нужно, чтобы определять живой код после switch.
         */
        boolean interrupted = false;

        FlowEnv(FlowEnv parent, boolean isSwitch) {
            this.parent = parent;
            this.isSwitch = isSwitch;

            switchCaseOffsets = isSwitch ? new IntArrayList() : null;
            caseLabelsConstantIndexes = isSwitch ? new IntArrayList() : null;
        }

        void resolveCont() {
            resolveCont(code.currentIP());
        }

        void resolveCont(int pc) {
            resolveChain(contjumps, pc);
        }

        void resolveExit() {
            resolveExit(code.currentIP());
        }

        void resolveExit(int pc) {
            resolveChain(exitjumps, pc);
        }
    }

    void resolveChain(IntArrayList sequence, int pc) {
        for (int jumpPC : sequence) resolveJump(jumpPC, pc);
    }

    void resolveJump(int jumpPC) {
        resolveJump(jumpPC, code.currentIP());
    }

    void resolveJump(int jumpPC, int pc) {
        code.getJump(jumpPC).offset = pc - jumpPC;
    }

    boolean genBranch(Statement tree) {
        Assert.check(!(tree instanceof Expression));

        int savedstacktop = code.curStackTop();

        try {
            tree.accept(this);
            return code.alive;
        } finally {
            code.alive = true;
            assertStacktopEquality(savedstacktop);
        }
    }
}