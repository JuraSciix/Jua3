package jua.interpreter;

import jua.interpreter.instruction.Instruction;
import jua.runtime.ValueType;
import jua.runtime.code.ConstantPool;
import jua.runtime.heap.MapHeap;
import jua.runtime.heap.Operand;
import jua.runtime.heap.StringHeap;

public final class InterpreterState {

    private final Instruction[] code;

    public final Address[] stack, locals;

    private final ConstantPool constantPool;

    // todo: This fields should be typed with short
    private int cp, sp, cpAdvance;

    private final InterpreterThread thread;

    // Trusting constructor.
    InterpreterState(Instruction[] code,
                     int maxStack,
                     int maxLocals,
                     ConstantPool constantPool,
                     InterpreterThread thread) {
        this.code = code;
        this.stack = Address.allocateMemory(maxStack, 0);
        this.locals = Address.allocateMemory(maxLocals, 0);
        this.constantPool = constantPool;
        this.thread = thread;
    }

    public Instruction currentInstruction() {
        return code[cp];
    }

    public void runDiscretely() {
        currentInstruction().run(this);
    }

    public InterpreterThread thread() {
        return thread;
    }

    public Instruction[] code() {
        return code;
    }

    public int cp() {
        return cp & 0xffff;
    }

    public void set_cp(int cp) {
        this.cp = cp;
    }

    public int sp() {
        return sp & 0xffff;
    }

    public void set_sp(int sp) {
        this.sp = sp;
    }

    public int cp_advance() {
        return cpAdvance & 0xffff;
    }

    public void set_cp_advance(int cpAdvance) {
        this.cpAdvance = cpAdvance;
    }

    public void next() {
        cp++;
    }

    public void offset(int offset) {
        cp += offset;
    }

    @Deprecated
    public byte getMsg() {
        return thread.msg();
    }

    public void setMsg(byte msg) {
        thread.set_msg(msg);
    }

    public ConstantPool constant_pool() {
        return constantPool;
    }

    public void advance() {
        cp += cpAdvance;
        cpAdvance = 0;
    }

    public void getconst(int id) {
        thread.environment().getConstant(id).writeToAddress(peekStack());
        sp++;
    }

    @Deprecated
    public Address getConstantById(int id) {
        throw new UnsupportedOperationException();
    }

    public void pushStack(long value) {
        top().set(value);
    }

    @Deprecated
    public void pushStack(Operand value) {
        value.writeToAddress(top());
    }

    public void pushStack(Address address) {
        stack[sp++].set(address);
    }

    public Address popStack() {
        return stack[--sp];
    }

    public Address top() {
        return stack[sp++];
    }

    public long popInt() {
        return getInt(popStack());
    }

    public Address peekStack() {
        return stack[sp - 1];
    }

    public long getInt(Address a) {
        return a.longVal();
    }

    @Deprecated
    public void cleanupStack() {
        for (int i = stack.length - 1; i >= sp; i--)
            stack[i].reset();
    }

    @Deprecated
    public void store(int index, Operand value) {
        value.writeToAddress(locals[index]);
    }

    public void store(int index, Address value) {
        locals[index].set(value);
    }

    public void load(int index) {
        if (locals[index] == null) {
            thread.error("accessing an undefined variable '" +
                    thread.currentFrame().owningFunction().codeSegment().localNameTable().nameOf(index) + "'.");
        }
        peekStack().set(locals[index]);
        sp++;
    }

    /* ОПЕРАЦИИ НА СТЕКЕ */

    public void push(short value) {
        pushStack(value);
        next();
    }

    public void dup() {
        Address peek = peekStack();
        top().set(peek);
        next();

    }

    public void dup2() {
        Address a = popStack();
        Address b = popStack();
        pushStack(b);
        pushStack(a);
        pushStack(b);
        pushStack(a);
        next();
    }

    public void dup1_x1() {
        stack[sp].set(stack[sp - 1]);
        stack[sp - 1].set(stack[sp - 2]);
        stack[sp - 2].set(stack[sp]);
        sp++;
        next();
    }

    public void dup1_x2() {
        stack[sp].set(stack[sp - 1]);
        stack[sp - 1].set(stack[sp - 2]);
        stack[sp - 2].set(stack[sp - 3]);
        stack[sp - 3].set(stack[sp]);
        sp++;
        next();
    }

    public void dup2_x1() {
        stack[sp + 1].set(stack[sp - 1]);
        stack[sp].set(stack[sp - 2]);
        stack[sp - 2].set(stack[sp - 3]);
        stack[sp - 3].set(stack[sp + 1]);
        stack[sp - 4].set(stack[sp]);
        sp += 2;
        next();
    }

    public void dup2_x2() {
        stack[sp + 1].set(stack[sp - 1]);
        stack[sp].set(stack[sp - 2]);
        stack[sp - 1].set(stack[sp - 3]);
        stack[sp - 2].set(stack[sp - 4]);
        stack[sp - 4].set(stack[sp + 1]);
        stack[sp - 5].set(stack[sp]);
        sp += 2;
        next();
    }

