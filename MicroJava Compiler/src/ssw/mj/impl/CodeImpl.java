package ssw.mj.impl;

import ssw.mj.Errors;
import ssw.mj.Parser;
import ssw.mj.codegen.Code;
import ssw.mj.codegen.Label;
import ssw.mj.codegen.Operand;
import ssw.mj.symtab.Tab;

public final class CodeImpl extends Code {

	public CodeImpl(Parser p) {
		super(p);
	}

	/**
	 * generates unconditional jump instruction to lab
	 */
	void jump(Label lab) {
		put(OpCode.jmp);
		lab.put();
	}

	/**
	 * generates conditional jump instruction for true jump x represents the
	 * condition
	 */
	void tJump(Operand x) {
		setJump(x.op); // set correct OpCode
		x.tLabel.put();
	}

	/**
	 * generates conditional jump instruction for false jump x represents the
	 * condition
	 */
	void fJump(Operand x) {
		setJump(CompOp.invert(x.op)); // need to set inverted OpCode
		x.fLabel.put();
	}

	/**
	 * Helper Method which set the correct OpCode depending on the CompOp
	 * 
	 * @param comp
	 */
	private void setJump(CompOp comp) {
		switch (comp) {// set jeq, jne, jlt, jle, ...
		case eq:
			put(OpCode.jeq);
			break;
		case ne:
			put(OpCode.jne);
			break;
		case lt:
			put(OpCode.jlt);
			break;
		case le:
			put(OpCode.jle);
			break;
		case gt:
			put(OpCode.jgt);
			break;
		case ge:
			put(OpCode.jge);
			break;
		}
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
		store(x); // call assign operation for single operand
	}

	/**
	 * for pplus mminus, ... only one operand is needed
	 * 
	 * @param x
	 */
	void store(Operand x) {
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
	 * Method which does the doplication depending on the operand
	 * 
	 * @param op
	 */
	public void duplicate(Operand op) {
		if (op.kind == Operand.Kind.Fld) {
			put(OpCode.dup);
		} else if (op.kind == Operand.Kind.Elem) {
			put(OpCode.dup2);
		}
	}

	/**
	 * Method wich handels the increment part
	 * 
	 * @param op
	 * @param val
	 *            - can be zero or one
	 * 
	 */
	public void inc(Operand op, int val) {
		if (op.kind == Operand.Kind.Local) {
			put(OpCode.inc);
			put(op.adr);
			put(val);
		} else {
			Operand.Kind tempKind = op.kind; // we need to remember the type
			load(op); // type is now stack
			op.kind = tempKind; // reset type
			load(new Operand(val));
			put(OpCode.add);
			store(op);
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
