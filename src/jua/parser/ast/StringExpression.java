package jua.parser.ast;

public class StringExpression extends Expression {

    public String value;

    public StringExpression(Position position, String value) {
        super(position);
        this.value = value;
    }

    @Override
    public boolean isLiteral() {
        return true;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitString(this);
    }
}
