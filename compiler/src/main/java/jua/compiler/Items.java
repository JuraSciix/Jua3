package jua.compiler;

import jua.compiler.Code.Chain;
import jua.compiler.Tree.Tag;
import jua.interpreter.instruction.*;

import java.util.Objects;

import static jua.compiler.InstructionFactory.*;
import static jua.compiler.InstructionUtils.arrayIncreaseFromTag;
import static jua.compiler.InstructionUtils.increaseFromTag;

public class Items {

    private final Code code;

    private final StackItem stackItem = new StackItem();

    public Items(Code code) {
        this.code = Objects.requireNonNull(code);
    }

    abstract class Item {

        Tree tree;

        @SuppressWarnings("unchecked")
        <T extends Item>T treeify(Tree tree) {
            this.tree = tree;
            return (T)this;
        }

        Item load() {
            throw new AssertionError(this);
        }

        void drop() {
            load();
            code.addInstruction(pop);
        }

        void duplicate() {
            load().duplicate();
        }

        void stash() {
            throw new AssertionError(this);
        }

        void store() {
            throw new AssertionError(this);
        }

        CondItem asCond() {
            load();
            return new CondItem(new Ifnz());
        }

        CondItem nonNullCheck() {
            load();
            return new CondItem(new Ifnonnull());
        }

        CondItem presentCheck() {
            throw new AssertionError(this);
        }

        int constantIndex() {
            throw new AssertionError(this);
        }

        SafeItem asSafe() {
            return new SafeItem(this);
        }

        Item increase(Tag increaseTag) {
            throw new AssertionError(this);
        }

        Item coalesce(Item value, Chain whenItemPresentChain) {
            throw new AssertionError(this);
        }
    }

    /**
     * Динамическое значение.
     */
    class StackItem extends Item {

        @Override
        Item load() {
            return this;
        }

        @Override
        void drop() {
            code.addInstruction(pop);
        }

        @Override
        void duplicate() {
            code.addInstruction(dup);
        }

        @Override
        void stash() {
            code.addInstruction(dup);
        }
    }

    /**
     * Литерал.
     */
    class LiteralItem extends Item {

        final Types.Type type;

        LiteralItem(Types.Type type) {
            this.type = type;
        }

        @Override
        Item load() {
            code.position(tree);
            if (type.isBoolean()) {
                if (type.booleanValue()) {
                    code.addInstruction(const_true);
                } else {
                    code.addInstruction(const_false);
                }
            } else if (type.isNull()) {
                code.addInstruction(const_null);
            } else {
                if (type.isLong()) {
                    long lv = type.longValue();
                    if (-1L <= lv && lv <= 1L) {
                        code.addInstruction(const_ix[(int) lv + 1]);
                        return makeStack();
                    }
                }
                code.addInstruction(new Push(type.resolvePoolConstant(code)));
            }
            return makeStack();
        }

        @Override
        void drop() { /* no-op */ }

        @Override
        int constantIndex() {
            return type.resolvePoolConstant(code);
        }
    }

    /**
     * Обращение к локальной переменной.
     */
    class LocalItem extends Item {

        final int index;
        LocalItem(int index) {
            this.index = index;
        }

        @Override
        void duplicate() {
            // none
        }

        @Override
        Item load() {
            code.position(tree);
            code.addInstruction((index <= 2) ? load_x[index] : new Load(index));
            return makeStack();
        }

        @Override
        void drop() {
            // Ранее переменная загружалась для того,
            // чтобы убедиться в её существовании во
            // времени выполнения. Сейчас переменная
            // декларируется в коде явно, поэтому
            // эта механика неактуальна.
//            load();
//            code.addInstruction(pop);
        }

        @Override
        void store() {
            code.position(tree);
            code.addInstruction((index <= 2) ? store_x[index] : new Store(index));
        }

        @Override
        CondItem presentCheck() {
            return nonNullCheck();
        }

        @Override
        Item increase(Tag increaseTag) {
            return new LocalIncreaseItem(this, increaseTag);
        }

        @Override
        void stash() {
            code.addInstruction(dup);
        }

        @Override
        Item coalesce(Item value, Chain whenItemPresentChain) {
            return new LocalCoalesceItem(this, value, whenItemPresentChain);
        }
    }

    class LocalIncreaseItem extends Item {
        final LocalItem item;
        final Tag increaseTag;

        LocalIncreaseItem(LocalItem item, Tag increaseTag) {
            this.item = item;
            this.increaseTag = increaseTag;
        }

        @Override
        Item load() {
            if (increaseTag == Tag.PREINC || increaseTag == Tag.PREDEC) {
                drop();
                item.load();
            } else {
                item.load();
                drop();
            }
            return makeStack();
        }

        @Override
        void drop() {
            code.addInstruction(increaseFromTag(increaseTag, item.index));
        }
    }

    class LocalCoalesceItem extends Item {
        final LocalItem item;
        final Item value;
        final Chain whenItemPresentChain;

        LocalCoalesceItem(LocalItem item, Item value, Chain whenItemPresentChain) {
            this.item = item;
            this.value = value;
            this.whenItemPresentChain = whenItemPresentChain;
        }

        @Override
        Item load() {
            Item result = value.load();
            result.duplicate();
            item.store();
            Chain exitChain = code.branch(new Goto());
            code.resolve(whenItemPresentChain);
            item.load();
            code.resolve(exitChain);
            return result;
        }

        @Override
        void drop() {
            value.load();
            item.store();
            code.resolve(whenItemPresentChain);
        }

