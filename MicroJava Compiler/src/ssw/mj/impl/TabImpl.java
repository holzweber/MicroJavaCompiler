package ssw.mj.impl;

import ssw.mj.Parser;
import ssw.mj.symtab.Tab;

public final class TabImpl extends Tab {

    // TODO Exercise 4: implementation of symbol table

    /**
     * Set up "universe" (= predefined names).
     */
    public TabImpl(Parser p) {
        super(p);
    }
}
