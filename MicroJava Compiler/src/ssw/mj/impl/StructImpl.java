package ssw.mj.impl;

import ssw.mj.symtab.Struct;
import ssw.mj.symtab.Tab;

public final class StructImpl extends Struct {

	private StructImpl(Kind kind, StructImpl elemType) {
		super(kind, elemType);
	}

	public StructImpl(Kind kind) {
		super(kind);
	}

	public StructImpl(StructImpl elemType) {
		super(elemType);
	}

	// checks for different kinds of type compatibility - taken from the
	// exercise sheets

	@Override
	public boolean compatibleWith(StructImpl other) {
		return this.equals(other) || (this == Tab.nullType && other.isRefType())
				|| (other == Tab.nullType && this.isRefType());
	}

	@Override
	public boolean assignableTo(StructImpl dest) {
		return this.equals(dest) || (this == Tab.nullType && dest.isRefType())
				|| (this.kind == Kind.Arr && dest.kind == Kind.Arr
						&& dest.elemType == Tab.noType); // for function len()
	}

	boolean isRefType() {
		return kind == Kind.Class || kind == Kind.Arr;
	}

	private boolean equals(Struct other) {
		if (kind == Kind.Arr) {
			return other.kind == Kind.Arr && elemType.equals(other.elemType);
		} else {
			// must be same type node
			return this == other;
		}
	}
}