        // todo: Оптимизация выражения (a ??= b) ?? c
//        @Override
//        SafeItem asSafe() {
//            SafeItem safeItem = new SafeItem(this);
//            safeItem.exitChain = whenItemPresentChain;
//            return safeItem;
//        }
    }

    /**
     * Обращение к элементу массива.
     */
    class AccessItem extends Item {

        @Override
        Item load() {
            code.position(tree);
            code.addInstruction(aload);
            return makeStack();
        }

        @Override
        void drop() {
            code.addInstruction(pop2);
        }

        @Override
        void store() {
            code.position(tree);
            code.addInstruction(astore);
        }

        @Override
        CondItem presentCheck() {
            return new CondItem(new IfPresent());
        }

        @Override
        Item increase(Tag increaseTag) {
            return new AccessIncreaseItem(this, increaseTag);
        }

        @Override
        Item coalesce(Item value, Chain whenItemPresentChain) {
            return new AccessCoalesceItem(this, value, whenItemPresentChain);
        }

        @Override
        void duplicate() {
            code.addInstruction(dup2);
        }

        @Override
        void stash() {
            code.addInstruction(dup_x2);
        }
    }

    class AccessIncreaseItem extends Item {
        final AccessItem item;
        final Tag increaseTag;

        AccessIncreaseItem(AccessItem item, Tag increaseTag) {
            this.item = item;
            this.increaseTag = increaseTag;
        }

        @Override
        Item load() {
            // adec/ainc оставляют после себя старое значение.
            if (increaseTag == Tag.PREINC || increaseTag == Tag.PREDEC) {
                item.duplicate();
                drop();
                item.load();
            } else {
                code.addInstruction(arrayIncreaseFromTag(increaseTag));
            }
            return makeStack();
        }

        @Override
        void drop() {
            code.addInstruction(arrayIncreaseFromTag(increaseTag));
            code.addInstruction(pop);
        }
    }

    class AccessCoalesceItem extends Item {
        final AccessItem item;
        final Item value;
        final Chain whenItemPresentChain;

        AccessCoalesceItem(AccessItem item, Item value, Chain whenItemPresentChain) {
            this.item = item;
            this.value = value;
            this.whenItemPresentChain = whenItemPresentChain;
        }

        @Override
        Item load() {
            value.load();
            item.stash();
            item.store();
            Chain exitChain = code.branch(new Goto());
            code.resolve(whenItemPresentChain);
            item.load();
            code.resolve(exitChain);
            return makeStack();
        }

        @Override
        void drop() {
            value.load();
            item.store();
            Chain exitChain = code.branch(new Goto());
            code.resolve(whenItemPresentChain);
            item.drop();
            code.resolve(exitChain);
        }
    }

    /**
     * Присвоение.
     */
    class AssignItem extends Item {

        final Item var;

        AssignItem(Item var) {
            this.var = var;
        }

        @Override
        Item load() {
            var.stash();
            var.store();
            return makeStack();
        }

        @Override
        void drop() {
            var.store();
        }

        @Override
        void stash() {
            var.stash();
        }
    }

    class SafeItem extends Item {

        final Item child;

        Chain whenNullChain, whenNonNullChain;


        SafeItem(Item child) {
            this.child = child;
        }

        @Override
        Item load() {
            child.load();
            Chain avoidNullinChain = code.branch(new Goto());
            code.resolve(whenNullChain);
            code.addInstruction(pop);
            code.addInstruction(const_null);
            code.resolve(whenNonNullChain);
            code.resolve(avoidNullinChain);
            return makeStack();
        }

        @Override
        void stash() {
            child.stash();
        }

        @Override
        CondItem presentCheck() {
            return new CondItem(new IfPresent());
        }

        @Override
        SafeItem asSafe() {
            return this;
        }
    }

    /**
     * Условное разветвление.
     */
    class CondItem extends Item {

        final Instruction opcode;
        Chain thenChain;
        Chain elseChain;

        CondItem(Instruction opcode) {
            this(opcode, null, null);
        }

        CondItem(Instruction opcode, Chain thenChain, Chain elseChain) {
            this.opcode = opcode;
            this.thenChain = thenChain;
            this.elseChain = elseChain;
        }

        @Override
        Item load() {
            Chain falseJumps = elseJumps();
            code.resolve(thenChain);
            code.addInstruction(const_true);
            Chain trueChain = code.branch(new Goto());
            code.resolve(falseJumps);
            code.addInstruction(const_false);
            code.resolve(trueChain);
            return makeStack();
        }

        @Override
        CondItem asCond() {
            return this;
        }

        CondItem negate() {
            return new CondItem(opcode.negated(), elseChain, thenChain).treeify(tree);
        }

        Chain thenJumps() {
            code.position(tree);
            return Code.mergeChains(thenChain, code.branch(opcode));
        }

        Chain elseJumps() {
            code.position(tree);
            return Code.mergeChains(elseChain, code.branch(opcode.negated()));
        }
    }

    StackItem makeStack() {
        return stackItem;
    }

    LiteralItem makeLiteral(Types.Type type) {
        return new LiteralItem(type);
    }

    LocalItem makeLocal(int index) {
        return new LocalItem(index);
    }

    AccessItem makeAccess() {
        return new AccessItem();
    }

    AssignItem makeAssign(Item var) {
        return new AssignItem(var);
    }

    SafeItem makeNullSafe(Item child) {
        return new SafeItem(child);
    }

    CondItem makeCond(Instruction opcode) {
        return new CondItem(opcode);
    }

    CondItem makeCond(Instruction opcode, Chain truejumps, Chain falsejumps) {
        return new CondItem(opcode, truejumps, falsejumps);
    }
}
