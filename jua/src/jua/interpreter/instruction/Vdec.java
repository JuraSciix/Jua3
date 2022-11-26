package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;

public final class Vdec implements Instruction {

    private final int id;

    public Vdec(int id) {
        this.id = id;
    }

    @Override
    public int stackAdjustment() { return 0; }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("vdec");
        printer.printLocal(id);
    }

    @Override
    public void run(InterpreterState state) {
        state.stackVDec(id);
    }
}