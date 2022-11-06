package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;
import jua.runtime.heap.NullOperand;

public final class ConstNull implements Instruction {

    public static final ConstNull INSTANCE = new ConstNull();

    private ConstNull() { super(); }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("const_null");
    }

    @Override
    public int run(InterpreterState state) {
        state.constNull();
        return NEXT;
    }
}