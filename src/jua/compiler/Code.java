package jua.compiler;

import jua.interpreter.lang.Operand;
import jua.interpreter.lang.OperandFunction;
import jua.interpreter.states.JumpState;
import jua.interpreter.states.State;

import java.util.*;

import static jua.interpreter.Program.Builder;

public class Code {

    private static class Chain {

        static final int UNSATISFIED_DESTINATION = Integer.MIN_VALUE;

        final Map<Integer, JumpState> states = new HashMap<>();

        int destination = UNSATISFIED_DESTINATION;
    }

    private static class Scope {

        boolean alive = true;
    }

    private static class Context {

        final String filename;

        final List<State> states = new ArrayList<>();

        final List<Integer> lines = new ArrayList<>();

        final List<String> locals = new ArrayList<>();

        final Map<Integer, Chain> chainMap = new HashMap<>();

        final Stack<Scope> scopes = new Stack<>();

        int stackTop = 0;

        int stackSize = 0;

        private Context(String filename) {
            this.filename = filename;
        }
    }

    private final Stack<Context> contexts = new Stack<>();

    private final Map<Object, Operand> internMap = new HashMap<>();

    public boolean empty() {
        return contexts.isEmpty();
    }

    public void enterContext(String filename) {
        contexts.add(new Context(filename));
    }

    public void exitContext() {
        Context context = contexts.pop();
        context.states.clear();
        context.lines.clear();
        context.locals.clear();
        context.chainMap.clear();
        context.scopes.clear();
    }

    public void enterScope() {
        currentContext().scopes.add(new Scope());
    }

    public void exitScope() {
        currentContext().scopes.pop();
    }

    public void deathScope() {
        currentScope().alive = false;
    }

    public boolean scopeAlive() {
        return currentScope().alive;
    }

    public void addState(State state) {
        addState(0, state);
    }

    public void addState(int line, State state) {
        if (isAlive()) {
            currentContext().lines.add(line);
            currentContext().states.add(state);
        }
    }

    public int statesCount() {
        return currentContext().states.size();
    }

    public int createFlow() {
        if (!isAlive()) {
            return -1;
        }
        Map<Integer, Chain> map = currentChainMap();
        int id = map.size();
        map.put(id, new Chain());
        return id;
    }

    public void addFlow(int id, JumpState state) {
        addFlow(id, 0, state);
    }

    public void addFlow(int id, int line, JumpState state) {
        if (isAlive()) {
            Chain chain = currentChainMap().get(id);
            int bci = currentContext().states.size();
            if (chain.destination != Chain.UNSATISFIED_DESTINATION) {
                state.setDestination(chain.destination - bci);
            }
            chain.states.put(bci, state);
            addState(line, state);
        }
    }

    public void resolveFlow(int id) {
        if (isAlive()) {
            Chain chain = currentChainMap().get(id);
            int result = currentContext().states.size();
            chain.states.forEach((sBci, s) -> s.setDestination(result - sBci));
            chain.destination = result;
        }
    }

    public int getLocal(String name) {
        if (!isAlive()) {
            return -1;
        }
        List<String> locals = currentLocals();
        if (!locals.contains(name)) {
            locals.add(name);
        }
        return locals.indexOf(name);
    }

    public void incStack() {
        incStack(1);
    }

    public void incStack(int n) {
        if (isAlive()) {
            currentContext().stackSize += n;
            updateCurrentStack();
        }
    }

    public void decStack() {
        decStack(1);
    }

    public void decStack(int n) {
        if (isAlive()) {
            currentContext().stackSize -= n;
        }
    }

    public Builder getBuilder() {
        Context context = currentContext();
        return new Builder(context.filename,
                context.states.toArray(new State[0]),
                context.lines.stream().mapToInt(i -> i).toArray(),
                context.stackTop,
                context.locals.size());
    }

    public <T> Operand intern(T value, OperandFunction<T> supplier) {
        Operand operand = internMap.get(value);

        if (operand == null) {
            internMap.put(value, operand = supplier.apply(value));
        }
        return operand;
    }

    private boolean isAlive() {
        return currentScope().alive;
    }

    private List<String> currentLocals() {
        return currentContext().locals;
    }

    private Map<Integer, Chain> currentChainMap() {
        return currentContext().chainMap;
    }

    private Scope currentScope() {
        return currentContext().scopes.peek();
    }

    private void updateCurrentStack() {
        Context context = currentContext();
        if (context.stackSize > context.stackTop) {
            context.stackTop = context.stackSize;
        }
    }

    private Context currentContext() {
        return contexts.peek();
    }
}
