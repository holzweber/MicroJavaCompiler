package ssw.mj.codegen;

/**
 * MircoJava Jump Label. Forward jumps use labels which are still undefined.
 * Such jump instructions are linked in the code. A fixup is made as soon as the
 * label becomes defined. Backward jumps use labels which are already defined.
 * Such jump instructions are already generated with the definite label address.
 */
public abstract class Label {
    /**
     * The code buffer this Label belongs to.
     */
    protected final Code code;

    /**
     * Jump destination address.
     */
    protected int adr;

    public Label(Code code) {
        this.code = code;
    }

    abstract public void put();

    abstract public void here();

    public int getAdr() {
        return adr;
    }

}