    public void stackAdd() {
        if (lhs().add(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    private Address lhs() {
        return stack[sp - 2];
    }

    private Address rhs() {
        return stack[sp - 1];
    }

    public void stackAnd() {
        if (lhs().and(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    @Deprecated
    public void stackClone() {
        throw new UnsupportedOperationException();
    }

    public void constFalse() {
        top().set(false);
        next();
    }

    public void constNull() {
        top().setNull();
        next();
    }

    public void constTrue() {
        top().set(true);
        next();
    }

    public void stackInc() {
        if (peekStack().inc(peekStack())) {
            next();
        }
    }

    public void stackDec() {
        if (peekStack().dec(peekStack())) {
            next();
        }
    }

    public void stackDiv() {
        if (lhs().div(rhs(), lhs())) {
            sp--;
            next();
        }
    }
    
    public void ifeq(int offset) {
        if (stackCmpeq()) {
            next();
        } else {
            offset(offset);
        }
    }

    public void ifne(int offset) {
        if (stackCmpne()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifgt(int offset) {
        if (stackCmpgt()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifge(int offset) {
        if (stackCmpge()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void iflt(int offset) {
        if (stackCmplt()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifle(int offset) {
        if (stackCmple()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifconsteq(short value, int offset) {
        if (peekStack().compareShort(value, 1) == 0) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifconstne(short value, int offset) {
        if (peekStack().compareShort(value, 0) != 0) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifconstgt(short value, int offset) {
        if (peekStack().compareShort(value, 0) > 0) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifconstge(short value, int offset) {
        if (peekStack().compareShort(value, -1) >= 0) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifconstlt(short value, int offset) {
        if (peekStack().compareShort(value, 0) < 0) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifconstle(short value, int offset) {
        if (peekStack().compareShort(value, 1) <= 0) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifnull(int offset) {
        if (popStack().isNull()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifnonnull(int offset) {
        if (!popStack().isNull()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifz(int offset) {
        if (!popStack().booleanVal()) {
            offset(offset);
        } else {
            next();
        }
    }

    public void ifnz(int offset) {
        if (!popStack().booleanVal()) {
            offset(offset);
        } else {
            next();
        }
    }

    public boolean stackCmpeq() {
        int cmp = lhs().compare(rhs(), 1);
        sp -= 2;
        return cmp == 0;
    }
    
    public boolean stackCmpne() {
        int cmp = lhs().compare(rhs(), 0);
        sp -= 2;
        return cmp != 0;
    }

    public boolean stackCmpge() {
        int cmp = lhs().compare(rhs(), -1);
        sp -= 2;
        return cmp >= 0;
    }

    public boolean stackCmpgt() {
        int cmp = lhs().compare(rhs(), -1);
        sp -= 2;
        return cmp > 0;
    }

    public boolean stackCmple() {
        int cmp = lhs().compare(rhs(), 1);
        sp -= 2;
        return cmp <= 0;
    }

    public boolean stackCmplt() {
        int cmp = lhs().compare(rhs(), 1);
        sp -= 2;
        return cmp < 0;
    }

    public void stackLength() {
        switch (peekStack().typeCode()) {
            case ValueType.STRING:
                pushStack(popStack().stringVal().length());
                break;
            case ValueType.MAP:
                pushStack(popStack().mapValue().size());
                break;
            default:
                thread.error("Invalid length");
                return;
        }
        next();
    }

    public void stackMul() {
        if (lhs().mul(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackNeg() {
        if (peekStack().neg(peekStack())) {
            next();
        }
    }

    public void stackNewArray() {
        top().set(new MapHeap());
        next();
    }

    public void stackNot() {
        if (peekStack().not(peekStack())) {
            next();
        }
    }

    public void stackNanosTime() {
        pushStack(System.nanoTime());
        next();
    }

    public void stackOr() {
        if (lhs().or(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackPos() {
        peekStack().pos(peekStack());
        next();
    }

    public void stackRem() {
        if (lhs().rem(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackShl() {
        if (lhs().shl(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackShr() {
        if (lhs().shr(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackSub() {
        if (lhs().sub(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackXor() {
        if (lhs().xor(rhs(), lhs())) {
            sp--;
            next();
        }
    }

    public void stackGettype() {
        stack[sp - 1].set(new StringHeap(stack[sp - 1].typeName()));
        next();
    }

    public void stackAload() {
        if (stack[sp - 2].testType(ValueType.MAP)) {
            Address val = stack[sp - 2].mapValue().get(stack[sp - 1]);
            if (val == null || val.typeCode() == ValueType.UNDEFINED) {
                stack[sp - 2].setNull();
            } else {
                stack[sp - 2].set(val);
            }
            sp--;
            next();
        }
    }

    public void stackAstore() {
         if (stack[sp - 3].testType(ValueType.MAP)) {
             stack[sp - 3].mapValue().put(stack[sp - 2], stack[sp - 1]);
             sp -= 3;
             next();
         }
    }

    public void stackLDC(int constantIndex) {
        constant_pool().at(constantIndex).writeToAddress(top());
        next();
    }

    /* ОПЕРАЦИИ С ПЕРЕМЕННЫМИ */

    public void stackVDec(int id) {
        if (testLocal(id)) {
            locals[id].dec(locals[id]);
            next();
        }
    }

    public void stackVInc(int id) {
        if (testLocal(id)) {
            locals[id].inc(locals[id]);
            next();
        }
    }

    public void stackVLoad(int id) {
        if (testLocal(id)) {
            pushStack(locals[id]);
            next();
        }
    }

    public void stackVStore(int id) {
        locals[id].set(popStack());
        next();
    }

    private boolean testLocal(int id) {
        if (locals[id].typeCode() == ValueType.UNDEFINED) {
            thread.error("Access to undefined variable: " +
                    thread.currentFrame().owningFunction().codeSegment().localNameTable().nameOf(id));
            return false;
        }
        return true;
    }
}
