package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;

// todo: rename to Load (vload -> load)
public final class Vload1 implements Instruction {

    @Override
    public int stackAdjustment() { return 1; }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("vload1");
    }

    @Override
    public void run(InterpreterState state) {
        state.stackVLoad(1);
    }
}