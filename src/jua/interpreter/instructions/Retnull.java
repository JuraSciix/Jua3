package jua.interpreter.instructions;

import jua.interpreter.InterpreterRuntime;
import jua.interpreter.Trap;
import jua.runtime.NullOperand;
import jua.compiler.CodePrinter;

public enum Retnull implements Instruction {

    INSTANCE;

    @Override
    public void print(CodePrinter printer) {
        printer.printName("retnull");
    }

    @Override
    public int run(InterpreterRuntime env) {
        env.exitCall(NullOperand.NULL);
        if (env.getFrame() != null) env.getFrame().incPC();
        Trap.bti();
        return 0; // unreachable
    }
}