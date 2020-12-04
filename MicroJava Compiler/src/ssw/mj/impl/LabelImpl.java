package ssw.mj.impl;

import java.util.ArrayList;
import java.util.List;

import ssw.mj.codegen.Code;
import ssw.mj.codegen.Label;

public final class LabelImpl extends Label {
	private List<Integer> fixupList; // fixup adresses

	public LabelImpl(Code code) {
		super(code);
		adr = -1; // startvalue
		fixupList = new ArrayList<>();
	}

	private boolean isDefined() {
		return adr >= 0;
	}

	/**
	 * Generates code for a jump to this label.
	 */
	@Override
	public void put() {
		if (isDefined()) {
			code.put2(adr - (code.pc - 1));
		} else {
			fixupList.add(code.pc);
			// insert place holder
			code.put2(0);
		}
	}

	/**
	 * Defines <code>this</code> label to be at the current pc position
	 */
	@Override
	public void here() {
		if (isDefined()) {
			// should never happen
			throw new IllegalStateException("label defined twice");
		}
		for (int pos : fixupList) {
			code.put2(pos, code.pc - (pos - 1));
		}
		fixupList = null;
		adr = code.pc;
	}
}
