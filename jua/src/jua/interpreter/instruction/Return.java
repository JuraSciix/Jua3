package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;

public final class Return implements Instruction {

    public static final Return RETURN = new Return();

    @Override
    public int stackAdjustment() { return -1; }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("return");
    }

    @Override
    public int run(InterpreterState state) {
        state.thread().set_returnee(state.popStack());
        return UNREACHABLE;
    }
}