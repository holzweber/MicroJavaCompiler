package ssw.mj.impl;

import ssw.mj.Errors;
import ssw.mj.Parser;
import ssw.mj.codegen.Code;
import ssw.mj.codegen.Operand;
import ssw.mj.symtab.Tab;

public final class CodeImpl extends Code {

	public CodeImpl(Parser p) {
		super(p);
	}

	/**
	 * Assigns Operand y to Operand x here an adaption is made, if operand y is
	 * null, we just want to store the x variable, without loading y
	 * 
	 * @param x
	 * @param y
	 */
	void assign(Operand x, Operand y) {
		load(y);
		assign(x); // call assign operation for single operand
	}

	/**
	 * for pplus mminus, ... only one operand is needed
	 * 
	 * @param x
	 */
	void assign(Operand x) {
		switch (x.kind) {
		case Local:
			switch (x.adr) {
			case 0:
				put(OpCode.store_0);
				break;
			case 1:
				put(OpCode.store_1);
				break;
			case 2:
				put(OpCode.store_2);
				break;
			case 3:
				put(OpCode.store_3);
				break;
			default:
				put(OpCode.store);
				put(x.adr);
				break;
			}
			break;
		case Static:
			put(OpCode.putstatic);
			put2(x.adr);
			break;
		case Fld:
			put(OpCode.putfield);
			put2(x.adr);
			break;
		case Elem:
			if (x.type == Tab.charType) {
				put(OpCode.bastore);
			} else {
				put(OpCode.astore);
			}
			break;
		default:
			parser.error(Errors.Message.NO_VAR);
		}
	}

	/**
	 * Method which loads operand and produces needed code taken from the
	 * ex-presentation
	 * 
	 * @param x
	 */
	public void load(Operand x) {
		switch (x.kind) {
		case Con:
			loadConst(x.val);
			break;
		case Local:
			switch (x.adr) {
			case 0:
				put(OpCode.load_0);
				break;
			case 1:
				put(OpCode.load_1);
				break;
			case 2:
				put(OpCode.load_2);
				break;
			case 3:
				put(OpCode.load_3);
				break;
			default:
				put(OpCode.load);
				put(x.adr);
				break;
			}
			break;
		case Static:
			put(OpCode.getstatic);
			put2(x.adr);
			break;
		case Stack:
			break; // nothing to do (already loaded)
		case Fld:
			put(OpCode.getfield);
			put2(x.adr);
			break;
		case Elem:
			if (x.type == Tab.charType) {
				put(OpCode.baload);
			} else {
				put(OpCode.aload);
			}
			break;
		default:
			parser.error(Errors.Message.NO_VAL);
		}
		x.kind = Operand.Kind.Stack; // remember that value is now loaded
	}

	/**
	 * Special emthod for loading Constants
	 * 
	 * @param val
	 *            - constant value to be loaded
	 */
	private void loadConst(int val) {
		switch (val) {
		case 0: // val -1 to 5 are special constant, which need less opcode
			put(OpCode.const_0);
			break;
		case 1:
			put(OpCode.const_1);
			break;
		case 2:
			put(OpCode.const_2);
			break;
		case 3:
			put(OpCode.const_3);
			break;
		case 4:
			put(OpCode.const_4);
			break;
		case 5:
			put(OpCode.const_5);
			break;
		case -1:
			put(OpCode.const_m1);
			break;
		default: // we dont have a special constant
			put(OpCode.const_);
			put4(val);
			break;
		}
	}
}
