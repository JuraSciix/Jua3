package jua.interpreter.states;

import jua.interpreter.Environment;
import jua.interpreter.InterpreterError;
import jua.interpreter.lang.Function;
import jua.tools.CodePrinter;

public class FunctionCall implements State {

    private final String name;

    private final int argc;

    public FunctionCall(String name, int argc) {
        this.name = name;
        this.argc = argc;
    }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("invoke");
        printer.printOperand(name);
        printer.printOperand(argc);
    }

    @Override
    public void run(Environment env) {
        Function fn = env.getFunctionByName(name);

        if (fn == null) {
            throw InterpreterError.functionNotExists(name);
        }
        fn.call(env, name, argc);
    }
}
