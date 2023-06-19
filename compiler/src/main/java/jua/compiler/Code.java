package jua.compiler;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2IntRBTreeMap;
import jua.interpreter.Address;
import jua.interpreter.AddressUtils;
import jua.interpreter.instruction.Binaryswitch;
import jua.interpreter.instruction.Instruction;
import jua.runtime.code.CodeData;
import jua.runtime.code.ConstantPool;
import jua.runtime.code.LineNumberTable;
import jua.utils.Assert;

import java.util.ArrayList;

public final class Code {

    static class Chain {

        final int pc;

        final Chain next;

        final int tos;

        Chain(int pc, Chain next, int tos) {
            Assert.check(next == null || next.tos == tos);
            this.pc = pc;
            this.next = next;
            this.tos = tos;
        }
    }

    static Chain mergeChains(Chain lhs, Chain rhs) {
        if (lhs == null) return rhs;
        if (rhs == null) return lhs;
        // Рекурсивная сортировка
        if (lhs.pc <= rhs.pc)
            return new Chain(lhs.pc, mergeChains(lhs.next, rhs), lhs.tos);
        else  // lhs.pc > rhs.pc
            return new Chain(rhs.pc, mergeChains(rhs.next, lhs), rhs.tos);
    }

    private final ArrayList<Instruction> instructions = new ArrayList<>();

    private final Short2IntRBTreeMap lineTable = new Short2IntRBTreeMap();

    private final Object2IntLinkedOpenHashMap<String> localNames = new Object2IntLinkedOpenHashMap<>();

    private final ConstantPoolWriter constantPoolWriter = new ConstantPoolWriter();


    private int nlocals = 0;

    /** Top of stack. */
    private int tos = 0;

    /** Top of stack limit. */
    private int limTos = 0;

    /** Current line number. */
    private int cLineNum = 0;

    private boolean alive = true;

    public final ProgramScope programScope;
    public final Gen gen;

    private LineMap lineMap;

    public Code(ProgramScope programScope, Source source) {
        this.programScope = programScope;
        this.lineMap = source.getLineMap();
        gen = new Gen();
        gen.code = this;
        gen.source = source;
    }

    public Chain branch(Instruction instr) {
        return new Chain(addInstruction(instr), null, tos());
    }

    public Chain branch(Instruction instr, int tos) {
        return new Chain(addInstruction(instr), null, tos);
    }

    public int lineNum() {
        return cLineNum;
    }

    public Instruction get(int pc) {
        return instructions.get(pc);
    }

    public int pc() {
        return this.instructions.size();
    }

    public void setInstruction(int cp, Instruction instruction) {
        instructions.set(cp, instruction);
    }

    public int addInstruction(Instruction instr) {
        int pc = instructions.size();
        if (isAlive()) {
            instructions.add(instr);
            adjustStack(instr.stackAdjustment());
        }
        return pc;
    }

    private int addInstruction0(Instruction instruction) {
        int pc = instructions.size();
        if (isAlive()) {
            instructions.add(instruction);
            adjustStack(instruction.stackAdjustment());
        }
        return pc;
    }

    public void position(Tree tree) {
        if (tree != null) {
            putPos(tree.pos);
        }
    }

    public void putPos(int pos) {
        int line = lineMap.getLineNumber(pos);

        // todo: line always have int type
        if (line > 0 && line < (1<<16) && line != this.cLineNum) {
            this.lineTable.put((short) pc(), (short) line);
            this.cLineNum = line;
        }
    }

    private void adjustStack(int stackAdjustment) {
        tos(tos + stackAdjustment);
        Assert.check(tos >= 0, () ->
                String.format(
                        "negative tos: PC=%d, LINE=%d",
                        pc(), lineNum()));
    }

    public int tos() {
        return tos;
    }

    public void tos(int _tos) {
        tos = _tos;
        limTos = Math.max(limTos, _tos);
    }

    public void dead() {
        alive = false;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive() {
        alive = true;
    }

    public boolean localExists(String name) {
        return this.localNames.containsKey(name);
    }

    public int resolveLocal(Name name) {
        return resolveLocal(name.toString());
    }

    public int resolveLocal(String name) {
//        if (!isAlive()) return -1;
        if (!localExists(name)) {
            int newIndex = this.nlocals++;
            this.localNames.put(name, newIndex);
            return newIndex;
        } else {
            return this.localNames.getInt(name);
        }
    }

    public ConstantPoolWriter constantPoolWriter() {
        return constantPoolWriter;
    }

    public CodeData buildCodeSegment() {
        ConstantPool cp = buildConstantPool();
        return new CodeData(
                this.limTos,
                this.nlocals,
                buildCode(cp),
                cp,
                buildLineNumberTable());
    }

    private static final Instruction[] EMPTY_INSTRUCTIONS = new Instruction[0];
    private Instruction[] buildCode(ConstantPool cp) {
        Instruction[] instructions = this.instructions.toArray(EMPTY_INSTRUCTIONS);
        for (Instruction instruction : instructions) {
            if (instruction.getClass() == Binaryswitch.class) {
                Binaryswitch switch_ = (Binaryswitch) instruction;
                switch_.sort(cp);
            }
        }
        return instructions;
    }

    private LineNumberTable buildLineNumberTable() {
        return new LineNumberTable(
                lineTable.keySet().toShortArray(),
                lineTable.values().toIntArray()
        );
    }

    private ConstantPool buildConstantPool() {
        Object[] values = constantPoolWriter().toArray();
        Address[] addresses = AddressUtils.allocateMemory(values.length, 0);
        for (int i = 0; i < values.length; i++) {
            AddressUtils.assignObject(addresses[i], values[i]);
        }
        return new ConstantPool(addresses);
    }

    public void resolve(Chain chain) {
        resolve(chain, pc());
    }

    public void resolve(Chain chain, int destPC) {
        if (chain == null) return;
        assertTosEquality(chain.tos);
        do {
            get(chain.pc).elsePoint(destPC - chain.pc);
            chain = chain.next;
        } while (chain != null);
    }

    public void assertTosEquality(int tos) {
        Assert.check(tos() == tos, () ->
                String.format(
                        "TOS violation: BEFORE=%d, AFTER=%d, PC=%d, LINE=%d",
                        tos, tos(), pc(), lineNum()));
    }
}