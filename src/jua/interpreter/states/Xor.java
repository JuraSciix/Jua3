package jua.interpreter.states;

import jua.interpreter.Environment;
import jua.interpreter.InterpreterError;
import jua.interpreter.lang.Operand;
import jua.tools.CodePrinter;

public class Xor implements State {

    public static final Xor XOR = new Xor();

    private Xor() {
        super();
    }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("xor");
    }

    @Override
    public void run(Environment env) {
        Operand rhs = env.popStack();
        Operand lhs = env.popStack();

        if (lhs.isInt() && rhs.isInt()) {
            env.pushStack(lhs.intValue() ^ rhs.intValue());
        } else if (lhs.isBoolean() && rhs.isBoolean()) {
            env.pushStack(lhs.booleanValue() ^ rhs.booleanValue());
        } else {
            throw InterpreterError.binaryApplication("^", lhs.type(), rhs.type());
        }
        env.nextPC();
    }
}
